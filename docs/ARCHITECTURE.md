# 系统架构

## 1. 总体边界

```text
用户 / Knife4j
  -> HTTP Controllers
  -> Application Services
  -> WritingWorkflow
  -> Agent runtime or deterministic domain services
  -> Repository contracts
  -> JDBC / Memory infrastructure
```

Controller 只处理 HTTP 映射。应用服务负责用例编排和事务边界。Workflow 只判断阶段能力和提交条件，不调用模型、不访问数据库。AgentScope 只在一次阶段运行内推理，不拥有整篇论文的生命周期。

## 2. 主要包

```text
org.example.paperaiagent
├─ agent.runtime
│  ├─ PaperAgentRuntime
│  ├─ AgentRunCommand / AgentRunResult / AgentRuntimeEvent
│  └─ agentscope
│     ├─ AgentScopeRuntimeAdapter
│     ├─ AgentScopeConfiguration
│     └─ PaperToolAdapter
├─ knowledge
│  ├─ CloudKnowledgeSearchClient
│  └─ EvidenceCandidate
├─ tools
│  ├─ ArxivSearchTool
│  ├─ ArxivPdfDownloaderTool
│  └─ PdfReaderTool
├─ writing
│  ├─ application
│  ├─ workflow
│  ├─ task
│  ├─ run
│  ├─ evidence
│  ├─ outline
│  ├─ section
│  ├─ citation
│  ├─ document
│  └─ infrastructure
│     ├─ memory
│     └─ persistence
└─ controller
```

## 3. 一次研究运行

```text
POST research-runs
  -> WritingTaskApplicationService
  -> WritingWorkflow 校验当前阶段
  -> StageCapabilityPolicy 生成工具白名单
  -> 创建并标记 TaskRun(RUNNING)
  -> PaperAgentRuntime.run(command)
  -> AgentScope 在白名单内选择工具
  -> 工具事件返回结构化 EvidenceCandidate
  -> TaskEvidenceAdoptionService 校验、去重、冻结内容快照和哈希
  -> TaskRun(SUCCEEDED / FAILED)
```

知识库响应中的临时签名 URL 不会成为长期证据。`TaskEvidence` 只保存稳定来源标识、文档名、章节、原始页码语义、内容快照和内容哈希。

## 4. 写作与引用

```text
TaskEvidence
  -> OutlineVersion + OutlineSection
  -> WritingContextBuilder
  -> SectionVersion
  -> SectionClaim + ClaimCitation
  -> DocumentAssembler
  -> DocumentVersion + frozen citation projection
```

提纲和章节生成会调用模型。全文组装不调用模型：它按照确认提纲的章节顺序读取每个确认章节版本，校验任务归属、提纲归属、内容哈希和引用状态，再生成不可变 Markdown 快照。

## 5. 状态与版本

`WritingStage` 描述整篇论文业务状态：

```text
CREATED -> RESEARCHING -> OUTLINE_PENDING -> OUTLINE_CONFIRMED
        -> DRAFTING -> DRAFT_COMPLETED -> DOCUMENT_READY
```

`TaskRunStatus` 只描述一次模型运行：

```text
CREATED -> RUNNING -> SUCCEEDED
                   -> FAILED
```

两者不可混用。研究、提纲和章节生成会创建 `TaskRun`；确定性全文组装不创建模型运行记录。

## 6. 工具权限

工具权限通过每次运行实际构造的 Toolkit 子集实现，不只依赖 Prompt：

- 研究阶段可注册知识库、arXiv 搜索、下载和 PDF 阅读工具；
- 提纲生成默认不注册外部研究工具，只使用已采纳证据；
- 章节生成只使用工作流装配的已确认提纲和任务证据；
- 全文组装不注册任何工具。

## 7. Repository 接口为何保留

领域包中的 Repository 是端口，`infrastructure.persistence` 和 `infrastructure.memory` 是适配器。生产使用 JDBC 不等于业务层应直接依赖 JDBC。

以 `ClaimCitationRepository` 为例：

```text
SectionWritingApplicationService
  -> ClaimCitationRepository
       |-> JdbcClaimCitationRepository       external / production
       `-> InMemoryClaimCitationRepository   local / deterministic tests
```

这使业务规则可在没有数据库的情况下验证，也让 SQL Schema 或持久化技术变化不会改写应用服务。只有在所有调用者都不再需要该抽象，并且已有另一条清晰的持久化边界时才应删除接口；“现在主要用 JDBC”本身不是删除理由。

## 8. 事务与恢复语义

长时间模型调用不占用数据库事务：

```text
短事务：创建 TaskRun
  -> 无事务：调用模型和工具
  -> 短事务：采纳产物并完成或失败 TaskRun
```

当前恢复保证是“已提交产物可在进程重启后重新查询和继续后续阶段”。运行中进程崩溃后的 Checkpoint 恢复、取消和事件重放尚未实现，不能将当前能力表述为完整断点续跑。

## 9. 配置模式

| Profile | Repository | 数据库 | 用途 |
| --- | --- | --- | --- |
| `local` | memory | 不需要 | 快速启动、接口浏览、默认测试 |
| `external` | jdbc | PostgreSQL + Flyway | 持久化验收、真实链路 |

所有 Repository 实现由同一个 `paper.writing.repository-type` 开关整体选择，避免同一应用上下文混用内存和 JDBC 实现。

