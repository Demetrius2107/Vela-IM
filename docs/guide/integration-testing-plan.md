# Vela IM — 全链路联调方案

> 目标：验证一条消息从 Web/Android → HTTP/TCP → 后端服务 → MySQL/Redis → 推送回接收方的完整路径
> 状态：**待执行**
> 前置条件：`docker-compose up -d` 全部服务运行中

---

## 一、联调架构总览

```
发送方                         接收方
Web 前端 ──HTTP──┐             Web 前端 ←── WebSocket
Android  ──HTTP──┤                        ←── WebSocket
                 ▼
          ┌──────────────┐    ┌──────────────┐
          │  vela-service │    │  vela-tcp     │
          │  REST API     │───▶│  TCP/WS 网关  │──▶ 推送
          │  :8000        │    │  :9000/19000  │
          └──────┬───────┘    └──────────────┘
                 │  RabbitMQ
          ┌──────▼───────┐
          │ vela-message │
          │ -store       │──▶ MySQL / ES
          └──────────────┘
```

---

## 二、联调步骤（按顺序执行）

### Step 1：基础环境验证（5 分钟）

**目标**：确认所有服务正常运行

```bash
# 1. 检查所有容器状态
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# 期望输出：所有 vela-* 容器状态为 Up
# vela-mysql        Up     3306
# vela-redis        Up     6379
# vela-rabbitmq     Up     5672/15672
# vela-zookeeper    Up     2181
# vela-service      Up     8000
# vela-message-store Up   8001
# vela-tcp          Up     9000/19000
# web               Up     3000

# 2. 验证 HTTP 服务
curl -s http://localhost:8000/actuator/health | jq .

# 期望输出：{"status":"UP"}

# 3. 验证 TCP 端口可连
nc -zv localhost 9000
nc -zv localhost 19000

# 4. 验证 MySQL
docker exec vela-mysql mysql -uroot -e "SELECT 1 AS test;"
```

**判定通过**：所有容器 Up，actuator 返回 UP，端口可连。

---

### Step 2：用户注册 + 登录（10 分钟）

**目标**：创建两个用户，获取 token

```bash
# 1. 注册用户 A
curl -s -X POST "http://localhost:8000/v1/user/register" \
  -H "Content-Type: application/json" \
  -d '{"userId":"test_a","nickName":"测试A","password":"123456"}' | jq .

# 期望：{"code":200,"msg":"success","data":...}

# 2. 注册用户 B
curl -s -X POST "http://localhost:8000/v1/user/register" \
  -H "Content-Type: application/json" \
  -d '{"userId":"test_b","nickName":"测试B","password":"123456"}' | jq .

# 3. 用户 A 登录
curl -s -X POST "http://localhost:8000/v1/user/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"test_a","password":"123456"}' | jq .

# 记录返回的 token
TOKEN_A=$(curl -s -X POST "http://localhost:8000/v1/user/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"test_a","password":"123456"}' | jq -r '.data')
echo "TOKEN_A=$TOKEN_A"
```

**判定通过**：注册返回 200，登录返回非空 token。

---

### Step 3：添加好友（10 分钟）

**目标**：A 添加 B 为好友

```bash
# 1. A 搜索 B
curl -s "http://localhost:8000/v1/user/search?userId=test_b" \
  -H "token: $TOKEN_A" | jq .

# 2. A 向 B 发送好友请求
curl -s -X POST "http://localhost:8000/v1/friend/request" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_A" \
  -d '{"fromId":"test_a","toId":"test_b","appId":10000}' | jq .

# 3. B 登录获取 token
TOKEN_B=$(curl -s -X POST "http://localhost:8000/v1/user/login" \
  -H "Content-Type: application/json" \
  -d '{"userId":"test_b","password":"123456"}' | jq -r '.data')

# 4. B 查看好友请求
curl -s "http://localhost:8000/v1/friend/request/list" \
  -H "token: $TOKEN_B" | jq .

# 5. B 同意请求
curl -s -X POST "http://localhost:8000/v1/friend/approve" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_B" \
  -d '{"fromId":"test_b","toId":"test_a","appId":10000}' | jq .

# 6. 验证好友关系
curl -s "http://localhost:8000/v1/friend/getAllFriend?appId=10000&fromId=test_a" \
  -H "token: $TOKEN_A" | jq .
```

**判定通过**：
- 请求列表能看到好友请求
- 同意后 `getAllFriend` 返回包含对方

---

### Step 4：发送 P2P 消息（15 分钟）

**目标**：A 向 B 发送一条消息，验证消息存储和投递

```bash
# 1. A 发送消息给 B（通过 HTTP）
curl -s -X POST "http://localhost:8000/v1/message/send" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_A" \
  -d '{
    "fromId":"test_a",
    "toId":"test_b",
    "appId":10000,
    "messageBody":"你好，这是第一条真实消息！"
  }' | jq .

# 期望：{"code":200,"data":{"messageKey":1,"messageTime":...}}

# 2. 验证消息已写入数据库
docker exec vela-mysql mysql -uroot -e "
  USE vela;
  SELECT message_key, from_id, to_id, message_body, message_time
  FROM im_message_body
  WHERE from_id='test_a' AND to_id='test_b'
  ORDER BY message_time DESC LIMIT 5;
"

# 期望：能看到刚才发送的消息

# 3. 验证消息历史表
docker exec vela-mysql mysql -uroot -e "
  USE vela;
  SELECT message_key, from_id, to_id, message_body
  FROM im_message_history
  WHERE from_id='test_a'
  ORDER BY message_time DESC LIMIT 5;
"

# 4. B 拉取离线消息
curl -s -X POST "http://localhost:8000/v1/message/syncOfflineMessage" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_B" \
  -d '{"operater":"test_b","appId":10000,"lastSequence":0,"maxLimit":100}' | jq .
```

**判定通过**：
- `message/send` 返回 200 + messageKey
- MySQL `im_message_body` 表能查到记录
- `syncOfflineMessage` 能拉出消息

---

### Step 5：验证 TCP/WS 推送（10 分钟）

**目标**：确认消息能通过 TCP/WS 网关推送到接收方

```bash
# 1. 通过 WebSocket 连接（需要 websocat 或 wscat 工具）
# npm install -g wscat
wscat -c "ws://localhost:19000?userId=test_b&appId=10000"

# 2. 在另一个终端用 A 发送消息
# 此时 B 的 wscat 应该能收到推送消息

# 3. 检查 TCP 网关日志
docker logs vela-tcp --tail 50 | grep -E "test_a|test_b|message"
```

**判定通过**：WS 连接成功，发送消息后接收方能收到推送。

---

### Step 6：群聊消息（10 分钟）

**目标**：创建群组，发送群消息

```bash
# 1. A 创建群组
curl -s -X POST "http://localhost:8000/v1/group/create" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_A" \
  -d '{
    "fromId":"test_a",
    "appId":10000,
    "groupId":"group_01",
    "groupName":"测试群"
  }' | jq .

# 2. A 拉 B 进群
curl -s -X POST "http://localhost:8000/v1/group/member/add" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_A" \
  -d '{
    "fromId":"test_a",
    "appId":10000,
    "groupId":"group_01",
    "memberIds":["test_b"]
  }' | jq .

# 3. A 在群内发消息
curl -s -X POST "http://localhost:8000/v1/message/send" \
  -H "Content-Type: application/json" \
  -H "token: $TOKEN_A" \
  -d '{
    "fromId":"test_a",
    "toId":"group_01",
    "appId":10000,
    "messageBody":"大家好，这是群消息测试"
  }' | jq .
```

**判定通过**：群创建成功、成员添加成功、群消息发送成功。

---

### Step 7：验证监控数据（5 分钟）

```bash
# 1. Prometheus 指标
curl -s http://localhost:9090/api/v1/query?query=up | jq .

# 2. Actuator 指标
curl -s http://localhost:8000/actuator/prometheus | head -30

# 3. 访问 Grafana
# http://localhost:3000  admin/admin
```

**判定通过**：Prometheus target 状态为 UP，/actuator/prometheus 返回指标数据。

---

## 三、问题排查速查表

### 消息发送失败

| 现象 | 排查命令 | 常见原因 |
|-----|---------|---------|
| 401 Unauthorized | `echo $TOKEN_A` | token 过期或为空 |
| 400 Bad Request | 检查请求体 JSON 格式 | fromId/toId 大小写错误 |
| 500 Internal Error | `docker logs vela-service --tail 50` | MySQL 连接断开 |
| 消息发不出去了 | `docker logs vela-tcp --tail 50` | TCP 网关未启动 |
| 收不到推送 | `docker logs vela-rabbitmq --tail 20` | RabbitMQ 队列未绑定 |

### 数据库排查

```bash
# 连接 MySQL
docker exec -it vela-mysql mysql -uroot -p velacom

# 查看消息表
SELECT COUNT(*) FROM im_message_body;
SELECT COUNT(*) FROM im_message_history;

# 查看用户表
SELECT user_id, nick_name FROM im_user_data;

# 查看好友关系
SELECT * FROM im_friendship WHERE from_id='test_a';
```

### 日志排查

```bash
# 业务日志
docker logs vela-service --tail 100
docker logs vela-message-store --tail 100
docker logs vela-tcp --tail 100

# 实时跟踪
docker logs -f vela-service

# RabbitMQ 管理
# http://localhost:15672  guest/guest
```

---

## 四、联调判定标准

### ✅ 通过标准（全部满足）

| 检查项 | 判定 | 验证方式 |
|-------|----|---------|
| 服务全部 Up | ✅ | `docker ps` |
| 注册/登录 | ✅ | API 返回 200 + token |
| 添加好友 | ✅ | `getAllFriend` 返回对方 |
| P2P 消息发送 | ✅ | API 返回 messageKey |
| 消息落库 | ✅ | MySQL 可查到 |
| 离线消息拉取 | ✅ | syncOffline 返回消息 |
| WebSocket 推送 | ✅ | wscat 收到消息 |
| 群组创建+消息 | ✅ | 群消息发送成功 |
| Prometheus 指标 | ✅ | `/actuator/prometheus` 有数据 |
| 管理后台可用 | ✅ | Web 访问 3000 端口 |

### ⚠️ 部分通过

如果仅 WSS 推送不通，其余都 OK → 可上线，WS 推送单独修复
如果消息落库但推送不通 → TCP 网关问题，单独排查
如果消息都通但监控没数据 → actuator 配置问题，确认 application.yml

### ❌ 不通过

核心不可用标准：
- 注册/登录失败 → 后端服务挂了，先修服务
- 消息发送返回 500 → 检查 MySQL 连接
- 所有 API 都超时 → docker-compose 没起起来

---

## 五、恢复环境

如果联调过程中数据搞脏了，重建干净环境：

```bash
# 停止并删除所有容器和数据卷
docker-compose down -v

# 重新拉起
docker-compose up -d

# 等待服务就绪（约 30 秒）
sleep 30

# 从头执行 Step 1
```
