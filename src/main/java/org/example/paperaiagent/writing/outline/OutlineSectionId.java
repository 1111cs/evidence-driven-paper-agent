package org.example.paperaiagent.writing.outline;

import java.util.Objects;
import java.util.UUID;

public record OutlineSectionId(UUID value) {
    public OutlineSectionId {
        Objects.requireNonNull(value, "value");
    }

    public static OutlineSectionId newId() {
        return new OutlineSectionId(UUID.randomUUID());
    }

    public static OutlineSectionId from(String value) {
        return new OutlineSectionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
