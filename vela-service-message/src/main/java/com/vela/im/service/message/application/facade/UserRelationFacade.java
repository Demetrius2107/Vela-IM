package com.vela.im.service.message.application.facade;

import com.vela.im.service.common.entity.ImFriendShipEntity;
import com.vela.im.service.friendship.application.dto.req.GetRelationReq;
import com.vela.im.service.friendship.domain.service.ImFriendService;
import com.vela.im.service.user.domain.entity.ImUserDataEntity;
import com.vela.im.service.user.domain.service.ImUserService;
import com.vela.im.shared.base.Result;
import org.springframework.stereotype.Component;

/**
 * <p>Title: UserRelationFacade</p>
 * <p>Description: 用户/好友域门面（防腐层），收敛 message 模块对 user/friendship 模块 domain 服务的跨模块调用。
 * message.domain 层不再直接依赖 user/friendship 模块的 domain.service，跨模块引用统一经本门面（application 层）。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
@Component
public class UserRelationFacade {

    private final ImUserService imUserService;
    private final ImFriendService imFriendService;

    public UserRelationFacade(ImUserService imUserService, ImFriendService imFriendService) {
        this.imUserService = imUserService;
        this.imFriendService = imFriendService;
    }

    /**
     * 查询用户信息（含禁言/禁用标志）
     *
     * @param fromId 用户 ID
     * @param appId  应用 ID
     * @return 用户信息结果
     */
    public Result<ImUserDataEntity> getSingleUserInfo(String fromId, Integer appId) {
        return imUserService.getSingleUserInfo(fromId, appId);
    }

    /**
     * 查询两用户间的好友关系
     *
     * @param req 关系查询请求
     * @return 好友关系结果
     */
    public Result<ImFriendShipEntity> getRelation(GetRelationReq req) {
        return imFriendService.getRelation(req);
    }
}
