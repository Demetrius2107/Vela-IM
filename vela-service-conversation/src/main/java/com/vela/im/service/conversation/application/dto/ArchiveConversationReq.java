package com.vela.im.service.conversation.application.dto;

import com.vela.im.shared.types.RequestBase;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * <p>Title: ArchiveConversationReq</p>
 * <p>Description: 会话归档请求，isArchive 1-归档 0-取消归档。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
@Data
public class ArchiveConversationReq extends RequestBase {

    /** 会话 ID */
    @NotBlank(message = "会话id不能为空")
    private String conversationId;

    /** 是否归档：1-归档 0-取消归档 */
    private Integer isArchive;

    /** 操作用户 ID */
    @NotBlank(message = "fromId不能为空")
    private String fromId;
}
