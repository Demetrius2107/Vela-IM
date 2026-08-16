package com.vela.im.service.user.domain.service;

import com.vela.im.service.user.application.dto.UserStatusChangeNotifyContent;
import com.vela.im.service.user.application.dto.req.SetUserCustomerStatusReq;
import com.vela.im.service.user.application.dto.req.SubscribeUserOnlineStatusReq;
import com.vela.im.service.user.infrastructure.service.ImUserStatusServiceImpl;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.shared.constants.ImConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * <p>Title: ImUserStatusServiceTest</p>
 * <p>Description: 用户在线状态服务单元测试：订阅索引维护、自定义状态写入、上下线通知推送。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
class ImUserStatusServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private MessageProducer messageProducer;
    private ImUserStatusService userStatusService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        messageProducer = mock(MessageProducer.class);
        userStatusService = new ImUserStatusServiceImpl(stringRedisTemplate, messageProducer);
    }

    /**
     * 订阅时写入正向索引（订阅者→被订阅者）与反向索引（被订阅者→订阅者）
     */
    @Test
    void subscribeWritesBothIndexes() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);

        SubscribeUserOnlineStatusReq req = new SubscribeUserOnlineStatusReq();
        req.setAppId(10000);
        req.setOperater("subscriberA");
        req.setSubUserId(Arrays.asList("targetB", "targetC"));

        userStatusService.subscribeUserOnlineStatus(req);

        // 正向索引：订阅者集合被重写
        verify(stringRedisTemplate).delete("10000" + ImConstants.Redis.USER_SUBSCRIBE_PREFIX + "subscriberA");
        verify(setOps).add("10000" + ImConstants.Redis.USER_SUBSCRIBE_PREFIX + "subscriberA", "targetB", "targetC");
        // 反向索引：每个被订阅者记录订阅者
        verify(setOps).add("10000" + ImConstants.Redis.USER_SUBSCRIBED_PREFIX + "targetB", "subscriberA");
        verify(setOps).add("10000" + ImConstants.Redis.USER_SUBSCRIBED_PREFIX + "targetC", "subscriberA");
    }

    /**
     * 自定义状态按 customStatus|customText 格式覆盖写入
     */
    @Test
    void setCustomStatusWritesFormattedValue() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        SetUserCustomerStatusReq req = new SetUserCustomerStatusReq();
        req.setAppId(10000);
        req.setUserId("userA");
        req.setCustomStatus(2);
        req.setCustomText("离开");

        userStatusService.setUserCustomerStatus(req);

        verify(valueOps).set("10000" + ImConstants.Redis.USER_CUSTOM_STATUS_PREFIX + "userA", "2|离开");
    }

    /**
     * 用户上线后，向反向索引中的订阅者推送状态变更通知
     */
    @Test
    void onlineNotifyPushesToSubscribers() {
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenReturn(Collections.singleton("subscriberA"));
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        UserStatusChangeNotifyContent content = new UserStatusChangeNotifyContent();
        content.setAppId(10000);
        content.setUserId("userA");
        content.setStatus(1);

        userStatusService.processUserOnlineStatusNotify(content);

        // 上线标记写入 + 通知订阅者
        verify(valueOps).set(anyString(), eq("online"), anyLong(), any());
        verify(messageProducer).sendToUser(eq("subscriberA"), any(), eq(content), eq(10000));
    }
}
