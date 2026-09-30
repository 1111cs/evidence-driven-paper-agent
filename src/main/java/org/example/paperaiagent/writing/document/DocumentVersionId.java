package org.example.paperaiagent.writing.document;

import java.util.Objects;
import java.util.UUID;

public record DocumentVersionId(UUID value) {
    public DocumentVersionId { Objects.requireNonNull(value, "value"); }
    public static DocumentVersionId newId() { return new DocumentVersionId(UUID.randomUUID()); }
    public static DocumentVersionId from(String value) { return new DocumentVersionId(UUID.fromString(value)); }
    @Override public String toString() { return value.toString(); }
}
