# HTTP API

## 1. 访问地址

应用默认监听 `8123` 端口，统一上下文为 `/api`：

- Knife4j：<http://localhost:8123/api/doc.html>
- OpenAPI JSON：<http://localhost:8123/api/v3/api-docs>
- 业务 API 前缀：`http://localhost:8123/api/writing`

所有示例 ID 都是占位符。复制返回 JSON 中的纯 UUID，不要附带引号、逗号或尖括号。

## 2. 启动方式

### local

默认使用内存 Repository，不需要数据库，知识库默认关闭：

```powershell
.\mvnw.cmd spring-boot:run
```

该模式适合查看接口和执行不依赖真实模型结果的检查。进程退出后业务数据会丢失。

### external

先启动数据库：

```powershell
docker compose up -d
```

再在 IDEA Environment variables 或 PowerShell 设置：

```powershell
$env:DEEPSEEK_API_KEY = '<your-deepseek-api-key>'
$env:PAPER_KNOWLEDGE_ENABLED = 'true'
$env:PAPER_KNOWLEDGE_ENDPOINT = '<your-bailian-retrieval-endpoint>'
$env:PAPER_KNOWLEDGE_API_KEY = '<your-bailian-api-key>'
$env:PAPER_KNOWLEDGE_INDEX_ID = '<your-bailian-index-id>'
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:54333/paper_agent'
$env:SPRING_DATASOURCE_USERNAME = 'paper_agent'
$env:SPRING_DATASOURCE_PASSWORD = 'paper_agent'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=external"
```

`external` 模式使用 JDBC Repository，Flyway 会在启动时校验并迁移 Schema。

## 3. 接口总览

### 任务、研究和证据

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/writing/tasks` | 创建论文任务 |
| `POST` | `/writing/tasks/{taskId}/research-runs` | 启动一次研究运行 |
| `GET` | `/writing/tasks/{taskId}` | 查询任务 |
| `GET` | `/writing/runs/{runId}` | 查询一次模型运行 |
| `GET` | `/writing/tasks/{taskId}/evidence` | 查询任务采纳的证据快照 |

### 提纲

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/writing/tasks/{taskId}/outline-versions` | 生成新的提纲草稿版本 |
| `GET` | `/writing/tasks/{taskId}/outline-versions` | 查询任务全部提纲版本 |
| `GET` | `/writing/outline-versions/{outlineVersionId}` | 查询指定提纲版本 |
| `POST` | `/writing/tasks/{taskId}/outline-versions/{outlineVersionId}/confirm` | 确认指定提纲版本 |
| `GET` | `/writing/tasks/{taskId}/outline-sections` | 查询已确认提纲的结构化章节 |

### 章节与引用

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/writing/tasks/{taskId}/sections/{sectionKey}/versions` | 为一个可写叶子章节生成新版本 |
| `GET` | `/writing/tasks/{taskId}/sections` | 查询任务的章节版本 |
| `GET` | `/writing/tasks/{taskId}/sections/{sectionKey}/versions` | 查询一个章节的所有版本 |
| `GET` | `/writing/section-versions/{sectionVersionId}` | 查询指定章节版本 |
| `GET` | `/writing/section-versions/{sectionVersionId}/claims` | 查询章节论点 |
| `GET` | `/writing/section-versions/{sectionVersionId}/citations` | 查询结构化引用与来源定位 |
| `POST` | `/writing/tasks/{taskId}/sections/{sectionKey}/versions/{sectionVersionId}/confirm` | 确认指定章节版本 |

### 全文

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/writing/tasks/{taskId}/document-versions` | 确定性组装新全文版本 |
| `GET` | `/writing/tasks/{taskId}/document-versions` | 查询任务的全文版本 |
| `GET` | `/writing/document-versions/{documentVersionId}` | 查询全文元数据 |
| `GET` | `/writing/document-versions/{documentVersionId}/citations` | 查询全文冻结的引用投影 |
| `GET` | `/writing/document-versions/{documentVersionId}/markdown` | 下载 Markdown 全文 |

## 4. 完整演示顺序

### 4.1 创建任务

`POST /writing/tasks`

```json
{
  "title": "多模态情感分析研究",
  "topic": "ALMT 的核心机制与实验结论",
  "requirements": "基于真实证据说明方法、数据集和主要结果，并标注来源"
}
```

保存响应中的 `taskId`。

### 4.2 启动研究

`POST /writing/tasks/{taskId}/research-runs`

```json
{
  "message": "优先查询已有知识库；证据不足时再搜索和阅读论文。回答中说明来源文档、章节和页码。",
  "requestId": "research-demo-001",
  "sessionId": "demo-session"
}
```

成功响应是一个 `TaskRunResponse`。应检查：

```text
action = RESEARCH
status = SUCCEEDED
adoptedEvidenceCount >= 1
```

同一个 `requestId` 和相同请求再次提交时返回幂等结果；相同 `requestId` 携带不同内容会被拒绝。

### 4.3 查询证据

`GET /writing/tasks/{taskId}/evidence`

关键字段：

```json
{
  "evidenceId": "<evidence-id>",
  "sourceType": "CLOUD_KNOWLEDGE",
  "documentId": "<stable-document-id>",
  "documentName": "<source-document-name>",
  "section": "<source-section>",
  "sourcePages": [0],
  "pageNumberingScheme": "SOURCE_PROVIDED",
  "pageDisplayText": "<source-page-display>",
  "contentSnapshot": "<immutable-evidence-content>",
  "contentHash": "<sha-256>"
}
```

`sourcePages` 保留上游返回的原始页码值，不擅自把 `0` 改成 `1`。面向用户展示时优先使用 `pageDisplayText` 和 `pageNumberingScheme`，不要假定它一定等于 PDF 印刷页码。

### 4.4 生成提纲

`POST /writing/tasks/{taskId}/outline-versions`

```json
{
  "requestId": "outline-demo-001",
  "instruction": "生成三级以内的论文提纲。标题节点只包含正文内容，不要把证据自检或写作说明写成 Markdown 标题。",
  "sessionId": "demo-session"
}
```

响应包含 `run` 和 `outline`。提纲生成只读取已经采纳的 `TaskEvidence`，默认不重新开放研究工具。

### 4.5 确认提纲

`POST /writing/tasks/{taskId}/outline-versions/{outlineVersionId}/confirm`

必须确认一个具体版本。确认成功后，任务的 `confirmedOutlineVersionId` 指向该版本，任务进入 `OUTLINE_CONFIRMED`。已确认提纲不可原地修改。

### 4.6 查询可写章节

`GET /writing/tasks/{taskId}/outline-sections`

只对 `writable: true` 的叶子节点生成正文。使用响应中的 `sectionKey`，不要用标题文本代替。

### 4.7 生成章节

`POST /writing/tasks/{taskId}/sections/{sectionKey}/versions`

```json
{
  "requestId": "section-method-demo-001",
  "instruction": "围绕本节目标形成清晰论述，只引用上下文提供的 TaskEvidence。",
  "sessionId": "demo-session"
}
```

响应包含本次 `TaskRun` 和新的 `SectionVersion`。章节正文会被解析为 `SectionClaim` 与 `ClaimCitation`；引用必须属于当前任务，并能定位到证据快照。

可分别调用 claims 和 citations 接口检查：

- `claimKey` 与 `claimText`；
- `evidenceId` 是否存在且属于当前任务；
- `documentName`、`section`、`sourcePages`、`pageDisplayText`；
- `supportingQuote` 是否位于对应证据快照中。

### 4.8 确认章节

`POST /writing/tasks/{taskId}/sections/{sectionKey}/versions/{sectionVersionId}/confirm`

一个章节可以有多个草稿版本，但只有符合引用校验规则的版本可以确认。确认新稿不会覆盖旧稿。全部可写叶子章节都有确认版本后，任务进入 `DRAFT_COMPLETED`。

### 4.9 组装全文

`POST /writing/tasks/{taskId}/document-versions`

```json
{
  "requestId": "document-demo-001",
  "format": "MARKDOWN"
}
```

全文由 Java 按确认提纲顺序确定性组装，不再次调用模型。响应包含 `documentVersionId`、`contentHash`、`sourceFingerprint` 和冻结的章节/论点数量。

### 4.10 下载全文

`GET /writing/document-versions/{documentVersionId}/markdown`

响应类型为 `text/markdown;charset=UTF-8`，附件名形如 `document-v1.md`。

## 5. 状态门禁

| 操作 | 主要前置条件 | 成功后的状态 |
| --- | --- | --- |
| 创建任务 | 无 | `CREATED` |
| 研究 | `CREATED` 或 `RESEARCHING` | `RESEARCHING` |
| 生成提纲 | 已有已采纳证据 | `OUTLINE_PENDING` |
| 重新生成提纲 | `OUTLINE_PENDING` | `OUTLINE_PENDING` |
| 确认提纲 | 版本属于当前任务且为草稿 | `OUTLINE_CONFIRMED` |
| 生成章节 | 已确认提纲，目标为可写叶子节点 | `DRAFTING` |
| 确认章节 | 引用已验证，版本属于目标章节 | `DRAFTING` 或 `DRAFT_COMPLETED` |
| 组装全文 | 全部可写叶子章节已有且仅有一个确认版本 | `DOCUMENT_READY` |

## 6. 幂等规则

以下写操作的请求体包含 `requestId`：

- 研究；
- 生成提纲；
- 生成章节；
- 组装全文。

服务会把业务动作、任务、目标对象和请求参数计算为请求哈希：

- 相同 `requestId` + 相同哈希：返回之前的产物；
- 相同 `requestId` + 不同哈希：返回冲突；
- 不同 `requestId`：创建新的不可变版本。

客户端在网络超时后重试同一动作时，应复用原 `requestId`。

## 7. 常见错误

错误使用 RFC 7807 Problem Detail。领域冲突通常在 `code` 扩展属性中提供稳定错误码。

| HTTP 状态 | 典型错误码或原因 |
| --- | --- |
| `400` | UUID 格式错误、请求字段缺失、提纲结构或引用输出无效 |
| `404` | task、run、outline、section version 或 document version 不存在 |
| `409` | `IDEMPOTENCY_CONFLICT`、`TASK_RUN_CONFLICT`、阶段不允许当前操作 |
| `409` | `OUTLINE_ALREADY_CONFIRMED`、`SECTION_ALREADY_CONFIRMED` |
| `409` | `CITATION_VALIDATION_REQUIRED`、`SECTION_EVIDENCE_REQUIRED` |
| `409` | `DOCUMENT_SECTION_MISSING`、`DOCUMENT_SECTION_MULTIPLE_CONFIRMED` |
| `409` | `DOCUMENT_CONTENT_HASH_MISMATCH`、`DOCUMENT_SOURCE_CONFLICT` |
| `500` | `RUNTIME_ERROR`、工具失败或结果持久化失败 |

模型或工具失败会把对应 `TaskRun` 标记为 `FAILED` 并保留 `errorCode`、`errorSummary`，不会把异常字符串当作成功答案，也不会推进业务阶段。

## 8. 来源引用在哪里生成

“回答主动引用来源文档、章节和页码”由三层共同完成：

```text
TaskEvidence
  保存 documentName / section / sourcePages / pageDisplayText / contentSnapshot

Section prompt + CitationOutputParser
  要求模型按结构化契约返回论点、evidenceId 和 supportingQuote

CitationValidator + ClaimCitationRepository
  确认证据属于当前任务、来源定位完整、引用片段真实存在于快照
```

API 最终通过以下两个入口返回可展示来源：

```text
GET /writing/section-versions/{sectionVersionId}/citations
GET /writing/document-versions/{documentVersionId}/citations
```

系统不会从最终自然语言答案中反向猜测引用，也不会把百炼的临时签名 URL 当作长期来源。

