package org.example.paperaiagent.agent.runtime.agentscope;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.knowledge.KnowledgeToolAdapter;
import org.example.paperaiagent.tools.ArxivPdfDownloaderTool;
import org.example.paperaiagent.tools.ArxivSearchTool;
import org.example.paperaiagent.tools.PdfReaderTool;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "paper.agent.runtime", havingValue = "agentscope")
public class AgentScopeConfiguration {

    @Bean("agentScopeModel")
    public Model agentScopeModel(
            @Value("${paper.agent.agentscope.api-key}") String apiKey,
            @Value("${paper.agent.agentscope.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${paper.agent.agentscope.model:deepseek-chat}") String modelName
    ) {
        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .stream(true)
                .build();
    }

    @Bean
    public PaperToolAdapter paperToolAdapter(
            ArxivSearchTool searchTool,
            ArxivPdfDownloaderTool downloaderTool,
            PdfReaderTool pdfReaderTool
    ) {
        return new PaperToolAdapter(searchTool, downloaderTool, pdfReaderTool);
    }

    @Bean
    public Toolkit paperToolkit(
            PaperToolAdapter paperToolAdapter,
            ObjectProvider<KnowledgeToolAdapter> knowledgeToolAdapter
    ) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(paperToolAdapter);
        knowledgeToolAdapter.ifAvailable(toolkit::registerTool);
        return toolkit;
    }

    @Bean
    public PaperAgentRuntime agentScopeRuntimeAdapter(
            @Qualifier("agentScopeModel") Model model,
            Toolkit paperToolkit,
            @Value("${paper.agent.max-iterations:10}") int maxIterations,
            @Value("${paper.agent.agentscope.tool-timeout:PT10M}") String toolTimeout,
            ObjectMapper objectMapper
    ) {
        return new AgentScopeRuntimeAdapter(
                model,
                paperToolkit,
                maxIterations,
                Duration.parse(toolTimeout),
                objectMapper
        );
    }
}
