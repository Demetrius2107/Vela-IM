# 端对端加密（E2EE）设计方案

> 创建时间: 2026-07-20
> 作者: wanqiu
> 适用项目: Vela IM System

---

## 目录

1. [核心矛盾与权衡](#1-核心矛盾与权衡)
2. [WhatsApp 加密方案（Signal 协议）](#2-whatsapp-加密方案signal-协议)
3. [Telegram 加密方案（MTProto 2.0）](#3-telegram-加密方案mtproto-20)
4. [方案对比：WhatsApp vs Telegram](#4-方案对比whatsapp-vs-telegram)
5. [Vela 项目推荐方案](#5-vela-项目推荐方案)
6. [E2EE 下的日志治理与元数据监控](#6-e2ee-下的日志治理与元数据监控)
7. [实施路线图](#7-实施路线图)

---

## 1. 核心矛盾与权衡

### 1.1 矛盾的本质

```
E2EE 加密：
  客户端持有密钥 → 服务端无法解密消息体
  ✓ 隐私安全（用户数据不可见）
  ✗ 无法做内容审核、搜索、日志排查、敏感词过滤

无 E2EE（服务端明文）：
  服务端可见消息内容
  ✓ 可做搜索/审核/日志/去重/推荐
  ✗ 隐私风险（服务端泄露、监管压力）
```

### 1.2 可权衡的维度

| 维度 | E2EE | 服务端明文 | 混合方案 |
|------|------|-----------|---------|
| 消息内容隐私 | ✅ 最高 | ❌ 无 | ⚠️ 按消息类型 |
| 服务端搜索 | ❌ 不可行 | ✅ 全文检索 | ✅ 元数据搜索 |
| 内容审核 | ❌ 不可行 | ✅ 可审核 | ⚠️ 仅明文类型 |
| 离线消息 | ✅ 存密文 | ✅ 存明文 | ✅ 存密文 |
| 多端同步 | ⚠️ 需密钥同步 | ✅ 天然支持 | ⚠️ 需密钥管理 |
| 服务端排障 | ❌ 仅元数据 | ✅ 全量日志 | ⚠️ 部分可见 |

---

## 2. WhatsApp 加密方案（Signal 协议）

### 2.1 概述

WhatsApp 使用 **Signal 协议**（由 Open Whisper Systems 设计），这是目前业界公认最安全的 E2EE 协议之一。所有消息、群聊、文件、语音/视频通话均受 E2EE 保护。

**核心设计原则：**
- 服务端**永远不持有**任何客户端的私钥
- 即使密钥被物理窃取，也无法解密历史消息（前向安全性）
- 每个消息使用独立的 Message Key，一次一密

### 2.2 密钥体系

```
安装时生成（客户端本地）：
├── Identity Key Pair    — 长期 Curve25519 密钥对（身份标识）
├── Signed Pre Key       — 中期 Curve25519 密钥对（签名后上传）
├── One-Time Pre Keys    — 一次性 Curve25519 密钥队列（用完即弃）
└── Ephemeral Key        — 每次会话临时生成

会话建立后衍生：
├── Root Key     — 32字节，用于派生 Chain Key
├── Chain Key    — 32字节，用于派生 Message Key（hash ratchet）
└── Message Key  — 80字节（AES-256密钥32B + HMAC-SHA256密钥32B + IV 16B）
```

### 2.3 会话建立流程

```
发起方 A                         服务端                          接收方 B
   │                               │                               │
   │── 请求 B 的 Identity Key ────▶│                               │
   │   Signed Pre Key + OTPK      │                               │
   │◀── 返回 B 的公钥 ────────────│                               │
   │                               │                               │
   │ 计算 master_secret:           │                               │
   │ ECDH(I_A, S_B) ||            │                               │
   │ ECDH(E_A, I_B)  ||           │                               │
   │ ECDH(E_A, S_B)  ||           │                               │
   │ ECDH(E_A, O_B)               │                               │
   │                               │                               │
   │ HKDF → Root Key + Chain Key  │                               │
   │                               │                               │
   │── 加密消息（含会话建立头）───▶│── 转发 ──────────────────────▶│
   │                               │                               │
   │                               │                               │ 计算 master_secret
   │                               │                               │ HKDF → Root Key + Chain Key
   │                               │                               │ 删除已用的 OTPK
```

### 2.4 Double Ratchet 算法（双棘轮）

这是 Signal 协议的核心，提供**前向安全性**：

```
发送消息时（Hash Ratchet）：
  Message Key = HMAC-SHA256(Chain Key, 0x01)
  Chain Key   = HMAC-SHA256(Chain Key, 0x02)  ← 棘轮向前

收到回复时（DH Ratchet）：
  ephemeral_secret = ECDH(Ephemeral_A, Ephemeral_B)
  Chain Key, Root Key = HKDF(Root Key, ephemeral_secret)
  ← 双方同时更新

特性：
  ✓ 每个消息使用不同的 Message Key（一次一密）
  ✓ 泄露当前 Message Key 无法推算过去或未来的 Key
  ✓ 消息可以乱序到达、延迟到达而不破坏安全性
  ✓ 旧密钥被安全丢弃，无法重建
```

### 2.5 群聊方案：Sender Keys

```
群聊消息采用 Sender Keys 机制：
  1. 每个群成员首次发消息时，生成自己的 Sender Key
     - 随机 32 字节 Chain Key
     - 随机 Curve25519 Signature Key 密钥对
  2. Sender Key 通过 pairwise 加密通道分发给每个群成员
  3. 后续消息：用 Chain Key 派生 Message Key，加密后单条消息发给服务端
  4. 服务端做 server-side fan-out 分发给所有群成员
  5. 有成员离开时，所有群成员清除 Sender Key 并重新生成

优点：服务端只需分发 1 条密文，效率高
缺点：没有前向安全性（群成员可解密之前消息）
```

### 2.6 密钥验证

WhatsApp 提供**安全码（Security Code）** 验证：
- 基于双方 Identity Key 的 60 位数字指纹
- 可通过二维码扫描或对比数字串验证
- **非强制**——默认 Trust On First Use (TOFU)

---

## 3. Telegram 加密方案（MTProto 2.0）

### 3.1 概述

Telegram 使用自研的 **MTProto 2.0** 协议，采用**分层架构**：

```
分层架构：
  ┌─────────────────────────────────┐
  │  API 查询层（TL 序列化）         │
  ├─────────────────────────────────┤
  │  加密层（AES-256 IGE）           │
  ├─────────────────────────────────┤
  │  传输层（TCP/HTTP/WS/UDP）       │
  └─────────────────────────────────┘
```

**重要区别：** Telegram 默认的"云聊天"**不是 E2EE**，只有"秘密聊天"（Secret Chats）才是端对端加密的。

### 3.2 双轨制：云聊天 vs 秘密聊天

| 特性 | 云聊天（Cloud Chat） | 秘密聊天（Secret Chat） |
|------|---------------------|----------------------|
| 加密范围 | 客户端 ↔ 服务端 | 客户端 ↔ 客户端（E2EE） |
| 服务端可见 | ✅ 明文 | ❌ 密文 |
| 多端同步 | ✅ 全平台同步 | ❌ 仅限两台设备 |
| 消息转发 | ✅ 支持 | ❌ 禁止 |
| 前向安全性 | ✅ 支持 | ✅ 支持 |
| 密钥存储 | 服务端持有 | 仅客户端持有 |

### 3.3 认证密钥（Authorization Key）建立

```
客户端首次运行时：
  1. 客户端生成随机 256 位密钥对 (a, g_a)
  2. 发送 g_a 到服务端
  3. 服务端生成随机 b，计算 g_b = pow(g, b) mod p
  4. 服务端返回 g_b
  5. 客户端计算 key = pow(g_b, a) mod p（2048 位）
  6. 服务端计算 key = pow(g_a, b) mod p（2048 位）
  7. 双方得到相同的 2048 位 auth_key

auth_key 永不通过网络传输，除非重装应用
```

### 3.4 消息加密过程

```
客户端 → 服务端（云聊天加密）：

  1. 构造消息体（session_id, msg_id, seq_no, 消息内容, padding）
  2. 计算 msg_key = SHA256(substr(auth_key, 88, 32) + 消息体 + padding) 的中间 128 位
  3. 派生 AES 密钥和 IV：
     sha256_a = SHA256(msg_key + substr(auth_key, x, 36))
     sha256_b = SHA256(substr(auth_key, 40+x, 36) + msg_key)
     aes_key = substr(sha256_a, 0, 8) + substr(sha256_b, 8, 16) + substr(sha256_a, 24, 8)
     aes_iv  = substr(sha256_b, 0, 8) + substr(sha256_a, 8, 16) + substr(sha256_b, 24, 8)
     （x=0 客户端→服务端，x=8 服务端→客户端）
  4. AES-256-IGE 加密
  5. 附加 auth_key_id(64bit) + msg_key(128bit) 头部

秘密聊天额外层：
  1. 先用 Secret Chat Key 加密消息体
  2. 再用 auth_key 加密（同上）
```

### 3.5 秘密聊天密钥交换

```
A 发起                         服务端                          B
  │                              │                              │
  │── 请求创建秘密聊天 ─────────▶│                              │
  │                              │── 通知 B ──────────────────▶│
  │                              │◀─ 接受 ─────────────────────│
  │◀─ 返回 g_b, p, fingerprint ─│                              │
  │                              │                              │
  │ 计算 key = pow(g_b, a) mod p │                              │
  │                              │                              │
  │── 加密消息（含 g_a） ───────▶│── 转发 ────────────────────▶│
  │                              │                              │ 计算 key = pow(g_a, b) mod p
  │                              │                              │ 验证 fingerprint
```

### 3.6 密钥更新（Perfect Forward Secrecy）

```
条件：每 100 条消息 或 超过 1 周（只要发过至少 1 条消息）

流程：
  A ── RequestKey(交换ID, g_a) ─────────────▶ B
  A ◀── AcceptKey(交换ID, g_b, fingerprint) ── B
  A ── CommitKey(交换ID) ───────────────────▶ B
  A ── (新密钥加密的消息) ──────────────────▶ B

  ✓ 旧密钥安全丢弃，无法重建
  ✓ 即使攻击者获取当前密钥，也无法解密历史消息
```

---

## 4. 方案对比：WhatsApp vs Telegram

| 维度 | WhatsApp (Signal) | Telegram (MTProto 2.0) |
|------|------------------|----------------------|
| 协议来源 | 第三方审计 + 开源 | 自研 + 开源客户端 |
| 默认加密 | ✅ 全部 E2EE | ❌ 仅秘密聊天 E2EE |
| 群聊 E2EE | ✅ Sender Keys | ❌ 仅服务端加密 |
| 前向安全性 | ✅ Double Ratchet | ✅ 定期密钥更新 |
| 多端同步 | ⚠️ 需密钥同步 | ✅ 云聊天天然支持 |
| 密钥验证 | 安全码（非强制） | 指纹（非强制） |
| 抗量子计算 | 🚧 PQXDH 开发中 | ❌ 未支持 |
| 协议复杂度 | 高（双棘轮） | 中（AES-IGE） |
| 密码学审计 | ✅ 多次独立审计 | ⚠️ 有争议（UKS 攻击） |
| 服务器角色 | 不可信（仅转发） | 部分可信（云存储） |

### 4.1 争议点

**Telegram 的争议：**
- 自研协议 MTProto 受到密码学界批评（"不要自己造轮子"）
- 云聊天非 E2EE，服务端可读取所有消息内容
- 曾有研究发现 MTProto 对 Unknown Key-Share 攻击存在理论漏洞

**WhatsApp 的争议：**
- 虽然协议是开源的，但客户端实现是闭源的
- 密钥备份到 iCloud/Google Drive 时可能被政府调取
- 2021 年曾有研究发现 WhatsApp 的群聊协议存在一个设计缺陷（虽然已修复）

---

## 5. Vela 项目推荐方案

### 5.1 当前阶段（L1-L2 边界条件）：暂不引入 E2EE

```
当前策略：
  - 消息体明文存储（MySQL）
  - 仅对个别敏感字段预留加密字段
  - 重点建设：元数据日志体系 + 可靠投递 + 异常处理

理由：
  - E2EE 引入复杂度高，当前阶段应优先打牢基础
  - 需要先完善：消息可靠投递、ACK 机制、离线消息、多端同步
  - 需要先建立：TraceId 日志追踪、监控指标、告警体系
```

### 5.2 中期阶段（L3-L4）：混合加密方案（推荐）

```
消息结构设计：
{
  "fromId": "A",                          // 明文 — 路由
  "toId": "B",                            // 明文 — 路由
  "messageKey": 12345,                    // 明文 — 去重
  "messageSequence": 10,                  // 明文 — 保序
  "messageType": "text|image|system",     // 明文 — 路由/展示
  "messageBody": "...",                   // 按消息类型可选加密
  "encryptedBody": "..."                  // 密文（E2EE 时使用）
  "encryptVersion": 1,                    // 加密版本号
  "encryptKeyId": "key_fingerprint"       // 公钥指纹
}

按消息类型分层：
  - 文本消息:     messageBody 加密，encryptedBody 存密文
  - 图片/文件:    文件本身加密存储，缩略图服务端可见
  - 系统消息:     messageBody 明文（服务端生成）
  - 红包/转账:    明文（业务需要服务端校验）
```

### 5.3 长期阶段（L5）：Signal 协议集成

```
计划：
  1. 引入 libsignal-protocol-java 库
  2. 实现密钥生成与存储（客户端本地）
  3. 实现会话建立与 Triple-DH 握手
  4. 实现 Double Ratchet 加密/解密
  5. 实现群聊 Sender Keys
  6. 实现密钥验证机制

前提条件：
  - 已有完善的客户端 SDK
  - 已有可靠的消息推送通道
  - 已有完善的元数据日志体系
```

---

## 6. E2EE 下的日志治理与元数据监控

### 6.1 可记录的内容（安全区域）

即使启用了 E2EE，服务端依然可以记录以下**元数据**：

```java
// ✅ 可记录的安全元数据
logger.info("Message sent: fromId={}, toId={}, msgKey={}, seq={}, status={}",
    fromId, toId, msgKey, seq, status);

// ✅ 可监控的性能指标
// - 消息 QPS（按消息量统计）
// - 发送延迟（服务端时间戳差值）
// - 送达率（ACK 成功数 / 总发送数）
// - 在线用户数（连接数）
// - 投递成功率（按 status 统计）
```

### 6.2 不可记录的内容（隐私区域）

```java
// ❌ 不可记录的消息内容
// logger.info("Message body: {}", messageBody);       // 明文不可记录
// logger.info("Encrypted body: {}", encryptedBody);    // 密文也不应记录
// logger.info("Decryption key: {}", key);              // 密钥绝不记录
```

### 6.3 服务端仍然能保证的能力

| 能力 | 说明 |
|------|------|
| ✅ 消息顺序 | sequence 由服务端生成，不受 E2EE 影响 |
| ✅ 消息去重 | messageKey 幂等，服务端独立校验 |
| ✅ 可靠投递 | ACK 机制基于元数据，不依赖消息体 |
| ✅ 离线消息 | 存储密文，上线后直接推送 |
| ✅ 多端同步 | 增量同步基于 sequence，纯元数据 |
| ✅ 已读回执 | 基于 messageKey，纯元数据 |
| ✅ 消息撤回 | 基于 messageKey 和 time，纯元数据 |
| ✅ 监控告警 | QPS/延迟/成功率，均可从元数据统计 |

### 6.4 服务端丧失的能力及替代方案

| 丧失的能力 | 替代方案 |
|-----------|---------|
| ❌ 全文搜索 | 客户端本地索引（如 SQLite FTS） |
| ❌ 敏感词过滤 | 客户端上报 + 服务端策略下发 |
| ❌ 内容审核 | 用户举报 + 人工审核（仅看举报消息） |
| ❌ 消息推荐 | 基于元数据（频率/联系人关系）的推荐 |

---

## 7. 实施路线图

### Phase 1：基础建设（当前 ~ 2 个月）

```
目标：建立完善的元数据日志体系
  - 全链路 TraceId 追踪
  - 消息状态流转日志（发送→ACK→已读→撤回）
  - Prometheus 监控指标（QPS/延迟/成功率）
  - 告警阈值配置
  - 关键元数据落表（用于排障分析）
```

### Phase 2：消息类型分层（2 ~ 4 个月）

```
目标：按消息类型区分加密策略
  - 消息体增加 messageType / encryptVersion 字段
  - 系统消息明文（服务端生成）
  - 用户消息预留 encryptedBody 字段
  - 实现字段级加密（服务端持有密钥，非 E2EE）
  - 客户端展示层适配
```

### Phase 3：E2EE 试点（4 ~ 8 个月）

```
目标：单聊 E2EE 试点
  - 引入 Signal 协议库
  - 客户端密钥生成与管理
  - 会话建立（Triple-DH）
  - 消息加密/解密（Double Ratchet）
  - 群聊 Sender Keys
  - 密钥验证界面
```

### Phase 4：全量 E2EE（8 ~ 12 个月）

```
目标：默认全部 E2EE
  - 所有消息类型支持 E2EE
  - 文件/图片 E2EE 加密
  - 密封发送方（Sealed Sender）
  - 后量子安全扩展（PQXDH）
  - 第三方安全审计
```

---

## 附录：参考资源

| 资源 | 链接 |
|------|------|
| Signal Protocol 规范 | https://signal.org/docs/ |
| Double Ratchet 算法 | https://signal.org/docs/specifications/doubleratchet/ |
| WhatsApp 加密白皮书 | https://cryptome.org/2016/04/whatsapp-crypto.pdf |
| Telegram MTProto 2.0 | https://core.telegram.org/mtproto |
| Telegram 秘密聊天规范 | https://core.telegram.org/api/end-to-end |
| libsignal-protocol-java | https://github.com/signalapp/libsignal-protocol-java |
| OMEMO（XMPP E2EE） | https://conversations.im/omemo/ |
| PQXDH 后量子密钥协商 | https://signal.org/docs/specifications/pqxdh/ |