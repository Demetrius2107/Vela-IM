package com.vela.im.tcp.interfaces.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ImConnectStatusEnum;
import com.vela.im.shared.types.enums.MessageErrorCode;
import com.vela.im.shared.types.enums.command.GroupEventCommand;
import com.vela.im.shared.types.enums.command.MessageCommand;
import com.vela.im.shared.types.enums.command.SystemCommand;
import com.vela.im.shared.types.enums.command.UserEventCommand;
import com.vela.im.tcp.interfaces.fegin.FeignMessageService;
import com.vela.im.tcp.interfaces.fegin.TraceIdFeignInterceptor;
import com.vela.im.shared.types.UserClientDto;
import com.vela.im.shared.types.UserSession;
import com.vela.im.shared.types.message.CheckSendMessageReq;
import com.vela.im.codec.pack.LoginPack;
import com.vela.im.codec.pack.message.ChatMessageAck;
import com.vela.im.codec.pack.user.LoginAckPack;
import com.vela.im.codec.pack.user.UserStatusChangeNotifyPack;
import com.vela.im.codec.protocol.Message;
import com.vela.im.codec.protocol.MessagePack;
import com.vela.im.tcp.interfaces.publish.MqMessageProducer;
import com.vela.im.tcp.infrastructure.redis.RedisManager;
import com.vela.im.shared.trace.TraceIdContext;
import com.vela.im.tcp.infrastructure.utils.SessionSocketHolder;
import com.vela.im.tcp.interfaces.utils.ChannelWriteUtil;
import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import java.net.InetAddress;

/**
 * <p>Title: NettyServerHandler</p>
 * <p>Description: Netty 服务端消息处理器，处理登录/登出、心跳、私聊/群聊消息的路由与分发</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 * <p>
 * Copyright © 2026 wanqiu All rights reserved
 * @since 1.1
 */
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<Message> {

    /**
     * 当前 Broker ID
     */
    private final Integer brokerId;

    /**
     * 逻辑层服务 URL
     */
    private final String logicUrl;

    /**
     * Feign 远程调用服务
     */
    private final FeignMessageService feignMessageService;

    /**
     * 构造 Netty 服务端处理器
     *
     * @param brokerId 当前 Broker ID
     * @param logicUrl 逻辑层服务 URL
     */
    public NettyServerHandler(Integer brokerId, String logicUrl) {
        this.brokerId = brokerId;
        this.logicUrl = logicUrl;
        feignMessageService = Feign.builder()
                .encoder(new JacksonEncoder())
                .decoder(new JacksonDecoder())
                .options(new Request.Options(1000, 3500))
                .requestInterceptor(new TraceIdFeignInterceptor())
                .target(FeignMessageService.class, logicUrl);
    }

    /**
     * 处理客户端消息：登录、登出、心跳、私聊/群聊消息等
     *
     * @param ctx 通道处理器上下文
     * @param msg 消息体
     * @throws Exception 处理异常
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        // 每个请求进入网关时生成/复用 TraceId，并绑定到当前线程 MDC
        TraceIdContext.set(null);
        try {
            doChannelRead0(ctx, msg);
        } finally {
            // 请求处理完成后清理 MDC，防止线程复用时上下文污染
            TraceIdContext.clear();
        }
    }

    private void doChannelRead0(ChannelHandlerContext ctx, Message msg) throws Exception {
        Integer command = msg.getMessageHeader().getCommand();

        // 登录command
        if (command == SystemCommand.LOGIN.getCommand()) {
            LoginPack loginPack = JSON.parseObject(JSONObject.toJSONString(msg.getMessagePackage()),
                    new TypeReference<LoginPack>() {
                    }.getType());
            /** 登陆事件 **/
            String userId = loginPack.getUserId();
            /** 为channel设置用户id **/
            ctx.channel().attr(AttributeKey.valueOf(ImConstants.USER_ID)).set(userId);
            String clientImei = msg.getMessageHeader().getClientType() + ":" + msg.getMessageHeader().getImei();
            /** 为channel设置client和imel **/
            ctx.channel().attr(AttributeKey.valueOf(ImConstants.CLIENT_IMEI)).set(clientImei);
            /** 为channel设置appId **/
            ctx.channel().attr(AttributeKey.valueOf(ImConstants.APP_ID)).set(msg.getMessageHeader().getAppId());
            /** 为channel设置ClientType **/
            ctx.channel().attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE))
                    .set(msg.getMessageHeader().getClientType());
            /** 为channel设置Imei **/
            ctx.channel().attr(AttributeKey.valueOf(ImConstants.IMEI))
                    .set(msg.getMessageHeader().getImei());

            UserSession userSession = new UserSession();
            userSession.setAppId(msg.getMessageHeader().getAppId());
            userSession.setClientType(msg.getMessageHeader().getClientType());
            userSession.setUserId(loginPack.getUserId());
            userSession.setConnectState(ImConnectStatusEnum.ONLINE_STATUS.getCode());
            userSession.setBrokerId(brokerId);
            userSession.setImei(msg.getMessageHeader().getImei());
            try {
                InetAddress localHost = InetAddress.getLocalHost();
                userSession.setBrokerHost(localHost.getHostAddress());
            } catch (Exception e) {
                log.error("Failed to get local host address", e);
            }

            RedissonClient redissonClient = RedisManager.getRedissonClient();

            RMap<String, String> map = redissonClient.getMap(msg.getMessageHeader().getAppId()
                    + ImConstants.Redis.USER_SESSION_PREFIX + loginPack.getUserId());
            map.put(msg.getMessageHeader().getClientType()
                            + ":"
                            + msg.getMessageHeader().getImei()
                    , JSONObject.toJSONString(userSession));
            // Session 存入Redis
            SessionSocketHolder
                    .put(msg.getMessageHeader().getAppId(), loginPack.getUserId(),
                            msg.getMessageHeader().getClientType(),
                            msg.getMessageHeader().getImei(), (NioSocketChannel) ctx.channel());

            // 广播用户上线通知
            UserClientDto dto = new UserClientDto();
            dto.setImei(msg.getMessageHeader().getImei());
            dto.setUserId(loginPack.getUserId());
            dto.setClientType(msg.getMessageHeader().getClientType());
            dto.setAppId(msg.getMessageHeader().getAppId());
            RTopic topic = redissonClient.getTopic(ImConstants.Redis.USER_LOGIN_CHANNEL);
            topic.publish(JSONObject.toJSONString(dto));

            UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
            userStatusChangeNotifyPack.setAppId(msg.getMessageHeader().getAppId());
            userStatusChangeNotifyPack.setUserId(loginPack.getUserId());
            userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.ONLINE_STATUS.getCode());
            MqMessageProducer.sendMessage(userStatusChangeNotifyPack, msg.getMessageHeader(), UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

            // 返回登录成功响应
            MessagePack<LoginAckPack> loginSuccess = new MessagePack<>();
            LoginAckPack loginAckPack = new LoginAckPack();
            loginAckPack.setUserId(loginPack.getUserId());
            loginSuccess.setCommand(SystemCommand.LOGINACK.getCommand());
            loginSuccess.setData(loginAckPack);
            loginSuccess.setImei(msg.getMessageHeader().getImei());
            loginSuccess.setAppId(msg.getMessageHeader().getAppId());
            ChannelWriteUtil.safeWrite(ctx.channel(), loginSuccess, "login-ack");
        }
        // 登出command
        else if (command == SystemCommand.LOGOUT.getCommand()) {
            SessionSocketHolder.removeUserSession((NioSocketChannel) ctx.channel());
        }
        // ping Command
        else if (command == SystemCommand.PING.getCommand()) {
            ctx.channel()
                    .attr(AttributeKey.valueOf(ImConstants.READ_TIME)).set(System.currentTimeMillis());
        } else if (command == MessageCommand.MSG_P2P.getCommand()
                || command == GroupEventCommand.MSG_GROUP.getCommand()) {
            // 私聊/群聊消息：校验发送权限后投递到 MQ
            try {
                String toId = "";
                CheckSendMessageReq req = new CheckSendMessageReq();
                req.setAppId(msg.getMessageHeader().getAppId());
                req.setCommand(msg.getMessageHeader().getCommand());
                JSONObject jsonObject = JSON.parseObject(JSONObject.toJSONString(msg.getMessagePackage()));
                String fromId = jsonObject.getString("fromId");
                if (command == MessageCommand.MSG_P2P.getCommand()) {
                    toId = jsonObject.getString("toId");
                } else {
                    toId = jsonObject.getString("groupId");
                }
                req.setToId(toId);
                req.setFromId(fromId);

                Result responseVO = null;
                // Retry Feign call up to 2 times with backoff on transient failures
                for (int retry = 0; retry < 2; retry++) {
                    try {
                        responseVO = feignMessageService.checkSendMessage(req);
                        break;
                    } catch (Exception e) {
                        log.warn("Feign checkSendMessage failed (attempt {}/2), fromId={}, error={}",
                                retry + 1, fromId, e.getMessage());
                        if (retry == 1) {
                            // Last attempt failed — produce a server-error ACK
                            responseVO = Result.fail(MessageErrorCode.MESSAGE_SEND_FAILED);
                            break;
                        }
                        try {
                            Thread.sleep(200L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            responseVO = Result.fail(MessageErrorCode.MESSAGE_SEND_FAILED);
                            break;
                        }
                    }
                }
                if (responseVO.isOk()) {
                    MqMessageProducer.sendMessage(msg, command);
                } else {
                    Integer ackCommand = 0;
                    if (command == MessageCommand.MSG_P2P.getCommand()) {
                        ackCommand = MessageCommand.MSG_ACK.getCommand();
                    } else {
                        ackCommand = GroupEventCommand.GROUP_MSG_ACK.getCommand();
                    }

                    ChatMessageAck chatMessageAck = new ChatMessageAck(jsonObject.getString("messageId"));
                    responseVO.setData(chatMessageAck);
                    MessagePack<Result> ack = new MessagePack<>();
                    ack.setData(responseVO);
                    ack.setCommand(ackCommand);
                    ChannelWriteUtil.safeWrite(ctx.channel(), ack, "msg-ack");
                }
            } catch (Exception e) {
                log.error("Failed to process message", e);
            }
        } else {
            MqMessageProducer.sendMessage(msg, command);
        }
    }

    /**
     * 通道不活动时，执行用户离线处理
     *
     * @param ctx 通道处理器上下文
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        SessionSocketHolder.offlineUserSession((NioSocketChannel) ctx.channel());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("NettyServerHandler exception", cause);
        super.exceptionCaught(ctx, cause);
    }
}