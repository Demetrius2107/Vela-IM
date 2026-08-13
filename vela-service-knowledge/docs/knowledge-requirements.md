# Vela 知识库（Knowledge Base）功能需求文档

> 版本：v1.0 ｜ 日期：2026-08-13 ｜ 模块：vela-service-knowledge
> 服务端口：8019 ｜ 技术栈：Spring Boot 2.3 / Java 8 / MyBatis-Plus / MySQL 8 / Redis

---

## 1. 背景与目标

Vela 是一个同时覆盖 **办公 IM（企业协同）** 与 **ToC IM（大众社交）** 的即时通讯平台。
知识库模块为两类用户提供「组织沉淀知识、聊天中快速获取知识」的能力：

- **办公侧**：团队 Wiki、内部文档、发布审批、版本追溯、阅读统计、权限管控。
- **ToC 侧**：公共 FAQ、热榜问答、机器人问答、分享卡片。

当前 `vela-service-knowledge` 仅有 `vela_document` 单表 CRUD（create/list/get/update/delete），
本需求文档规划 P0~P3 四个阶段，将其建设为完整知识库服务。

## 2. 总体架构

### 2.1 分层结构（遵循项目既有 DDD 分层）

```
interfaces/rest    —— REST API（Controller）
domain/service     —— 业务逻辑（Service）
domain/entity      —— 领域实体（Entity）
infrastructure/persistence/mapper —— MyBatis-Plus Mapper
infrastructure/persistence/dao    —— 自定义 SQL（如向量检索）
```

### 2.2 核心数据模型

| 表名 | 说明 | 引入阶段 |
|------|------|----------|
| vela_document | 文档主表（扩展字段：状态/软删/计数） | P0（改造） |
| vela_category | 分类目录树（父子层级） | P0 |
| vela_doc_favorite | 文档收藏 | P0 |
| vela_doc_permission | 文档级权限（RBAC） | P0 |
| vela_doc_read | 阅读记录/统计 | P2 |
| vela_doc_version | 文档版本快照 | P2 |
| vela_doc_approval | 发布审批流 | P2 |
| vela_doc_vector | 文档向量（片段级） | P3 |

### 2.3 身份与隔离

- `appId`：业务隔离维度（办公组织 / ToC 应用各占一个 appId）。
- `userId`：操作人，与兄弟服务（office 等）一致，从请求参数传入（网关层已鉴权）。
- 权限模型分三级：**应用级（appId）→ 分类级 → 文档级**。

---

## 3. P0 —— 可用的知识库（基础能力）

### 3.1 功能一：分类目录树（vela_category）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P0-CAT-01 | 分类增删改查 | 支持创建/编辑/删除/查询分类 |
| P0-CAT-02 | 父子层级 | `parentId` 自关联，支持无限层级（限制 5 层内） |
| P0-CAT-03 | 树形查询 | 一次返回整棵树（children 嵌套） |
| P0-CAT-04 | 删除保护 | 分类下有子分类或文档时禁止删除 |
| P0-CAT-05 | 文档挂载 | 文档 `categoryId` 关联分类，支持移动到其他分类 |

**实体字段**：`id, appId, parentId, name, sort, createTime, updateTime`

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/knowledge/category/create | 创建分类 |
| POST | /v1/knowledge/category/update | 重命名/调整排序/换父级 |
| POST | /v1/knowledge/category/delete | 删除分类（有子项/文档时报错） |
| GET | /v1/knowledge/category/tree | 树形查询（appId 维度） |

### 3.2 功能二：全文检索

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P0-SRCH-01 | 多字段检索 | 标题、正文、摘要、标签 四字段可配权重检索 |
| P0-SRCH-02 | 分类过滤 | `categoryId` 可选过滤 |
| P0-SRCH-03 | 状态过滤 | 仅检索已发布文档（配合 P2 状态字段） |
| P0-SRCH-04 | 关键词高亮 | 返回命中片段（标题/正文上下文），供前端渲染 |
| P0-SRCH-05 | 分页排序 | 按相关度/更新时间排序，分页返回 |

**实现策略**：P0 用 MySQL `LIKE` 多字段查询 + 应用层关键词高亮；
P3 升级为向量/全文检索（见第 6 章），对外接口签名保持不变。

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/search | 关键词检索（keyword + categoryId + page + size） |

### 3.3 功能三：收藏（vela_doc_favorite）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P0-FAV-01 | 收藏/取消 | 幂等操作，重复收藏不报错（已存在返回成功） |
| P0-FAV-02 | 收藏列表 | 按收藏时间倒序，分页返回文档摘要 |
| P0-FAV-03 | 收藏数 | 文档详情/列表返回 `favoriteCount` |
| P0-FAV-04 | 收藏状态 | 文档详情返回当前用户 `favorited` 布尔值 |

**实体字段**：`id, appId, userId, docId, createTime`（唯一索引 appId+userId+docId）

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/knowledge/favorite/add | 收藏文档 |
| POST | /v1/knowledge/favorite/remove | 取消收藏 |
| GET | /v1/knowledge/favorite/list | 我的收藏列表 |

### 3.4 功能四：权限控制（RBAC）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P0-PERM-01 | 角色模型 | 应用管理员 / 空间管理员 / 编辑者 / 只读者 |
| P0-PERM-02 | 文档级权限 | 文档可单独配置协作者（userId → 角色），未配置时继承应用默认策略 |
| P0-PERM-03 | 写操作校验 | 创建/编辑/删除/审批均校验写权限，无权限返回 403 |
| P0-PERM-04 | 读操作校验 | 私密文档仅授权者可读；公开文档所有人可读 |
| P0-PERM-05 | 管理员 | appId 下 `isAdmin` 标记可绕过权限 |

**实现策略**：默认策略存 `vela_category` 级（或应用级配置）；文档级覆盖存 `vela_doc_permission`。
P0 提供 Service 层校验工具 `PermissionChecker`，Controller 调用；管理后台配置入口后续补。

**实体字段**：`vela_doc_permission: id, appId, docId, userId, role(owner/editor/reader), createTime`

---

## 4. P1 —— 融入 IM（聊天场景）

### 4.1 功能一：消息卡片预览

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P1-CARD-01 | 链接预览 | 用户在聊天中粘贴文档链接，前端调用预览接口渲染卡片 |
| P1-CARD-02 | 卡片内容 | 返回标题/摘要/封面图/作者/更新时间/阅读数，供卡片展示 |
| P1-CARD-03 | 权限适配 | 无权限用户返回「无权访问」占位，不泄露正文 |

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/preview?id={docId} | 文档预览卡片信息（脱敏：不返回全文） |

### 4.2 功能二：聊天引用

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P1-REF-01 | 引用文档 | 聊天消息引用文档，消息体携带 `docId`，端侧渲染摘要卡片 |
| P1-REF-02 | 引用跳转 | 点击卡片跳转文档详情页 |
| P1-REF-03 | 引用校验 | 引用接口校验文档存在与可读权限，返回摘要信息 |

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/reference?id={docId} | 消息引用摘要（比 preview 更精简） |

> 注：消息发送链路复用 vela-service-message 的引用回复机制，知识库侧只提供引用信息查询。

### 4.3 功能三：机器人检索问答

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P1-BOT-01 | 提问入口 | 群聊/单聊中 @知识库机器人 提问（如 `/kb 如何开通Vela账号`） |
| P1-BOT-02 | 检索回答 | 检索知识库返回 Top-N 候选（标题+摘要+链接） |
| P1-BOT-03 | 无结果处理 | 无匹配返回引导话术与 FAQ 入口 |
| P1-BOT-04 | 引用出处 | 每条答案携带来源文档链接，可点击溯源 |

**实现策略**：知识库侧提供统一问答检索接口（复用 search），机器人服务
（vela-service-bot）调用该接口组装消息回复。P1 为「检索式问答」，P3 升级为「生成式 RAG」。

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/bot/ask?appId=&question=&limit= | 机器人问答检索（Top-N 候选） |

---

## 5. P2 —— 运营化（办公刚需）

### 5.1 功能一：版本历史（vela_doc_version）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P2-VER-01 | 自动快照 | 每次「保存新版本」写入快照（标题+内容+摘要） |
| P2-VER-02 | 历史列表 | 按版本号倒序分页查询 |
| P2-VER-03 | 版本回滚 | 回滚到指定版本：当前内容覆盖到新版本，历史保留 |
| P2-VER-04 | 版本号 | 单调递增 `versionNo`，首个版本为 1 |
| P2-VER-05 | 容量保护 | 每文档保留最近 50 个版本，超出自动清理最旧 |

**实体字段**：`id, appId, docId, versionNo, title, content, summary, editorId, createTime`

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/version/list?docId=&page=&size= | 版本历史 |
| POST | /v1/knowledge/version/rollback?docId=&versionNo= | 回滚到指定版本 |

### 5.2 功能二：阅读统计 + 收藏数（vela_doc_read）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P2-READ-01 | 阅读记录 | 打开详情时记录一条阅读（userId+docId 当日去重） |
| P2-READ-02 | 阅读数 | 文档列表/详情返回 `readCount`（全量 PV） |
| P2-READ-03 | 收藏数 | 文档列表/详情返回 `favoriteCount`（P0 已有，此处聚合到文档表冗余字段） |
| P2-READ-04 | 阅读去重 | 同一用户同一文档每天只计一次 |

**实体字段**：`vela_doc_read: id, appId, docId, userId, readDate, createTime`
`vela_document` 新增冗余字段：`readCount, favoriteCount`

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/knowledge/read/record?docId= | 记录一次阅读 |
| GET | /v1/knowledge/stats?docId= | 文档统计（阅读数/收藏数） |

### 5.3 功能三：回收站（软删除）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P2-REC-01 | 软删除 | delete 改为逻辑删除（`isDeleted=1`），列表/检索/详情默认过滤 |
| P2-REC-02 | 回收站列表 | 分页查看已删除文档 |
| P2-REC-03 | 恢复 | 恢复后正常可见 |
| P2-REC-04 | 永久删除 | 物理删除 + 级联清理版本/收藏/权限/阅读记录（限管理员） |

**实现**：`vela_document` 新增 `isDeleted` 字段；MyBatis-Plus `@TableLogic` 统一处理。

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/recycle/list | 回收站列表 |
| POST | /v1/knowledge/recycle/restore?id= | 恢复文档 |
| POST | /v1/knowledge/recycle/purge?id= | 永久删除（管理员） |

### 5.4 功能四：发布审批流（vela_doc_approval）

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P2-APR-01 | 状态机 | DRAFT(草稿) → PENDING(待审) → PUBLISHED(已发布) / REJECTED(驳回) → DRAFT |
| P2-APR-02 | 提交审核 | 编辑者提交审核（仅 DRAFT/REJECTED 可提交） |
| P2-APR-03 | 审批 | 审批人通过/驳回，需填写驳回原因 |
| P2-APR-04 | 权限 | 审批人 = 应用管理员/空间管理员 |
| P2-APR-05 | 检索联动 | 检索/列表默认只出 PUBLISHED（P0-SRCH-03） |
| P2-APR-06 | 可见性 | 作者可见自己全部状态；其他用户仅见 PUBLISHED |

**实现**：`vela_document` 新增 `status` 字段（0 草稿/1 待审/2 已发布/3 驳回）。
审批记录存 `vela_doc_approval`（简化：单级审批，一人通过即发布）。

**实体字段**：`vela_doc_approval: id, appId, docId, approverId, action(approve/reject), reason, createTime`

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/knowledge/approval/submit?docId= | 提交审核 |
| POST | /v1/knowledge/approval/handle?docId=&action=&reason= | 审批（通过/驳回） |
| GET | /v1/knowledge/approval/pending?appId=&page=&size= | 待审批列表（审批人视角） |

---

## 6. P3 —— 智能化

### 6.1 功能一：向量检索 + RAG 问答

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P3-VEC-01 | 文本分块 | 文档正文按段落/固定长度分块（chunk），每块入库 |
| P3-VEC-02 | 向量化 | 通过可插拔 Embedding 服务生成向量（接口抽象，默认本地哈希降维占位，可替换为 OpenAI/BGE 等） |
| P3-VEC-03 | 相似检索 | 问题向量化后按余弦相似度取 Top-N 片段 |
| P3-VEC-04 | 生成式回答 | 拼接候选片段调用 LLM（可插拔）生成带引用的答案；未配置 LLM 时降级为候选列表 |
| P3-VEC-05 | 索引维护 | 文档创建/更新/回滚时增量重建索引；删除时清理 |

**实现策略**

```
VectorStore (接口)
  ├── MySqlVectorStore（默认实现：hash 桶 + 余弦相似度，数据量小可用）
  └── (预留) ElasticsearchVectorStore / MilvusVectorStore

RagAnswerer (接口)
  ├── NoopRagAnswerer（默认：返回候选片段，不生成）
  └── (预留) HttpLlmRagAnswerer（调用外部 LLM）
```

**实体字段**：`vela_doc_vector: id, appId, docId, chunkNo, content, vector(JSON/二进制), createTime`

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/rag/ask?appId=&question=&limit= | RAG 问答（向量检索+可生成） |
| POST | /v1/knowledge/vector/reindex?docId= | 重建单文档向量索引（运维） |

### 6.2 功能二：自动摘要

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P3-SUM-01 | 自动生成 | 文档保存时若摘要为空，自动提取首段/关键句作为摘要 |
| P3-SUM-02 | 可覆盖 | 手动填写摘要优先 |
| P3-SUM-03 | 可插拔 | 预留 LLM 摘要接口，默认本地抽取式（首段+关键句） |

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /v1/knowledge/summary/generate?docId= | 生成并保存摘要 |

### 6.3 功能三：FAQ 热榜

**需求点**

| 编号 | 需求 | 说明 |
|------|------|------|
| P3-FAQ-01 | 热榜数据 | 基于阅读数/收藏数加权（readCount×1 + favoriteCount×5）排序 |
| P3-FAQ-02 | 榜单接口 | 返回 Top-N 文档（标题/摘要/热度分） |
| P3-FAQ-03 | 时间窗 | 支持 近7天/近30天/全部 时间窗（ToC 首页 FAQ 常用） |

**接口**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /v1/knowledge/faq/hot?appId=&window=&limit= | FAQ 热榜 |

---

## 7. 数据库 DDL（汇总）

```sql
-- 文档主表（P0 改造 + P2 扩展）
CREATE TABLE vela_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL COMMENT '应用ID',
    title VARCHAR(255) NOT NULL COMMENT '标题',
    content LONGTEXT COMMENT '正文',
    summary VARCHAR(1000) COMMENT '摘要',
    creator_id VARCHAR(64) COMMENT '创建人',
    category_id BIGINT DEFAULT 0 COMMENT '分类ID',
    tags VARCHAR(1000) COMMENT '标签，逗号分隔',
    status TINYINT DEFAULT 0 COMMENT '0草稿 1待审 2已发布 3驳回',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    read_count BIGINT DEFAULT 0 COMMENT '阅读数',
    favorite_count BIGINT DEFAULT 0 COMMENT '收藏数',
    create_time BIGINT COMMENT '创建时间',
    update_time BIGINT COMMENT '更新时间',
    KEY idx_app_cat (app_id, category_id),
    KEY idx_app_status (app_id, status),
    KEY idx_app_upd (app_id, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- 分类目录树（P0）
CREATE TABLE vela_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    parent_id BIGINT DEFAULT 0 COMMENT '父分类，0为根',
    name VARCHAR(100) NOT NULL,
    sort INT DEFAULT 0,
    create_time BIGINT,
    update_time BIGINT,
    KEY idx_app_parent (app_id, parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类';

-- 文档收藏（P0）
CREATE TABLE vela_doc_favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    doc_id BIGINT NOT NULL,
    create_time BIGINT,
    UNIQUE KEY uk_user_doc (app_id, user_id, doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档收藏';

-- 文档级权限（P0）
CREATE TABLE vela_doc_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    doc_id BIGINT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL COMMENT 'owner/editor/reader',
    create_time BIGINT,
    UNIQUE KEY uk_doc_user (app_id, doc_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档权限';

-- 文档版本（P2）
CREATE TABLE vela_doc_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    doc_id BIGINT NOT NULL,
    version_no INT NOT NULL COMMENT '版本号',
    title VARCHAR(255),
    content LONGTEXT,
    summary VARCHAR(1000),
    editor_id VARCHAR(64),
    create_time BIGINT,
    UNIQUE KEY uk_doc_ver (doc_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本';

-- 阅读记录（P2）
CREATE TABLE vela_doc_read (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    doc_id BIGINT NOT NULL,
    user_id VARCHAR(64),
    read_date VARCHAR(10) COMMENT 'yyyy-MM-dd',
    create_time BIGINT,
    UNIQUE KEY uk_doc_user_date (doc_id, user_id, read_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档阅读记录';

-- 审批记录（P2）
CREATE TABLE vela_doc_approval (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    doc_id BIGINT NOT NULL,
    approver_id VARCHAR(64),
    action VARCHAR(16) COMMENT 'approve/reject',
    reason VARCHAR(500) COMMENT '驳回原因',
    create_time BIGINT,
    KEY idx_app_status (app_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档审批记录';

-- 文档向量（P3）
CREATE TABLE vela_doc_vector (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    app_id INT NOT NULL,
    doc_id BIGINT NOT NULL,
    chunk_no INT NOT NULL,
    content TEXT,
    vector VARCHAR(4096) COMMENT '向量JSON',
    create_time BIGINT,
    KEY idx_doc (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档向量分块';
```

---

## 8. 错误码规划（BusinessErrorCode 97xxx 段扩展）

| 枚举 | 码 | 说明 |
|------|----|------|
| DOCUMENT_NOT_FOUND | 97001 | 文档不存在（已有） |
| CATEGORY_NOT_FOUND | 97002 | 分类不存在 |
| CATEGORY_HAS_CHILDREN | 97003 | 分类下有子分类 |
| CATEGORY_HAS_DOCUMENTS | 97004 | 分类下有文档 |
| DOCUMENT_PERMISSION_DENIED | 97005 | 无文档权限 |
| DOCUMENT_ALREADY_FAVORITED | 97006 | 已收藏（幂等场景返回成功，一般不触发） |
| DOCUMENT_STATUS_ILLEGAL | 97007 | 文档状态不允许该操作 |
| VERSION_NOT_FOUND | 97008 | 版本不存在 |
| APPROVAL_REASON_REQUIRED | 97009 | 驳回必须填写原因 |
| DOCUMENT_IS_DELETED | 97010 | 文档已删除 |

---

## 9. 验收标准

- **P0**：目录树 CRUD 与树查询可用；检索支持关键词+分类过滤+高亮；收藏幂等；权限拦截写操作。
- **P1**：preview/reference 接口返回脱敏卡片信息；bot/ask 返回 Top-N 候选。
- **P2**：保存产生版本、可回滚；阅读/收藏计数准确；软删除回收站可用；审批状态机流转正确、检索只出已发布。
- **P3**：向量检索可替换后端；RAG 无 LLM 时降级候选；热榜加权排序正确。
- **通用**：所有接口统一 `Result<T>` 返回；新增单测覆盖核心 Service 逻辑；`mvn -pl vela-service-knowledge test` 全绿。
