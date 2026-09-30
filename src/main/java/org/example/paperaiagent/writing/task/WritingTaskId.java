package org.example.paperaiagent.writing.task;

import java.util.Objects;
import java.util.UUID;

public record WritingTaskId(UUID value) {

    public WritingTaskId {
        Objects.requireNonNull(value, "value");
    }

    public static WritingTaskId newId() {
        return new WritingTaskId(UUID.randomUUID());
    }

    public static WritingTaskId from(String value) {
        return new WritingTaskId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
