package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.vela.im.service.knowledge.domain.entity.DocApprovalEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocApprovalMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 文档发布审批流服务（状态机）。
 *
 * 状态机转移表：
 * <pre>
 *   DRAFT(0)    --submit-->  PENDING(1)
 *   REJECTED(3) --submit-->  PENDING(1)
 *   PENDING(1)  --approve--> PUBLISHED(2)
 *   PENDING(1)  --reject-->  REJECTED(3)  （必须填写原因）
 *   PUBLISHED(2)--unpublish-> DRAFT(0)
 * </pre>
 * 非法流转一律返回 DOCUMENT_STATUS_ILLEGAL；审批人必须是应用管理员。
 */
@Service
public class ApprovalService {

    private final DocApprovalMapper approvalMapper;
    private final DocumentMapper documentMapper;
    private final PermissionService permissionService;

    public ApprovalService(DocApprovalMapper approvalMapper, DocumentMapper documentMapper,
                           PermissionService permissionService) {
        this.approvalMapper = approvalMapper;
        this.documentMapper = documentMapper;
        this.permissionService = permissionService;
    }

    /** 提交审核：仅 DRAFT / REJECTED 可提交，且作者本人或编辑者 */
    @Transactional
    public Result<Void> submit(Integer appId, String userId, Long docId) {
        DocumentEntity doc = requireDoc(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!permissionService.canWrite(appId, userId, doc)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        int s = doc.getStatus() == null ? DocumentService.STATUS_DRAFT : doc.getStatus();
        if (s != DocumentService.STATUS_DRAFT && s != DocumentService.STATUS_REJECTED) {
            return Result.fail(BusinessErrorCode.DOCUMENT_STATUS_ILLEGAL);
        }
        doc.setStatus(DocumentService.STATUS_PENDING);
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.updateById(doc);
        return Result.ok();
    }

    /** 审批：仅应用管理员可操作，PENDING 状态才允许 */
    @Transactional
    public Result<Void> handle(Integer appId, String approverId, Long docId, String action, String reason) {
        DocumentEntity doc = requireDoc(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!permissionService.isAdmin(appId, approverId)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        int s = doc.getStatus() == null ? DocumentService.STATUS_DRAFT : doc.getStatus();
        if (s != DocumentService.STATUS_PENDING) {
            return Result.fail(BusinessErrorCode.DOCUMENT_STATUS_ILLEGAL);
        }
        boolean approve = "approve".equalsIgnoreCase(action);
        boolean reject = "reject".equalsIgnoreCase(action);
        if (!approve && !reject) return Result.fail(BusinessErrorCode.BAD_REQUEST);
        if (reject && !StringUtils.hasText(reason)) {
            return Result.fail(BusinessErrorCode.APPROVAL_REASON_REQUIRED);
        }
        doc.setStatus(approve ? DocumentService.STATUS_PUBLISHED : DocumentService.STATUS_REJECTED);
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.updateById(doc);

        DocApprovalEntity rec = new DocApprovalEntity();
        rec.setAppId(appId);
        rec.setDocId(docId);
        rec.setApproverId(approverId);
        rec.setAction(reject ? "reject" : "approve");
        rec.setReason(reason);
        rec.setCreateTime(System.currentTimeMillis());
        approvalMapper.insert(rec);
        return Result.ok();
    }

    /** 下架：已发布文档撤回到草稿（作者/管理员），重新编辑后再走审批 */
    @Transactional
    public Result<Void> unpublish(Integer appId, String userId, Long docId) {
        DocumentEntity doc = requireDoc(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!permissionService.canWrite(appId, userId, doc)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        int s = doc.getStatus() == null ? DocumentService.STATUS_DRAFT : doc.getStatus();
        if (s != DocumentService.STATUS_PUBLISHED) {
            return Result.fail(BusinessErrorCode.DOCUMENT_STATUS_ILLEGAL);
        }
        doc.setStatus(DocumentService.STATUS_DRAFT);
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.updateById(doc);
        return Result.ok();
    }

    /** 待审批列表（审批人视角）：仅管理员可查 */
    public Result<Map<String, Object>> pending(Integer appId, String approverId, int page, int size) {
        if (!permissionService.isAdmin(appId, approverId)) {
            return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        }
        QueryWrapper<DocumentEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId)
                .eq("is_deleted", 0)
                .eq("status", DocumentService.STATUS_PENDING)
                .orderByAsc("update_time");
        IPage<DocumentEntity> p = documentMapper.selectPage(new Page<>(page + 1, size), q);
        Map<String, Object> r = new HashMap<>();
        r.put("list", p.getRecords());
        r.put("total", p.getTotal());
        return Result.ok(r);
    }

    private DocumentEntity requireDoc(Long docId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) return null;
        return doc;
    }
}
