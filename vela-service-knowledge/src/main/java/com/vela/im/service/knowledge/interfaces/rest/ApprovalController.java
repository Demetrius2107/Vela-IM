package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.service.ApprovalService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 文档发布审批接口：提交审核 / 审批 / 下架 / 待审批列表 */
@RestController
@RequestMapping("/v1/knowledge/approval")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) { this.service = service; }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestParam Integer appId, @RequestParam String userId,
                               @RequestParam Long docId) {
        return service.submit(appId, userId, docId);
    }

    @PostMapping("/handle")
    public Result<Void> handle(@RequestParam Integer appId, @RequestParam String approverId,
                               @RequestParam Long docId, @RequestParam String action,
                               @RequestParam(required = false) String reason) {
        return service.handle(appId, approverId, docId, action, reason);
    }

    @PostMapping("/unpublish")
    public Result<Void> unpublish(@RequestParam Integer appId, @RequestParam String userId,
                                  @RequestParam Long docId) {
        return service.unpublish(appId, userId, docId);
    }

    @GetMapping("/pending")
    public Result<Map<String, Object>> pending(@RequestParam Integer appId,
                                               @RequestParam String approverId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.pending(appId, approverId, page, size);
    }
}
