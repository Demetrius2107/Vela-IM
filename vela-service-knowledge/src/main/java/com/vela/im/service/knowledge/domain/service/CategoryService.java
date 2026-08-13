package com.vela.im.service.knowledge.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.vela.im.service.knowledge.domain.entity.CategoryEntity;
import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.CategoryMapper;
import com.vela.im.service.knowledge.infrastructure.persistence.mapper.DocumentMapper;
import com.vela.im.shared.base.Result;
import com.vela.im.shared.types.enums.BusinessErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分类目录树服务：增删改查 + 树形组装。
 */
@Service
public class CategoryService {

    /** 最大允许层级 */
    private static final int MAX_DEPTH = 5;

    private final CategoryMapper categoryMapper;
    private final DocumentMapper documentMapper;

    public CategoryService(CategoryMapper categoryMapper, DocumentMapper documentMapper) {
        this.categoryMapper = categoryMapper;
        this.documentMapper = documentMapper;
    }

    public Result<CategoryEntity> create(Integer appId, String name, Long parentId, Integer sort) {
        if (name == null || name.trim().isEmpty()) {
            return Result.fail(BusinessErrorCode.BAD_REQUEST);
        }
        if (parentId == null) parentId = 0L;
        if (parentId != 0L) {
            CategoryEntity parent = categoryMapper.selectById(parentId);
            if (parent == null || !appId.equals(parent.getAppId())) {
                return Result.fail(BusinessErrorCode.CATEGORY_NOT_FOUND);
            }
            if (depth(parentId) >= MAX_DEPTH) {
                return Result.fail(BusinessErrorCode.BAD_REQUEST);
            }
        }
        CategoryEntity e = new CategoryEntity();
        e.setAppId(appId);
        e.setParentId(parentId);
        e.setName(name.trim());
        e.setSort(sort == null ? 0 : sort);
        long now = System.currentTimeMillis();
        e.setCreateTime(now);
        e.setUpdateTime(now);
        categoryMapper.insert(e);
        return Result.ok(e);
    }

    public Result<Void> update(Long id, String name, Long parentId, Integer sort) {
        CategoryEntity e = categoryMapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.CATEGORY_NOT_FOUND);
        if (name != null && !name.trim().isEmpty()) e.setName(name.trim());
        if (parentId != null) {
            if (parentId.equals(id)) return Result.fail(BusinessErrorCode.BAD_REQUEST);
            e.setParentId(parentId);
        }
        if (sort != null) e.setSort(sort);
        e.setUpdateTime(System.currentTimeMillis());
        categoryMapper.updateById(e);
        return Result.ok();
    }

    public Result<Void> delete(Long id) {
        CategoryEntity e = categoryMapper.selectById(id);
        if (e == null) return Result.fail(BusinessErrorCode.CATEGORY_NOT_FOUND);
        QueryWrapper<CategoryEntity> childQ = new QueryWrapper<>();
        childQ.eq("parent_id", id);
        if (categoryMapper.selectCount(childQ) > 0) {
            return Result.fail(BusinessErrorCode.CATEGORY_HAS_CHILDREN);
        }
        QueryWrapper<DocumentEntity> docQ = new QueryWrapper<>();
        docQ.eq("category_id", id);
        if (documentMapper.selectCount(docQ) > 0) {
            return Result.fail(BusinessErrorCode.CATEGORY_HAS_DOCUMENTS);
        }
        categoryMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 树形查询：一次返回整棵树。
     */
    public Result<List<CategoryEntity>> tree(Integer appId) {
        QueryWrapper<CategoryEntity> q = new QueryWrapper<>();
        q.eq("app_id", appId).orderByAsc("sort").orderByAsc("id");
        List<CategoryEntity> all = categoryMapper.selectList(q);
        Map<Long, CategoryEntity> byId = new HashMap<>();
        for (CategoryEntity c : all) byId.put(c.getId(), c);
        List<CategoryEntity> roots = new ArrayList<>();
        for (CategoryEntity c : all) {
            CategoryEntity parent = c.getParentId() == null ? null : byId.get(c.getParentId());
            if (parent == null || c.getParentId() == 0L) {
                roots.add(c);
            } else {
                parent.getChildren().add(c);
            }
        }
        return Result.ok(roots);
    }

    private int depth(Long id) {
        int d = 1;
        Long cursor = id;
        while (cursor != null && cursor != 0L) {
            CategoryEntity e = categoryMapper.selectById(cursor);
            if (e == null) break;
            cursor = e.getParentId();
            d++;
            if (d > MAX_DEPTH) break;
        }
        return d;
    }
}
