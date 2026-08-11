package com.vela.im.tcp.interfaces.publish;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.command.CommandType;
import com.vela.im.codec.protocol.Message;
import com.vela.im.codec.protocol.MessageHeader;
import com.vela.im.tcp.infrastructure.utils.MqFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.vela.im.shared.trace.TraceIdContext;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Title: MqMessageProducer</p>
 * <p>Description: MQ 消息生产者，网关层将消息投递到逻辑层对应的 RabbitMQ 队列</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Slf4j
public class MqMessageProducer {

    /**
     * 发送 Message 消息到 MQ，根据 command 类型路由到对应的服务队列
     * <p>MQ 发布失败时自动重试 3 次（指数退避），重试耗尽仍失败则记录错误但不抛出异常（不阻断网关）。</p>
     *
     * @param message 消息体
     * @param command 指令类型
     */
    public static void sendMessage(Message message ,Integer command){
        String channelName = resolveChannelName(command);
        if (channelName == null || channelName.isEmpty()) {
            log.warn("Unknown command type for MQ routing, command={}", command);
            return;
        }

        byte[] body;
        try {
            JSONObject o = (JSONObject) JSON.toJSON(message.getMessagePackage());
            o.put("command",command);
            o.put("clientType",message.getMessageHeader().getClientType());
            o.put("imei",message.getMessageHeader().getImei());
            o.put("appId",message.getMessageHeader().getAppId());
            body = o.toJSONString().getBytes();
        } catch (Exception e) {
            log.error("Failed to serialize MQ message body, command={}, error={}", command, e.getMessage());
            return;
        }

        publishWithRetry(channelName, body, "sendMessage-" + command, 3, 100L);
    }

    /**
     * 发送消息体与消息头到 MQ，根据 command 类型路由到对应的服务队列
     * <p>MQ 发布失败时自动重试 3 次（指数退避），重试耗尽仍失败则记录错误但不抛出异常（不阻断网关）。</p>
     *
     * @param message 消息体对象
     * @param header  消息头
     * @param command 指令类型
     */
    public static void sendMessage(Object message, MessageHeader header, Integer command){
        String channelName = resolveChannelName(command);
        if (channelName == null || channelName.isEmpty()) {
            log.warn("Unknown command type for MQ routing, command={}", command);
            return;
        }

        byte[] body;
        try {
            JSONObject o = (JSONObject) JSON.toJSON(message);
            o.put("command",command);
            o.put("clientType",header.getClientType());
            o.put("imei",header.getImei());
            o.put("appId",header.getAppId());
            body = o.toJSONString().getBytes();
        } catch (Exception e) {
            log.error("Failed to serialize MQ message body, command={}, error={}", command, e.getMessage());
            return;
        }

        publishWithRetry(channelName, body, "sendMessage2-" + command, 3, 100L);
    }

    /**
     * Publish message to MQ with retry (exponential backoff).
     *
     * @param channelName  MQ channel/queue name
     * @param body         serialized message body
     * @param operationName name for logging
     * @param maxRetries   max retry attempts
     * @param baseDelayMs  initial delay in ms
     */
    private static void publishWithRetry(String channelName, byte[] body, String operationName,
                                          int maxRetries, long baseDelayMs) {
        for (int i = 0; i < maxRetries; i++) {
            Channel channel = null;
            try {
                channel = MqFactory.getChannel(channelName);
                AMQP.BasicProperties props = buildTraceProperties();
                channel.basicPublish(channelName, "", props, body);
                return; // Success
            } catch (Exception e) {
                log.warn("MQ publish failed (attempt {}/{}), channel={}, error={}",
                        i + 1, maxRetries, channelName, e.getMessage());
                if (i == maxRetries - 1) {
                    log.error("MQ publish retries exhausted after {} attempts, channel={}, operation={}",
                            maxRetries, channelName, operationName);
                    // Do NOT throw — gateway should not crash due to MQ unavailability
                    return;
                }
                try {
                    long delay = baseDelayMs * (1L << i);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("MQ publish retry interrupted, channel={}", channelName);
                    return;
                }
            }
        }
    }

    /**
     * Resolve MQ channel/queue name from command type.
     *
     * @param command the command code
     * @return channel name, or empty if unknown
     */
    private static String resolveChannelName(Integer command) {
        String com = command.toString();
        String commandSub = com.substring(0, 1);
        CommandType commandType = CommandType.getCommandType(commandSub);
        if (commandType == CommandType.MESSAGE) {
            return ImConstants.RabbitMQ.IM_TO_MESSAGE_SERVICE;
        } else if (commandType == CommandType.GROUP) {
            return ImConstants.RabbitMQ.IM_TO_GROUP_SERVICE;
        } else if (commandType == CommandType.FRIEND) {
            return ImConstants.RabbitMQ.IM_TO_FRIENDSHIP_SERVICE;
        } else if (commandType == CommandType.USER) {
            return ImConstants.RabbitMQ.IM_TO_USER_SERVICE;
        }
        return "";
    }

    /**
     * 构建携带 TraceId 的 AMQP BasicProperties
     *
     * @return AMQP 消息属性
     */
    private static AMQP.BasicProperties buildTraceProperties() {
        Map<String, Object> headers = new HashMap<>(2);
        String traceId = TraceIdContext.get();
        if (traceId != null && !traceId.isEmpty()) {
            headers.put(ImConstants.TraceId.MQ_HEADER_NAME, traceId);
        }
        return new AMQP.BasicProperties.Builder()
                .headers(headers)
                .build();
    }

}