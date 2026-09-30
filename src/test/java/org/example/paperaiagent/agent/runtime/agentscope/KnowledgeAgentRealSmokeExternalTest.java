package org.example.paperaiagent.agent.runtime.agentscope;

import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRunResult;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("external")
@SpringBootTest(properties = {
        "paper.agent.runtime=agentscope",
        "paper.agent.max-iterations=5",
        "paper.knowledge.enabled=true"
})
class KnowledgeAgentRealSmokeExternalTest {

    @Autowired
    private PaperAgentRuntime runtime;

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void deepSeekUsesKnowledgeToolAndCitesAlmtSource() {
        String prompt = """
                请只使用 search_knowledge 查询一次，不要调用 arXiv 或 PDF 工具。
                回答：ALMT 解决了什么问题，核心模块是什么？
                回答必须基于检索切片，并在末尾明确给出来源文档名、章节和知识库返回的页码或页索引。
                """;
        AgentRunCommand command = new AgentRunCommand(prompt, "external-almt-knowledge-smoke");
        AgentRunResult result = runtime.run(command).block(Duration.ofMinutes(4));

        assertNotNull(result);
        assertEquals(AgentRunResult.Status.COMPLETED, result.status(),
                () -> result.errorCode() + ": " + result.errorMessage());
        List<String> tools = result.events().stream()
                .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_STARTED)
                .map(AgentRuntimeEvent::toolName)
                .toList();
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().allMatch("search_knowledge"::equals), tools.toString());

        String answer = result.finalAnswer();
        assertFalse(answer.isBlank());
        assertTrue(answer.toLowerCase().contains("almt"), answer);
        assertTrue(answer.toLowerCase().contains("ahl"), answer);
        assertTrue(answer.contains("来源"), answer);
        assertTrue(answer.matches("(?s).*(页码|页索引|page).*\\d+.*"), answer);

        System.out.println("Knowledge smoke tools: " + tools);
        System.out.println("Knowledge smoke answer: " + answer);
    }
}
