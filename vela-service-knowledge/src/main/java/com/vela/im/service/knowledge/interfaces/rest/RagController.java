package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.RagService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** RAG 问答与向量索引接口 */
@RestController
@RequestMapping("/v1/knowledge")
public class RagController {

    private final RagService service;

    public RagController(RagService service) { this.service = service; }

    @GetMapping("/rag/ask")
    public Result<Map<String, Object>> ask(@RequestParam Integer appId,
                                           @RequestParam String question,
                                           @RequestParam(defaultValue = "3") int limit) {
        return service.ask(appId, question, limit);
    }

    @PostMapping("/vector/reindex")
    public Result<Void> reindex(@RequestParam Long docId) {
        return service.reindexById(docId);
    }
}
