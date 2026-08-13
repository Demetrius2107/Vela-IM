package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.entity.DocVersionEntity;
import com.vela.im.service.knowledge.domain.service.VersionService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 文档版本接口：版本历史 / 回滚 */
@RestController
@RequestMapping("/v1/knowledge/version")
public class VersionController {

    private final VersionService service;

    public VersionController(VersionService service) { this.service = service; }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam Long docId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(docId, page, size);
    }

    @PostMapping("/rollback")
    public Result<Void> rollback(@RequestParam Integer appId, @RequestParam String userId,
                                 @RequestParam Long docId, @RequestParam Integer versionNo) {
        return service.rollback(appId, userId, docId, versionNo);
    }
}
