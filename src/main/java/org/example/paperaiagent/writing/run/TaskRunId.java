package org.example.paperaiagent.writing.run;

import java.util.Objects;
import java.util.UUID;

public record TaskRunId(UUID value) {

    public TaskRunId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskRunId newId() {
        return new TaskRunId(UUID.randomUUID());
    }

    public static TaskRunId from(String value) {
        return new TaskRunId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
