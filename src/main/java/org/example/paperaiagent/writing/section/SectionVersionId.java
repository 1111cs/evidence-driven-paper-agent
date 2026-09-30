package org.example.paperaiagent.writing.section;

import java.util.Objects;
import java.util.UUID;

public record SectionVersionId(UUID value) {
    public SectionVersionId {
        Objects.requireNonNull(value, "value");
    }

    public static SectionVersionId newId() {
        return new SectionVersionId(UUID.randomUUID());
    }

    public static SectionVersionId from(String value) {
        return new SectionVersionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
