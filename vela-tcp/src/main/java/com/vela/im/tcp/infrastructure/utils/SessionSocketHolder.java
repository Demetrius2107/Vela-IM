package com.vela.im.tcp.infrastructure.utils;

import com.alibaba.fastjson.JSONObject;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ImConnectStatusEnum;
import com.vela.im.shared.types.enums.command.UserEventCommand;
import com.vela.im.shared.types.UserClientDto;
import com.vela.im.shared.types.UserSession;
import com.vela.im.codec.pack.user.UserStatusChangeNotifyPack;
import com.vela.im.codec.protocol.MessageHeader;
import com.vela.im.tcp.interfaces.publish.MqMessageProducer;
import com.vela.im.tcp.infrastructure.redis.RedisManager;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Title: SessionSocketHolder</p>
 * <p>Description: 用户 Session 与 Netty Channel 的持有者，管理客户端连接映射关系及离线/下线处理</p>
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
public class SessionSocketHolder {

    /** 用户客户端与 Channel 的映射关系 */
    private static final Map<UserClientDto, NioSocketChannel> CHANNELS = new ConcurrentHashMap<>();

    /**
     * 存储用户 Channel 映射
     *
     * @param appId      应用ID
     * @param userId     用户ID
     * @param clientType 客户端类型
     * @param imei       设备IMEI
     * @param channel    Netty 通道
     */
    public static void put(Integer appId, String userId, Integer clientType, String imei
            , NioSocketChannel channel) {
        UserClientDto dto = new UserClientDto();
        dto.setImei(imei);
        dto.setAppId(appId);
        dto.setClientType(clientType);
        dto.setUserId(userId);
        CHANNELS.put(dto, channel);
    }

    /**
     * 根据用户标识获取指定端的 Channel
     *
     * @param appId      应用ID
     * @param userId     用户ID
     * @param clientType 客户端类型
     * @param imei       设备IMEI
     * @return NioSocketChannel 或 null
     */
    public static NioSocketChannel get(Integer appId,String userId,
                                       Integer clientType,String imei){
        UserClientDto dto = new UserClientDto();
        dto.setImei(imei);
        dto.setAppId(appId);
        dto.setClientType(clientType);
        dto.setUserId(userId);
        return CHANNELS.get(dto);
    }

    /**
     * 获取某个用户的所有端 Channel 列表
     *
     * @param appId 应用ID
     * @param id    用户ID
     * @return 该用户所有在线的 Channel 列表
     */
    public static List<NioSocketChannel> get(Integer appId , String id) {
        Set<UserClientDto> channelInfos = CHANNELS.keySet();
        List<NioSocketChannel> channels = new ArrayList<>();

        channelInfos.forEach(channel ->{
            if(channel.getAppId().equals(appId) && id.equals(channel.getUserId())){
                channels.add(CHANNELS.get(channel));
            }
        });

        return channels;
    }

    /**
     * 移除指定端的 Channel 映射
     *
     * @param appId      应用ID
     * @param userId     用户ID
     * @param clientType 客户端类型
     * @param imei       设备IMEI
     */
    public static void remove(Integer appId,String userId,Integer clientType,String imei){
        UserClientDto dto = new UserClientDto();
        dto.setAppId(appId);
        dto.setImei(imei);
        dto.setClientType(clientType);
        dto.setUserId(userId);
        CHANNELS.remove(dto);
    }

    /**
     * 根据 Channel 实例移除映射
     *
     * @param channel 要移除的 Netty 通道
     */
    public static void remove(NioSocketChannel channel){
        CHANNELS.entrySet().stream().filter(entity -> entity.getValue() == channel)
                .forEach(entry -> CHANNELS.remove(entry.getKey()));
    }

    /**
     * 删除用户 Session：清理本地 Channel 映射、Redis 缓存，广播下线通知并关闭连接
     *
     * @param nioSocketChannel 用户 Netty 通道
     */
    public static void removeUserSession(NioSocketChannel nioSocketChannel){
        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.APP_ID)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE)).get();
        String imei = (String) nioSocketChannel
                .attr(AttributeKey.valueOf(ImConstants.IMEI)).get();

        SessionSocketHolder.remove(appId,userId,clientType,imei);
        // Redis 清理失败不阻断下线通知（尽力而为清理）
        try {
            RedissonClient redissonClient = RedisManager.getRedissonClient();
            RMap<Object, Object> map = redissonClient.getMap(appId +
                    ImConstants.Redis.USER_SESSION_PREFIX + userId);
            map.remove(clientType+":"+imei);
        } catch (Exception e) {
            log.error("Failed to remove session from Redis, userId={}, imei={}", userId, imei, e);
        }

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack,messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

        nioSocketChannel.close();
    }

    /**
     * 离线用户 Session：更新 Redis 中的连接状态为离线，广播下线通知并关闭连接
     *
     * @param nioSocketChannel 用户 Netty 通道
     */
    public static void offlineUserSession(NioSocketChannel nioSocketChannel){
        String userId = (String) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.USER_ID)).get();
        Integer appId = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.APP_ID)).get();
        Integer clientType = (Integer) nioSocketChannel.attr(AttributeKey.valueOf(ImConstants.CLIENT_TYPE)).get();
        String imei = (String) nioSocketChannel
                .attr(AttributeKey.valueOf(ImConstants.IMEI)).get();
        SessionSocketHolder.remove(appId,userId,clientType,imei);
        // Redis 状态更新失败不阻断下线通知（尽力而为清理）
        try {
            RedissonClient redissonClient = RedisManager.getRedissonClient();
            RMap<String, String> map = redissonClient.getMap(appId +
                    ImConstants.Redis.USER_SESSION_PREFIX + userId);
            String sessionStr = map.get(clientType.toString()+":" + imei);

            if(!StringUtils.isBlank(sessionStr)){
                UserSession userSession = JSONObject.parseObject(sessionStr, UserSession.class);
                userSession.setConnectState(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
                map.put(clientType.toString()+":"+imei, JSONObject.toJSONString(userSession));
            }
        } catch (Exception e) {
            log.error("Failed to update offline status in Redis, userId={}, imei={}", userId, imei, e);
        }

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.setAppId(appId);
        messageHeader.setImei(imei);
        messageHeader.setClientType(clientType);

        UserStatusChangeNotifyPack userStatusChangeNotifyPack = new UserStatusChangeNotifyPack();
        userStatusChangeNotifyPack.setAppId(appId);
        userStatusChangeNotifyPack.setUserId(userId);
        userStatusChangeNotifyPack.setStatus(ImConnectStatusEnum.OFFLINE_STATUS.getCode());
        MqMessageProducer.sendMessage(userStatusChangeNotifyPack,messageHeader, UserEventCommand.USER_ONLINE_STATUS_CHANGE.getCommand());

        nioSocketChannel.close();
    }

}