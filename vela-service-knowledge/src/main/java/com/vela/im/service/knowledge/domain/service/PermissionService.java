package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocPermissionEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocPermissionMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档权限服务（RBAC）。
 * 角色：admin(应用管理员) / owner(作者) / editor(编辑) / reader(只读)。
 * docId=0 的记录表示应用级角色（如应用管理员）。
 */
@Service
public class PermissionService {

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_EDITOR = "editor";
    public static final String ROLE_READER = "reader";

    private final DocPermissionMapper permissionMapper;
    private final DocumentMapper documentMapper;

    public PermissionService(DocPermissionMapper permissionMapper, DocumentMapper documentMapper) {
        this.permissionMapper = permissionMapper;
        this.documentMapper = documentMapper;
    }

    // ==================== 校验 ====================

    /** 是否应用管理员（docId=0 且 role=admin） */
    public boolean isAdmin(Integer appId, String userId) {
        if (userId == null) return false;
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", 0).eq("user_id", userId).eq("role", ROLE_ADMIN);
        return permissionMapper.selectCount(q) > 0;
    }

    /** 是否可读：作者/管理员/授权用户可读；公开文档所有人可读 */
    public boolean canRead(Integer appId, String userId, DocumentEntity doc) {
        if (doc == null) return false;
        if (doc.getVisibility() == null || doc.getVisibility() == 0) return true; // 公开
        if (userId == null) return false;
        if (doc.getCreatorId() != null && doc.getCreatorId().equals(userId)) return true;
        if (isAdmin(appId, userId)) return true;
        return hasAnyRole(appId, doc.getId(), userId);
    }

    /** 是否可写：作者/管理员/editor 及以上角色可写 */
    public boolean canWrite(Integer appId, String userId, DocumentEntity doc) {
        if (doc == null || userId == null) return false;
        if (doc.getCreatorId() != null && doc.getCreatorId().equals(userId)) return true;
        if (isAdmin(appId, userId)) return true;
        return hasRole(appId, doc.getId(), userId, ROLE_EDITOR) || hasRole(appId, doc.getId(), userId, ROLE_OWNER);
    }

    private boolean hasAnyRole(Integer appId, Long docId, String userId) {
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", docId).eq("user_id", userId);
        return permissionMapper.selectCount(q) > 0;
    }

    private boolean hasRole(Integer appId, Long docId, String userId, String role) {
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", docId).eq("user_id", userId).eq("role", role);
        return permissionMapper.selectCount(q) > 0;
    }

    // ==================== 授权管理 ====================

    /** 授权/改角色：仅作者或管理员可操作 */
    public Result<Void> grant(Integer appId, Long docId, String operatorId, String targetUserId, String role) {
        if (!StringUtils.hasText(targetUserId) || !StringUtils.hasText(role)
                || !(ROLE_EDITOR.equals(role) || ROLE_READER.equals(role))) {
            return Result.fail(BusinessErrorCode.BAD_REQUEST);
        }
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!canWrite(appId, operatorId, doc)) return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);

        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", docId).eq("user_id", targetUserId);
        DocPermissionEntity p = permissionMapper.selectOne(q);
        if (p == null) {
            p = new DocPermissionEntity();
            p.setAppId(appId);
            p.setDocId(docId);
            p.setUserId(targetUserId);
            p.setRole(role);
            p.setCreateTime(System.currentTimeMillis());
            permissionMapper.insert(p);
        } else {
            p.setRole(role);
            permissionMapper.updateById(p);
        }
        return Result.ok();
    }

    /** 收回权限：仅作者或管理员可操作 */
    public Result<Void> revoke(Integer appId, Long docId, String operatorId, String targetUserId) {
        DocumentEntity doc = documentMapper.selectById(docId);
        if (doc == null) return Result.fail(BusinessErrorCode.DOCUMENT_NOT_FOUND);
        if (!canWrite(appId, operatorId, doc)) return Result.fail(BusinessErrorCode.DOCUMENT_PERMISSION_DENIED);
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", docId).eq("user_id", targetUserId);
        permissionMapper.delete(q);
        return Result.ok();
    }

    public Result<List<DocPermissionEntity>> list(Integer appId, Long docId) {
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", docId).orderByAsc("role");
        return Result.ok(permissionMapper.selectList(q));
    }

    /** 应用管理员列表（docId=0） */
    public Result<List<String>> adminList(Integer appId) {
        QueryWrapper<DocPermissionEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).eq("doc_id", 0).eq("role", ROLE_ADMIN);
        List<DocPermissionEntity> list = permissionMapper.selectList(q);
        List<String> ids = new ArrayList<>();
        for (DocPermissionEntity e : list) ids.add(e.getUserId());
        return Result.ok(ids);
    }
}
