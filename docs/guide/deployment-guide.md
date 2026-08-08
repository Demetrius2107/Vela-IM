# Vela IM — Docker 一键部署指南

> 从零到完整 IM 系统启动的步骤说明
> 涵盖：后端服务 / 中间件 / 前端 / 监控 / ELK

---

## 一、前置条件

| 工具 | 版本要求 |
|------|----------|
| Docker | ≥ 20.10 |
| Docker Compose | ≥ 2.0 |
| Git | 任意版本 |

## 二、快速启动

### 2.1 克隆并构建

```bash
git clone <repo-url> Vela
cd Vela

# 构建所有 Java 服务
mvn clean package -DskipTests -q
```

### 2.2 启动全部服务

```bash
# 启动全部（中间件 + 业务 + 前端 + 监控 + ELK）
docker-compose up -d

# 跟踪启动日志
docker-compose logs -f
```

### 2.3 验证状态

```bash
docker-compose ps
```

所有服务状态为 `Up` 即启动成功。

---

## 三、服务总览

| 服务 | 容器名 | 端口 | 说明 |
|----|------|----|-----|
| MySQL | vela-mysql | 3307 | 业务数据库 |
| Redis | vela-redis | 6379 | 缓存/离线消息/序列号 |
| RabbitMQ | vela-rabbitmq | 5672 | 消息队列 |
| ZooKeeper | vela-zk | 2181 | 服务注册发现 |
| vela-service | vela-service | 8000 | 核心业务服务 |
| vela-message-store | vela-message-store | — | 消息存储服务 |
| vela-tcp | vela-tcp | 9000/19000 | TCP/WebSocket 网关 |
| web | vela-web | 3000 | Vue 3 前端 IM |
| Elasticsearch | vela-es | 9200 | 全文检索 + 日志存储 |
| Kibana | vela-kibana | 5601 | 日志可视化 |
| Logstash | vela-logstash | 5044/5000 | 日志采集管道 |
| Prometheus | vela-prometheus | 9090 | 指标采集 |
| Grafana | vela-grafana | 3000 | 指标可视化 |
| SkyWalking | vela-skywalking-oap | 11800/12800 | APM 链路追踪 |

---

## 四、访问入口

| 入口 | 地址 | 说明 |
|----|-----|-----|
| IM Web 端 | http://localhost:3000 | 完整 IM 客户端 |
| 管理后台 | http://localhost:3000/#/admin | 运营管理界面 |
| Kibana | http://localhost:5601 | 日志检索 |
| Grafana | http://localhost:3000 | 监控看板（admin/admin） |
| Prometheus | http://localhost:9090 | 指标查询 |
| SkyWalking UI | http://localhost:8088 | APM 链路追踪 |
| RabbitMQ 管理 | http://localhost:15672 | 消息队列管理（guest/guest）|

---

## 五、按需启动

```bash
# 只要中间件（不启动业务服务）
docker-compose up -d mysql redis rabbitmq zookeeper elasticsearch

# 只要业务服务
docker-compose up -d vela-service vela-message-store vela-tcp web

# 只要监控
docker-compose up -d prometheus grafana skywalking-oap skywalking-ui

# 只要 ELK
docker-compose up -d elasticsearch kibana logstash
```

---

## 六、环境变量

vela-service 的环境变量配置：

| 变量 | 默认值 | 说明 |
|----|-------|-----|
| `SPRING_DATASOURCE_URL` | jdbc:mysql://mysql:3306/vela | 数据库连接 |
| `SPRING_DATASOURCE_USERNAME` | root | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | root | 数据库密码 |
| `SPRING_REDIS_HOST` | redis | Redis 地址 |
| `SPRING_RABBITMQ_HOST` | rabbitmq | RabbitMQ 地址 |
| `APP_CONFIG_ZKADDR` | zookeeper:2181 | ZooKeeper 地址 |

---

## 七、数据库初始化

首次启动时，MySQL 容器会自动执行初始化脚本：

| 脚本 | 内容 |
|----|-----|
| `docs/MySQL/vela-study.sql` | 建表 DDL（12 张核心表）|
| `docs/MySQL/vela-send.sql` | 测试数据 |

如需重置数据库：

```bash
docker-compose down -v    # 删除所有数据卷
docker-compose up -d      # 重新启动
```

---

## 八、常见问题

| 问题 | 原因 | 解决 |
|----|-----|-----|
| 端口被占用 | 本地已有服务占用 3306/6379/9200 等 | 修改 docker-compose.yml 映射端口 |
| MySQL 连不上 | 启动顺序问题 | 等 30s 后重试 |
| ES 内存不足 | 默认分配 512MB 不够 | 调整 `ES_JAVA_OPTS=-Xms1g -Xmx1g` |
| 前端空白页 | API 地址配置不对 | 检查 web/Dockerfile 中的 API 代理 |

---

## 九、停止与清理

```bash
# 停止全部服务
docker-compose down

# 停止全部 + 删除数据卷（会清空所有数据）
docker-compose down -v

# 查看日志
docker-compose logs -f vela-service
docker-compose logs -f vela-tcp
```
