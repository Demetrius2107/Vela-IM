package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.BotAskService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 知识库机器人问答接口：群聊/单聊中 @知识库机器人 提问（如 /kb 如何开通Vela账号）。
 */
@RestController
@RequestMapping("/v1/knowledge/bot")
public class BotAskController {

    private final BotAskService service;

    public BotAskController(BotAskService service) { this.service = service; }

    @GetMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestParam Integer appId,
                                           @RequestParam String question,
                                           @RequestParam(defaultValue = "3") int limit) {
        return service.ask(appId, question, limit);
    }
}
