package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.SummaryService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

/** 自动摘要接口：摘要生成 */
@RestController
@RequestMapping("/v1/knowledge/summary")
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) { this.service = service; }

    @PostMapping("/generate")
    public Result<String> generate(@RequestParam Long docId,
                                   @RequestParam(defaultValue = "false") boolean force) {
        return service.generate(docId, force);
    }
}
