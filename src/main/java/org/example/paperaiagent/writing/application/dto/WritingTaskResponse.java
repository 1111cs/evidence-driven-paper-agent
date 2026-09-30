package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;

import java.time.Instant;

public record WritingTaskResponse(
        String taskId,
        String title,
        String topic,
        String requirements,
        WritingStage stage,
        String confirmedOutlineVersionId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    public static WritingTaskResponse from(WritingTask task) {
        return new WritingTaskResponse(
                task.id().toString(),
                task.title(),
                task.topic(),
                task.requirements(),
                task.stage(),
                task.confirmedOutlineVersionId() == null ? null : task.confirmedOutlineVersionId().toString(),
                task.createdAt(),
                task.updatedAt(),
                task.version()
        );
    }
}
