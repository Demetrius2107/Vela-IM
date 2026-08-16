package com.vela.im.service.message.interfaces.rest;

import com.vela.im.service.message.domain.service.MessageReadService;
import com.vela.im.service.message.interfaces.feign.GroupServiceFeignClient;
import com.vela.im.shared.base.Result;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/v1/message/read")
public class MessageReadController {

    private final MessageReadService messageReadService;
    private final GroupServiceFeignClient groupServiceFeignClient;

    public MessageReadController(MessageReadService messageReadService,
                                 GroupServiceFeignClient groupServiceFeignClient) {
        this.messageReadService = messageReadService;
        this.groupServiceFeignClient = groupServiceFeignClient;
    }

    @GetMapping("/members")
    public Result<List<String>> getReadMembers(@RequestParam Long messageKey) {
        return messageReadService.getReadMembers(messageKey);
    }

    /**
     * 查询群聊消息的未读成员列表。
     * <p>通过群服务获取全量成员，减去已读成员即未读成员。</p>
     */
    @GetMapping("/unreadMembers")
    public Result<List<String>> getUnreadMembers(@RequestParam Integer appId,
                                                 @RequestParam String groupId,
                                                 @RequestParam Long messageKey) {
        Result<List<String>> groupResult = groupServiceFeignClient.getGroupMemberId(groupId, appId);
        List<String> allMembers = groupResult != null && groupResult.getData() != null
                ? groupResult.getData() : new ArrayList<>();
        return messageReadService.getUnreadMembers(messageKey, allMembers);
    }

    @PostMapping("/mark")
    public Result<Void> markRead(@RequestParam Integer appId,
                                 @RequestParam(required = false) String groupId,
                                 @RequestParam Long messageKey,
                                 @RequestParam String memberId) {
        messageReadService.markRead(appId, groupId, messageKey, memberId);
        return Result.ok();
    }
}
