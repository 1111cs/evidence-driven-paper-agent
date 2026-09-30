package org.example.paperaiagent.agent.runtime.agentscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.tools.ArxivPdfDownloaderTool;
import org.example.paperaiagent.tools.ArxivSearchTool;
import org.example.paperaiagent.tools.PdfReaderTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AgentScopeConfiguration.class)
            .withPropertyValues(
                    "paper.agent.runtime=agentscope",
                    "paper.agent.agentscope.api-key=test-key",
                    "paper.agent.agentscope.base-url=https://api.deepseek.com",
                    "paper.agent.agentscope.model=deepseek-chat",
                    "paper.agent.max-iterations=4"
            )
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ArxivSearchTool.class, ArxivSearchTool::new)
            .withBean(ArxivPdfDownloaderTool.class, ArxivPdfDownloaderTool::new)
            .withBean(PdfReaderTool.class, PdfReaderTool::new);

    @Test
    void agentScopeRuntimeStartsInSpringContext() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(Model.class);
            assertThat(context).hasSingleBean(PaperAgentRuntime.class);
            assertThat(context).hasSingleBean(PaperToolAdapter.class);
        });
    }
}
