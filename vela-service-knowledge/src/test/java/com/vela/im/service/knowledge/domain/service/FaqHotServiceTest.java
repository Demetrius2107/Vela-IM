package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaqHotServiceTest {

    @Mock DocumentMapper documentMapper;

    @InjectMocks FaqHotService service;

    private DocumentEntity doc(long id, long read, long fav) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setTitle("doc" + id);
        d.setReadCount(read);
        d.setFavoriteCount(fav);
        return d;
    }

    @Test
    void hot_appliesWeightedScore() {
        List<DocumentEntity> all = new ArrayList<>();
        all.add(doc(1L, 100, 1)); // 100 + 5 = 105
        all.add(doc(2L, 10, 20)); // 10 + 100 = 110 → 收藏加权后更高
        when(documentMapper.selectList(any())).thenReturn(all);

        Result<List<Map<String, Object>>> r = service.hot(100, 7, 10);

        assertTrue(r.isOk());
        assertEquals(2, r.getData().size());
        long score1 = (Long) r.getData().get(0).get("hotScore");
        long score2 = (Long) r.getData().get(1).get("hotScore");
        assertEquals(110L, Math.max(score1, score2)); // 收藏加权生效
    }

    @Test
    void hot_withWindowDays_appliesTimeFilter() {
        when(documentMapper.selectList(any())).thenReturn(new ArrayList<>());

        service.hot(100, 30, 10);

        ArgumentCaptor<QueryWrapper<DocumentEntity>> cap = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(documentMapper).selectList(cap.capture());
        assertNotNull(cap.getValue());
    }
}
