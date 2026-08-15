package com.vela.im.service.knowledge.domain.service;

import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocApprovalMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock DocApprovalMapper approvalMapper;
    @Mock DocumentMapper documentMapper;
    @Mock PermissionService permissionService;

    @InjectMocks ApprovalService service;

    private DocumentEntity doc(int status) {
        DocumentEntity d = new DocumentEntity();
        d.setId(1L);
        d.setAppId(100);
        d.setStatus(status);
        return d;
    }

    @Test
    void submit_fromDraft_goesPending() {
        DocumentEntity d = doc(DocumentService.STATUS_DRAFT);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(true);

        Result<Void> r = service.submit(100, "u1", 1L);

        assertTrue(r.isOk());
        assertEquals(DocumentService.STATUS_PENDING, d.getStatus());
        verify(documentMapper).updateById(d);
    }

    @Test
    void submit_fromPublished_isIllegal() {
        DocumentEntity d = doc(DocumentService.STATUS_PUBLISHED);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(true);

        Result<Void> r = service.submit(100, "u1", 1L);

        assertEquals(97007, r.getCode()); // DOCUMENT_STATUS_ILLEGAL
        verify(documentMapper, never()).updateById(any());
    }

    @Test
    void submit_withoutWritePermission_denied() {
        DocumentEntity d = doc(DocumentService.STATUS_DRAFT);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(false);

        Result<Void> r = service.submit(100, "u1", 1L);

        assertEquals(97005, r.getCode()); // DOCUMENT_PERMISSION_DENIED
    }

    @Test
    void handle_approve_byAdmin_publishes() {
        DocumentEntity d = doc(DocumentService.STATUS_PENDING);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.isAdmin(100, "admin1")).thenReturn(true);

        Result<Void> r = service.handle(100, "admin1", 1L, "approve", null);

        assertTrue(r.isOk());
        assertEquals(DocumentService.STATUS_PUBLISHED, d.getStatus());
        verify(approvalMapper).insert(any());
    }

    @Test
    void handle_reject_withoutReason_fails() {
        DocumentEntity d = doc(DocumentService.STATUS_PENDING);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.isAdmin(100, "admin1")).thenReturn(true);

        Result<Void> r = service.handle(100, "admin1", 1L, "reject", null);

        assertEquals(97009, r.getCode()); // APPROVAL_REASON_REQUIRED
        verify(approvalMapper, never()).insert(any());
    }

    @Test
    void handle_byNonAdmin_denied() {
        DocumentEntity d = doc(DocumentService.STATUS_PENDING);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.isAdmin(100, "u1")).thenReturn(false);

        Result<Void> r = service.handle(100, "u1", 1L, "approve", null);

        assertEquals(97005, r.getCode()); // DOCUMENT_PERMISSION_DENIED
    }

    @Test
    void handle_reject_recordsReason() {
        DocumentEntity d = doc(DocumentService.STATUS_PENDING);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.isAdmin(100, "admin1")).thenReturn(true);

        Result<Void> r = service.handle(100, "admin1", 1L, "reject", "内容不完整");

        assertTrue(r.isOk());
        assertEquals(DocumentService.STATUS_REJECTED, d.getStatus());
    }

    @Test
    void unpublish_onlyFromPublished() {
        DocumentEntity d = doc(DocumentService.STATUS_DRAFT);
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(true);

        Result<Void> r = service.unpublish(100, "u1", 1L);

        assertEquals(97007, r.getCode()); // DOCUMENT_STATUS_ILLEGAL
    }
}
