package com.vela.im.service.knowledge.domain.service;

import com.vela.im.service.knowledge.domain.entity.DocVersionEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocVersionMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock DocVersionMapper versionMapper;
    @Mock DocumentMapper documentMapper;
    @Mock PermissionService permissionService;
    @Mock RagService ragService;

    @InjectMocks VersionService service;

    private DocumentEntity doc() {
        DocumentEntity d = new DocumentEntity();
        d.setId(1L);
        d.setAppId(100);
        d.setTitle("t1");
        d.setContent("c1");
        d.setSummary("s1");
        return d;
    }

    @Test
    void saveSnapshot_createsVersion1_whenNoLatest() {
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(1);

        Result<DocVersionEntity> r = service.saveSnapshot(doc(), "u1");

        assertTrue(r.isOk());
        assertEquals(1, r.getData().getVersionNo());
        verify(versionMapper).insert(any(DocVersionEntity.class));
    }

    @Test
    void saveSnapshot_skips_whenContentUnchanged() {
        DocVersionEntity latest = new DocVersionEntity();
        latest.setDocId(1L);
        latest.setVersionNo(3);
        latest.setTitle("t1");
        latest.setContent("c1");
        latest.setSummary("s1");
        when(versionMapper.selectOne(any())).thenReturn(latest);

        Result<DocVersionEntity> r = service.saveSnapshot(doc(), "u1");

        assertTrue(r.isOk());
        verify(versionMapper, never()).insert(any());
    }

    @Test
    void saveSnapshot_incrementsVersionNo_whenChanged() {
        DocVersionEntity latest = new DocVersionEntity();
        latest.setDocId(1L);
        latest.setVersionNo(2);
        latest.setTitle("OLD");
        latest.setContent("c1");
        latest.setSummary("s1");
        when(versionMapper.selectOne(any())).thenReturn(latest);
        when(versionMapper.selectCount(any())).thenReturn(3);

        Result<DocVersionEntity> r = service.saveSnapshot(doc(), "u1");

        assertEquals(3, r.getData().getVersionNo());
        verify(versionMapper).insert(any(DocVersionEntity.class));
    }

    @Test
    void saveSnapshot_trimsOldVersions_beyondMax() {
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectCount(any())).thenReturn(VersionService.MAX_VERSIONS + 10);
        List<DocVersionEntity> old = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            DocVersionEntity v = new DocVersionEntity();
            v.setId((long) i);
            v.setDocId(1L);
            v.setVersionNo(i);
            old.add(v);
        }
        when(versionMapper.selectList(any())).thenReturn(old);

        service.saveSnapshot(doc(), "u1");

        verify(versionMapper, times(10)).deleteById(any());
    }

    @Test
    void rollback_requiresWritePermission() {
        DocumentEntity d = doc();
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(false);

        Result<Void> r = service.rollback(100, "u1", 1L, 1);

        assertFalse(r.isOk());
        assertEquals(97005, r.getCode()); // DOCUMENT_PERMISSION_DENIED
        verify(documentMapper, never()).updateById(any());
    }

    @Test
    void rollback_restoresTargetContent() {
        DocumentEntity d = doc();
        when(documentMapper.selectById(1L)).thenReturn(d);
        when(permissionService.canWrite(100, "u1", d)).thenReturn(true);

        DocVersionEntity target = new DocVersionEntity();
        target.setDocId(1L);
        target.setVersionNo(2);
        target.setTitle("ROLLED_BACK_TITLE");
        target.setContent("ROLLED_BACK_CONTENT");
        target.setSummary("ROLLED_BACK_SUMMARY");
        when(versionMapper.selectOne(any())).thenReturn(target);
        when(versionMapper.selectCount(any())).thenReturn(2);

        Result<Void> r = service.rollback(100, "u1", 1L, 2);

        assertTrue(r.isOk());
        ArgumentCaptor<DocumentEntity> cap = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentMapper, atLeastOnce()).updateById(cap.capture());
        assertEquals("ROLLED_BACK_TITLE", d.getTitle());
        verify(ragService).reindex(d);
    }
}
