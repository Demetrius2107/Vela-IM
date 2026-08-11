<div align="center">

# Vela — 企业级即时通讯与办公生态一体化系统

**Spring Boot + Netty + Vue 3 + Kotlin Multiplatform 全栈 IM 解决方案**

[**中文**](README.md) | [**English**](README.en.md) | [**日本語**](README.ja.md)

![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)
![Java](https://img.shields.io/badge/Java-17%20%2F%208-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.2-brightgreen.svg)
![Netty](https://img.shields.io/badge/Netty-4.1-green.svg)
![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D.svg)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)

</div>

---

## 目录

- [项目概述](#项目概述)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [架构设计](#架构设计)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [多端客户端](#多端客户端)
- [文档](#文档)
- [开发规范](#开发规范)
- [贡献指南](#贡献指南)
- [许可证](#许可证)

---

## 项目概述

Vela 是一个涵盖 **IM 即时通讯、管理后台、办公生态、音视频通话** 的全栈项目。后端基于 **DDD 六边形架构**，支持 **TCP / WebSocket** 双协议长连接；前端覆盖 **Web / Android / Electron / Flutter / iOS** 多端。

- 全链路联通：TCP 网关 → 业务服务 → 消息存储，Phase 1 连通性测试 11/11 全部通过
- 可靠投递：ACK 重推、指数退避重试、离线消息增量拉取、DB 降级补偿
- 微服务架构：12 个模块按 DDD 分层拆分，MQ 异步解耦，可独立部署

### 核心架构

```
Client ──TCP/WS──→ vela-tcp(网关) ──MQ──→ vela-service(业务) ──MQ──→ vela-message-store(存储)
                       │                         │
                       ├── Redis (缓存/会话)      ├── MySQL (持久化)
                       ├── RabbitMQ (事件)        ├── Elasticsearch (全文检索)
                       └── ZooKeeper (注册)        └── Logstash (日志采集)
                                                       └── Kibana (可视化)
```

### 统计

| 指标 | 数据 |
|:----|:----:|
| Java 源码 | ~400+ 文件 |
| 单元测试 | 4 个（vela-tcp，其余模块待补） |
| REST 端点 | 60+ 个 |
| 服务模块 | 12 个 |
| Docker 容器 | 16 个 |
| Git 提交 | 100+ 个 |

---

## 功能特性

### IM 核心（Phase 0-4）✅

| 模块 | 功能 |
|:----|:------|
| 文字消息 | P2P + 群聊消息收发、ACK、去重、多端同步 |
| 消息撤回 | 可配置撤回窗口 + 时钟偏差容错 |
| 已读回执 | 单聊 + 群聊已读通知 |
| 离线消息 | Redis ZSet 增量拉取 + 超限降级 DB |
| 会话管理 | 置顶/免打扰/删除/标记已读 |
| 好友关系 | 增删改查/分组/黑名单/请求审批 |
| 群组管理 | 创建/解散/禁言/转让/角色管理/群公告/群投票 |
| 多端登录 | 4 种策略（单端 ~ 不限制）|
| TCP/WS 网关 | Netty 双协议 + 心跳 + 注册发现 |
| 链路追踪 | MDC TraceId 全链路透传 |

### L2 异常边界（Phase 0.5）✅

| 功能 | 说明 |
|:----|:------|
| 消息重试 | 指数退避重试（可配 3 次）|
| ACK 重推 | PendingAckTracker + 定时扫描 |
| 降级框架 | ServiceDegradationManager（Redis/MQ 熔断）|
| DB 补偿 | MessageCompensationStore + 定时重试 |
| 并发锁 | MessageLockManager（ReadWriteLock 协调撤回 ↔ 推送）|
| 时间容错 | 可配时钟偏差 + 反向偏差检查 |

### 管理后台（Phase 5）✅

| 模块 | 功能 |
|:----|:------|
| 数据看板 | 统计卡片 + 消息趋势 + Top 10 群组 |
| 用户管理 | 搜索/分页/详情/批量禁用/登录日志 |
| 群组管理 | 列表/状态筛选/详情/解散/导出 |
| 消息审计 | ES 全文搜索 + SQL LIKE 降级 |
| 操作日志 | 自动记录全部管理操作 |
| 管理员 | 超管/运营/审计三级权限 |
| 系统配置 | 动态参数调整 |

### 办公生态（Phase 6）✅

| 模块 | 功能 |
|:----|:------|
| 日程管理 | 创建/列表/状态/删除 |
| 待办管理 | 创建/列表/优先级/完成 |
| 审批流程 | 提交/审批通过/拒绝 |
| 知识库/文档 | 文档 CRUD + 在线编辑器 |
| Bot 市场 | Bot 安装/订阅/管理 + 指令配置 |
| 消息收藏 | 收藏 CRUD + 跨端同步 |

---

## 技术栈

| 类别 | 技术 | 用途 |
|------|------|------|
| 开发语言 | Java 8/17 + Kotlin | 后端 + Android |
| 框架 | Spring Boot 2.3.2 | 业务服务容器 |
| 网络框架 | Netty 4.1 | TCP/WebSocket 长连接 |
| ORM | MyBatis-Plus 3.4.2 | 数据库访问 |
| 缓存 | Redis 6.2 | Session/离线消息/序列号 |
| 消息队列 | RabbitMQ 3.8 | 异步解耦/事件驱动 |
| 注册中心 | ZooKeeper 3.6 | 网关节点发现 |
| 全文检索 | Elasticsearch 7.17 | 消息搜索 + 日志存储 |
| 日志采集 | Logstash + Kibana 7.17 | ELK 日志体系 |
| 序列化 | Protostuff | TCP 协议编解码 |
| 前端 | Vue 3 + Naive UI | Web 端 IM |
| 桌面端 | Electron 28 | 桌面 IM 客户端 |
| 移动端 | Kotlin + Jetpack Compose | Android 客户端 |
| 监控 | Prometheus + Grafana + SkyWalking | 指标/APM |
| 构建 | Maven + Gradle | 后端 + Android |

---

## 架构设计

遵循 DDD 分层依赖：**interfaces → application → domain ← infrastructure**，跨模块引用只允许单向依赖。

详细设计文档见 [`docs/architecture/`](docs/architecture/)：

| 文档 | 说明 |
|------|------|
| [system-architecture.md](docs/architecture/system-architecture.md) | 系统整体架构 |
| [DDD-Hexagonal-Architecture.md](docs/architecture/DDD-Hexagonal-Architecture.md) | DDD 六边形架构设计 |
| [concurrent-conflict-handling.md](docs/architecture/concurrent-conflict-handling.md) | 并发冲突处理 |
| [e2e-encryption-design.md](docs/architecture/e2e-encryption-design.md) | 端到端加密（E2EE）设计 |

---

## 快速开始

### Docker 一键启动（推荐）

```bash
# 1. 构建后端
mvn clean package -DskipTests -q

# 2. 启动全部服务
docker-compose up -d
```

### 手动启动

```bash
# 1. 启动中间件：MySQL / Redis / RabbitMQ / ZooKeeper
docker-compose -f docker-compose.middleware.yml up -d

# 2. 启动 API 网关（端口 8889）
cd vela-gateway && mvn spring-boot:run

# 3. 启动业务服务（user / friendship / group / message / conversation ...）
cd vela-service-user && mvn spring-boot:run

# 4. 启动 TCP/WS 网关（端口 9000）
cd vela-tcp && mvn spring-boot:run

# 5. 启动前端
cd web && npm install && npm run dev
```

> 部署排错与完整指南见 [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md)、[`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md)。

### 访问入口

| 入口 | 地址 |
|:----|:-----|
| IM Web 端 | http://localhost:3000 |
| 管理后台 | http://localhost:3000/#/admin |
| 办公生态 | http://localhost:3000/#/office |
| Kibana | http://localhost:5601 |
| Grafana | http://localhost:3000 (admin/admin) |

---

## 项目结构

```
Vela/
├── vela-common/           # 共享内核层（枚举/常量/消息类型/配置）
├── vela-codec/            # 基础设施：TCP/WS 协议编解码
├── vela-tcp/              # 接口适配层：Netty TCP/WS 网关
├── vela-gateway/          # API 网关
├── vela-service-*/        # 业务服务（DDD 分层，12 个模块）
│   ├── user/              # 用户域
│   ├── friendship/        # 好友关系域
│   ├── group/             # 群组域（含群公告/投票/标签/文件）
│   ├── message/           # 消息域（含 ES 搜索/已读跟踪）
│   ├── conversation/      # 会话域
│   ├── admin/             # 管理后台
│   ├── bot/               # Bot 机器人
│   ├── office/            # 办公生态（日程/待办/审批）
│   └── ...
├── vela-message-store/    # 基础设施：消息持久化服务
├── web/                   # Vue 3 前端（IM/管理后台/办公）
├── android/               # Android 客户端（Kotlin + Compose）
├── electron/              # Electron 桌面端
├── flutter_desktop/       # Flutter 桌面端（实验）
├── ios/                   # iOS 客户端（SwiftUI）
├── deploy/                # 部署配置（Logstash/Prometheus/脚本）
├── docs/                  # 文档中心
│   ├── guide/             # 部署/Docker/联调指南
│   ├── analysis/          # 差距分析/功能对比
│   ├── roadmap/           # 迭代计划/TODO 清单
│   ├── architecture/      # 架构设计文档
│   ├── api/               # REST API 文档
│   ├── logs/              # 历史运行日志归档
│   └── 会议记录/           # 会话工作记录
├── docker-compose.yml     # 16 容器编排
└── AGENTS.md              # 项目开发规范（AI 辅助编码）
```

---

## 多端客户端

| 平台 | 状态 | 说明 |
|:----|:----:|:------|
| Web (Vue 3) | ✅ | 完整 IM + 管理后台 + 办公生态 |
| Android (Compose) | ✅ | 登录/注册/会话列表/聊天/通讯录 |
| Electron 桌面端 | ✅ | Web 套壳 + 系统托盘 + 窗口管理 |
| Flutter 桌面端 | 🚧 | 实验性多端方案 |
| iOS (SwiftUI) | 🚧 | 原生客户端开发中 |

---

## 文档

| 分类 | 文档 |
|------|------|
| API 文档 | [`docs/api/api-documentation.md`](docs/api/api-documentation.md) |
| 部署指南 | [`docs/guide/deployment-guide.md`](docs/guide/deployment-guide.md) |
| Docker 指南 | [`docs/guide/docker-complete-guide.md`](docs/guide/docker-complete-guide.md) |
| Docker 排错 | [`docs/guide/docker-troubleshooting.md`](docs/guide/docker-troubleshooting.md) |
| 联调计划 | [`docs/guide/integration-testing-plan.md`](docs/guide/integration-testing-plan.md) |
| 差距分析 | [`docs/analysis/current-state-gap-analysis.md`](docs/analysis/current-state-gap-analysis.md) |
| 功能对比 | [`docs/analysis/feature-gap-analysis.md`](docs/analysis/feature-gap-analysis.md) |
| 迭代路线图 | [`docs/roadmap/feature-roadmap.md`](docs/roadmap/feature-roadmap.md) |
| 启动问题清单 | [`docs/Vela项目启动问题完整清单.md`](docs/Vela项目启动问题完整清单.md) |
| MySQL 重构 | [`docs/MySQL/database-refactor-plan.md`](docs/MySQL/database-refactor-plan.md) |

---

## 开发规范

参见 [`AGENTS.md`](AGENTS.md)。核心规则：

```
1. DDD 分层依赖：interfaces → application → domain ← infrastructure
2. 构造器注入，非 @Autowired
3. 函数不超过 50 行，硬编码常量抽到配置
4. 新建实体同步建表 SQL / 修改消息模型同步更新 OfflineMessageContent
5. 注释写"为什么这么做"而非"做了什么"
6. Git 提交格式：<type>(<scope>): <subject>
```

---

## 贡献指南

1. Fork 本仓库并创建功能分支：`git checkout -b feat/<description>`
2. 遵循 [`AGENTS.md`](AGENTS.md) 中的编码与提交规范
3. 提交前运行 `mvn -B clean compile` 确保通过
4. 发起 Pull Request 到 `master` 分支

---

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

---

> Copyright © 2026 Vela Contributors. Released under the MIT License.
