package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.application.dto.TaskRunResponse;
import org.example.paperaiagent.writing.application.dto.WritingTaskResponse;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("external")
@SpringBootTest(properties = {
        "paper.agent.runtime=agentscope",
        "paper.knowledge.enabled=true",
        "paper.writing.repository-type=memory"
})
class WritingResearchRealSmokeExternalTest {

    private static final Logger log = LoggerFactory.getLogger(WritingResearchRealSmokeExternalTest.class);

    @Autowired
    private WritingTaskApplicationService applicationService;

    @Test
    void researchesAlmtThroughKnowledgeToolAndAdoptsEvidence() {
        WritingTaskResponse task = applicationService.createTask(new CreateWritingTaskRequest(
                "ALMT research smoke",
                "ALMT multimodal sentiment analysis",
                "Answer from the configured knowledge base and cite document, section, and page"
        ));

        TaskRunResponse run = applicationService.startResearch(task.taskId(), new ResearchRequest(
                "Use search_knowledge to explain the main contribution of ALMT. "
                        + "Cite the source document, section, and page in the answer.",
                "external-almt-smoke",
                "external-almt-smoke"
        ));

        assertEquals(TaskRunStatus.SUCCEEDED, run.status(), run.toString());
        assertFalse(run.finalAnswer().isBlank());
        assertTrue(run.adoptedEvidenceCount() > 0, run.toString());
        var evidence = applicationService.listEvidence(task.taskId());
        assertFalse(evidence.isEmpty());

        log.info("REAL_SMOKE_RUN runId={} status={} adoptedEvidenceCount={}",
                run.runId(), run.status(), run.adoptedEvidenceCount());
        log.info("REAL_SMOKE_ANSWER {}", run.finalAnswer());
        evidence.forEach(item -> log.info(
                "REAL_SMOKE_EVIDENCE document={} section={} pages={} contentHashPrefix={}",
                item.documentName(),
                item.section(),
                item.pages(),
                item.contentHash().substring(0, 12)
        ));
    }
}
