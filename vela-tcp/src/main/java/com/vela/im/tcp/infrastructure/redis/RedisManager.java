package com.vela.im.tcp.infrastructure.redis;

import com.vela.im.codec.config.BootStrapConfig;
import com.vela.im.tcp.interfaces.reciver.UserLoginMessageListener;
import org.redisson.api.RedissonClient;

/**
 * <p>Title: RedisManager</p>
 * <p>Description: Redis 管理器，封装 Redisson 客户端初始化与用户登录监听器注册</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public class RedisManager {

    /** Redisson 客户端 */
    private static RedissonClient redissonClient;

    /** 登录模式：1单端登录 2双端登录 3三端登录 4不处理 */
    private static Integer loginModel;

    /**
     * 初始化 Redis 连接与用户登录监听器
     *
     * @param config 启动配置
     */
    public static void init(BootStrapConfig config){
        loginModel = config.getServerConfig().getLoginModel();
        SingleClientStrategy singleClientStrategy = new SingleClientStrategy();
        redissonClient = singleClientStrategy.getRedissonClient(config.getServerConfig().getRedisConfig());
        UserLoginMessageListener userLoginMessageListener = new UserLoginMessageListener(loginModel);
        userLoginMessageListener.listenerUserLogin();
    }

    /**
     * 获取 Redisson 客户端实例
     *
     * @return RedissonClient 实例
     */
    public static RedissonClient getRedissonClient(){
        return redissonClient;
    }

}