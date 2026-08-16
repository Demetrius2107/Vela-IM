package com.vela.im.service.user.application.dto.req;

import com.vela.im.shared.types.RequestBase;
import lombok.Data;

import java.util.List;

/**
 * 好友在线状态查询请求
 *
 * @author wanqiu
 */
@Data
public class PullFriendOnlineStatusReq extends RequestBase {

    /** 待查询好友用户 ID 集合 */
    private List<String> userList;

}
