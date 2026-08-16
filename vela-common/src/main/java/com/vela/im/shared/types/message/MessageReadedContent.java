package com.vela.im.shared.types.message;


import com.vela.im.shared.types.ClientInfo;
import lombok.Data;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.0
 * @createTime 2025-03-03
 * @updateTime 2026-07-19
 *
 * Copyright © 2026 wanqiu All rights reserved
 */
@Data
public class MessageReadedContent extends ClientInfo {

    /** 消息Key，关联具体消息 */
    private Long messageKey;

    private long messageSequence;

    private String fromId;

    private String groupId;

    private String toId;

    private Integer conversationType;

}
