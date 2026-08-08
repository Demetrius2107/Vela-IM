# Vela IM — 会话工作记录

> 日期：2026-07-28
> 分支：feat/message-reply
> 状态：开发进行中

---

## 本次完成工作

### 1. 管理后台角色权限绑定

| 端点 | 所需权限 |
|------|----------|
| `/admins/list` | super_admin |
| `/admins/toggle` | super_admin |
| `/users/update` | operator+ |
| `/users/toggleForbidden` | operator+ |
| `/users/batchForbidden` | operator+ |
| `/groups/dissolve` | operator+ |
| `/configs/update` | operator+ |

### 2. Android 对接真实 API

| 文件 | 改动 |
|------|------|
| `VelaApi.kt` | 新增 `getFriends` 接口 |
| `ChatViewModel.kt` | 从 mock 数据改为 Retrofit 调用后端 API |
| `MainActivity.kt` | Home 页加载时调用 `loadConversations()` |

### 3. Bot 深挖

| 功能 | 状态 |
|------|----|
| 回复富文本 | ✅ Webhook JSON 响应解析（type/content/fileUrl）|
| 图片/文件回复 | ✅ `sendReply` 支持 `fileUrl` + `fileType` 透传 |
| 群聊 @Bot | ✅ `handleGroupMention` 方法 |
| 速率限制 | ✅ 每 Bot 每 500ms 一条 |

## Git 提交记录

```
3141f97 feat(all): 角色权限/Android对接API/Bot深挖
```

---

> 文档版本: v1.0 | 创建时间: 2026-07-28
