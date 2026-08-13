package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.entity.CategoryEntity;
import com.vela.im.service.knowledge.domain.service.CategoryService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 知识库分类接口：分类增删改查与树形查询 */
@RestController
@RequestMapping("/v1/knowledge/category")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) { this.service = service; }

    @PostMapping("/create")
    public Result<CategoryEntity> create(@RequestParam Integer appId, @RequestParam String name,
                                         @RequestParam(required = false, defaultValue = "0") Long parentId,
                                         @RequestParam(required = false) Integer sort) {
        return service.create(appId, name, parentId, sort);
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestParam Long id,
                               @RequestParam(required = false) String name,
                               @RequestParam(required = false) Long parentId,
                               @RequestParam(required = false) Integer sort) {
        return service.update(id, name, parentId, sort);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) { return service.delete(id); }

    @GetMapping("/tree")
    public Result<List<CategoryEntity>> tree(@RequestParam Integer appId) { return service.tree(appId); }
}
