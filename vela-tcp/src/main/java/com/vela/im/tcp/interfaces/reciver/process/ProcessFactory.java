package com.vela.im.tcp.interfaces.reciver.process;

/**
 * <p>Title: ProcessFactory</p>
 * <p>Description: 消息处理工厂，根据 command 返回对应的消息处理器</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public class ProcessFactory {

    /** 默认消息处理器（空实现） */
    private static BaseProcess defaultProcess;

    static {
        defaultProcess = new BaseProcess() {
            @Override
            public void processBefore() {
            }

            @Override
            public void processAfter() {
            }
        };
    }

    /**
     * 根据指令类型获取对应的消息处理器
     *
     * @param command 指令类型
     * @return BaseProcess 消息处理器实例
     */
    public static BaseProcess getMessageProcess(Integer command){
        return defaultProcess;
    }

}