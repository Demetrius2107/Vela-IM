package com.vela.im.tcp.infrastructure.utils;

import com.vela.im.codec.config.BootStrapConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * <p>Title: MqFactory</p>
 * <p>Description: MQ连接工厂，管理 RabbitMQ ConnectionFactory 和 Channel 缓存，提供初始化与获取通道能力</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public class MqFactory {

    /** 连接工厂 */
    private static ConnectionFactory factory = null;

    /** 默认管道 */
    private static Channel defaultChannel;

    /** 通道缓存，key为通道名称，value为Channel实例 */
    private static ConcurrentHashMap<String, Channel> channelMap = new ConcurrentHashMap<>();

    /**
     * 建立与 RabbitMQ 的新连接
     *
     * @return Connection 连接实例
     * @throws IOException IO异常
     * @throws TimeoutException 连接超时异常
     */
    private static Connection getConnection() throws IOException, TimeoutException {
        Connection connection = factory.newConnection();
        return connection;
    }

    /**
     * 根据通道名称获取Channel，若不存在则新建并缓存
     *
     * @param channelName 通道名称
     * @return Channel 通道实例
     * @throws IOException IO异常
     * @throws TimeoutException 连接超时异常
     */
    public static Channel getChannel(String channelName) throws IOException, TimeoutException {
        Channel channel = channelMap.get(channelName);
        if (channel == null) {
            channel = getConnection().createChannel();
            channelMap.put(channelName, channel);
        }
        return channel;
    }

    /**
     * 初始化 MQ 连接工厂，配置 RabbitMQ 连接参数
     *
     * @param rabbitMqConfig RabbitMQ配置
     */
    public static void init(BootStrapConfig.RabbitMqConfig rabbitMqConfig) {
        if (factory == null) {
            factory = new ConnectionFactory();
            factory.setHost(rabbitMqConfig.getHost());
            factory.setPort(rabbitMqConfig.getPort());
            factory.setUsername(rabbitMqConfig.getUserName());
            factory.setPassword(rabbitMqConfig.getPassword());
            factory.setVirtualHost(rabbitMqConfig.getVirtualHost());
        }
    }

}