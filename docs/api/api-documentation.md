# Vela IM — API 接口文档

> 版本: v1.0 | 更新: 2026-07-29
> 基础路径: `http://localhost:8000`（dev） / `http://localhost:8888`（gateway）

---

## 一、用户域

### 1.1 用户注册/登录

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/user/register` | 用户注册，body: `{userId, nickName, password}` |
| POST | `/v1/user/login` | 用户登录，body: `{userId, password}` → token |

### 1.2 用户资料

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/user/getUserInfo?userId=&appId=&modifySequence=` | 获取用户信息 |
| GET | `/v1/user/getSingleUserInfo?userId=&appId=` | 获取单一用户信息 |
| PUT | `/v1/user/modifyUserInfo` | 修改用户资料 |

---

## 二、好友域

### 2.1 好友管理

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/friendship/importFriendShip` | 批量导入好友 |
| POST | `/v1/friendship/addFriend` | 添加好友 |
| POST | `/v1/friendship/updateFriend` | 更新好友信息 |
| POST | `/v1/friendship/deleteFriend` | 删除好友 |
| DELETE | `/v1/friendship/deleteAllFriend` | 删除所有好友 |
| GET | `/v1/friendship/getAllFriend?appId=&fromId=` | 获取所有好友列表 |
| GET | `/v1/friendship/getFriend?appId=&fromId=&toId=` | 获取指定好友 |

### 2.2 好友分组

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/friendship/group/add` | 添加好友分组 |
| POST | `/v1/friendship/group/del` | 删除好友分组 |
| PUT | `/v1/friendship/group/update` | 更新好友分组 |

### 2.3 好友请求

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/friendshipRequest/approveFriendRequest` | 审批好友请求 |
| GET | `/v1/friendshipRequest/getFriendRequest?appId=&fromId=` | 获取好友请求列表 |
| POST | `/v1/friendshipRequest/readAllFriendRequest` | 标记全部已读 |

### 2.4 黑名单

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/friendship/addBlack` | 添加黑名单 |
| DELETE | `/v1/friendship/deleteBlack` | 移除黑名单 |
| GET | `/v1/friendship/getBlackList?appId=&fromId=` | 获取黑名单列表 |

---

## 三、群组域

### 3.1 群组管理

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/importGroup` | 批量导入群组 |
| POST | `/v1/group/createGroup` | 创建群组 |
| POST | `/v1/group/updateGroupInfo` | 更新群信息 |
| GET | `/v1/group/getGroupInfo?groupId=&appId=` | 获取群信息 |
| GET | `/v1/group/getJoinedGroups?fromId=&appId=` | 获取已加入群列表 |

### 3.2 群成员

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/member/importGroupMember` | 批量导入群成员 |
| POST | `/v1/group/member/add` | 添加群成员 |
| POST | `/v1/group/member/remove` | 移除群成员 |
| POST | `/v1/group/member/update` | 更新群成员信息 |
| GET | `/v1/group/member/list?groupId=&appId=` | 获取群成员列表 |

### 3.3 群公告

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/announcement/add` | 添加群公告 |
| GET | `/v1/group/announcement/list?groupId=` | 获取群公告列表 |
| POST | `/v1/group/announcement/update` | 更新群公告 |
| GET | `/v1/group/announcement/detail?id=` | 获取群公告详情 |
| POST | `/v1/group/announcement/delete` | 删除群公告 |

### 3.4 群投票

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/poll/create` | 创建投票 |
| GET | `/v1/group/poll/list?groupId=` | 投票列表 |
| POST | `/v1/group/poll/vote` | 参与投票 |
| GET | `/v1/group/poll/result?pollId=` | 投票结果 |
| POST | `/v1/group/poll/close` | 关闭投票 |

### 3.5 群标签

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/tag/create` | 创建标签 |
| GET | `/v1/group/tag/list?groupId=` | 标签列表 |
| POST | `/v1/group/tag/assign` | 分配标签 |
| POST | `/v1/group/tag/remove` | 移除标签 |
| GET | `/v1/group/tag/members?tagId=` | 标签成员列表 |
| POST | `/v1/group/tag/delete` | 删除标签 |

### 3.6 群文件 & 加群

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/group/file/upload` | 上传群文件 |
| GET | `/v1/group/file/list?groupId=&page=&size=` | 群文件列表 |
| POST | `/v1/group/file/delete` | 删除群文件 |
| GET | `/v1/group/join/list?groupId=` | 入群申请列表 |
| POST | `/v1/group/join/approve` | 审批入群 |
| POST | `/v1/group/join/apply` | 申请入群 |
| POST | `/v1/group/join/invite` | 邀请入群 |

---

## 四、消息域

### 4.1 消息发送

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/message/send` | 发送消息（P2P/群聊） |
| POST | `/v1/message/checkSend` | 校验消息可发送性 |

### 4.2 消息同步

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/message/syncOfflineMessage` | 同步离线消息 |

### 4.3 已读回执

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/message/read/mark` | 标记消息已读 |
| GET | `/v1/message/read/list?messageId=` | 已读用户列表 |

### 4.4 文件

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/file/upload` | 上传文件 |
| GET | `/v1/file/download?fileId=&type=` | 下载文件 |

### 4.5 贴纸

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/sticker/packs?appId=` | 贴纸包列表 |
| GET | `/v1/sticker/list?packId=&appId=` | 贴纸列表 |

---

## 五、会话域

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/conversation/deleteConversation` | 删除会话 |
| POST | `/v1/conversation/updateConversation` | 更新会话（置顶/免打扰） |
| POST | `/v1/conversation/syncConversationList` | 增量同步会话列表 |

---

## 六、Bot 域

### 6.1 Bot 管理

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/bot/register` | 注册 Bot |
| GET | `/v1/bot/list?appId=` | Bot 列表 |
| GET | `/v1/bot/get?botId=` | Bot 详情 |
| POST | `/v1/bot/toggle?botId=` | 启用/禁用 Bot |
| POST | `/v1/bot/regen-key?botId=` | 重新生成 API Key |
| POST | `/v1/bot/update-webhook?botId=&webhookUrl=` | 更新 Webhook |
| POST | `/v1/bot/delete?botId=` | 删除 Bot |

### 6.2 Bot 市场

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/bot/market/list?appId=&category=&keyword=` | 市场列表 |
| GET | `/v1/bot/market/categories?appId=` | 分类统计 |
| POST | `/v1/bot/market/install?appId=&userId=&botId=` | 安装 Bot |
| POST | `/v1/bot/market/uninstall?appId=&userId=&botId=` | 卸载 Bot |
| GET | `/v1/bot/market/my?appId=&userId=` | 我的 Bot 列表 |
| GET | `/v1/bot/market/installed?appId=&userId=&botId=` | 检查是否已安装 |

### 6.3 行内查询

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/bot/inline/query?appId=&botId=&query=&userId=` | 行内查询 |

---

## 七、配置中心

### 7.1 用户配置

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/user/config/get?appId=&userId=&clientType=` | 批量获取用户配置 |
| POST | `/v1/user/config/save` | 批量保存用户配置，body: `[{key, value}]` |

### 7.2 功能开关

| 方法 | 路径 | 说明 |
|-----|-----|------|
| GET | `/v1/feature/flags?appId=&userId=` | 客户端拉取功能开关 |
| GET | `/v1/feature/admin/list` | 管理后台列出所有开关 |
| POST | `/v1/feature/admin/update?id=&enabled=&userWhitelist=` | 更新功能开关 |

---

## 八、管理后台

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/admin/login` | 管理员登录 |
| POST | `/v1/admin/admins/create` | 创建管理员 |
| GET | `/v1/admin/admins/list` | 管理员列表 |
| POST | `/v1/admin/admins/toggle` | 启用/禁用管理员 |
| GET | `/v1/admin/dashboard` | 数据看板 |
| GET | `/v1/admin/message/trend?days=` | 消息趋势 |
| GET | `/v1/admin/groups/top?limit=` | 最活跃群组 |
| GET | `/v1/admin/users?keyword=&page=&size=` | 用户列表 |
| GET | `/v1/admin/users/detail?userId=` | 用户详情 |
| POST | `/v1/admin/users/update` | 更新用户资料 |
| POST | `/v1/admin/users/toggleForbidden` | 禁用/解禁用户 |
| POST | `/v1/admin/users/batchForbidden` | 批量禁言 |
| GET | `/v1/admin/users/loginLogs` | 登录日志 |
| GET | `/v1/admin/groups?keyword=&page=&size=&status=` | 群组列表 |
| GET | `/v1/admin/groups/detail?groupId=` | 群组详情 |
| POST | `/v1/admin/groups/dissolve` | 解散群组 |
| GET | `/v1/admin/messages/search` | 消息审计搜索 |
| GET | `/v1/admin/operations` | 操作日志 |
| GET | `/v1/admin/configs` | 系统配置列表 |
| POST | `/v1/admin/configs/update` | 更新系统配置 |
| GET | `/v1/admin/users/trend?days=` | 注册趋势 |
| GET | `/v1/admin/groups/export` | 导出群组 |
| GET | `/v1/admin/bots?page=&size=&keyword=` | Bot 列表（管理） |
| POST | `/v1/admin/bots/create` | 创建 Bot（管理） |
| POST | `/v1/admin/bots/toggle` | 启用/禁用 Bot（管理）|
| POST | `/v1/admin/bots/delete` | 删除 Bot（管理） |
| GET | `/v1/admin/feature-flags` | 功能开关列表（管理） |
| POST | `/v1/admin/feature-flags/update` | 更新功能开关（管理）|

---

## 九、办公生态

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/office/schedule/create` | 创建日程 |
| GET | `/v1/office/schedule/list` | 日程列表 |
| POST | `/v1/office/schedule/update` | 更新日程 |
| POST | `/v1/office/schedule/delete` | 删除日程 |
| POST | `/v1/office/todo/create` | 创建待办 |
| GET | `/v1/office/todo/list` | 待办列表 |
| POST | `/v1/office/todo/update` | 更新待办 |
| POST | `/v1/office/todo/delete` | 删除待办 |
| POST | `/v1/office/approval/create` | 提交审批 |
| GET | `/v1/office/approval/list` | 审批列表 |
| POST | `/v1/office/approval/process` | 审批处理（通过/拒绝）|

---

## 十、知识库

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/knowledge/create` | 创建文档 |
| GET | `/v1/knowledge/list?category=&keyword=&page=&size=` | 文档列表 |
| GET | `/v1/knowledge/get?id=` | 文档详情 |
| POST | `/v1/knowledge/update` | 更新文档 |
| POST | `/v1/knowledge/delete` | 删除文档 |

---

## 十一、消息收藏

| 方法 | 路径 | 说明 |
|-----|-----|------|
| POST | `/v1/favorite/add` | 收藏消息 |
| POST | `/v1/favorite/remove` | 取消收藏 |
| GET | `/v1/favorite/list?appId=&userId=&page=&size=` | 收藏列表 |
| GET | `/v1/favorite/check` | 检查是否已收藏 |

---

## 十二、通用说明

### 请求格式

- Content-Type: `application/json`
- 认证 Header: `token: <login_token>`（需要鉴权的接口）
- Admin Role Header: `X-Admin-Role: admin|operator|auditor`（管理后台接口）

### 响应格式

```json
{
  "code": 200,
  "msg": "success",
  "data": { ... }
}
```

### 错误码范围

| 范围 | 模块 |
|-----|------|
| 0-99 | 通用 |
| 91001-91006 | Bot |
| 92001-92004 | Admin |
| 93001 | User |
| 94001 | Group |
| 95001 | Message |
| 96001-96004 | Office |
| 97001 | Document |
| 98001-98002 | Favorite |

### 前端静态资源

| 路径 | 说明 |
|-----|------|
| `http://localhost:3000` | IM Web 端 |
| `http://localhost:3000/#/admin` | 管理后台 |
| `http://localhost:3000/#/office` | 办公生态 |
| `http://localhost:3000/#/knowledge` | 知识库 |
