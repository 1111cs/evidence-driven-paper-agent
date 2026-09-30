package org.example.paperaiagent.writing.outline;

import java.util.Objects;
import java.util.UUID;

public record OutlineVersionId(UUID value) {
    public OutlineVersionId {
        Objects.requireNonNull(value, "value");
    }

    public static OutlineVersionId newId() {
        return new OutlineVersionId(UUID.randomUUID());
    }

    public static OutlineVersionId from(String value) {
        return new OutlineVersionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
