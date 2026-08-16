package com.vela.im.service.office.domain.service;

import com.vela.im.service.office.domain.entity.ApprovalEntity;
import com.vela.im.service.office.infrastructure.persistence.mapper.ApprovalMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.StatusConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * <p>Title: ApprovalServiceTest</p>
 * <p>Description: 审批链服务单元测试：多级流转、层级审批人校验、撤回。</p>
 * <p>项目名称: Vela</p>
 *
 * @author wanqiu
 * @since 1.2
 * @createTime 2026-08-16
 */
class ApprovalServiceTest {

    private ApprovalMapper mapper;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ApprovalMapper.class);
        service = new ApprovalService(mapper);
    }

    /**
     * 提交时根据审批链初始化：当前层=1、总层级=链长、审批人=链上第 1 人
     */
    @Test
    void submitInitializesChain() {
        ApprovalEntity entity = new ApprovalEntity();
        entity.setAppId(10000);
        entity.setApplicantId("applicant");
        entity.setTitle("请假");
        entity.setApproverChain("u001,u002");

        when(mapper.insert(any(ApprovalEntity.class))).thenReturn(1);
        Result<ApprovalEntity> result = service.submit(entity);

        assertTrue(result.isOk());
        assertEquals(2, entity.getMaxLevel());
        assertEquals(1, entity.getCurrentLevel());
        assertEquals("u001", entity.getApproverId());
        assertEquals(StatusConstants.PENDING, entity.getStatus());
    }

    /**
     * 首层通过后流转至下一层（审批人切换为链上第 2 人，状态仍为待审批）
     */
    @Test
    void approveFirstLevelFlowsToNext() {
        ApprovalEntity entity = chainEntity(2, 1, "u001");
        when(mapper.selectById(1L)).thenReturn(entity);

        Result<Void> result = service.approve(1L, "u001", "同意", true);

        assertTrue(result.isOk());
        assertEquals(2, entity.getCurrentLevel());
        assertEquals("u002", entity.getApproverId());
        assertEquals(StatusConstants.PENDING, entity.getStatus());
    }

    /**
     * 末层通过后审批完成（DONE）
     */
    @Test
    void approveLastLevelCompletes() {
        ApprovalEntity entity = chainEntity(2, 2, "u002");
        when(mapper.selectById(1L)).thenReturn(entity);

        Result<Void> result = service.approve(1L, "u002", "同意", true);

        assertTrue(result.isOk());
        assertEquals(StatusConstants.DONE, entity.getStatus());
    }

    /**
     * 非当前层审批人无权审批
     */
    @Test
    void approveRejectsNonCurrentApprover() {
        ApprovalEntity entity = chainEntity(2, 1, "u001");
        when(mapper.selectById(1L)).thenReturn(entity);

        Result<Void> result = service.approve(1L, "u002", "越权", true);

        assertFalse(result.isOk());
        assertEquals(StatusConstants.PENDING, entity.getStatus());
    }

    /**
     * 拒绝即终态（REJECTED），不再流转
     */
    @Test
    void approveRejectIsTerminal() {
        ApprovalEntity entity = chainEntity(2, 1, "u001");
        when(mapper.selectById(1L)).thenReturn(entity);

        Result<Void> result = service.approve(1L, "u001", "不同意", false);

        assertTrue(result.isOk());
        assertEquals(StatusConstants.REJECTED, entity.getStatus());
    }

    /**
     * 撤回仅限待审批状态
     */
    @Test
    void recallOnlyPending() {
        ApprovalEntity entity = chainEntity(1, 1, "u001");
        entity.setStatus(StatusConstants.DONE);
        when(mapper.selectById(1L)).thenReturn(entity);

        Result<Void> result = service.recall(1L);

        assertFalse(result.isOk());
        assertEquals(StatusConstants.DONE, entity.getStatus());
    }

    private ApprovalEntity chainEntity(int maxLevel, int currentLevel, String approverId) {
        ApprovalEntity e = new ApprovalEntity();
        e.setId(1L);
        e.setAppId(10000);
        e.setApplicantId("applicant");
        e.setTitle("请假");
        e.setApproverChain("u001,u002");
        e.setMaxLevel(maxLevel);
        e.setCurrentLevel(currentLevel);
        e.setApproverId(approverId);
        e.setStatus(StatusConstants.PENDING);
        return e;
    }
}
