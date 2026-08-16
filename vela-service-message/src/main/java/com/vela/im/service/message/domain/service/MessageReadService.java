package com.vela.im.service.message.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.message.domain.entity.ImMessageReadEntity;
import com.vela.im.service.message.infrastructure.persistence.mapper.ImMessageReadMapper;
import com.vela.im.shared.base.Result;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息已读跟踪服务，记录每条消息的已读成员列表。
 */
@Service
public class MessageReadService {

    private final ImMessageReadMapper readMapper;

    public MessageReadService(ImMessageReadMapper readMapper) {
        this.readMapper = readMapper;
    }

    /**
     * 标记消息为已读（幂等）。
     * <p>并发安全：依赖表唯一索引 uk_message_member (message_key, member_id)，
     * 并发重复插入时捕获 DuplicateKeyException 视为幂等成功。</p>
     */
    public void markRead(Integer appId, String groupId, Long messageKey, String memberId) {
        QueryWrapper<ImMessageReadEntity> check = new QueryWrapper<>();
        check.eq("message_key", messageKey).eq("member_id", memberId);
        if (readMapper.selectCount(check) > 0) return;

        ImMessageReadEntity entity = new ImMessageReadEntity();
        entity.setAppId(appId);
        entity.setGroupId(groupId);
        entity.setMessageKey(messageKey);
        entity.setMemberId(memberId);
        entity.setReadTime(System.currentTimeMillis());
        try {
            readMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 并发重复标记：唯一索引兜底，视为已读成功
        }
    }

    /** 批量标记已读（用于同步离线消息后） */
    public void markBatchRead(Integer appId, String groupId, List<Long> messageKeys, String memberId) {
        for (Long key : messageKeys) {
            markRead(appId, groupId, key, memberId);
        }
    }

    /** 获取已读成员列表 */
    public Result<List<String>> getReadMembers(Long messageKey) {
        QueryWrapper<ImMessageReadEntity> query = new QueryWrapper<>();
        query.eq("message_key", messageKey).orderByAsc("read_time");
        List<ImMessageReadEntity> list = readMapper.selectList(query);
        List<String> members = list.stream().map(ImMessageReadEntity::getMemberId).collect(Collectors.toList());
        return Result.ok(members);
    }

    /** 获取未读成员列表 */
    public Result<List<String>> getUnreadMembers(Long messageKey, List<String> allMembers) {
        List<String> readMembers = getReadMembers(messageKey).getData();
        List<String> unread = allMembers.stream()
                .filter(m -> !readMembers.contains(m))
                .collect(Collectors.toList());
        return Result.ok(unread);
    }
}
