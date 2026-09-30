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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("external")
@SpringBootTest(properties = {
        "paper.agent.runtime=agentscope",
        "paper.agent.max-iterations=8"
})
class AgentScopeRealSmokeExternalTest {

    @Autowired
    private PaperAgentRuntime runtime;

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void deepSeekSearchesDownloadsReadsAndAnswers() throws Exception {
        String prompt = """
                Find the original DETR paper on arXiv. You must call exactly these tools in this order:
                arxiv_search once, arxiv_download once, and pdf_read once. Then return a concise final
                answer summarizing the paper's core contributions based on the parsed PDF.
                """;

        AgentRunCommand command = new AgentRunCommand(prompt, "external-detr-smoke");
        AgentRunResult result = runtime.stream(command)
                .doOnNext(this::printLiveEvent)
                .collectList()
                .map(events -> AgentRunResult.fromEvents(command.conversationId(), events))
                .block(Duration.ofMinutes(14));

        assertNotNull(result);
        printTrace(result);
        assertEquals(AgentRunResult.Status.COMPLETED, result.status(),
                () -> result.errorCode() + ": " + result.errorMessage());
        assertValidToolOrder(result);
        assertFalse(result.finalAnswer().isBlank());
        assertFalse(hasPartFiles(Path.of(System.getProperty("user.dir"), "tmp", "pdf")));
    }

    private void assertValidToolOrder(AgentRunResult result) {
        List<String> tools = result.events().stream()
                .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_STARTED)
                .map(AgentRuntimeEvent::toolName)
                .toList();
        int downloadIndex = tools.indexOf("arxiv_download");
        int readIndex = tools.indexOf("pdf_read");

        assertTrue(downloadIndex > 0, () -> "Expected search before download, got " + tools);
        assertTrue(tools.subList(0, downloadIndex).stream().allMatch("arxiv_search"::equals),
                () -> "Only searches may precede download, got " + tools);
        assertEquals(1, tools.stream().filter("arxiv_download"::equals).count(), tools.toString());
        assertEquals(1, tools.stream().filter("pdf_read"::equals).count(), tools.toString());
        assertEquals(downloadIndex + 1, readIndex, () -> "PDF read must immediately follow download: " + tools);
        assertEquals(readIndex, tools.size() - 1, () -> "No extra tools may run after PDF read: " + tools);
    }

    private void printTrace(AgentRunResult result) {
        System.out.println("AgentScope external smoke status: " + result.status());
        result.events().stream()
                .filter(event -> event.type() == AgentRuntimeEvent.Type.TOOL_STARTED)
                .forEach(event -> System.out.println(
                        event.sequence() + " " + event.type() + " " + event.toolName() + " " + event.data()
                ));
        String answer = result.finalAnswer();
        if (answer != null) {
            System.out.println("Final answer: " + answer.substring(0, Math.min(answer.length(), 1000)));
        }
    }

    private void printLiveEvent(AgentRuntimeEvent event) {
        if (event.type() == AgentRuntimeEvent.Type.TOOL_STARTED) {
            System.out.println("LIVE " + event.sequence() + " " + event.type() + " "
                    + event.toolName() + " " + event.data());
        } else if (event.type() == AgentRuntimeEvent.Type.TOOL_COMPLETED) {
            Object result = event.data().get("result");
            int resultLength = result == null ? 0 : result.toString().length();
            System.out.println("LIVE " + event.sequence() + " " + event.type() + " "
                    + event.toolName() + " resultLength=" + resultLength);
        } else if (event.type() == AgentRuntimeEvent.Type.TOOL_FAILED
                || event.type() == AgentRuntimeEvent.Type.RUN_COMPLETED
                || event.type() == AgentRuntimeEvent.Type.RUN_FAILED) {
            System.out.println("LIVE " + event.sequence() + " " + event.type() + " "
                    + event.toolName() + " " + event.data());
        }
    }

    private boolean hasPartFiles(Path directory) throws Exception {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".part"));
        }
    }
}
