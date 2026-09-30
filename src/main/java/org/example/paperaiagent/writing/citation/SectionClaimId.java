package org.example.paperaiagent.writing.citation;

import java.util.Objects;
import java.util.UUID;

public record SectionClaimId(UUID value) {
    public SectionClaimId {
        Objects.requireNonNull(value, "value");
    }

    public static SectionClaimId newId() {
        return new SectionClaimId(UUID.randomUUID());
    }

    public static SectionClaimId from(String value) {
        return new SectionClaimId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
