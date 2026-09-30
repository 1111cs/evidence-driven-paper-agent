package org.example.paperaiagent.writing.evidence;

import java.util.Objects;
import java.util.UUID;

public record TaskEvidenceId(UUID value) {

    public TaskEvidenceId {
        Objects.requireNonNull(value, "value");
    }

    public static TaskEvidenceId newId() {
        return new TaskEvidenceId(UUID.randomUUID());
    }

    public static TaskEvidenceId from(String value) {
        return new TaskEvidenceId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
