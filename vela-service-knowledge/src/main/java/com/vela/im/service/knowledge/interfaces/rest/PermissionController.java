package com.vela.im.service.knowledge.interfaces.rest;

import com.vela.im.service.knowledge.domain.entity.DocPermissionEntity;
import com.vela.im.service.knowledge.domain.service.PermissionService;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/knowledge/permission")
public class PermissionController {

    private final PermissionService service;

    public PermissionController(PermissionService service) { this.service = service; }

    @PostMapping("/grant")
    public Result<Void> grant(@RequestParam Integer appId, @RequestParam Long docId,
                              @RequestParam String operatorId, @RequestParam String targetUserId,
                              @RequestParam String role) {
        return service.grant(appId, docId, operatorId, targetUserId, role);
    }

    @PostMapping("/revoke")
    public Result<Void> revoke(@RequestParam Integer appId, @RequestParam Long docId,
                               @RequestParam String operatorId, @RequestParam String targetUserId) {
        return service.revoke(appId, docId, operatorId, targetUserId);
    }

    @GetMapping("/list")
    public Result<List<DocPermissionEntity>> list(@RequestParam Integer appId, @RequestParam Long docId) {
        return service.list(appId, docId);
    }

    @GetMapping("/admins")
    public Result<List<String>> admins(@RequestParam Integer appId) {
        return service.adminList(appId);
    }
}
