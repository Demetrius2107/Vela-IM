package com.vela.im.service.conversation.interfaces.rest;

import com.vela.im.service.conversation.application.dto.ArchiveConversationReq;
import com.vela.im.service.conversation.application.dto.DeleteConversationReq;
import com.vela.im.service.conversation.application.dto.UpdateConversationReq;
import com.vela.im.service.conversation.domain.service.ConversationService;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.SyncReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>Title: ConversationController</p>
 * <p>Description: 会话管理 REST 接口，处理会话删除、更新（置顶/免打扰）、增量同步等请求。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-06
 * @updateTime 2026-07-20
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@RestController
@RequestMapping("v1/conversation")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @RequestMapping("/deleteConversation")
    public Result deleteConversation(@RequestBody @Validated DeleteConversationReq
                                                     req, Integer appId, String identifier)  {
        req.setAppId(appId);
//        req.setOperater(identifier);
        return conversationService.deleteConversation(req);
    }

    @RequestMapping("/updateConversation")
    public Result updateConversation(@RequestBody @Validated UpdateConversationReq
                                                 req, Integer appId, String identifier)  {
        req.setAppId(appId);
//        req.setOperater(identifier);
        return conversationService.updateConversation(req);
    }

    @RequestMapping("/archiveConversation")
    public Result archiveConversation(@RequestBody @Validated ArchiveConversationReq
                                                 req, Integer appId, String identifier)  {
        req.setAppId(appId);
//        req.setOperater(identifier);
        return conversationService.archiveConversation(req);
    }

    @RequestMapping("/syncConversationList")
    public Result syncFriendShipList(@RequestBody @Validated SyncReq req, Integer appId)  {
        req.setAppId(appId);
        return conversationService.syncConversationSet(req);
    }

    @RequestMapping("/listConversation")
    public Result listConversation(String fromId, Integer appId)  {
        return conversationService.listConversation(fromId, appId);
    }

}
