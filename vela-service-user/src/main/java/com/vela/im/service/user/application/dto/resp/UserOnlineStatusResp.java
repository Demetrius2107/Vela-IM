package com.vela.im.service.user.application.dto.resp;

import com.vela.im.shared.types.UserSession;
import lombok.Data;

import java.util.List;

/**
 * @author wanqiu
 */
@Data
public class UserOnlineStatusResp {

    /** 连接状态：0-离线，1-在线 */
    private Integer connectState;

    private List<UserSession> session;

    private String customText;

    private Integer customStatus;
}
