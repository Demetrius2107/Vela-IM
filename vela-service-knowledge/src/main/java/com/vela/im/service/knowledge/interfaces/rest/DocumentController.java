package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.entity.DocumentEntity;
import com.vela.im.service.knowledge.domain.service.DocumentService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/knowledge")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @PostMapping("/create")
    public Result<DocumentEntity> create(@RequestBody DocumentEntity entity) { return service.create(entity); }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam Integer appId,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long categoryId,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(appId, keyword, categoryId, status, page, size);
    }

    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam Integer appId,
                                              @RequestParam String keyword,
                                              @RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.search(appId, keyword, categoryId, status, page, size);
    }

    @GetMapping("/get")
    public Result<DocumentEntity> get(@RequestParam Integer appId, @RequestParam String userId,
                                      @RequestParam Long id) {
        return service.get(appId, userId, id);
    }

    @GetMapping("/preview")
    public Result<Map<String, Object>> preview(@RequestParam Integer appId, @RequestParam String userId,
                                               @RequestParam Long id) {
        return service.preview(appId, userId, id);
    }

    @GetMapping("/reference")
    public Result<Map<String, Object>> reference(@RequestParam Integer appId, @RequestParam String userId,
                                                 @RequestParam Long id) {
        return service.reference(appId, userId, id);
    }

    @PostMapping("/update")
    public Result<Void> update(@RequestParam Integer appId, @RequestParam String userId,
                               @RequestBody DocumentEntity entity) {
        return service.update(appId, userId, entity);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Integer appId, @RequestParam String userId,
                               @RequestParam Long id) {
        return service.delete(appId, userId, id);
    }

    // ==================== 回收站 ====================

    @GetMapping("/recycle/list")
    public Result<Map<String, Object>> recycleList(@RequestParam Integer appId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return service.recycleList(appId, page, size);
    }

    @PostMapping("/recycle/restore")
    public Result<Void> restore(@RequestParam Integer appId, @RequestParam String userId,
                                @RequestParam Long id) {
        return service.restore(appId, userId, id);
    }

    @PostMapping("/recycle/purge")
    public Result<Void> purge(@RequestParam Integer appId, @RequestParam String userId,
                              @RequestParam Long id) {
        return service.purge(appId, userId, id);
    }
}
