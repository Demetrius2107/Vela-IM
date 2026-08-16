package com.vela.im.service.conversation.domain.service;

import com.vela.im.service.conversation.application.dto.UpdateConversationReq;
import com.vela.im.service.conversation.infrastructure.persistence.mapper.ImConversationSetMapper;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.service.common.utils.WriteUserSeq;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.types.enums.ConversationErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * <p>Title: ConversationServiceTest</p>
 * <p>Description: 会话域服务单元测试：会话 ID 转换、更新参数校验等不依赖中间件的业务逻辑。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
class ConversationServiceTest {

    private final ConversationService conversationService = new ConversationService(
            mock(ImConversationSetMapper.class),
            mock(MessageProducer.class),
            mock(ImServerProperties.class),
            mock(RedisSeq.class),
            mock(WriteUserSeq.class));

    /**
     * 会话 ID 由（类型, 发送方, 接收方）拼接生成
     */
    @Test
    void convertConversationId() {
        assertEquals("0_lld_lld2", conversationService.convertConversationId(0, "lld", "lld2"));
        assertEquals("1_group1_member", conversationService.convertConversationId(1, "group1", "member"));
    }

    /**
     * 置顶与免打扰同时为空时必须返回参数错误（业务校验）
     */
    @Test
    void updateConversationRejectsEmptyParams() {
        UpdateConversationReq req = new UpdateConversationReq();
        req.setConversationId("0_lld_lld2");
        req.setAppId(10000);
        // isTop 与 isMute 均为空
        Result result = conversationService.updateConversation(req);
        assertFalse(result.isOk());
        assertEquals(ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR.getCode(), result.getCode());
    }

    /**
     * 置顶或免打扰任一非空即通过参数校验（后续落库由 Mapper 承担，此处验证不抛参数错误）
     */
    @Test
    void updateConversationAcceptsSingleParam() {
        UpdateConversationReq req = new UpdateConversationReq();
        req.setConversationId("0_lld_lld2");
        req.setAppId(10000);
        req.setIsTop(1);
        req.setFromId("lld");
        Result result = conversationService.updateConversation(req);
        // 参数合法：不应返回参数错误（实体不存在时返回 ok，不抛异常）
        assertTrue(result.isOk() || result.getCode() != ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR.getCode());
    }
}
