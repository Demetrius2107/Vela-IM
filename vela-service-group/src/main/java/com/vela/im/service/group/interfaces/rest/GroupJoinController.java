package com.vela.im.service.group.interfaces.rest;

import com.vela.im.service.group.application.dto.req.AddGroupMemberRequest;
import com.vela.im.service.group.application.dto.req.GroupMemberDto;
import com.vela.im.service.group.domain.entity.ImGroupEntity;
import com.vela.im.service.group.domain.service.ImGroupMemberService;
import com.vela.im.service.group.domain.service.ImGroupService;
import com.vela.im.shared.base.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/v1/group/join")
public class GroupJoinController {

    private final ImGroupService imGroupService;
    private final ImGroupMemberService imGroupMemberService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String INVITE_KEY_PREFIX = "group_invite:";

    public GroupJoinController(ImGroupService imGroupService,
                               ImGroupMemberService imGroupMemberService,
                               StringRedisTemplate stringRedisTemplate) {
        this.imGroupService = imGroupService;
        this.imGroupMemberService = imGroupMemberService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @GetMapping("/link")
    public Result<String> getJoinLink(@RequestParam String groupId,
                                      @RequestParam Integer appId) {
        Result<ImGroupEntity> groupResult = imGroupService.getGroup(groupId, appId);
        if (!groupResult.isOk()) {
            return Result.fail(500, "群组不存在");
        }
        String joinLink = "/v1/group/join?groupId=" + groupId + "&appId=" + appId;
        return Result.ok(joinLink);
    }

    /** 生成带有效期的邀请令牌 */
    @PostMapping("/invite")
    public Result<String> createInviteToken(@RequestParam String groupId,
                                            @RequestParam Integer appId,
                                            @RequestParam(defaultValue = "86400") long ttlSeconds) {
        Result<ImGroupEntity> groupResult = imGroupService.getGroup(groupId, appId);
        if (!groupResult.isOk()) {
            return Result.fail(500, "群组不存在");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        String value = appId + ":" + groupId;  // 存储值供后续验证
        stringRedisTemplate.opsForValue().set(INVITE_KEY_PREFIX + token, value, ttlSeconds, TimeUnit.SECONDS);
        return Result.ok(token);
    }

    /** 通过邀请令牌加入群组 */
    @PostMapping("/invite/accept")
    public Result<Void> joinByToken(@RequestParam String token,
                                    @RequestParam String memberId) {
        String value = stringRedisTemplate.opsForValue().get(INVITE_KEY_PREFIX + token);
        if (value == null) {
            return Result.fail(500, "邀请链接已过期或无效");
        }
        String[] parts = value.split(":");
        if (parts.length < 2) {
            return Result.fail(500, "邀请令牌格式错误");
        }
        Integer appId = Integer.parseInt(parts[0]);
        String groupId = parts[1];

        AddGroupMemberRequest req = new AddGroupMemberRequest();
        req.setGroupId(groupId);
        GroupMemberDto dto = new GroupMemberDto();
        dto.setMemberId(memberId);
        req.setMembers(Arrays.asList(dto));
        return imGroupMemberService.addMember(req);
    }

    @PostMapping("")
    public Result<Void> joinByLink(@RequestParam String groupId,
                                   @RequestParam Integer appId,
                                   @RequestParam String memberId) {
        AddGroupMemberRequest req = new AddGroupMemberRequest();
        req.setGroupId(groupId);
        GroupMemberDto dto = new GroupMemberDto();
        dto.setMemberId(memberId);
        req.setMembers(Arrays.asList(dto));
        return imGroupMemberService.addMember(req);
    }
}
