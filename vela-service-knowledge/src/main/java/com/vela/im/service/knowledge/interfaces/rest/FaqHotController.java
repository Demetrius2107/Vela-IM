package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.FaqHotService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/knowledge/faq")
public class FaqHotController {

    private final FaqHotService service;

    public FaqHotController(FaqHotService service) { this.service = service; }

    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam Integer appId,
                                                 @RequestParam(defaultValue = "7") int windowDays,
                                                 @RequestParam(defaultValue = "10") int limit) {
        return service.hot(appId, windowDays, limit);
    }
}
