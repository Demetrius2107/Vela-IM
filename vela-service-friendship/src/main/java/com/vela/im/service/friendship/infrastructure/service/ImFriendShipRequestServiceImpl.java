package com.vela.im.service.friendship.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import com.vela.im.service.friendship.domain.entity.ImFriendShipRequestEntity;
import com.vela.im.service.friendship.infrastructure.persistence.mapper.ImFriendShipRequestMapper;
import com.vela.im.service.friendship.application.dto.req.ApproveFriendRequestReq;
import com.vela.im.service.friendship.application.dto.req.FriendDto;
import com.vela.im.service.friendship.application.dto.req.ReadFriendShipRequestReq;
import com.vela.im.service.friendship.domain.service.ImFriendService;
import com.vela.im.service.friendship.domain.service.ImFriendShipRequestService;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.service.common.utils.WriteUserSeq;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ApproverFriendRequestStatusEnum;
import com.vela.im.shared.types.enums.FriendShipErrorCode;
import com.vela.im.shared.types.enums.command.FriendshipEventCommand;
import com.vela.im.shared.exception.ApplicationException;
import com.vela.im.codec.pack.friendship.ApproverFriendRequestPack;
import com.vela.im.codec.pack.friendship.ReadAllFriendRequestPack;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * <p>Title: ImFriendShipRequestServiceImpl</p>
 * <p>Description: 好友请求管理实现，处理好友申请的添加、审批、查询、已读等。</p>
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
public class ImFriendShipRequestServiceImpl implements ImFriendShipRequestService {

    private final ImFriendShipRequestMapper imFriendShipRequestMapper;
    private final ImFriendService imFriendShipService;
    private final MessageProducer messageProducer;
    private final RedisSeq redisSeq;
    private final WriteUserSeq writeUserSeq;

    public ImFriendShipRequestServiceImpl(ImFriendShipRequestMapper imFriendShipRequestMapper,
                                          @Lazy ImFriendService imFriendShipService,
                                          MessageProducer messageProducer,
                                          RedisSeq redisSeq,
                                          WriteUserSeq writeUserSeq) {
        this.imFriendShipRequestMapper = imFriendShipRequestMapper;
        this.imFriendShipService = imFriendShipService;
        this.messageProducer = messageProducer;
        this.redisSeq = redisSeq;
        this.writeUserSeq = writeUserSeq;
    }

    @Override
    public Result getFriendRequest(String fromId, Integer appId) {

        QueryWrapper<ImFriendShipRequestEntity> query = new QueryWrapper();
        query.eq("app_id", appId);
        query.eq("to_id", fromId);

        List<ImFriendShipRequestEntity> requestList = imFriendShipRequestMapper.selectList(query);

        return Result.ok(requestList);
    }


    //A + B

    public Result addFienshipRequest(String fromId, FriendDto dto, Integer appId) {

        QueryWrapper<ImFriendShipRequestEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("app_id",appId);
        queryWrapper.eq("from_id",fromId);
        queryWrapper.eq("to_id",dto.getToId());
        ImFriendShipRequestEntity request = imFriendShipRequestMapper.selectOne(queryWrapper);

        long seq = redisSeq.doGetSeq(appId+":"+
                ImConstants.Sequence.FRIENDSHIP_REQUEST);

        if(request == null){
            request = new ImFriendShipRequestEntity();
            request.setAddSource(dto.getAddSource());
            request.setAddWording(dto.getAddWording());
            request.setSequence(seq);
            request.setAppId(appId);
            request.setFromId(fromId);
            request.setToId(dto.getToId());
            request.setReadStatus(0);
            request.setApproveStatus(0);
            request.setRemark(dto.getRemark());
            request.setCreateTime(System.currentTimeMillis());
            imFriendShipRequestMapper.insert(request);

        }else {
            //修改记录内容 和更新时间
            if(StringUtils.isNotBlank(dto.getAddSource())){
                request.setAddWording(dto.getAddWording());
            }
            if(StringUtils.isNotBlank(dto.getRemark())){
                request.setRemark(dto.getRemark());
            }
            if(StringUtils.isNotBlank(dto.getAddWording())){
                request.setAddWording(dto.getAddWording());
            }
            request.setSequence(seq);
            request.setApproveStatus(0);
            request.setReadStatus(0);
            imFriendShipRequestMapper.updateById(request);
        }

        writeUserSeq.writeUserSeq(appId,dto.getToId(),
                ImConstants.Sequence.FRIENDSHIP_REQUEST,seq);

        //发送好友申请的tcp给接收方
        messageProducer.sendToUser(dto.getToId(),
                null, "", FriendshipEventCommand.FRIEND_REQUEST,
                request, appId);
        return Result.ok();
    }


    @Transactional
    public Result approverFriendRequest(ApproveFriendRequestReq req) {

        ImFriendShipRequestEntity imFriendShipRequestEntity = imFriendShipRequestMapper.selectById(req.getId());
        if(imFriendShipRequestEntity == null){
            throw new ApplicationException(FriendShipErrorCode.FRIEND_REQUEST_IS_NOT_EXIST);
        }

        if(!req.getOperater().equals(imFriendShipRequestEntity.getToId())){
            //只能审批发给自己的好友请求
            throw new ApplicationException(FriendShipErrorCode.NOT_APPROVER_OTHER_MAN_REQUEST);
        }

        long seq = redisSeq.doGetSeq(req.getAppId()+":"+
                ImConstants.Sequence.FRIENDSHIP_REQUEST);

        ImFriendShipRequestEntity update = new ImFriendShipRequestEntity();
        update.setApproveStatus(req.getStatus());
        update.setUpdateTime(System.currentTimeMillis());
        update.setSequence(seq);
        update.setId(req.getId());
        imFriendShipRequestMapper.updateById(update);

        writeUserSeq.writeUserSeq(req.getAppId(),req.getOperater(),
                ImConstants.Sequence.FRIENDSHIP_REQUEST,seq);

        if(ApproverFriendRequestStatusEnum.AGREE.getCode() == req.getStatus()){
            //同意 ===> 去执行添加好友逻辑
            FriendDto dto = new FriendDto();
            dto.setAddSource(imFriendShipRequestEntity.getAddSource());
            dto.setAddWording(imFriendShipRequestEntity.getAddWording());
            dto.setRemark(imFriendShipRequestEntity.getRemark());
            dto.setToId(imFriendShipRequestEntity.getToId());
            Result responseVO = imFriendShipService.doAddFriend(req,imFriendShipRequestEntity.getFromId(), dto,req.getAppId());
//            if(!responseVO.isOk()){
////                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
//                return responseVO;
//            }
            if(!responseVO.isOk() && responseVO.getCode() != FriendShipErrorCode.TO_IS_YOUR_FRIEND.getCode()){
                return responseVO;
            }
        }

        ApproverFriendRequestPack approverFriendRequestPack = new ApproverFriendRequestPack();
        approverFriendRequestPack.setId(req.getId());
        approverFriendRequestPack.setSequence(seq);
        approverFriendRequestPack.setStatus(req.getStatus());
        messageProducer.sendToUser(imFriendShipRequestEntity.getToId(),req.getClientType(),req.getImei(), FriendshipEventCommand
                .FRIEND_REQUEST_APPROVER,approverFriendRequestPack,req.getAppId());
        return Result.ok();
    }

    @Override
    public Result addFriendShipRequest(String fromId, FriendDto dto, Integer appId) {
        // 委托真实实现（addFienshipRequest 为历史拼写方法，承载完整申请逻辑）
        return addFienshipRequest(fromId, dto, appId);
    }

    @Override
    public Result approveFriendRequest(ApproveFriendRequestReq req) {
        // 委托真实实现（approverFriendRequest 为历史拼写方法，承载完整审批逻辑）
        return approverFriendRequest(req);
    }

    @Override
    public Result readFriendShipRequestReq(ReadFriendShipRequestReq req) {
        QueryWrapper<ImFriendShipRequestEntity> query = new QueryWrapper<>();
        query.eq("app_id", req.getAppId());
        query.eq("to_id", req.getFromId());

        long seq = redisSeq.doGetSeq(req.getAppId()+":"+
                ImConstants.Sequence.FRIENDSHIP_REQUEST);
        ImFriendShipRequestEntity update = new ImFriendShipRequestEntity();
        update.setReadStatus(1);
        update.setSequence(seq);
        imFriendShipRequestMapper.update(update, query);
        writeUserSeq.writeUserSeq(req.getAppId(),req.getOperater(),
                ImConstants.Sequence.FRIENDSHIP_REQUEST,seq);
        //TCP通知
        ReadAllFriendRequestPack readAllFriendRequestPack = new ReadAllFriendRequestPack();
        readAllFriendRequestPack.setFromId(req.getFromId());
        readAllFriendRequestPack.setSequence(seq);
        messageProducer.sendToUser(req.getFromId(),req.getClientType(),req.getImei(),FriendshipEventCommand
                .FRIEND_REQUEST_READ,readAllFriendRequestPack,req.getAppId());

        return Result.ok();
    }

}
