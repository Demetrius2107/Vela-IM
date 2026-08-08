# 并发冲突处理设计文档

> 版本: v1.0 | 创建时间: 2026-07-27

---

## 一、背景

在 IM 系统中，同一消息可能同时被多个操作触及。例如：

> 用户 A 发送了一条消息 → 消息正在推送给接收方 → 同时用户 A 点击撤回

或者：

> 用户 A 的 PC 端发起了撤回 → 同时手机端也发起了同一条消息的撤回

如果没有并发控制，可能会出现：

- 消息已被撤回但仍然推送到接收方
- 同一消息被重复撤回
- 已撤回的消息被标记为已读

---

## 二、当前状态分析

### 2.1 现有保护机制

| 位置 | 保护方式 | 范围 |
|------|----------|------|
| `recallMessage()` | `ConcurrentHashMap<Long, Object>` + `synchronized` | 仅同一 messageKey 的撤回操作互斥 |
| `rateLimitNode()` | `ConcurrentHashMap<String, Long>` 限流 | 仅阻止同一用户频繁发消息 |
| `DedupNode()` | Redis 缓存去重 | 仅阻止重复消息处理 |

### 2.2 现有保护的问题

```
当前保护:    Recall A ──→ lock ──→ DB update ──→ unlock
             Recall B ──→ wait   ──→ lock ──→ (rejected)

缺少的保护:  Recall ──→ lock ──→ DB update ──→ notify
               ↑                          ↑
               └── Push ────────────────┘ (同时进行，无互斥)
```

关键缺口：
- **撤回与推送同时发生**：`PersistAndPushNode` 与 `recallMessage` 没有共享锁
- **撤回与已读同时发生**：`readMark` 与 `recallMessage` 没有共享锁
- **多端撤回**：已有 `synchronized` 保护，但锁释放后无二次确认

---

## 三、目标范围

本次实现聚焦于以下三个场景：

| 优先级 | 场景 | 说明 |
|------|------|------|
| 🔴 P0 | **撤回 ↔ 推送** | 消息正在推送给接收方时被撤回，应阻止已撤回消息的推送 |
| 🔴 P0 | **多端重复撤回** | 已撤回的消息应拒绝后续撤回请求（已有锁，需增强幂等性） |
| 🟡 P1 | **撤回 ↔ 已读** | 已撤回的消息不应被标记为已读 |

---

## 四、设计方案

### 4.1 核心思路：Scoped ReadWriteLock

对每条消息（messageKey）使用 `ReentrantReadWriteLock`：

- **写锁（Write Lock）**：撤回操作独占，任何其他操作（推送、已读）必须等待或拒绝
- **读锁（Read Lock）**：推送、已读操作共享，互不阻塞，但与写锁互斥

```
         ┌────────────────────────────────┐
         │       MessageLockManager       │
         │                                │
         │  messageKey → ReadWriteLock    │
         │                                │
         │  lockRead(key)  → 读锁         │
         │  lockWrite(key) → 写锁         │
         │  unlock(key)    → 释放锁       │
         └────────────────────────────────┘
```

### 4.2 锁粒度策略

| 操作 | 锁类型 | 说明 |
|------|--------|------|
| 撤回消息 | **写锁** | 独占，推送/已读在此期间等待 |
| 推送消息 | **读锁** | 共享，可并行推送；但无法与撤回同时进行 |
| 已读回执 | **读锁** | 共享，与推送不互斥；但与撤回互斥 |

### 4.3 锁超时机制

避免因等锁导致的线程挂起：

- 读锁等待超时：**3 秒**
- 写锁等待超时：**1 秒**（撤回应该快速完成，超时直接拒绝）
- 超时后返回错误，不阻塞业务线程

### 4.4 锁清理

- 使用 `WeakHashMap` 或 `Cache` 管理锁实例，防止内存泄漏
- 长时间未使用的锁自动被回收

---

## 五、涉及文件变更

| 文件 | 改动 |
|------|------|
| `.../utils/MessageLockManager.java` | **新建** — 基于 messageKey 的 ReadWriteLock 管理器 |
| `.../message/.../MessageSyncService.java` | `recallMessage` 获取写锁后才处理撤回 |
| `.../pipeline/node/PersistAndPushNode.java` | 推送前获取读锁，检查消息是否已被撤回 |
| `.../message/.../MessageSyncService.java` | `readMark` 获取读锁，避免与撤回冲突 |

---

## 六、备用方案

如果 ReadWriteLock 过于复杂或有性能风险，简化版本：

**方案 B：全局消息状态机**

不依赖锁，而是依赖 DB 消息状态来判断冲突：

```
消息状态: SENDING → DELIVERED → READ
                          ↘→ RECALLED (最终状态)
```

- 每次操作前检查当前消息状态
- 如果状态已被推进到最终状态（RECALLED），拒绝后续操作
- 通过 DB 的乐观锁（version 字段）保证原子性

> 本次采用方案 A（ReadWriteLock），因为它更轻量，无需改表结构。
