package com.vela.im.service.admin.domain;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class AdminRoleConfig {

    /** 权限定义：role -> 可访问的action列表 */
    private static final Map<String, List<String>> PERMISSIONS = Map.of(
        "super_admin", Arrays.asList("dashboard", "users", "groups", "messages", "operations", "settings", "admins"),
        "operator",    Arrays.asList("dashboard", "users", "groups", "messages", "operations"),
        "auditor",     Arrays.asList("dashboard", "messages", "operations")
    );

    public boolean hasPermission(String role, String action) {
        List<String> allowed = PERMISSIONS.get(role);
        return allowed != null && allowed.contains(action);
    }

    public String getDefaultRole() { return "operator"; }
}
