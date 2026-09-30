# 验证记录

## 1. 冻结基准

公开清理前的本地冻结点：

| 项目 | 结果 |
| --- | --- |
| 默认测试 | 88 项通过 |
| JDBC external | 5 项通过 |
| Flyway | 最新脚本 V14，共 15 个迁移 |
| 最终业务阶段 | `DOCUMENT_READY` |
| 真实模型、知识库、PostgreSQL与重启查询 | 手工验收通过 |

冻结副本只保存在本地，不进入公开仓库。

## 2. 公开版清理

已移除普通问答入口、自研 Manus/ReAct 循环、Legacy Runtime、文件式 ChatMemory、旧 Advisor/MCP/PGVector 接线、未使用工具及对应依赖和测试。AgentScope 写作主链、四个论文工具、领域模型、JDBC/Flyway 和内存测试适配器保留。

清理后：

| 项目 | 结果 |
| --- | --- |
| 主代码文件 | 151 |
| 测试源文件 | 27 |
| 默认测试 | 82 项通过，0 失败，0 错误 |
| JDBC external | 5 项通过，0 失败，0 错误 |
| Flyway | 最新脚本 V14，共 15 个迁移 |
| Maven 编译 | 通过 |

测试数量减少来自已删除旧链路的测试，不是跳过新版主链测试。

## 3. 默认测试

```bash
mvn clean test
```

默认测试不得访问 DeepSeek、百炼、arXiv 或 PostgreSQL。它覆盖运行时契约、阶段门禁、工具白名单、证据采纳、提纲与章节版本、引用校验、全文确定性组装和 HTTP/OpenAPI 契约。

## 4. JDBC external 测试

先启动 `compose.yaml` 中的 PostgreSQL，然后执行：

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://localhost:54333/paper_agent'
$env:SPRING_DATASOURCE_USERNAME = 'paper_agent'
$env:SPRING_DATASOURCE_PASSWORD = 'paper_agent'
mvn -Pjdbc-external "-Dspring.profiles.active=external" test
```

external 测试组验证：

- Spring 上下文实际注入 JDBC Repository；
- Flyway Schema 能完成初始化和升级；
- Research、Outline、Section、ClaimCitation 和 DocumentVersion 能持久化；
- 重新构造 Repository 后仍能查询已提交产物；
- 全文、章节顺序、证据关联和哈希保持不变。

`jdbc-external` 只运行 5 个数据库测试，不访问 DeepSeek、百炼或 arXiv。需要真实网络冒烟时，完整配置外部服务后再运行 `mvn -Pexternal test`；该组可能产生模型费用并受公网状态影响。

## 5. 人工真实链路

真实模型和知识库调用会产生费用且依赖外部服务，因此不属于默认测试。人工验收应在 `external` Profile 下通过 Knife4j 完成：

```text
创建任务
  -> 研究并查询证据
  -> 生成和确认提纲
  -> 生成和确认全部正文叶子章节
  -> 校验结构化引用
  -> 组装并下载 Markdown 全文
  -> 重启应用
  -> 使用原 ID 查询任务及全部版本
```

验收时不得把真实密钥、签名 URL、数据库公网地址或本机绝对路径写入报告。

## 6. 封版标记

```text
WRITING_RESEARCH_V1_PASSED
WRITING_OUTLINE_V1_PASSED
WRITING_SECTION_V1_PASSED
WRITING_CLAIM_CITATION_V1_PASSED
WRITING_DOCUMENT_V1_PASSED
PUBLIC_REPOSITORY_READY
```

`PUBLIC_REPOSITORY_READY` 已于 2026-09-30 在 Apache-2.0 许可证确认、公开仓库推送、GitHub 页面复核，以及从远程全新克隆后执行 82 项默认测试全部通过后记录。

