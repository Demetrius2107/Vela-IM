# Vela IM — 系统架构文档

> 版本: v1.0 | 更新: 2026-07-29
> 本文档覆盖：整体架构、模块职责、数据流、部署拓扑、技术选型

---

## 一、整体架构图

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            客户端层                                      │
│  ┌─────────┐  ┌──────────────┐  ┌──────────┐  ┌──────────────────┐    │
│  │ Web 前端 │  │ Android 客户端│  │Electron  │  │ 第三方 API 调用方│    │
│  │ Vue 3   │  │ Kotlin+Compose│  │ 桌面端   │  │ (Bot Webhook)   │    │
│  └────┬────┘  └──────┬───────┘  └────┬─────┘  └────────┬─────────┘    │
│       │              │               │                  │              │
├───────┼──────────────┼───────────────┼──────────────────┼──────────────┤
│       │ HTTP/JSON    │ HTTP/JSON     │ HTTP/JSON        │ HTTP/JSON    │
│  ┌────▼──────────────▼───────────────▼──────────────────▼──────────┐  │
│  │                    API 网关 / 负载均衡层                         │  │
│  │          (vela-gateway / Spring Cloud Gateway)                   │  │
│  │             路由转发 / 鉴权 / 限流 / 日志                         │  │
│  └────────────────────────────┬────────────────────────────────────┘  │
│                               │                                       │
│                  ┌────────────▼────────────┐                          │
│                  │    WebSocket 推送上行     │                          │
│                  │    (vela-tcp :19000)     │                          │
│                  └────────────┬────────────┘                          │
│                               │                                       │
│  ┌────────────────────────────▼────────────────────────────────────┐  │
│  │                      业务服务层                                  │  │
│  │  ┌───────────┬───────────┬───────────┬───────────┬───────────┐ │  │
│  │  │ 用户域    │ 好友域    │ 群组域    │ 消息域    │ 会话域    │ │  │
│  │  │ UserService│FriendSvc │GroupSvc   │MsgService │ConvSvc    │ │  │
│  │  ├───────────┼───────────┼───────────┼───────────┼───────────┤ │  │
│  │  │ Bot域     │ 配置中心  │ 管理后台  │ 办公域    │ 知识库    │ │  │
│  │  │ BotService│Config/F.F│Admin      │Office     │Knowledge  │ │  │
│  │  └───────────┴───────────┴───────────┴───────────┴───────────┘ │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                               │                                       │
│  ┌────────────────────────────▼────────────────────────────────────┐  │
│  │                   消息队列 (RabbitMQ)                             │  │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │   │ P2P 消息队列  │  │ 群聊消息队列  │  │ 离线消息队列     │    │  │
│  │   └──────────────┘  └──────────────┘  └──────────────────┘    │  │
│  └────────────────────────────┬────────────────────────────────────┘  │
│                               │                                       │
│  ┌────────────────────────────▼────────────────────────────────────┐  │
│  │                   消息存储层 (vela-message-store)                │  │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐    │  │
│  │   │ MySQL 持久化  │  │ES 全文检索   │  │ Redis 缓存       │    │  │
│  │   └──────────────┘  └──────────────┘  └──────────────────┘    │  │
│  └────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、模块职责

### 2.1 模块清单

| 模块 | 端口 | 职责 | 技术栈 |
|-----|----|-----|-------|
| `vela-codec` | — | TCP 协议编解码，消息 Pack 定义 | Netty + Protostuff |
| `vela-common` | — | 共享类型、常量、错误码、工具类 | Spring Boot |
| `vela-tcp` | 9000/19000 | TCP/WebSocket 网关，连接管理，消息路由 | Netty |
| `vela-gateway` | 8888 | HTTP API 网关，路由转发，鉴权 | Spring Cloud Gateway |
| `vela-service` | 8000 | 核心业务服务（10 个业务域） | Spring Boot |
| `vela-message-store` | 8001 | 消息持久化，MySQL 写 + ES 索引 | Spring Boot |

### 2.2 业务域（vela-service 内部）

| 子域 | 包路径 | 核心职责 |
|-----|-------|---------|
| user | `service/user` | 用户注册/登录/资料管理/在线状态 |
| friendship | `service/friendship` | 好友添加/删除/黑名单/分组 |
| group | `service/group` | 群组 CRUD/成员管理/群聊消息 |
| message | `service/message` | 消息发送/已读/撤回/同步/搜索 |
| conversation | `service/conversation` | 会话列表/置顶/免打扰/同步 |
| bot | `service/bot` | Bot 注册/消息转发/Webhook/内联键盘/指令 |
| config | `service/config` | 用户配置/功能开关（UserConfig + FeatureFlag）|
| admin | `service/admin` | 管理后台/看板/审计/系统配置 |
| office | `service/office` | 日程/待办/审批/业务系统 |
| knowledge | `service/knowledge` | 文档 CRUD/分类/搜索 |

---

## 三、数据流

### 3.1 P2P 消息发送

```
发送方 Web/App                接收方 Web/App
     │                            ▲
     │ HTTP POST /v1/message/send │
     ▼                            │
┌──────────────┐                  │
│ vela-service  │                  │
│ 消息域        │                  │
│ ① 校验权限    │                  │
│ ② 校验好友    │                  │
│ ③ 写入 DB     │                  │
│ ④ 发 MQ       │                  │
└──────┬───────┘                  │
       │ RabbitMQ                 │
       │ P2P 消息队列              │
       ▼                          │
┌──────────────┐                  │
│ vela-message │                  │
│ -store       │                  │
│ ① 消息体入库  │                  │
│ ② 历史表入库  │                  │
│ ③ ES 索引     │                  │
└──────┬───────┘                  │
       │                          │
       ▼                          │
┌──────────────┐    WebSocket     │
│  vela-tcp    │─────────────────▶│
│  网关        │    消息推送       │
│  在线检测     │                  │
│  推送        │                  │
└──────────────┘                  │
```

### 3.2 群聊消息发送

```
发送方                     接收方群成员 N 人
  │                              ▲
  │ HTTP POST /v1/message/send   │
  ▼                              │
┌──────────┐  群聊 MQ 队列       │
│ vela-svc │────────────────────▶│  vela-tcp 网关 × N 次推送
│ 消息域    │   Fanout 广播       │
│          │                     │
│ ① 查群成员│   ┌────────────┐   │
│ ② 写入 DB │   │ vela-msg   │   │
│ ③ 发 MQ   │──▶│ -store     │──▶│ MySQL 批量写
└──────────┘   └────────────┘   │
```

### 3.3 Bot 消息交互

```
用户                          Bot
  │                            │
  │ 发送消息给 Bot ID           │
  ▼                            │
┌──────────┐  HTTP POST        │
│ vela-svc │─────────────────▶│  Bot Webhook 服务器
│ 消息域    │  body: {用户消息} │
│ ① 检测toId│                   │
│   是否为Bot│                   │
│ ② 转发到  │◀─────────────────│
│   Webhook │  HTTP 200        │
└──────────┘  body: {回复}     │
  │                            │
  │ 通过 TCP 推送回复给用户       │
  ▼                            │
vela-tcp ─────────────────────▶│
```

---

## 四、技术选型

| 层 | 技术 | 版本 | 选型理由 |
|---|-----|----|---------|
| 语言 | Java + Kotlin | 17+1.9 | 生态成熟 + Android 原生 |
| TCP 框架 | Netty | 4.x | IM 行业标准，百万连接支撑 |
| 协议编解码 | Protostuff | — | 高性能二进制序列化 |
| HTTP 框架 | Spring Boot | 2.3.x | 企业级开发标准 |
| API 网关 | Spring Cloud Gateway | Hoxton | 统一路由/鉴权 |
| ORM | MyBatis-Plus | — | 半自动 ORM，灵活可控 |
| 数据库 | MySQL | 8.0 | 关系型存储标准 |
| 缓存 | Redis | — | 在线状态/会话缓存 |
| 消息队列 | RabbitMQ | 3.8 | 异步解耦，可靠投递 |
| 注册中心 | ZooKeeper | 3.6 | TCP 网关节点发现 |
| 全文检索 | Elasticsearch | 7.17 | 消息搜索 + 日志存储 |
| 日志采集 | Logstash + Kibana | 7.17 | ELK 日志体系 |
| 监控 | Prometheus + Grafana | — | 指标采集和可视化 |
| APM | SkyWalking | — | 链路追踪（可选）|
| 前端 | Vue 3 + Naive UI | — | 响应式，组件丰富 |
| 桌面端 | Electron | 28 | 跨平台桌面客户端 |
| 移动端 | Kotlin + Compose | — | Android 原生体验 |

---

## 五、部署拓扑

```
                         ┌──────────┐
                         │   Nginx   │
                         │ 反向代理   │
                         │ :80/:443  │
                         └────┬─────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
         ┌────▼────┐    ┌────▼────┐    ┌─────▼─────┐
         │ Web 静态 │    │ API 网关 │    │ TCP 网关   │
         │ :3000   │    │ :8888   │    │ :9000     │
         │ (Vue)   │    │(Gateway)│    │ :19000    │
         └─────────┘    └────┬────┘    └─────┬─────┘
                             │               │
                        ┌────▼───────────────▼─────┐
                        │     业务服务              │
                        │     vela-service :8000    │
                        │     vela-message-store    │
                        │          :8001           │
                        └────┬──────────────┬──────┘
                             │              │
                    ┌────────▼──┐    ┌──────▼──────┐
                    │   MySQL   │    │  RabbitMQ   │
                    │   :3306   │    │  :5672      │
                    └───────────┘    └─────────────┘
                             │              │
                    ┌────────▼──┐    ┌──────▼──────┐
                    │  Redis    │    │ ZooKeeper   │
                    │  :6379    │    │  :2181      │
                    └───────────┘    └─────────────┘
                             │
                    ┌────────▼──────────────────────┐
                    │  Elasticsearch :9200          │
                    │  Logstash :5000               │
                    │  Kibana :5601                 │
                    │  Prometheus :9090             │
                    │  Grafana :3000                │
                    └───────────────────────────────┘
```

### 一键部署

```bash
# 全部启动
docker-compose up -d

# 单独启动某组
docker-compose up -d mysql redis rabbitmq           # 中间件
docker-compose up -d vela-service vela-tcp web       # 业务
docker-compose up -d prometheus grafana              # 监控
docker-compose up -d elasticsearch kibana logstash   # ELK

# 查看状态
docker ps --format "table {{.Names}}\t{{.Status}}"
```

---

## 六、数据库核心表

| 表名 | 说明 | 模块 |
|-----|-----|-----|
| `im_user_data` | 用户数据 | user |
| `im_friendship` | 好友关系 | friendship |
| `im_friendship_group` | 好友分组 | friendship |
| `im_group` | 群组 | group |
| `im_group_member` | 群成员 | group |
| `im_message_body` | 消息体 | message |
| `im_message_history` | 消息历史 | message |
| `im_conversation_set` | 会话设置 | conversation |
| `vela_bot` | Bot机器人 | bot |
| `vela_user_bot` | 用户-Bot订阅 | bot |
| `vela_user_config` | 用户配置 | config |
| `vela_feature_flag` | 功能开关 | config |
| `vela_system_config` | 系统配置 | admin |
| `vela_message_favorite` | 消息收藏 | message |
| `vela_office_schedule` | 日程 | office |
| `vela_office_todo` | 待办 | office |
| `vela_office_approval` | 审批 | office |

---

## 七、关键设计决策

| 决策 | 选项 | 选择 | 原因 |
|-----|-----|-----|------|
| 网关协议 | HTTP vs TCP | TCP + WS | IM 实时推送必须长连接 |
| 序列化 | JSON vs Protostuff | Protostuff | 二进制，体积小 60%，性能高 3x |
| 消息存储 | 写扩散 vs 读扩散 | 写扩散 | 群聊写 1 次 vs 读拉取 N 次，写扩散快 |
| 离线消息 | 拉取 vs 推送 | 拉取 | 上线时 syncOfflineMessage |
| 配置管理 | 本地 vs 中心化 | 本地+自建 | 不需要 Nacos 那么大，FeatureFlag 够用 |
| 客户端 | native vs Hybrid | native | Android Compose + Web Vue 各自原生体验 |

---

## 八、同类型 IM 对比

| 维度 | Vela | Teamtalk(Momo) | Tim(腾讯) |
|-----|-----|---------------|----------|
| 协议 | TCP+Protostuff | TCP+Protobuf | 私有协议 |
| 网关 | Netty 自研 | Netty 自研 | 自研 |
| 存储 | MySQL+ES | MySQL+ES | 自研 |
| 客户端 | Vue3+Android+Electron | iOS+Android+PC | iOS+Android+PC+Mac |
| 管理后台 | 完整 | 完整 | 完整 |
| 监控 | Prometheus+Grafana+ELK | — | 自研 |
| 办公生态 | 日程/待办/审批 | — | 文档/会议/日程 |
| Bot | Webhook+内联键盘 | — | 小程序 |
