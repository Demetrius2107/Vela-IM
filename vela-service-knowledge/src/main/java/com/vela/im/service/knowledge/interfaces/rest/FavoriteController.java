package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.FavoriteService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 文档收藏接口：收藏 / 取消 / 收藏列表 */
@RestController
@RequestMapping("/v1/knowledge/favorite")
public class FavoriteController {

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) { this.service = service; }

    @PostMapping("/add")
    public Result<Void> add(@RequestParam Integer appId, @RequestParam String userId, @RequestParam Long docId) {
        return service.add(appId, userId, docId);
    }

    @PostMapping("/remove")
    public Result<Void> remove(@RequestParam Integer appId, @RequestParam String userId, @RequestParam Long docId) {
        return service.remove(appId, userId, docId);
    }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam Integer appId, @RequestParam String userId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(appId, userId, page, size);
    }
}
