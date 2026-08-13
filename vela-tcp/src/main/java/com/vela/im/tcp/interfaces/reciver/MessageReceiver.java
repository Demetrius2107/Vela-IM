package com.vela.im.tcp.interfaces.reciver;

import com.alibaba.fastjson.JSONObject;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.codec.protocol.MessagePack;
import com.vela.im.tcp.interfaces.reciver.process.BaseProcess;
import com.vela.im.tcp.interfaces.reciver.process.ProcessFactory;
import com.vela.im.tcp.infrastructure.utils.MqFactory;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * <p>Title: MessageReceiver</p>
 * <p>Description: 消息接收器，监听 RabbitMQ 队列，消费逻辑层投递到网关层的消息并交由 Process 处理</p>
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
public class MessageReceiver {

    /** 当前 Broker ID */
    private static String brokerId;

    /**
     * 启动消息监听，消费 MessageService2Im 队列中的消息
     */
    private static void startReceiverMessage() {
        try {
            Channel channel = MqFactory.getChannel(ImConstants.RabbitMQ.MESSAGE_SERVICE_TO_IM + brokerId);
            channel.queueDeclare(ImConstants.RabbitMQ.MESSAGE_SERVICE_TO_IM + brokerId,
                    true, false, false, null);

            channel.queueBind(ImConstants.RabbitMQ.MESSAGE_SERVICE_TO_IM + brokerId,
                    ImConstants.RabbitMQ.MESSAGE_SERVICE_TO_IM, brokerId);

            channel.basicConsume(ImConstants.RabbitMQ.MESSAGE_SERVICE_TO_IM + brokerId, false,
                    new DefaultConsumer(channel) {

                        /**
                         * 处理收到的消息：反序列化后交由 ProcessFactory 处理
                         */
                        @Override
                        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                            try {
                                String msgStr = new String(body);
                                log.info(msgStr);
                                MessagePack messagePack = JSONObject.parseObject(msgStr, MessagePack.class);
                                BaseProcess messageProcess = ProcessFactory.getMessageProcess(messagePack.getCommand());
                                messageProcess.process(messagePack);
                                // 手动确认消息
                                channel.basicAck(envelope.getDeliveryTag(), false);
                            } catch (Exception e) {
                                e.printStackTrace();
                                channel.basicNack(envelope.getDeliveryTag(), false, false);
                            }
                        }
                    }
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 初始化消息接收器（无参）
     */
    public static void init() {
        startReceiverMessage();
    }

    /**
     * 初始化消息接收器，设置 BrokerId 后启动监听
     *
     * @param brokerId 当前服务 Broker ID
     */
    public static void init(String brokerId) {
        if (StringUtils.isBlank(MessageReceiver.brokerId)) {
            MessageReceiver.brokerId = brokerId;
        }
        startReceiverMessage();
    }
}