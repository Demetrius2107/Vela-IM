package com.vela.im.tcp.interfaces.fegin;

import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.message.CheckSendMessageReq;
import feign.Headers;
import feign.RequestLine;

/**
 * <p>Title: FeignMessageService</p>
 * <p>Description: Feign 远程调用接口，用于校验消息是否可发送</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-05
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
public interface FeignMessageService {

    /**
     * 校验消息是否可发送
     *
     * @param o 校验发送消息请求
     * @return 校验结果
     */
    @Headers({"Content-Type: application/json", "Accept:application/json"})
    @RequestLine("POST /message/checkSend")
    public Result checkSendMessage(CheckSendMessageReq o);
}