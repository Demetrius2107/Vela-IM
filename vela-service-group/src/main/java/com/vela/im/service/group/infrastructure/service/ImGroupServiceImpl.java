package com.vela.im.service.group.infrastructure.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.vela.im.service.group.domain.entity.ImGroupEntity;
import com.vela.im.service.group.infrastructure.persistence.mapper.ImGroupMapper;
import com.vela.im.service.group.application.dto.callback.DestroyGroupCallbackDto;
import com.vela.im.service.group.application.dto.req.*;
import com.vela.im.service.group.application.dto.resp.GetGroupResp;
import com.vela.im.service.group.application.dto.resp.GetJoinedGroupResp;
import com.vela.im.service.group.application.dto.resp.GetRoleInGroupResp;
import com.vela.im.service.group.domain.service.ImGroupMemberService;
import com.vela.im.service.group.domain.service.ImGroupService;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
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
import com.vela.im.shared.exception.ApplicationException;
import com.vela.im.shared.exception.BaseErrorCode;
import com.vela.im.shared.types.ClientInfo;
import org.springframework.context.annotation.Lazy;
import com.vela.im.shared.types.SyncReq;
import com.vela.im.shared.types.SyncResp;
import com.vela.im.codec.pack.group.CreateGroupPack;
import com.vela.im.codec.pack.group.DestroyGroupPack;
import com.vela.im.codec.pack.group.UpdateGroupInfoPack;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * <p>Title: ImGroupServiceImpl</p>
 * <p>Description: 群组管理实现，处理群组的创建、解散、更新、转让、禁言等。</p>
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
public class ImGroupServiceImpl implements ImGroupService {

    private final ImGroupMapper imGroupDataMapper;
    private final ImGroupMemberService groupMemberService;
    private final ImServerProperties appConfig;
    private final CallbackService callbackService;
    private final GroupMessageProducer groupMessageProducer;
    private final RedisSeq redisSeq;

    public ImGroupServiceImpl(ImGroupMapper imGroupDataMapper,
                              @Lazy ImGroupMemberService groupMemberService,
                              ImServerProperties appConfig,
                              CallbackService callbackService,
                              GroupMessageProducer groupMessageProducer,
                              RedisSeq redisSeq) {
        this.imGroupDataMapper = imGroupDataMapper;
        this.groupMemberService = groupMemberService;
        this.appConfig = appConfig;
        this.callbackService = callbackService;
        this.groupMessageProducer = groupMessageProducer;
        this.redisSeq = redisSeq;
    }

    @Override
    public Result importGroup(ImportGroupRequest req) {

        //1.判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();

        if (StringUtils.isEmpty(req.getGroupId())) {
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
        } else {
            query.eq("group_id", req.getGroupId());
            query.eq("app_id", req.getAppId());
            Integer integer = imGroupDataMapper.selectCount(query);
            if (integer > 0) {
                throw new ApplicationException(GroupErrorCode.GROUP_IS_EXIST);
            }
        }

        ImGroupEntity imGroupEntity = new ImGroupEntity();

        if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode() && StringUtils.isBlank(req.getOwnerId())) {
            throw new ApplicationException(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
        }

        if (req.getCreateTime() == null) {
            imGroupEntity.setCreateTime(System.currentTimeMillis());
        }
        imGroupEntity.setStatus(GroupStatusEnum.NORMAL.getCode());
        BeanUtils.copyProperties(req, imGroupEntity);
        int insert = imGroupDataMapper.insert(imGroupEntity);

        if (insert != 1) {
            throw new ApplicationException(GroupErrorCode.IMPORT_GROUP_ERROR);
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result createGroup(CreateGroupRequest req) {

        boolean isAdmin = false;

        if (!isAdmin) {
            req.setOwnerId(req.getOperater());
        }

        //1.判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();

        if (StringUtils.isEmpty(req.getGroupId())) {
            req.setGroupId(UUID.randomUUID().toString().replace("-", ""));
        } else {
            query.eq("group_id", req.getGroupId());
            query.eq("app_id", req.getAppId());
            Integer integer = imGroupDataMapper.selectCount(query);
            if (integer > 0) {
                throw new ApplicationException(GroupErrorCode.GROUP_IS_EXIST);
            }
        }

        if (req.getGroupType() == GroupTypeEnum.PUBLIC.getCode() && StringUtils.isBlank(req.getOwnerId())) {
            throw new ApplicationException(GroupErrorCode.PUBLIC_GROUP_MUST_HAVE_OWNER);
        }

        ImGroupEntity imGroupEntity = new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + ImConstants.Sequence.GROUP);
        imGroupEntity.setSequence(seq);
        imGroupEntity.setCreateTime(System.currentTimeMillis());
        imGroupEntity.setStatus(GroupStatusEnum.NORMAL.getCode());
        BeanUtils.copyProperties(req, imGroupEntity);
        int insert = imGroupDataMapper.insert(imGroupEntity);

        GroupMemberDto groupMemberDto = new GroupMemberDto();
        groupMemberDto.setMemberId(req.getOwnerId());
        groupMemberDto.setRole(GroupMemberRoleEnum.OWNER.getCode());
        groupMemberDto.setJoinTime(System.currentTimeMillis());
        groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), groupMemberDto);

        //插入群成员
        for (GroupMemberDto dto : req.getMembers()) {
            groupMemberService.addGroupMember(req.getGroupId(), req.getAppId(), dto);
        }

        if(appConfig.getCallback().isCreateGroupAfterCallback()){
            callbackService.callback(req.getAppId(), ImConstants.CallbackCommand.CREATE_GROUP_AFTER,
                    JSONObject.toJSONString(imGroupEntity));
        }

        CreateGroupPack createGroupPack = new CreateGroupPack();
        BeanUtils.copyProperties(imGroupEntity, createGroupPack);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.CREATED_GROUP, createGroupPack
                , new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));
        return Result.ok();
    }

    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description 修改群基础信息，如果是后台管理员调用，则不检查权限，如果不是则检查权限，如果是私有群（微信群）任何人都可以修改资料，公开群只有管理员可以修改
     * 如果是群主或者管理员可以修改其他信息。
     * @author wanqiu
     */
    @Override
    @Transactional
    public Result updateBaseGroupInfo(UpdateGroupRequest req) {

        //1.判断群id是否存在
        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
        query.eq("group_id", req.getGroupId());
        query.eq("app_id", req.getAppId());
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(query);
        if (imGroupEntity == null) {
            throw new ApplicationException(GroupErrorCode.GROUP_IS_EXIST);
        }

        if(imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        boolean isAdmin = false;

        if (!isAdmin) {
            //不是后台调用需要检查权限
            Result<GetRoleInGroupResp> role = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());

            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode() || roleInfo == GroupMemberRoleEnum.OWNER.getCode();

            //公开群只能群主修改资料
            if (!isManager && GroupTypeEnum.PUBLIC.getCode() == imGroupEntity.getGroupType()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }

        }

        ImGroupEntity update = new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + ImConstants.Sequence.GROUP);
        BeanUtils.copyProperties(req, update);
        update.setUpdateTime(System.currentTimeMillis());
        update.setSequence(seq);
        int row = imGroupDataMapper.update(update, query);
        if (row != 1) {
            throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
        }

        if(appConfig.getCallback().isModifyGroupAfterCallback()){
            callbackService.callback(req.getAppId(),ImConstants.CallbackCommand.UPDATE_GROUP_AFTER,
                    JSONObject.toJSONString(imGroupDataMapper.selectOne(query)));
        }

        UpdateGroupInfoPack pack = new UpdateGroupInfoPack();
        BeanUtils.copyProperties(req, pack);
        groupMessageProducer.producer(req.getOperater(), GroupEventCommand.UPDATED_GROUP,
                pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));


        return Result.ok();
    }

    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description 获取用户加入的群组
     * @author wanqiu
     */
    @Override
    public Result getJoinedGroup(GetJoinedGroupRequest req) {

        Result<Collection<String>> memberJoinedGroup = groupMemberService.getMemberJoinedGroup(req);
        if (memberJoinedGroup.isOk()) {

            GetJoinedGroupResp resp = new GetJoinedGroupResp();

            if (CollectionUtils.isEmpty(memberJoinedGroup.getData())) {
                resp.setTotalCount(0);
                resp.setGroupList(new ArrayList<>());
                return Result.ok(resp);
            }

            QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
            query.eq("app_id", req.getAppId());
            query.in("group_id", memberJoinedGroup.getData());

            if (CollectionUtils.isNotEmpty(req.getGroupType())) {
                query.in("group_type", req.getGroupType());
            }

            List<ImGroupEntity> groupList = imGroupDataMapper.selectList(query);
            resp.setGroupList(groupList);
            if (req.getLimit() == null) {
                resp.setTotalCount(groupList.size());
            } else {
                resp.setTotalCount(imGroupDataMapper.selectCount(query));
            }
            return Result.ok(resp);
        } else {
            return memberJoinedGroup;
        }
    }


    /**
     * @param
     * @return com.vela.im.shared.Result
     * @description 解散群组，只支持后台管理员和群主解散
     * @author wanqiu
     */
    @Override
    @Transactional
    public Result destroyGroup(DestroyGroupRequest req) {

        boolean isAdmin = false;

        QueryWrapper<ImGroupEntity> objectQueryWrapper = new QueryWrapper<>();
        objectQueryWrapper.eq("group_id", req.getGroupId());
        objectQueryWrapper.eq("app_id", req.getAppId());
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(objectQueryWrapper);
        if (imGroupEntity == null) {
            throw new ApplicationException(GroupErrorCode.PRIVATE_GROUP_CAN_NOT_DESTORY);
        }

        if(imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        if (!isAdmin) {
            if (imGroupEntity.getGroupType() == GroupTypeEnum.PUBLIC.getCode()) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }

            if (imGroupEntity.getGroupType() == GroupTypeEnum.PUBLIC.getCode() &&
                    !imGroupEntity.getOwnerId().equals(req.getOperater())) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
            }
        }

        ImGroupEntity update = new ImGroupEntity();
        long seq = redisSeq.doGetSeq(req.getAppId() + ":" + ImConstants.Sequence.GROUP);

        update.setStatus(GroupStatusEnum.DESTROY.getCode());
        update.setSequence(seq);
        int update1 = imGroupDataMapper.update(update, objectQueryWrapper);
        if (update1 != 1) {
            throw new ApplicationException(GroupErrorCode.UPDATE_GROUP_BASE_INFO_ERROR);
        }

        if(appConfig.getCallback().isModifyGroupAfterCallback()){
            DestroyGroupCallbackDto dto = new DestroyGroupCallbackDto();
            dto.setGroupId(req.getGroupId());
            callbackService.callback(req.getAppId()
                    ,ImConstants.CallbackCommand.DESTROY_GROUP_AFTER,
                    JSONObject.toJSONString(dto));
        }

        DestroyGroupPack pack = new DestroyGroupPack();
        pack.setSequence(seq);
        pack.setGroupId(req.getGroupId());
        groupMessageProducer.producer(req.getOperater(),
                GroupEventCommand.DESTROY_GROUP, pack, new ClientInfo(req.getAppId(), req.getClientType(), req.getImei()));

        return Result.ok();
    }

    @Override
    @Transactional
    public Result transferGroup(TransferGroupRequest req) {

        Result<GetRoleInGroupResp> roleInGroupOne = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());
        if (!roleInGroupOne.isOk()) {
            return roleInGroupOne;
        }

        if (roleInGroupOne.getData().getRole() != GroupMemberRoleEnum.OWNER.getCode()) {
            return Result.fail(GroupErrorCode.THIS_OPERATE_NEED_OWNER_ROLE);
        }

        Result<GetRoleInGroupResp> newOwnerRole = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOwnerId(), req.getAppId());
        if (!newOwnerRole.isOk()) {
            return newOwnerRole;
        }

        QueryWrapper<ImGroupEntity> objectQueryWrapper = new QueryWrapper<>();
        objectQueryWrapper.eq("group_id", req.getGroupId());
        objectQueryWrapper.eq("app_id", req.getAppId());
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(objectQueryWrapper);
        if(imGroupEntity.getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        ImGroupEntity updateGroup = new ImGroupEntity();
        updateGroup.setOwnerId(req.getOwnerId());
        UpdateWrapper<ImGroupEntity> updateGroupWrapper = new UpdateWrapper<>();
        updateGroupWrapper.eq("app_id", req.getAppId());
        updateGroupWrapper.eq("group_id", req.getGroupId());
        imGroupDataMapper.update(updateGroup, updateGroupWrapper);
        groupMemberService.transferGroupMember(req.getOwnerId(), req.getGroupId(), req.getAppId());

        return Result.ok();
    }

    @Override
    public Result getGroup(String groupId, Integer appId) {

        QueryWrapper<ImGroupEntity> query = new QueryWrapper<>();
        query.eq("app_id", appId);
        query.eq("group_id", groupId);
        ImGroupEntity imGroupEntity = imGroupDataMapper.selectOne(query);

        if (imGroupEntity == null) {
            return Result.fail(GroupErrorCode.GROUP_IS_NOT_EXIST);
        }
        return Result.ok(imGroupEntity);
    }

    @Override
    public Result getGroup(GetGroupRequest req) {

        Result group = this.getGroup(req.getGroupId(), req.getAppId());

        if(!group.isOk()){
            return group;
        }

        GetGroupResp getGroupResp = new GetGroupResp();
        BeanUtils.copyProperties(group.getData(), getGroupResp);
        try {
            Result<List<GroupMemberDto>> groupMember = groupMemberService.getGroupMember(req.getGroupId(), req.getAppId());
            if (groupMember.isOk()) {
                getGroupResp.setMemberList(groupMember.getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.ok(getGroupResp);
    }

    @Override
    public Result muteGroup(MuteGroupRequest req) {

        Result<ImGroupEntity> groupResp = getGroup(req.getGroupId(), req.getAppId());
        if (!groupResp.isOk()) {
            return groupResp;
        }

        if(groupResp.getData().getStatus() == GroupStatusEnum.DESTROY.getCode()){
            throw new ApplicationException(GroupErrorCode.GROUP_IS_DESTROY);
        }

        boolean isadmin = false;

        if (!isadmin) {
            //不是后台调用需要检查权限
            Result<GetRoleInGroupResp> role = groupMemberService.getRoleInGroupOne(req.getGroupId(), req.getOperater(), req.getAppId());

            if (!role.isOk()) {
                return role;
            }

            GetRoleInGroupResp data = role.getData();
            Integer roleInfo = data.getRole();

            boolean isManager = roleInfo == GroupMemberRoleEnum.MAMAGER.getCode() || roleInfo == GroupMemberRoleEnum.OWNER.getCode();

            //公开群只能群主修改资料
            if (!isManager) {
                throw new ApplicationException(GroupErrorCode.THIS_OPERATE_NEED_MANAGER_ROLE);
            }
        }

        ImGroupEntity update = new ImGroupEntity();
        update.setMute(req.getMute());

        UpdateWrapper<ImGroupEntity> wrapper = new UpdateWrapper<>();
        wrapper.eq("group_id",req.getGroupId());
        wrapper.eq("app_id",req.getAppId());
        imGroupDataMapper.update(update,wrapper);

        return Result.ok();
    }

    @Override
    public Result syncJoinedGroupList(SyncReq req) {
        if(req.getMaxLimit() > 100){
            req.setMaxLimit(100);
        }

        SyncResp<ImGroupEntity> resp = new SyncResp<>();

        Result<Collection<String>> memberJoinedGroup = groupMemberService.syncMemberJoinedGroup(req.getOperater(), req.getAppId());
        if(memberJoinedGroup.isOk()){

            Collection<String> data = memberJoinedGroup.getData();
            QueryWrapper<ImGroupEntity> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("app_id",req.getAppId());
            queryWrapper.in("group_id",data);
            queryWrapper.gt("sequence",req.getLastSequence());
            queryWrapper.last(" limit " + req.getMaxLimit());
            queryWrapper.orderByAsc("sequence");

            List<ImGroupEntity> list = imGroupDataMapper.selectList(queryWrapper);

            if(!CollectionUtils.isEmpty(list)){
                ImGroupEntity maxSeqEntity
                        = list.get(list.size() - 1);
                resp.setDataList(list);
                //设置最大seq
                Long maxSeq =
                        imGroupDataMapper.getGroupMaxSeq(data, req.getAppId());
                resp.setMaxSequence(maxSeq);
                //设置是否拉取完毕
                resp.setCompleted(maxSeqEntity.getSequence() >= maxSeq);
                return Result.ok(resp);
            }

        }
        resp.setCompleted(true);
        return Result.ok(resp);
    }

    @Override
    public Long getUserGroupMaxSeq(String userId, Integer appId) {

        Result<Collection<String>> memberJoinedGroup = groupMemberService.syncMemberJoinedGroup(userId, appId);
        if(!memberJoinedGroup.isOk()){
            throw new ApplicationException(BaseErrorCode.SYSTEM_ERROR);
        }
        Long maxSeq =
                imGroupDataMapper.getGroupMaxSeq(memberJoinedGroup.getData(),
                        appId);
        return maxSeq;
    }

}
