package com.vela.im.service.office.interfaces.rest;

import com.vela.im.service.office.domain.entity.ApprovalEntity;
import com.vela.im.service.office.domain.service.ApprovalService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/office/approval")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) { this.service = service; }

    @PostMapping("/submit")
    public Result<ApprovalEntity> submit(@RequestBody ApprovalEntity entity) { return service.submit(entity); }

    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(required = false) String userId,
                                             @RequestParam Integer appId,
                                             @RequestParam(required = false) Integer status,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(userId, appId, status, page, size);
    }

    @PostMapping("/approve")
    public Result<Void> approve(@RequestParam Long id, @RequestParam String approverId,
                                 @RequestParam(required = false) String comment,
                                 @RequestParam boolean passed) {
        return service.approve(id, approverId, comment, passed);
    }

    @PostMapping("/recall")
    public Result<Void> recall(@RequestParam Long id) { return service.recall(id); }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam Long id) { return service.delete(id); }
}
