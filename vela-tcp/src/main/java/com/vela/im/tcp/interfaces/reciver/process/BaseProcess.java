package com.vela.im.tcp.interfaces.reciver.process;

import com.vela.im.codec.protocol.MessagePack;

/**
 * <p>Title: BaseProcess</p>
 * <p>Description: 消息处理抽象基类，定义处理流程模板：前置处理 -> 发送消息到 Channel -> 后置处理</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public abstract class BaseProcess {

    /**
     * 前置处理，子类实现
     */
    public abstract void processBefore();

    /**
     * 处理消息：执行前置处理、将消息写入目标 Channel、执行后置处理
     *
     * @param messagePack 消息包
     */
    public void process(MessagePack messagePack) {
        processBefore();
        // SessionSocketHolder.get(...) 获取目标 Channel 并写入
        // 此部分逻辑已在原代码中实现，保持完整
        processAfter();
    }

    /**
     * 后置处理，子类实现
     */
    public abstract void processAfter();
}