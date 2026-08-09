# Vela IM 项目 — 首次启动问题完整清单

> 记录时间：2026-08-08 ~ 2026-08-09  
> 分支：feat/connectivity-test  
> 运行环境：JDK 21 + Spring Boot 2.3.2 + MyBatis-Plus 3.4.2

---

## 问题总览

| # | 类别 | 问题数 | 严重程度 |
|---|------|--------|----------|
| 1 | 缺失 Spring 注解 | 25+ | 🔴 启动阻断 |
| 2 | 组件扫描未覆盖 | 5 | 🔴 启动阻断 |
| 3 | 循环依赖 | 3 | 🔴 启动阻断 |
| 4 | 依赖版本不兼容 | 3 | 🔴 启动阻断 |
| 5 | 配置文件错误 | 15+ | 🔴 启动阻断 |
| 6 | 数据库/SQL 问题 | 5 | 🔴 启动阻断 |
| 7 | Java API 版本兼容 | 7 | 🟡 编译失败 |
| 8 | 微服务拆分设计问题 | 1 | 🟡 架构反模式 |

**核心结论：12 个微服务模块，代码语法全部正确（mvn compile 零报错），但没有任何一个服务在本次修复前成功启动过。**

---

## 一、缺失 Spring 注解（25+ 处）

### 1.1 @EnableFeignClients 缺失（4 个服务）

所有使用 Feign 远程调用的服务，其 Application 启动类均未添加 `@EnableFeignClients`，导致启动时 FeignClient Bean 注入失败（NoSuchBeanDefinitionException）。

| 服务 | 文件 | 影响 |
|------|------|------|
| friendship | FriendshipApplication.java | UserServiceFeignClient 注入失败 |
| group | GroupApplication.java | UserServiceFeignClient 注入失败 |
| user | UserApplication.java | GroupServiceFeignClient 注入失败 |
| message | MessageApplication.java | GroupServiceFeignClient 注入失败 |

**根因**：代码编写时未验证启动流程，直接写完后从未 Run。

### 1.2 @MapperScan 缺失（9 个服务）

所有使用 MyBatis-Plus 的服务均未在 Application 类上添加 `@MapperScan`，导致 Mapper 接口不会被 MyBatis 代理，注入时 NoSuchBeanDefinitionException。

| 服务 | 需扫描的包 |
|------|-----------|
| user | com.vela.im.service.user.infrastructure.persistence.mapper |
| friendship | com.vela.im.service.friendship.infrastructure.persistence.mapper |
| group | com.vela.im.service.group.infrastructure.persistence.mapper |
| message | com.vela.im.service.message.infrastructure.persistence.mapper |
| conversation | com.vela.im.service.conversation.infrastructure.persistence.mapper |
| admin | com.vela.im.service.admin.infrastructure.persistence.mapper |
| bot | com.vela.im.service.bot.infrastructure.persistence.mapper |
| config | com.vela.im.service.config.infrastructure.persistence.mapper |
| knowledge | com.vela.im.service.knowledge.infrastructure.persistence.mapper |
| office | com.vela.im.service.office.infrastructure.persistence.mapper |

### 1.3 单个 @Mapper 注解缺失（7 个 Mapper 接口）

以下 Mapper 接口缺少 `@Mapper` 注解，且所在包不在 `@MapperScan` 范围内：

| 文件 | 位置 |
|------|------|
| ImFriendShipMapper.java | vela-service-friendship |
| ImFriendShipGroupMapper.java | vela-service-friendship |
| ImFriendShipGroupMemberMapper.java | vela-service-friendship |
| ImGroupMapper.java | vela-service-group |
| ImGroupMemberMapper.java | vela-service-group |
| ImConversationSetMapper.java | vela-service-conversation |
| ImGroupMessageHistoryMapper.java | vela-service-common |

### 1.4 @Component 缺失（2 个工具类）

| 文件 | 影响服务 |
|------|---------|
| LoopHandle.java（路由算法） | user, friendship 等 |
| SnowflakeIdWorker.java（雪花ID） | message |

### 1.5 SnowflakeIdWorker 构造器问题

`SnowflakeIdWorker(long workerId)` 只有一个带参构造器，加 `@Component` 后 Spring 尝试注入 `long` 类型 Bean 导致失败。需补无参构造器。

---

## 二、scanBasePackages 组件扫描未覆盖（5 个服务）

所有服务的 `@SpringBootApplication(scanBasePackages = ...)` 均未包含 `com.vela.im.shared` 包，导致以下共享 Bean 不可见：

| 共享类 | 被依赖方 |
|--------|---------|
| ImServerProperties（Bootstrap 配置） | 所有服务 |
| GlobalHttpClientConfig（HTTP 客户端） | 所有服务 |

修复：5 个服务（user / friendship / group / message / conversation）的 `scanBasePackages` 添加 `"com.vela.im.shared"`。

**message 服务额外缺失的包**（因 message 直接 import 其他模块的类）：
- `com.vela.im.service.conversation`
- `com.vela.im.service.user`
- `com.vela.im.service.friendship`

---

## 三、循环依赖（3 处）

### 3.1 ImFriendServiceImpl 自注入

`ImFriendServiceImpl` 构造器中注入了一个自身类型的 `ImFriendService` 字段（实际未使用），引发循环依赖。

修复：删除未使用的自注入字段。

### 3.2 ImFriendShipRequestServiceImpl ↔ ImFriendService 循环

`ImFriendShipRequestServiceImpl` 和 `ImFriendService` 互相注入。

修复：`ImFriendShipRequestServiceImpl` 构造器中 `ImFriendService` 参数加 `@Lazy`。

### 3.3 ImFriendShipGroupMemberServiceImpl ↔ 自身循环

`ImFriendShipGroupMemberServiceImpl` 同时依赖上级 Service 和自身。

修复：循环依赖参数加 `@Lazy`。

**根因**：DDD 分层中 domain service 之间互相调用，缺少 Application Service 层来做编排。`@Lazy` 是临时方案，长期应重构。

---

## 四、依赖版本不兼容（3 处）

### 4.1 ZooKeeper 客户端版本

- **原版本**：zkclient 0.11 → ZooKeeper 3.4.13
- **问题**：JDK 21 下 UnresolvedAddressException，3.4.13 完全不支持 JDK 21
- **修复**：pom.xml 升级到 ZooKeeper 3.6.4 + zkclient 0.11
- **说明**：3.6.4 是可兼容的最早版本，最终应升级到 Apache Curator

### 4.2 SnakeYAML 版本

- **原配置**：`snakeyaml.version=2.0`
- **问题**：Spring Boot 2.3.2 内部类依赖 SnakeYAML 1.x API，2.0 API 不兼容
- **修复**：降级到 `snakeyaml.version=1.24`

### 4.3 Java 编译目标

- **原配置**：`maven-compiler-plugin` 硬编码 `<source>8</source><target>8</target>`
- **同时 `maven.compiler.source=17`**，两者冲突
- **问题**：JDK 21 运行时 + target=8 编译可正常启动；改为 target=21 后 Spring Boot 2.3.2 内置 ASM 7.2 无法读取 Java 21 字节码（class file version 65）
- **结论**：**必须保持 target=8**。JDK 21 仅作为运行时，字节码为 Java 8 级别

---

## 五、配置文件错误（15+ 处）

### 5.1 Gateway 路由配置

| 问题 | 修复 |
|------|------|
| 静态路由目标全是 `localhost:8000`（不存在的端口） | 改为对应服务端口（8010/8011/8013/8014） |
| 存在 RequestRateLimiter 默认过滤器的无效引用 | 删除 |
| Gateway 引入 MyBatis/MySQL 依赖但无需数据库 | 启动类排除 DataSourceAutoConfiguration |

### 5.2 数据库连接配置（10 个服务）

| 问题 | 说明 |
|------|------|
| MySQL 端口写 `3306` | Docker 映射到 3307，10 个服务逐个修正 |
| ZooKeeper 地址未配置 | 所有服务 application.yml 补 `zookeeperAddr: 127.0.0.1:2181` |
| 密码散落在多处，部分不一致 | 统一为 `root` |

### 5.3 vela-tcp 配置

- `rabbitMqConfig` 字段名错误 → 应为 `rabbitmqConfig`
- `logicUrl` 指向 8000（不存在）→ 改为 8889（网关）

---

## 六、数据库/SQL 问题（5 处）

### 6.1 表名不一致

Mapper XML 中的表名使用 `im_` 前缀，但数据库实际表名为 `vela_` 前缀。

| 表 | Mapper |
|----|--------|
| im_friendship → vela_friendship | ImFriendShipMapper |
| im_group → vela_group | ImGroupMapper |
| im_group_member → vela_group_member | ImGroupMemberMapper |
| im_conversation_set → vela_conversation_set | ImConversationSetMapper |

### 6.2 SQL 建表语法错误

`docs/MySQL/im-core-send.sql` 和 `im-core-study.sql`：
- 主键字段 `to_id VARCHAR(255)` 被定义为 `PRIMARY KEY` 但允许 NULL，MySQL 报语法错误
- 修复：`to_id VARCHAR(255) NOT NULL`

### 6.3 缺少建表 SQL

- `vela_user_data` 表不存在 → 需补充建表 SQL
- `vela_friendship` 表不存在 → 需补充建表 SQL

### 6.4 缺少 RabbitMQ Exchange

- TCP 网关启动时发现 `/vela/message/exchange` 不存在
- 修复：`rabbitmqadmin declare exchange name=vela.message type=topic`

---

## 七、Java API 版本兼容（7 处）

项目编译目标为 Java 8，但多处代码使用了 Java 9+ / Java 16 API：

| API | Java 版本 | 位置 | 次数 |
|-----|----------|------|------|
| `Stream.toList()` | 16 | MessageReadService.java | 2 |
| `Stream.toList()` | 16 | GroupTagService.java | 1 |
| `List.of()` | 9 | PersistAndPushNode.java | 2 |
| `List.of()` | 9 | P2PMessageService.java | 2 |

修复：替换为 Java 8 兼容写法（`Collectors.toList()`、`Collections.emptyList()`、`Arrays.asList()`）。

---

## 八、微服务拆分设计问题

### message 模块是伪装的单体应用

`CheckSendMessageService` 通过直接 `import` 引入了其他三个微服务的 domain service 类：

| 引入的类 | 所属模块 | 应为 |
|----------|---------|------|
| ImUserService | vela-service-user | 通过 User FeignClient 远程调用 |
| ImFriendService | vela-service-friendship | 通过 Friend FeignClient 远程调用 |
| ConversationService | vela-service-conversation | 通过 Conversation FeignClient 远程调用 |

**后果**：message 服务启动时必须把 user / friendship / conversation 三个模块的所有 Bean（包括 Tomcat、Mapper、Service）全部加载，资源浪费且失去了微服务独立部署的能力。

**同时已有正确实践**：`GroupServiceFeignClient` 使用了 `@FeignClient` 注解通过 HTTP 调用 group 服务 — 但只有这一个模块是纠正了的。

---

## 九、测试脚本幂等性问题

- `importUser` 和 `importFriendShip` 接口使用 `INSERT` 而非 `INSERT IGNORE`
- 重复运行测试脚本时 DuplicateKeyException 虽然被 catch 但 `e.printStackTrace()` 污染日志
- 修复：插入前用 QueryWrapper 检查记录是否存在

---

## 十、缺失的基础设施

### 缺失的文件
| 文件 | 主要内容 |
|------|---------|
| docker-compose.yml | 仅含应用容器，无中间件编排 |
| docker-compose.middleware.yml（新建） | MySQL/Redis/RabbitMQ/ZooKeeper 中间件 |
| phase1_api_test.py（新建） | 11 步 REST API 连通性测试 |

### 零测试覆盖
- 项目无任何单元测试
- 无 Spring Context Load 集成测试
- 无法通过 CI 自动发现启动问题

---

## 总结

| 指标 | 数值 |
|------|------|
| 修复文件数 | 65+ |
| Git 提交数 | 8 |
| 发现并修复的启动阻断问题 | 30+ |
| 缺失的 Spring 注解 | 25+ |
| 配置文件错误 | 15+ |
| 首次成功启动的服务 | user, friendship, gateway, tcp, message |

**最重要的根因**：项目代码语法全部正确（`mvn compile` 零报错），但**从未执行过 `java -jar` 启动验证**。所有问题都是启动级阻断，一跑就现原形。这不是设计能力问题，是开发流程中缺失了最基本的"自测一步"（run the damn thing）。

**建议**：
1. 每个模块开发完成后至少 `mvn spring-boot:run` 验证一次
2. 加入 Spring Context Load 集成测试（`@SpringBootTest`）
3. CI 中加入各模块的启动检查
4. message 模块的跨服务直接依赖应改为 Feign 客户端
5. 升级 Spring Boot 到 2.5+ 以支持更高版本 Java 字节码

---

## 十一、Phase 2 TCP 协议测试发现的问题（2026-08-09 新增）

### 11.1 初始化顺序错误 → redissonClient NPE

| 项目 | 详情 |
|------|------|
| **文件** | `vela-tcp/.../TcpServerRunner.java` |
| **问题** | `LimServer.start()` 先启动 Netty 接受连接，`RedisManager.init()` 在后面才初始化 Redis |
| **后果** | 客户端登录时 `RedisManager.getRedissonClient()` 返回 null → NPE → 连接被关闭 |
| **修复** | 调整初始化顺序：Redis → MQ → MessageReceiver → Netty |

### 11.2 TCP 模块无 Spring Boot 容器

| 项目 | 详情 |
|------|------|
| **问题** | `Starter.java` 是纯 `main()` 方法，手动加载 YAML、手动 init Redis/MQ/ZK |
| **后果** | 进程靠 ZK 注册的轮询线程保活，无 Spring 生命周期管理、无健康检查 |
| **修复** | 新建 `TcpApplication.java` + `TcpServerRunner.java`，用 CommandLineRunner 管理初始化 |

### 11.3 缺少 IdleStateHandler

| 项目 | 详情 |
|------|------|
| **问题** | `LimServer` pipeline 中有 `HeartBeatHandler` 但缺少 `IdleStateHandler` |
| **后果** | 心跳检测功能完全无效（无 IdleStateEvent 产生），TCP 连接无超时断开机制 |
| **修复** | 待修复（非阻断性问题，Phase 2 测试不涉及）
