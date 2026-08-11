package com.vela.im.tcp.infrastructure.redis;

import com.vela.im.codec.config.BootStrapConfig;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

/**
 * <p>Title: SingleClientStrategy</p>
 * <p>Description: Redis单机模式客户端策略，根据配置构建单机 RedissonClient</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public class SingleClientStrategy {

    /**
     * 根据 Redis 单机配置创建 Redisson 客户端
     *
     * @param redisConfig Redis单机配置
     * @return RedissonClient 实例
     */
    public RedissonClient getRedissonClient(BootStrapConfig.RedisConfig redisConfig){
        Config config = new Config();
        String node = redisConfig.getSingle().getAddress();
        node = node.startsWith("redis:://") ? node: "redis://" + node;
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(node)
                .setDatabase(redisConfig.getDatabase())
                .setTimeout(redisConfig.getTimeout())
                .setConnectionMinimumIdleSize(redisConfig.getPoolMinIdle())
                .setConnectTimeout(redisConfig.getPoolConnTimeout())
                .setConnectionPoolSize(redisConfig.getPoolSize());

        if(StringUtils.isNotBlank(redisConfig.getPassword())){
            serverConfig.setPassword(redisConfig.getPassword());
        }
        StringCodec stringCodec = new StringCodec();
        config.setCodec(stringCodec);
        return Redisson.create(config);
    }

}