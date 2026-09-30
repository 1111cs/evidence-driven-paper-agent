# Evidence-Driven Paper Agent

Paper AI Agent 是一个基于 Spring Boot、AgentScope 和 PostgreSQL 构建的证据驱动论文写作系统。系统将大模型能力纳入研究、提纲、章节、引用和全文版本工作流，并通过不可变快照、结构化引用、阶段门禁、请求幂等和数据库约束，使论文产物可追溯、可复核、可恢复。

它不是一个把全部历史消息塞给模型的普通聊天机器人。AgentScope 只在当前业务阶段允许的能力范围内规划研究路径；Java 应用层负责阶段权限、证据采纳、版本提交和确定性全文组装。

## 核心能力

- 自主研究：模型可在 `search_knowledge`、`arxiv_search`、`arxiv_download` 和 `pdf_read` 之间动态选择。
- 显式证据：检索结果先成为候选，采纳后保存为不可变 `TaskEvidence` 快照。
- 阶段门禁：未确认提纲不能写正文，未完成章节不能组装全文。
- 版本化产物：提纲、章节和全文都创建新版本，不原地覆盖旧稿。
- 结构化引用：章节论点通过 `ClaimCitation` 绑定任务证据，并进行确定性校验。
- 请求幂等：研究、提纲、章节和全文生成均使用 `requestId` 识别重试与冲突。
- 持久化：JDBC 模式使用 PostgreSQL 与 Flyway；已提交产物在应用重启后仍可查询。

## 业务工作流

```text
CREATED
  -> RESEARCHING             AgentScope 自主选择研究工具
  -> OUTLINE_PENDING         生成一个或多个不可变提纲草稿
  -> OUTLINE_CONFIRMED       用户确认具体提纲版本
  -> DRAFTING                按可写叶子章节生成、校验并确认版本
  -> DRAFT_COMPLETED         所有正文叶子章节已有确认版本
  -> DOCUMENT_READY          确定性组装不可变全文版本
```

研究材料的演进关系：

```text
EvidenceCandidate            工具返回的候选切片
  -> TaskEvidence            当前任务采纳的不可变内容快照
  -> ClaimCitation           章节论点与证据之间的结构化关系
  -> DocumentCitation        全文版本冻结后的引用投影
```

## 系统架构

```text
HTTP / Knife4j
  -> WritingTaskController / WritingDocumentController
  -> Application Services
  -> WritingWorkflow + Versioned Writing Domain
       |-> PaperAgentRuntime
       |    -> AgentScopeRuntimeAdapter
       |    -> Paper Toolkit
       |         search_knowledge / arxiv_search / arxiv_download / pdf_read
       |-> Evidence / Outline / Section / Citation Services
       |-> Deterministic DocumentAssembler
  -> Repository interfaces
       |-> JDBC implementations -> PostgreSQL + Flyway
       `-> In-memory implementations -> deterministic tests and local mode
```

职责边界：

| 决策 | 负责人 |
| --- | --- |
| 搜知识库还是 arXiv、搜索词、工具顺序 | AgentScope |
| 当前阶段允许哪些能力、何时需要用户确认 | `WritingWorkflow` |
| 证据是否可采纳、引用 ID 是否有效 | Java 领域规则 |
| 章节修改是否覆盖旧版本 | Version Service（永不覆盖） |
| 全文包含哪些确认章节以及顺序 | `DocumentAssembler` |
| 数据如何持久化 | Repository 的 JDBC 实现 |

### 为什么保留 Repository 接口

JDBC 是生产主实现，但 `ClaimCitationRepository`、`WritingTaskRepository` 等接口仍然是业务层与基础设施层的边界：

- 应用服务依赖领域契约，不直接依赖 SQL 或 `JdbcTemplate`；
- JDBC 实现可以独立演进，数据库细节不会污染业务规则；
- 内存实现支撑快速、确定性的领域和应用服务测试；
- Spring 通过 `paper.writing.repository-type` 为一次运行选择一组实现。

因此不会因为生产环境使用 JDBC 而删除这些接口。若未来不再需要内存模式，可以删掉内存实现，但接口仍有保留价值。

更完整的包边界与设计说明见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 技术栈

- Java 21
- Spring Boot 3.4.6
- AgentScope Java 2.0.3
- DeepSeek OpenAI-compatible API
- PostgreSQL 16 / pgvector 镜像
- Flyway
- Apache PDFBox
- Knife4j / OpenAPI
- Maven

## 快速启动

### 1. 准备环境

需要 Java 21、Maven 3.9+（也可使用项目自带 Wrapper）和 Docker。

复制 `.env.example` 中需要的变量到 IDEA Run Configuration 或当前终端。项目不会自动读取 `.env` 文件，密钥不要写入 YAML 或提交到 Git。

### 2. 启动 PostgreSQL

```bash
docker compose up -d
```

默认开发数据库位于 `localhost:54333`，数据库、用户名和密码均为 `paper_agent`。这些仅用于本机演示，部署时必须用环境变量覆盖。

### 3. 配置外部服务

PowerShell 示例：

```powershell
$env:DEEPSEEK_API_KEY = '<your-deepseek-api-key>'
$env:PAPER_KNOWLEDGE_ENABLED = 'true'
$env:PAPER_KNOWLEDGE_ENDPOINT = '<your-bailian-retrieval-endpoint>'
$env:PAPER_KNOWLEDGE_API_KEY = '<your-bailian-api-key>'
$env:PAPER_KNOWLEDGE_INDEX_ID = '<your-bailian-index-id>'
```

### 4. 启动应用

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=external"
```

打开：

- Knife4j：<http://localhost:8123/api/doc.html>
- OpenAPI JSON：<http://localhost:8123/api/v3/api-docs>

当前项目没有独立前端，Knife4j 是用于体验完整业务主链的交互界面。

不连接数据库和外部知识库时，可直接使用默认 `local` Profile 启动；该模式使用内存 Repository，适合接口浏览和确定性测试，不适合验收持久化。

## 最短体验路径

在 Knife4j 中依次执行：

```text
创建 WritingTask
  -> 发起 research-run
  -> 查询 TaskEvidence
  -> 生成 OutlineVersion
  -> 确认指定 OutlineVersion
  -> 查询 outline-sections
  -> 为每个可写叶子章节生成并确认 SectionVersion
  -> 组装 DocumentVersion
  -> 下载 Markdown
```

完整接口、请求示例和状态约束见 [docs/API.md](docs/API.md)。

## 测试与验收

默认测试不访问 DeepSeek、百炼或 PostgreSQL：

```bash
mvn clean test
```

需要本地 PostgreSQL 的 JDBC external 测试：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:54333/paper_agent'
$env:SPRING_DATASOURCE_USERNAME = 'paper_agent'
$env:SPRING_DATASOURCE_PASSWORD = 'paper_agent'
mvn -Pjdbc-external "-Dspring.profiles.active=external" test
```

本次公开版清理后的结果：默认测试 82 项通过，JDBC external 5 项通过，Flyway 完成 15 个迁移（最新脚本为 V14）。详细记录见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

## 当前边界

已经实现的是已提交业务产物的重启后恢复，不是运行中模型调用的断点续跑。当前版本尚未提供：

- 运行中取消、Checkpoint 与 SSE Replay；
- 独立 Web 前端；
- Word、LaTeX 或 PDF 成品导出；
- `ingest_paper` 等有持久副作用的自主工具；
- MCP、多 Agent 和复杂引用推理；
- 参考文献格式化与文献管理器导出。

## 数据库

数据库实例由部署环境创建，表、约束和演进由 `src/main/resources/db/migration` 下的 Flyway 脚本管理。不要手工编辑已应用的迁移脚本；新增变更应创建更高版本的迁移。

## 来源说明

本项目最初基于 zxTinF 的教学型论文助手原型，当前版本经原作者授权公开，并已围绕 AgentScope 运行时、证据快照、阶段工作流、不可变版本和确定性全文组装进行了重构。公开仓库不复制原项目的 Git 历史。

## 许可证

本项目采用 [Apache License 2.0](LICENSE)。第三方来源与改造说明见 [NOTICE](NOTICE)。

