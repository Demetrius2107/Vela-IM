package com.vela.im.service.group.infrastructure.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.vela.im.service.group.domain.entity.ImGroupEntity;
import com.vela.im.service.group.domain.entity.ImGroupMemberEntity;
import com.vela.im.service.group.infrastructure.persistence.mapper.ImGroupMemberMapper;
import com.vela.im.service.group.application.dto.callback.AddMemberAfterCallback;
import com.vela.im.service.group.application.dto.req.*;
import com.vela.im.service.group.application.dto.resp.AddMemberResp;
import com.vela.im.service.group.application.dto.resp.GetRoleInGroupResp;
import com.vela.im.service.group.domain.service.ImGroupMemberService;
import com.vela.im.service.group.domain.service.ImGroupService;
import com.vela.im.service.user.domain.entity.ImUserDataEntity;
import com.vela.im.service.user.domain.service.ImUserService;
import com.vela.im.service.common.utils.CallbackService;
import com.vela.im.service.message.domain.utils.GroupMessageProducer;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.GroupErrorCode;
import com.vela.im.shared.types.enums.GroupMemberRoleEnum;
import com.vela.im.shared.types.enums.GroupStatusEnum;
import com.vela.im.shared.types.enums.GroupTypeEnum;
import com.vela.im.shared.types.enums.command.GroupEventCommand;
import org.springframework.context.annotation.Lazy;
import com.vela.im.shared.exception.ApplicationException;
import com.vela.im.shared.types.ClientInfo;
import com.vela.im.codec.pack.group.AddGroupMemberPack;
import com.vela.im.codec.pack.group.GroupMemberSpeakPack;
import com.vela.im.codec.pack.group.RemoveGroupMemberPack;
import com.vela.im.codec.pack.group.UpdateGroupMemberPack;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * <p>Title: ImGroupMemberServiceImpl</p>
 * <p>Description: 群组成员管理实现，处理群成员的添加、移除、角色设置、禁言等。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.1
 * @createTime 2025-03-06
 * @updateTime 2026-07-20
 *
 * Copyright © 2026 wanqiu All rights reserved
 
 */
@Slf4j
@Service
public class ImGroupMemberServiceImpl implements ImGroupMemberService {

    private final ImGroupMemberMapper imGroupMemberMapper;
        private final ImUserService imUserService;
        private final ImGroupService groupService;
        private final ImGroupMemberService groupMemberService;
        private final CallbackService callbackService;
        private final GroupMessageProducer groupMessageProducer;
        private final ImServerProperties appConfig;

        public ImGroupMemberServiceImpl(ImGroupMemberMapper imGroupMemberMapper,
                                        ImUserService imUserService,
                                        @Lazy ImGroupService groupService,
                                        @Lazy ImGroupMemberService groupMemberService,
                                        CallbackService callbackService,
                                        GroupMessageProducer groupMessageProducer,
                                        ImServerProperties appConfig) {
            this.imGroupMemberMapper = imGroupMemberMapper;
            this.imUserService = imUserService;
            this.groupService = groupService;
            this.groupMemberService = groupMemberService;
            this.callbackService = callbackService;
            this.groupMessageProducer = groupMessageProducer;
            this.appConfig = appConfig;
        }

    @Override
    public Result importGroupMember(ImportGroupMemberRequest req) {

        List<AddMemberResp> resp = new ArrayList<>();

        Result<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        for (GroupMemberDto memberId :
                req.getMembers()) {
            Result responseVO = null;
            try {
                responseVO = groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), memberId);
            } catch (Exception e) {
                e.printStackTrace();
                responseVO = Result.fail();
            }
            AddMemberResp addMemberResp = new AddMemberResp();
            addMemberResp.setMemberId(memberId.getMemberId());
            if (responseVO.isOk()) {
                addMemberResp.setResult(0);
            } else if (responseVO.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                addMemberResp.setResult(2);
            } else {
                addMemberResp.setResult(1);
            }
            resp.add(addMemberResp);
        }

        return Result.ok(resp);
    }

    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description: 添加群成员，内部调用
     * @author wanqiu
     */
    @Override
    @Transactional
    public Result addGroupMember(String groupId, Integer appId, GroupMemberDto dto) {

        Result<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(dto.getMemberId(), appId);
        if(!singleUserInfo.isOk()){
            return singleUserInfo;
        }

        if (dto.getRole() != null && GroupMemberRoleEnum.OWNER.getCode() == dto.getRole()) {
            QueryWrapper<ImGroupMemberEntity> queryOwner = new QueryWrapper<>();
            queryOwner.eq("group_id", groupId);
            queryOwner.eq("app_id", appId);
            queryOwner.eq("role", GroupMemberRoleEnum.OWNER.getCode());
            Integer ownerNum = imGroupMemberMapper.selectCount(queryOwner);
            if (ownerNum > 0) {
                return Result.fail(GroupErrorCode.GROUP_IS_HAVE_OWNER);
            }
        }

        QueryWrapper<ImGroupMemberEntity> query = new QueryWrapper<>();
        query.eq("group_id", groupId);
        query.eq("app_id", appId);
        query.eq("member_id", dto.getMemberId());
        ImGroupMemberEntity memberDto = imGroupMemberMapper.selectOne(query);

        long now = System.currentTimeMillis();
        if (memberDto == null) {
            //初次加群
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);
            memberDto.setGroupId(groupId);
            memberDto.setAppId(appId);
            memberDto.setJoinTime(now);
            int insert = imGroupMemberMapper.insert(memberDto);
            if (insert == 1) {
                return Result.ok();
            }
            return Result.fail(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        } else if (GroupMemberRoleEnum.LEAVE.getCode() == memberDto.getRole()) {
            //重新进群
            memberDto = new ImGroupMemberEntity();
            BeanUtils.copyProperties(dto, memberDto);
            memberDto.setJoinTime(now);
            int update = imGroupMemberMapper.update(memberDto, query);
            if (update == 1) {
                return Result.ok();
            }
            return Result.fail(GroupErrorCode.USER_JOIN_GROUP_ERROR);
        }

        return Result.fail(GroupErrorCode.USER_IS_JOINED_GROUP);

    }

    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description: 删除群成员，内部调用
     * @author wanqiu
     */
    @Override
    public Result removeGroupMember(String groupId, Integer appId, String memberId) {

        Result<ImUserDataEntity> singleUserInfo = imUserService.getSingleUserInfo(memberId, appId);
        if(!singleUserInfo.isOk()){
            return singleUserInfo;
        }

        Result<GetRoleInGroupResp> roleInGroupOne = getRoleInGroupOne(groupId, memberId, appId);
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }

        GetRoleInGroupResp data = roleInGroupOne.getData();
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.LEAVE.getCode());
        imGroupMemberEntity.setLeaveTime(System.currentTimeMillis());
        imGroupMemberEntity.setGroupMemberId(data.getGroupMemberId());
        imGroupMemberMapper.updateById(imGroupMemberEntity);
        return Result.ok();
    }

    /**
     * @param groupId, memberId, appId]
     * @return com.vela.im.shared.Result<com.vela.im.service.group.application.dto.resp.GetRoleInGroupResp>
     * @description 查询用户在群内的角色
     * @author wanqiu
     */
    @Override
    public Result<GetRoleInGroupResp> getRoleInGroupOne(String groupId, String memberId, Integer appId) {

        GetRoleInGroupResp resp = new GetRoleInGroupResp();

        QueryWrapper<ImGroupMemberEntity> queryOwner = new QueryWrapper<>();
        queryOwner.eq("group_id", groupId);
        queryOwner.eq("app_id", appId);
        queryOwner.eq("member_id", memberId);

        ImGroupMemberEntity imGroupMemberEntity = imGroupMemberMapper.selectOne(queryOwner);
        if (imGroupMemberEntity == null || imGroupMemberEntity.getRole() == GroupMemberRoleEnum.LEAVE.getCode()) {
            return Result.fail(GroupErrorCode.MEMBER_IS_NOT_JOINED_GROUP);
        }

        resp.setSpeakDate(imGroupMemberEntity.getSpeakDate());
        resp.setGroupMemberId(imGroupMemberEntity.getGroupMemberId());
        resp.setMemberId(imGroupMemberEntity.getMemberId());
        resp.setRole(imGroupMemberEntity.getRole());
        return Result.ok(resp);
    }

    @Override
    public Result<Collection<String>> getMemberJoinedGroup(GetJoinedGroupRequest req) {

        if (req.getLimit() != null) {
            Page<ImGroupMemberEntity> objectPage = new Page<>(req.getOffset(), req.getLimit());
            QueryWrapper<ImGroupMemberEntity> query = new QueryWrapper<>();
            query.eq("app_id", req.getAppId());
            query.eq("member_id", req.getMemberId());
            IPage<ImGroupMemberEntity> imGroupMemberEntityPage = imGroupMemberMapper.selectPage(objectPage, query);

            Set<String> groupId = new HashSet<>();
            List<ImGroupMemberEntity> records = imGroupMemberEntityPage.getRecords();
            records.forEach(e -> {
                groupId.add(e.getGroupId());
            });

            return Result.ok(groupId);
        } else {
            return Result.ok(imGroupMemberMapper.getJoinedGroupId(req.getAppId(), req.getMemberId()));
        }
    }

    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description: 添加群成员，拉人入群的逻辑，直接进入群聊。如果是后台管理员，则直接拉入群，
     * 否则只有私有群可以调用本接口，并且群成员也可以拉人入群.只有私有群可以调用本接口
     * @author wanqiu
     */
    @Override
    public Result addMember(AddGroupMemberRequest req) {

        List<AddMemberResp> resp = new ArrayList<>();

        boolean isAdmin = false;
        Result<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        List<GroupMemberDto> memberDtos = req.getMembers();
        // 回调
        if(appConfig.getCallback().isAddGroupMemberBeforeCallback()){

            Result responseVO = callbackService.beforeCallback(req.getAppId(), ImConstants.CallbackCommand.GROUP_MEMBER_ADD_BEFORE
                    , JSONObject.toJSONString(req));
            if(!responseVO.isOk()){
                return responseVO;
            }

            try {
                memberDtos
                        = JSONArray.parseArray(JSONObject.toJSONString(responseVO.getData()), GroupMemberDto.class);
            }catch (Exception e){
                e.printStackTrace();
                log.error("GroupMemberAddBefore 回调失败：{}",req.getAppId());
            }
        }

        ImGroupEntity group = groupResp.getData();


        /**
         * 私有群（private）	类似普通微信群，创建后仅支持已在群内的好友邀请加群，且无需被邀请方同意或群主审批
         * 公开群（Public）	类似 QQ 群，创建后群主可以指定群管理员，需要群主或管理员审批通过才能入群
         * 群类型 1私有群（类似微信） 2公开群(类似qq）
         *
         */

        if (!isAdmin && GroupTypeEnum.PUBLIC.getCode() == group.getGroupType()) {
            throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
        }

        List<String> successId = new ArrayList<>();
        for (GroupMemberDto memberId :
                memberDtos) {
            Result responseVO = null;
            try {
                responseVO = groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), memberId);
            } catch (Exception e) {
                e.printStackTrace();
                responseVO = Result.fail();
            }
            AddMemberResp addMemberResp = new AddMemberResp();
            addMemberResp.setMemberId(memberId.getMemberId());
            if (responseVO.isOk()) {
                successId.add(memberId.getMemberId());
                addMemberResp.setResult(0);
            } else if (responseVO.getCode() == GroupErrorCode.USER_IS_JOINED_GROUP.getCode()) {
                addMemberResp.setResult(2);
                addMemberResp.setResultMessage(responseVO.getMsg());
            } else {
                addMemberResp.setResult(1);
                addMemberResp.setResultMessage(responseVO.getMsg());
            }
            resp.add(addMemberResp);
        }

        AddGroupMemberPack addGroupMemberPack = new AddGroupMemberPack();
        addGroupMemberPack.setGroupId(req.getGroupId());
        addGroupMemberPack.setMembers(successId);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.ADDED_MEMBER, addGroupMemberPack
                , new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));

        if(appConfig.getCallback().isAddGroupMemberAfterCallback()){
            AddMemberAfterCallback dto = new AddMemberAfterCallback();
            dto.setGroupId(req.getGroupId());
            dto.setGroupType(group.getGroupType());
            dto.setMemberId(resp);
            dto.setOperater(req.getOperater());
            callbackService.callback(req.getAppId()
                    ,ImConstants.CallbackCommand.GROUP_MEMBER_ADD_AFTER,
                    JSONObject.toJSONString(dto));
        }

        return Result.ok(resp);
    }

    @Override
    public Result removeMember(RemoveGroupMemberRequest req) {

        List<AddMemberResp> resp = new ArrayList<>();
        boolean isAdmin = false;
        Result<ImGroupEntity> groupResp = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        ImGroupEntity group = groupResp.getData();

        if (!isAdmin) {
            if (GroupTypeEnum.PUBLIC.getCode() == group.getGroupType()) {

                //获取操作人的权限 是管理员or群主or群成员
                Result<GetRoleInGroupResp> role = getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
                if (!role.isOk()) {
                    return role;
                }

                GetRoleInGroupResp data = role.getData();
                Integer roleInfo = data.getRole();

                boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
                boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

                if (!isOwner && !isManager) {
                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                }

                //私有群必须是群主才能踢人
                if (!isOwner && GroupTypeEnum.PRIVATE.getCode() == group.getGroupType()) {
                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }

                //公开群管理员和群主可踢人，但管理员只能踢普通群成员
                if (GroupTypeEnum.PUBLIC.getCode() == group.getGroupType()) {
//                    throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                    //获取被踢人的权限
                    Result<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
                    if (!roleInGroupOne.isOk()) {
                        return roleInGroupOne;
                    }
                    GetRoleInGroupResp memberRole = roleInGroupOne.getData();
                    if (memberRole.getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
                        throw new ApplicationException(GroupErrorCode.GROUP_OWNER_IS_NOT_REMOVE);
                    }
                    //是管理员并且被踢人不是群成员，无法操作
                    if (isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()) {
                        throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                    }
                }
            }
        }
        Result responseVO = groupMemberService.removeGroupMember(req.getGroupId(), req.getAppId(), req.getMemberId());
        if(responseVO.isOk()){

            RemoveGroupMemberPack removeGroupMemberPack = new RemoveGroupMemberPack();
            removeGroupMemberPack.setGroupId(req.getGroupId());
            removeGroupMemberPack.setMember(req.getMemberId());
            groupMessageProducer.producer(req.getMemberId(), GroupEventCommand.DELETED_MEMBER, removeGroupMemberPack
                    , new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
            if(appConfig.getCallback().isDeleteGroupMemberAfterCallback()){
                callbackService.callback(req.getAppId(),
                        ImConstants.CallbackCommand.GROUP_MEMBER_DELETE_AFTER,
                        JSONObject.toJSONString(req));
            }
        }

        return responseVO;
    }

    @Override
    public Result<List<GroupMemberDto>> getGroupMember(String groupId, Integer appId) {
        List<GroupMemberDto> groupMember = imGroupMemberMapper.getGroupMember(appId, groupId);
        return Result.ok(groupMember);
    }

    @Override
    public List<String> getGroupMemberId(String groupId, Integer appId) {
        return imGroupMemberMapper.getGroupMemberId(appId, groupId);
    }

    @Override
    public List<GroupMemberDto> getGroupManager(String groupId, Integer appId) {
        return imGroupMemberMapper.getGroupManager(groupId, appId);
    }

    @Override
    public Result updateGroupMember(UpdateGroupMemberRequest req) {

        boolean isadmin = false;

        Result<ImGroupEntity> group = groupService.getGroup(req.getGroupId(), req.getAppId());
        if (!group.isOk()) {
            return group;
        }

        ImGroupEntity groupData = group.getData();
        if (groupData.getStatus() == GroupStatusEnum.DESTROY.getCode()) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        //是否是自己修改自己的资料
        boolean isMeOperate = req.getOperater().equals(req.getMemberId());

        if (!isadmin) {
            //昵称只能自己修改 权限只能群主或管理员修改
            if (StringUtils.isBlank(req.getAlias()) && !isMeOperate) {
                return Result.fail(GroupErrorCode.THIS_OPERATE_NEED_ONESELF);
            }
            //私有群不能设置管理员
            if (groupData.getGroupType() == GroupTypeEnum.PRIVATE.getCode() &&
                    req.getRole() != null && (req.getRole() == GroupMemberRoleEnum.MAMAGER.getCode() ||
                    req.getRole() == GroupMemberRoleEnum.OWNER.getCode())) {
                return Result.fail(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //如果要修改权限相关的则走下面的逻辑
            if(req.getRole() != null){
                //获取被操作人的是否在群内
                Result<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getMemberId(), req.getAppId());
                if(!roleInGroupOne.isOk()){
                    return roleInGroupOne;
                }

                //获取操作人权限
                Result<GetRoleInGroupResp> operateRoleInGroupOne = this.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
                if(!operateRoleInGroupOne.isOk()){
                    return operateRoleInGroupOne;
                }

                GetRoleInGroupResp data = operateRoleInGroupOne.getData();
                Integer roleInfo = data.getRole();
                boolean isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
                boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

                //不是管理员不能修改权限
                if(req.getRole() != null && !isOwner && !isManager){
                    return Result.fail(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
                }

                //管理员只有群主能够设置
                if(req.getRole() != null && req.getRole() == GroupMemberRoleEnum.MAMAGER.getCode() && !isOwner){
                    return Result.fail(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
                }

            }
        }

        ImGroupMemberEntity update = new ImGroupMemberEntity();

        if (StringUtils.isNotBlank(req.getAlias())) {
            update.setAlias(req.getAlias());
        }

        //不能直接修改为群主
        if(req.getRole() != null && req.getRole() != GroupMemberRoleEnum.OWNER.getCode()){
            update.setRole(req.getRole());
        }

        UpdateWrapper<ImGroupMemberEntity> objectUpdateWrapper = new UpdateWrapper<>();
        objectUpdateWrapper.eq("app_id", req.getAppId());
        objectUpdateWrapper.eq("member_id", req.getMemberId());
        objectUpdateWrapper.eq("group_id", req.getGroupId());
        imGroupMemberMapper.update(update, objectUpdateWrapper);

        UpdateGroupMemberPack pack = new UpdateGroupMemberPack();
        BeanUtils.copyProperties(req, pack);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.UPDATED_MEMBER, pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));


        return Result.ok();
    }

    @Override
    @Transactional
    public Result transferGroupMember(String owner, String groupId, Integer appId) {

        //更新旧群主
        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        imGroupMemberEntity.setRole(GroupMemberRoleEnum.ORDINARY.getCode());
        UpdateWrapper<ImGroupMemberEntity> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("app_id", appId);
        updateWrapper.eq("group_id", groupId);
        updateWrapper.eq("role", GroupMemberRoleEnum.OWNER.getCode());
        imGroupMemberMapper.update(imGroupMemberEntity, updateWrapper);

        //更新新群主
        ImGroupMemberEntity newOwner = new ImGroupMemberEntity();
        newOwner.setRole(GroupMemberRoleEnum.OWNER.getCode());
        UpdateWrapper<ImGroupMemberEntity> ownerWrapper = new UpdateWrapper<>();
        ownerWrapper.eq("app_id", appId);
        ownerWrapper.eq("group_id", groupId);
        ownerWrapper.eq("member_id", owner);
        imGroupMemberMapper.update(newOwner, ownerWrapper);

        return Result.ok();
    }

    @Override
    public Result speak(SpeakMemberRequest speakMemberRequest) {

        Result<ImGroupEntity> groupResp = groupService.getGroup(speakMemberRequest.getGroupId(), speakMemberRequest.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        boolean isadmin = false;
        boolean isOwner = false;
        boolean isManager = false;
        GetRoleInGroupResp memberRole = null;

        if (!isadmin) {

            //获取操作人的权限 是管理员or群主or群成员
            Result<GetRoleInGroupResp> role = getRoleInGroupOne(speakMemberRequest.getGroupId(), speakMemberRequest.getOperater(), speakMemberRequest.getAppId());
            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            isOwner = roleInfo == GroupMemberRoleEnum.OWNER.getCode();
            isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode();

            if (!isOwner && !isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

            //获取被操作的权限
            Result<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(speakMemberRequest.getGroupId(), speakMemberRequest.getMemberId(), speakMemberRequest.getAppId());
            if (!roleInGroupOne.isOk()) {
                return roleInGroupOne;
            }
            memberRole = roleInGroupOne.getData();
            //被操作人是群主只能app管理员操作
            if (memberRole.getRole() == GroupMemberRoleEnum.OWNER.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_APPMANAGER_ROLE);
            }

            //是管理员并且被操作人不是群成员，无法操作
            if (isManager && memberRole.getRole() != GroupMemberRoleEnum.ORDINARY.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }

        ImGroupMemberEntity imGroupMemberEntity = new ImGroupMemberEntity();
        if(memberRole == null){
            //获取被操作的权限
            Result<GetRoleInGroupResp> roleInGroupOne = this.getRoleInGroupOne(speakMemberRequest.getGroupId(), speakMemberRequest.getMemberId(), speakMemberRequest.getAppId());
            if (!roleInGroupOne.isOk()) {
                return roleInGroupOne;
            }
            memberRole = roleInGroupOne.getData();
        }

        imGroupMemberEntity.setGroupMemberId(memberRole.getGroupMemberId());
        if(speakMemberRequest.getSpeakDate() > 0){
            imGroupMemberEntity.setSpeakDate(System.currentTimeMillis() + speakMemberRequest.getSpeakDate());
        }else{
            imGroupMemberEntity.setSpeakDate(speakMemberRequest.getSpeakDate());
        }

        int i = imGroupMemberMapper.updateById(imGroupMemberEntity);
        if(i == 1){
            GroupMemberSpeakPack pack = new GroupMemberSpeakPack();
            BeanUtils.copyProperties(speakMemberRequest,pack);
            groupMessageProducer.producer(speakMemberRequest.getOperater(),GroupEventCommand.SPEAK_GOUP_MEMBER,pack,
                    new ClientInfo(speakMemberRequest.getAppId(),speakMemberRequest.getClientType(),speakMemberRequest.getImei()));
        }
        return Result.ok();
    }

    @Override
    public Result<Collection<String>> syncMemberJoinedGroup(String operater, Integer appId) {
        return Result.ok(imGroupMemberMapper.syncJoinedGroupId(appId,operater,GroupMemberRoleEnum.LEAVE.getCode()));
    }


}
