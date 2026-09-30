package org.example.paperaiagent.writing.citation;

import java.util.Objects;
import java.util.UUID;

public record ClaimCitationId(UUID value) {
    public ClaimCitationId {
        Objects.requireNonNull(value, "value");
    }

    public static ClaimCitationId newId() {
        return new ClaimCitationId(UUID.randomUUID());
    }

    public static ClaimCitationId from(String value) {
        return new ClaimCitationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
