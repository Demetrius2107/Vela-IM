package com.vela.im.service.office.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vela.im.service.office.domain.entity.ApprovalEntity;
import com.vela.im.service.office.infrastructure.persistence.mapper.ApprovalMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import com.vela.im.shared.types.enums.StatusConstants;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.HashMap;

@Service
public class ApprovalService {

    private final ApprovalMapper mapper;

    public ApprovalService(ApprovalMapper mapper) { this.mapper = mapper; }

    /**
     * 提交审批：根据审批链初始化审批层级（当前层=第 1 层，审批人=链上第 1 人）
     *
     * @param entity 审批实体（approverChain 为逗号分隔的审批人 ID 列表）
     * @return 提交后的审批记录
     */
    public Result<ApprovalEntity> submit(ApprovalEntity entity) {
        entity.setStatus(StatusConstants.PENDING);
        entity.setCreateTime(System.currentTimeMillis());
        entity.setUpdateTime(entity.getCreateTime());
        // 审批链默认单级（仅 approverId）
        if (!StringUtils.hasText(entity.getApproverChain()) && StringUtils.hasText(entity.getApproverId())) {
            entity.setApproverChain(entity.getApproverId());
        }
        int maxLevel = 1;
        if (StringUtils.hasText(entity.getApproverChain())) {
            maxLevel = entity.getApproverChain().split(",").length;
        }
        entity.setMaxLevel(maxLevel);
        entity.setCurrentLevel(1);
        entity.setApproverId(firstApprover(entity.getApproverChain()));
        mapper.insert(entity);
        return Result.ok(entity);
    }

    public Result<Map<String, Object>> list(String userId, Integer appId, Integer status, int page, int size) {
        QueryWrapper<ApprovalEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId);
        if (userId != null) q.eq("applicant_id", userId);
        if (status != null) q.eq("status", status);
        q.orderByDesc("create_time");
        IPage<ApprovalEntity> p = mapper.selectPage(new Page<>(page + 1, size), q);
        Map<String, Object> r = new HashMap<>();
        r.put("list", p.getRecords()); r.put("total", p.getTotal());
        return Result.ok(r);
    }

    /**
     * 审批流转：仅当前层审批人可审批；通过后流转至下一层（非末层）或置为完成（末层），拒绝即终态
     *
     * @param id         审批记录 ID
     * @param approverId 审批人 ID
     * @param comment    审批意见
     * @param passed     是否通过
     * @return 操作结果
     */
    public Result<Void> approve(Long id, String approverId, String comment, boolean passed) {
        ApprovalEntity e = mapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.APPROVAL_NOT_FOUND);
        if (e.getStatus() != StatusConstants.PENDING) return Result.fail(BusinessErrorCode.APPROVAL_ALREADY_PROCESSED);
        // 非当前层审批人无权审批
        if (!approverId.equals(e.getApproverId())) {
            return Result.fail(BusinessErrorCode.APPROVAL_NOT_CURRENT_APPROVER);
        }

        if (passed) {
            int currentLevel = e.getCurrentLevel() == null ? 1 : e.getCurrentLevel();
            int maxLevel = e.getMaxLevel() == null ? 1 : e.getMaxLevel();
            if (currentLevel < maxLevel) {
                // 流转至下一层
                e.setCurrentLevel(currentLevel + 1);
                e.setApproverId(nthApprover(e.getApproverChain(), currentLevel + 1));
                e.setStatus(StatusConstants.PENDING);
            } else {
                // 末层通过，审批完成
                e.setStatus(StatusConstants.DONE);
            }
        } else {
            e.setStatus(StatusConstants.REJECTED);
        }
        e.setComment(comment);
        e.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(e);
        return Result.ok();
    }

    /**
     * 撤回审批：仅待审批状态可撤回（申请人发起）
     *
     * @param id 审批记录 ID
     * @return 操作结果
     */
    public Result<Void> recall(Long id) {
        ApprovalEntity e = mapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.APPROVAL_NOT_FOUND);
        if (e.getStatus() != StatusConstants.PENDING) return Result.fail(BusinessErrorCode.APPROVAL_ALREADY_PROCESSED);
        e.setStatus(StatusConstants.CANCELLED);
        e.setUpdateTime(System.currentTimeMillis());
        mapper.updateById(e);
        return Result.ok();
    }

    public Result<Void> delete(Long id) {
        mapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 取审批链首层审批人
     */
    private String firstApprover(String chain) {
        return nthApprover(chain, 1);
    }

    /**
     * 取审批链第 level 层审批人（逗号分隔，下标从 1 开始）
     */
    private String nthApprover(String chain, int level) {
        if (!StringUtils.hasText(chain)) return null;
        String[] levels = chain.split(",");
        if (level < 1 || level > levels.length) return null;
        return levels[level - 1];
    }
}
