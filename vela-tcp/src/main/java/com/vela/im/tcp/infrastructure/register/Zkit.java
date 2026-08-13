package com.vela.im.tcp.infrastructure.register;

import com.vela.im.shared.constants.ImConstants;
import org.I0Itec.zkclient.ZkClient;

/**
 * <p>Title: Zkit</p>
 * <p>Description: ZooKeeper 操作工具，封装 ZK 节点创建与路径校验</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public class Zkit {

    /** ZK 客户端 */
    private ZkClient zkClient;

    /**
     * 构造 ZK 工具
     *
     * @param zkClient ZK 客户端
     */
    public Zkit(ZkClient zkClient) {
        this.zkClient = zkClient;
    }

    /**
     * 创建根节点路径：im-coreRoot 及其子路径 tcp/web
     */
    public void createRootNode() {
        boolean exists = zkClient.exists(ImConstants.VELA_ZK_ROOT);
        if (!exists) {
            zkClient.createPersistent(ImConstants.VELA_ZK_ROOT);
        }

        boolean tcpExists = zkClient.exists(ImConstants.VELA_ZK_ROOT + ImConstants.VELA_ZK_ROOT_TCP);
        if (!tcpExists) {
            zkClient.createPersistent(ImConstants.VELA_ZK_ROOT + ImConstants.VELA_ZK_ROOT_TCP);
        }

        boolean webExists = zkClient.exists(ImConstants.VELA_ZK_ROOT + ImConstants.VELA_ZK_ROOT_WEB);
        if (!webExists) {
            zkClient.createPersistent(ImConstants.VELA_ZK_ROOT + ImConstants.VELA_ZK_ROOT_WEB);
        }
    }

    /**
     * 创建指定路径节点（持久节点），若已存在则跳过
     *
     * @param path 节点路径（如 /im-coreRoot/tcp/ip:port）
     */
    public void createNode(String path) {
        if (!zkClient.exists(path)) {
            zkClient.createPersistent(path);
        }
    }
}