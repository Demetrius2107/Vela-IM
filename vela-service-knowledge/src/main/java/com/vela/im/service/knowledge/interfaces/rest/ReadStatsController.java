package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.ReadStatsService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 阅读统计接口：阅读记录 / 文档统计 */
@RestController
@RequestMapping("/v1/knowledge")
public class ReadStatsController {

    private final ReadStatsService service;

    public ReadStatsController(ReadStatsService service) { this.service = service; }

    @PostMapping("/read/record")
    public Result<Void> record(@RequestParam Integer appId, @RequestParam String userId,
                               @RequestParam Long docId) {
        return service.record(appId, userId, docId);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(@RequestParam Long docId) {
        return service.stats(docId);
    }
}
