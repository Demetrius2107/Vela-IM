package com.vela.im.service.message.domain.service;


import com.vela.im.service.common.entity.ImFriendShipEntity;
import com.vela.im.service.friendship.application.dto.req.GetRelationReq;
import com.vela.im.service.message.application.facade.UserRelationFacade;
import com.vela.im.service.common.entity.ImGroupEntity;
import com.vela.im.service.common.entity.GetRoleInGroupResp;
import com.vela.im.service.message.interfaces.feign.GroupServiceFeignClient;

import com.vela.im.service.user.domain.entity.ImUserDataEntity;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.types.enums.*;
import org.springframework.stereotype.Service;

/**
 * <p>Title: CheckSendMessageService</p>
 * <p>Description: 消息发送前置校验服务，校验发送方禁言/禁用状态、好友关系、群组权限等。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-06
 * @updateTime 2026-07-20
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Service
public class CheckSendMessageService {

    private final UserRelationFacade userRelationFacade;
    
    private final GroupServiceFeignClient groupServiceFeignClient;
    private final ImServerProperties appConfig;

    public CheckSendMessageService(UserRelationFacade userRelationFacade,
                                   GroupServiceFeignClient groupServiceFeignClient,
                                   ImServerProperties appConfig) {
        this.userRelationFacade = userRelationFacade;
        
        this.groupServiceFeignClient = groupServiceFeignClient;
        this.appConfig = appConfig;
    }

    /**
     * Check if sender is muted or banned.
     *
     * @param fromId sender user ID
     * @param appId  application ID
     * @return success if allowed, error code if muted/banned
     */
    public Result checkSenderForvidAndMute(String fromId, Integer appId){

        Result<ImUserDataEntity> singleUserInfo
                = userRelationFacade.getSingleUserInfo(fromId, appId);
        if(!singleUserInfo.isOk()){
            return singleUserInfo;
        }

        ImUserDataEntity user = singleUserInfo.getData();
        if(user.getForbiddenFlag() == UserForbiddenFlagEnum.FORBIBBEN.getCode()){
            return Result.fail(MessageErrorCode.FROMER_IS_FORBIBBEN);
        }else if (user.getSilentFlag() == UserSilentFlagEnum.MUTE.getCode()){
            return Result.fail(MessageErrorCode.FROMER_IS_MUTE);
        }

        return Result.ok();
    }

    /**
     * Check friendship between sender and receiver.
     * <p>Validates friend status and blacklist, depending on configuration.</p>
     *
     * @param fromId sender user ID
     * @param toId   receiver user ID
     * @param appId  application ID
     * @return success if allowed, error code if not friends / blacklisted
     */
    public Result checkFriendShip(String fromId, String toId, Integer appId){

        if(appConfig.isSendMessageCheckFriend()){
            // Check from → to friendship
            GetRelationReq fromReq = buildRelationReq(fromId, toId, appId);
            Result<ImFriendShipEntity> fromRelation = userRelationFacade.getRelation(fromReq);
            if(!fromRelation.isOk()){
                return fromRelation;
            }

            // Check to → from friendship (reverse direction)
            GetRelationReq toReq = buildRelationReq(toId, fromId, appId);
            Result<ImFriendShipEntity> toRelation = userRelationFacade.getRelation(toReq);
            if(!toRelation.isOk()){
                return toRelation;
            }

            // Validate friend status (both directions must be normal)
            Result check = checkFriendStatus(fromRelation.getData(), toRelation.getData());
            if(!check.isOk()){
                return check;
            }
        }

        return Result.ok();
    }

    /**
     * Build a GetRelationReq for the given user pair.
     */
    private GetRelationReq buildRelationReq(String fromId, String toId, Integer appId) {
        GetRelationReq req = new GetRelationReq();
        req.setFromId(fromId);
        req.setToId(toId);
        req.setAppId(appId);
        return req;
    }

    /**
     * Validate friend status and blacklist for both directions.
     */
    private Result checkFriendStatus(ImFriendShipEntity fromRelation, ImFriendShipEntity toRelation) {
        if(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode() != fromRelation.getStatus()){
            return Result.fail(FriendShipErrorCode.FRIEND_IS_DELETED);
        }

        if(FriendShipStatusEnum.FRIEND_STATUS_NORMAL.getCode() != toRelation.getStatus()){
            return Result.fail(FriendShipErrorCode.FRIEND_IS_DELETED);
        }

        if(appConfig.isSendMessageCheckBlack()){
            if(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode() != fromRelation.getBlack()){
                return Result.fail(FriendShipErrorCode.FRIEND_IS_BLACK);
            }

            if(FriendShipStatusEnum.BLACK_STATUS_NORMAL.getCode() != toRelation.getBlack()){
                return Result.fail(FriendShipErrorCode.TARGET_IS_BLACK_YOU);
            }
        }

        return Result.ok();
    }

    /**
     * Check group message sending permissions.
     * <p>Validates: sender mute/ban → group exists → member in group → group mute (admin/owner exempt) → member mute.</p>
     *
     * @param fromId  sender user ID
     * @param groupId group ID
     * @param appId   application ID
     * @return success if allowed, error code otherwise
     */
    public Result checkGroupMessage(String fromId, String groupId, Integer appId){

        Result responseVO = checkSenderForvidAndMute(fromId, appId);
        if(!responseVO.isOk()){
            return responseVO;
        }

        // Check if group exists
        Result<ImGroupEntity> group = groupServiceFeignClient.getGroup(groupId, appId);
        if(!group.isOk()){
            return group;
        }

        // Check if sender is a group member
        Result<GetRoleInGroupResp> roleInGroupOne = groupServiceFeignClient.getRoleInGroupOne(groupId, fromId, appId);
        if(!roleInGroupOne.isOk()){
            return roleInGroupOne;
        }
        GetRoleInGroupResp data = roleInGroupOne.getData();

        // Check group-wide mute: only admin/owner can speak when muted
        ImGroupEntity groupData = group.getData();
        if(groupData.getMute() == GroupMuteTypeEnum.MUTE.getCode()
         && (data.getRole() == GroupMemberRoleEnum.MAMAGER.getCode() ||
                data.getRole() == GroupMemberRoleEnum.OWNER.getCode()  )){
            return Result.fail(GroupErrorCode.THIS_GROUP_IS_MUTE);
        }

        // Check individual member mute
        if(data.getSpeakDate() != null && data.getSpeakDate() > System.currentTimeMillis()){
            return Result.fail(GroupErrorCode.GROUP_MEMBER_IS_SPEAK);
        }

        return Result.ok();
    }


}
