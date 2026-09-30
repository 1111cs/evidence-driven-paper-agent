package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WritingDomainContractTest {

    @Test
    void taskMovesFromCreatedToResearchingAndAllowsAdditionalResearch() {
        WritingTask task = WritingTask.create("ALMT study", "ALMT", "Use source evidence");

        assertEquals(WritingStage.CREATED, task.stage());
        task.startResearch();
        assertEquals(WritingStage.RESEARCHING, task.stage());
        task.startResearch();
        assertEquals(WritingStage.RESEARCHING, task.stage());
        assertEquals(2, task.version());
    }

    @Test
    void terminalRunCannotBeRestartedOrCompletedAgain() {
        WritingTask task = WritingTask.create("ALMT study", "ALMT", "Use source evidence");
        TaskRun run = TaskRun.create(task.id(), "request-1", "session-1", "Research ALMT");
        run.start();
        run.succeed("done", 0);

        assertEquals(TaskRunStatus.SUCCEEDED, run.status());
        assertThrows(IllegalStateException.class, run::start);
        assertThrows(IllegalStateException.class, () -> run.fail("RUNTIME_ERROR", "late failure"));
        assertThrows(IllegalStateException.class, () -> run.succeed("again", 0));
    }
}
