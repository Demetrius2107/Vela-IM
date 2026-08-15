package com.vela.im.service.message.application.facade;

import com.vela.im.service.conversation.domain.service.ConversationService;
import com.vela.im.shared.types.message.MessageReadedContent;
import org.springframework.stereotype.Component;

/**
 * <p>Title: ConversationFacade</p>
 * <p>Description: 会话域门面（防腐层），收敛 message 模块对 conversation 模块 domain 服务的跨模块调用。
 * message.domain 层不再直接依赖 conversation.domain.service，跨模块引用统一经本门面（application 层）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
@Component
public class ConversationFacade {

    private final ConversationService conversationService;

    public ConversationFacade(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 将（会话类型，发送方，接收方）转换为会话 ID
     *
     * @param type   会话类型
     * @param fromId 发送方 ID
     * @param toId   接收方 ID
     * @return 会话 ID
     */
    public String convertConversationId(Integer type, String fromId, String toId) {
        return conversationService.convertConversationId(type, fromId, toId);
    }

    /**
     * 消息已读回执落会话
     *
     * @param messageReadedContent 已读回执内容
     */
    public void messageMarkRead(MessageReadedContent messageReadedContent) {
        conversationService.messageMarkRead(messageReadedContent);
    }
}
