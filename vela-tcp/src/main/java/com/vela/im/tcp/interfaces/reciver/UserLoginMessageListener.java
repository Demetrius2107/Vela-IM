package com.vela.im.tcp.interfaces.reciver;

import com.alibaba.fastjson.JSONObject;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.DeviceMultiLoginEnum;
import com.vela.im.shared.types.enums.command.SystemCommand;
import com.vela.im.shared.types.UserClientDto;
import com.vela.im.codec.protocol.MessagePack;
import com.vela.im.tcp.infrastructure.redis.RedisManager;
import com.vela.im.tcp.infrastructure.utils.SessionSocketHolder;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * <p>Title: UserLoginMessageListener</p>
 * <p>Description: 用户登录消息监听器，订阅 Redis 登录频道，根据登录模式处理多端互踢逻辑</p>
 * <p>项目名称: Vela</p>
 * <p>多端同步策略：
 * 1 单端登录：踢掉除本 clientType+imei 之外的所有设备
 * 2 双端登录：允许 PC/Mobile 一端 + Web 端，踢掉同端其他设备
 * 3 三端登录：允许手机+PC+Web，踢掉同端其他 imei（Web 除外）
 * 4 不做任何处理</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Slf4j
public class UserLoginMessageListener {

    /** 登录模式：1单端 2双端 3三端 4不处理 */
    private final Integer loginModel;

    /**
     * 构造用户登录监听器
     *
     * @param loginModel 登录模式
     */
    public UserLoginMessageListener(Integer loginModel){
        this.loginModel = loginModel;
    }

    /**
     * 订阅 Redis 用户登录频道，监听用户上线事件，根据 loginModel 执行多端互踢策略
     */
    public void listenerUserLogin(){
        RTopic topic = RedisManager.getRedissonClient().getTopic(ImConstants.Redis.USER_LOGIN_CHANNEL);

        topic.addListener(String.class, new MessageListener<String>() {
            @Override
            public void onMessage(CharSequence charSequence, String msg) {
                log.info("收到用户上线通知：" + msg);
                UserClientDto dto = JSONObject.parseObject(msg, UserClientDto.class);

                List<NioSocketChannel> nioSocketChannels = SessionSocketHolder.get(dto.getAppId(), dto.getUserId());

                for (NioSocketChannel nioSocketChannel : nioSocketChannels) {
                    // 单端登录：踢掉除本 clientType+imei 外所有设备
                    if(loginModel == DeviceMultiLoginEnum.ONE.getLoginMode()){
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.IMEI)).get();

                        if(!(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }
                    // 双端登录：Web 端不做处理，PC/Mobile 同端互踢
                    else if(loginModel == DeviceMultiLoginEnum.TWO.getLoginMode()){
                        if(dto.getClientType() == com.vela.im.shared.types.ClientType.WEB.getCode()){
                            continue;
                        }
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE)).get();

                        if (clientType == com.vela.im.shared.types.ClientType.WEB.getCode()){
                            continue;
                        }

                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.IMEI)).get();
                        if(!(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }
                    // 三端登录：手机/PC 同端互踢，Web 不做处理
                    else if(loginModel == DeviceMultiLoginEnum.THREE.getLoginMode()){
                        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE)).get();
                        String imei = (String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.IMEI)).get();
                        if(dto.getClientType() == com.vela.im.shared.types.ClientType.WEB.getCode()){
                            continue;
                        }

                        // iOS 和 Android 属于同端
                        Boolean isSameClient = false;
                        if((clientType == com.vela.im.shared.types.ClientType.IOS.getCode() ||
                                clientType == com.vela.im.shared.types.ClientType.ANDROID.getCode()) &&
                                (dto.getClientType() == com.vela.im.shared.types.ClientType.IOS.getCode() ||
                                        dto.getClientType() == com.vela.im.shared.types.ClientType.ANDROID.getCode())){
                            isSameClient = true;
                        }

                        if((clientType == com.vela.im.shared.types.ClientType.MAC.getCode() ||
                                clientType == com.vela.im.shared.types.ClientType.WINDOWS.getCode()) &&
                                (dto.getClientType() == com.vela.im.shared.types.ClientType.MAC.getCode() ||
                                        dto.getClientType() == com.vela.im.shared.types.ClientType.WINDOWS.getCode())){
                            isSameClient = true;
                        }

                        if(isSameClient && !(clientType + ":" + imei).equals(dto.getClientType()+":"+dto.getImei())){
                            MessagePack<Object> pack = new MessagePack<>();
                            pack.setToId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setUserId((String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get());
                            pack.setCommand(SystemCommand.MUTUALLOGIN.getCommand());
                            nioSocketChannel.writeAndFlush(pack);
                        }
                    }
                }
            }
        });
    }

}