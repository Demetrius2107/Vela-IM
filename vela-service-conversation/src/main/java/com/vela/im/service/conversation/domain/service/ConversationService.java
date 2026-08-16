package com.vela.im.service.conversation.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.vela.im.service.conversation.domain.entity.ImConversationSetEntity;
import com.vela.im.service.conversation.infrastructure.persistence.mapper.ImConversationSetMapper;
import com.vela.im.service.conversation.application.dto.ArchiveConversationReq;
import com.vela.im.service.conversation.application.dto.DeleteConversationReq;
import com.vela.im.service.conversation.application.dto.UpdateConversationReq;
import com.vela.im.service.common.infrastructure.seq.RedisSeq;
import com.vela.im.service.common.utils.MessageProducer;
import com.vela.im.service.common.utils.WriteUserSeq;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.config.ImServerProperties;
import com.vela.im.shared.constants.ImConstants;
import com.vela.im.shared.types.enums.ConversationErrorCode;
import com.vela.im.shared.types.enums.ConversationTypeEnum;
import com.vela.im.shared.types.enums.command.ConversationEventCommand;
import com.vela.im.shared.types.ClientInfo;
import com.vela.im.shared.types.SyncReq;
import com.vela.im.shared.types.SyncResp;
import com.vela.im.shared.types.message.MessageReadedContent;
import com.vela.im.codec.pack.conversation.DeleteConversationPack;
import com.vela.im.codec.pack.conversation.UpdateConversationPack;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>Title: ConversationService</p>
 * <p>Description: 会话管理服务，处理会话已读标记、删除、更新（置顶/免打扰）、增量同步等。</p>
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
public class ConversationService {

    private final ImConversationSetMapper imConversationSetMapper;
    private final MessageProducer messageProducer;
    private final ImServerProperties appConfig;
    private final RedisSeq redisSeq;
    private final WriteUserSeq writeUserSeq;

    public ConversationService(ImConversationSetMapper imConversationSetMapper,
                               MessageProducer messageProducer,
                               ImServerProperties appConfig,
                               RedisSeq redisSeq,
                               WriteUserSeq writeUserSeq) {
        this.imConversationSetMapper = imConversationSetMapper;
        this.messageProducer = messageProducer;
        this.appConfig = appConfig;
        this.redisSeq = redisSeq;
        this.writeUserSeq = writeUserSeq;
    }

    public String convertConversationId(Integer type,String fromId,String toId){
        return type + "_" + fromId + "_" + toId;
    }

    public void  messageMarkRead(MessageReadedContent messageReadedContent){

        String toId = messageReadedContent.getToId();
        if(messageReadedContent.getConversationType() == ConversationTypeEnum.GROUP.getCode()){
            toId = messageReadedContent.getGroupId();
        }
        String conversationId = convertConversationId(messageReadedContent.getConversationType(),
                messageReadedContent.getFromId(), toId);
        QueryWrapper<ImConversationSetEntity> query = new QueryWrapper<>();
        query.eq("conversation_id",conversationId);
        query.eq("app_id",messageReadedContent.getAppId());
        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(query);
        if(imConversationSetEntity == null){
            imConversationSetEntity = new ImConversationSetEntity();
            long seq = redisSeq.doGetSeq(messageReadedContent.getAppId() + ":" + ImConstants.Sequence.CONVERSATION);
            imConversationSetEntity.setConversationId(conversationId);
            BeanUtils.copyProperties(messageReadedContent,imConversationSetEntity);
            imConversationSetEntity.setReadedSequence(messageReadedContent.getMessageSequence());
            imConversationSetEntity.setToId(toId);
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.insert(imConversationSetEntity);
            writeUserSeq.writeUserSeq(messageReadedContent.getAppId(),
                    messageReadedContent.getFromId(),ImConstants.Sequence.CONVERSATION,seq);
        }else{
            long seq = redisSeq.doGetSeq(messageReadedContent.getAppId() + ":" + ImConstants.Sequence.CONVERSATION);
            imConversationSetEntity.setSequence(seq);
            imConversationSetEntity.setReadedSequence(messageReadedContent.getMessageSequence());
            imConversationSetMapper.readMark(imConversationSetEntity);
            writeUserSeq.writeUserSeq(messageReadedContent.getAppId(),
                    messageReadedContent.getFromId(),ImConstants.Sequence.CONVERSATION,seq);
        }
    }

    /**
     * @description: 删除会话
     * @param
     * @return com.vela.im.shared.Result
     * @author wanqiu
     */
    public Result deleteConversation(DeleteConversationReq req){

        // 删除会话：物理删除会话集记录（置顶/免打扰状态一并清除，会话列表不再出现），并广播删除事件
        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id",req.getConversationId());
        queryWrapper.eq("app_id",req.getAppId());
        imConversationSetMapper.delete(queryWrapper);

        if(appConfig.getDeleteConversationSyncMode() == 1){
            DeleteConversationPack pack = new DeleteConversationPack();
            pack.setConversationId(req.getConversationId());
            messageProducer.sendToUserExceptClient(req.getFromId(),
                    ConversationEventCommand.CONVERSATION_DELETE,
                    pack,new ClientInfo(req.getAppId(),req.getClientType(),
                            req.getImei()));
        }
        return Result.ok();
    }

    /**
     * @description: 更新会话 置顶or免打扰
     * @param
     * @return com.vela.im.shared.Result
     * @author wanqiu
     */
    public Result updateConversation(UpdateConversationReq req){



        if(req.getIsTop() == null && req.getIsMute() == null){
            return Result.fail(ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR);
        }
        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id",req.getConversationId());
        queryWrapper.eq("app_id",req.getAppId());
        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(queryWrapper);
        if(imConversationSetEntity != null){
            long seq = redisSeq.doGetSeq(req.getAppId() + ":" + ImConstants.Sequence.CONVERSATION);

            // 置顶/免打扰各自独立更新（修复原 isMute 判断写反导致 isTop 分支失效的问题）
            if(req.getIsTop() != null){
                imConversationSetEntity.setIsTop(req.getIsTop());
            }
            if(req.getIsMute() != null){
                imConversationSetEntity.setIsMute(req.getIsMute());
            }
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.update(imConversationSetEntity,queryWrapper);
            writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(),
                    ImConstants.Sequence.CONVERSATION, seq);

            UpdateConversationPack pack = new UpdateConversationPack();
            pack.setConversationId(req.getConversationId());
            pack.setIsMute(imConversationSetEntity.getIsMute());
            pack.setIsTop(imConversationSetEntity.getIsTop());
            pack.setSequence(seq);
            pack.setConversationType(imConversationSetEntity.getConversationType());
            messageProducer.sendToUserExceptClient(req.getFromId(),
                    ConversationEventCommand.CONVERSATION_UPDATE,
                    pack,new ClientInfo(req.getAppId(),req.getClientType(),
                            req.getImei()));
        }
        return Result.ok();
    }

    /**
     * @description: 归档/取消归档会话（置顶/免打扰状态一并清除，会话从列表隐藏但保留记录）
     * @param req 归档请求（isArchive 1-归档 0-取消归档）
     * @return com.vela.im.shared.Result
     * @author wanqiu
     */
    public Result archiveConversation(ArchiveConversationReq req){
        if(req.getIsArchive() == null){
            return Result.fail(ConversationErrorCode.CONVERSATION_UPDATE_PARAM_ERROR);
        }
        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id",req.getConversationId());
        queryWrapper.eq("app_id",req.getAppId());
        ImConversationSetEntity imConversationSetEntity = imConversationSetMapper.selectOne(queryWrapper);
        if(imConversationSetEntity != null){
            long seq = redisSeq.doGetSeq(req.getAppId() + ":" + ImConstants.Sequence.CONVERSATION);
            imConversationSetEntity.setIsArchive(req.getIsArchive());
            // 归档时同步清除置顶/免打扰状态，取消归档时恢复为正常会话
            if(req.getIsArchive() == 1){
                imConversationSetEntity.setIsTop(0);
                imConversationSetEntity.setIsMute(0);
            }
            imConversationSetEntity.setSequence(seq);
            imConversationSetMapper.update(imConversationSetEntity,queryWrapper);
            writeUserSeq.writeUserSeq(req.getAppId(), req.getFromId(),
                    ImConstants.Sequence.CONVERSATION, seq);

            UpdateConversationPack pack = new UpdateConversationPack();
            pack.setConversationId(req.getConversationId());
            pack.setIsMute(imConversationSetEntity.getIsMute());
            pack.setIsTop(imConversationSetEntity.getIsTop());
            pack.setSequence(seq);
            pack.setConversationType(imConversationSetEntity.getConversationType());
            messageProducer.sendToUserExceptClient(req.getFromId(),
                    ConversationEventCommand.CONVERSATION_UPDATE,
                    pack,new ClientInfo(req.getAppId(),req.getClientType(),
                            req.getImei()));
        }
        return Result.ok();
    }

    /**
     * @description: 会话列表查询（置顶优先 + 最近活跃优先，默认排除已归档会话）
     * @param fromId 用户 ID
     * @param appId  应用 ID
     * @return 排序后的会话集列表
     * @author wanqiu
     */
    public Result<List<ImConversationSetEntity>> listConversation(String fromId, Integer appId) {
        QueryWrapper<ImConversationSetEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("from_id", fromId);
        queryWrapper.eq("app_id", appId);
        // 已归档会话默认不进入会话列表，置顶会话优先（is_top=1 排最前），同级按 sequence 倒序（最近活跃的会话排前面）
        queryWrapper.eq("is_archive", 0);
        queryWrapper.orderByDesc("is_top");
        queryWrapper.orderByDesc("sequence");
        List<ImConversationSetEntity> list = imConversationSetMapper.selectList(queryWrapper);
        return Result.ok(list);
    }

    public Result syncConversationSet(SyncReq req) {
        if(req.getMaxLimit() > 100){
            req.setMaxLimit(100);
        }

        SyncResp<ImConversationSetEntity> resp = new SyncResp<>();
        //seq > req.getseq limit maxLimit
        QueryWrapper<ImConversationSetEntity> queryWrapper =
                new QueryWrapper<>();
        queryWrapper.eq("from_id",req.getOperater());
        queryWrapper.gt("sequence",req.getLastSequence());
        queryWrapper.eq("app_id",req.getAppId());
        queryWrapper.last(" limit " + req.getMaxLimit());
        queryWrapper.orderByAsc("sequence");
        List<ImConversationSetEntity> list = imConversationSetMapper
                .selectList(queryWrapper);

        if(!CollectionUtils.isEmpty(list)){
            ImConversationSetEntity maxSeqEntity = list.get(list.size() - 1);
            resp.setDataList(list);
            //设置最大seq
            Long friendShipMaxSeq = imConversationSetMapper.geConversationSetMaxSeq(req.getAppId(), req.getOperater());
            resp.setMaxSequence(friendShipMaxSeq);
            //设置是否拉取完毕
            resp.setCompleted(maxSeqEntity.getSequence() >= friendShipMaxSeq);
            return Result.ok(resp);
        }

        resp.setCompleted(true);
        return Result.ok(resp);

    }
}
