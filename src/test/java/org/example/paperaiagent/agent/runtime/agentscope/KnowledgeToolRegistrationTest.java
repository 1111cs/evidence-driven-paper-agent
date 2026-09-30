package org.example.paperaiagent.agent.runtime.agentscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.example.paperaiagent.knowledge.KnowledgeConfiguration;
import org.example.paperaiagent.knowledge.KnowledgeToolAdapter;
import org.example.paperaiagent.tools.ArxivPdfDownloaderTool;
import org.example.paperaiagent.tools.ArxivSearchTool;
import org.example.paperaiagent.tools.PdfReaderTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KnowledgeToolRegistrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AgentScopeConfiguration.class, KnowledgeConfiguration.class)
            .withPropertyValues(
                    "paper.agent.runtime=agentscope",
                    "paper.agent.agentscope.api-key=test-model-key",
                    "paper.agent.agentscope.base-url=https://api.deepseek.com",
                    "paper.agent.agentscope.model=deepseek-chat",
                    "paper.knowledge.enabled=true",
                    "paper.knowledge.endpoint=http://127.0.0.1:1/retrieve",
                    "paper.knowledge.api-key=test-knowledge-key",
                    "paper.knowledge.index-id=test-index"
            )
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(Model.class, () -> mock(Model.class))
            .withBean(ArxivSearchTool.class, ArxivSearchTool::new)
            .withBean(ArxivPdfDownloaderTool.class, ArxivPdfDownloaderTool::new)
            .withBean(PdfReaderTool.class, PdfReaderTool::new);

    @Test
    void enabledKnowledgeClientRegistersFourthAgentScopeTool() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(KnowledgeToolAdapter.class);
            assertThat(context.getBean(Toolkit.class).getToolNames())
                    .containsExactlyInAnyOrder(
                            "arxiv_search",
                            "arxiv_download",
                            "pdf_read",
                            "search_knowledge"
                    );
        });
    }
}
