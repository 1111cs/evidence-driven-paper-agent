package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunStatus;
import org.example.paperaiagent.writing.run.TaskRunAction;

import java.time.Instant;

public record TaskRunResponse(
        String runId,
        String taskId,
        TaskRunAction action,
        String requestId,
        String requestHash,
        String runtimeSessionId,
        TaskRunStatus status,
        String finalAnswer,
        String errorCode,
        String errorSummary,
        int adoptedEvidenceCount,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt
) {

    public static TaskRunResponse from(TaskRun run) {
        return new TaskRunResponse(
                run.id().toString(),
                run.taskId().toString(),
                run.action(),
                run.requestId(),
                run.requestHash(),
                run.runtimeSessionId(),
                run.status(),
                run.finalAnswer(),
                run.errorCode(),
                run.errorSummary(),
                run.adoptedEvidenceCount(),
                run.createdAt(),
                run.startedAt(),
                run.finishedAt()
        );
    }
}
