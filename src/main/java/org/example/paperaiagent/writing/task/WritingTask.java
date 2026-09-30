package org.example.paperaiagent.writing.task;

import org.example.paperaiagent.writing.outline.OutlineVersionId;
import java.time.Instant;
import java.util.Objects;

public final class WritingTask {

    private final WritingTaskId id;
    private final String title;
    private final String topic;
    private final String requirements;
    private final Instant createdAt;
    private WritingStage stage;
    private Instant updatedAt;
    private long version;
    private OutlineVersionId confirmedOutlineVersionId;

    private WritingTask(
            WritingTaskId id,
            String title,
            String topic,
            String requirements,
            WritingStage stage,
            Instant createdAt,
            Instant updatedAt,
            long version,
            OutlineVersionId confirmedOutlineVersionId
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = requireText(title, "title");
        this.topic = requireText(topic, "topic");
        this.requirements = requirements == null ? "" : requirements.trim();
        this.stage = Objects.requireNonNull(stage, "stage");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        this.version = version;
        this.confirmedOutlineVersionId = confirmedOutlineVersionId;
    }

    public static WritingTask create(String title, String topic, String requirements) {
        Instant now = Instant.now();
        return new WritingTask(
                WritingTaskId.newId(),
                title,
                topic,
                requirements,
                WritingStage.CREATED,
                now,
                now,
                0,
                null
        );
    }

    public static WritingTask restore(
            WritingTaskId id,
            String title,
            String topic,
            String requirements,
            WritingStage stage,
            Instant createdAt,
            Instant updatedAt,
            long version,
            OutlineVersionId confirmedOutlineVersionId
    ) {
        return new WritingTask(id, title, topic, requirements, stage, createdAt, updatedAt, version,
                confirmedOutlineVersionId);
    }

    public synchronized void startResearch() {
        if (stage != WritingStage.CREATED && stage != WritingStage.RESEARCHING) {
            throw new IllegalStateException("Research cannot start from stage " + stage);
        }
        stage = WritingStage.RESEARCHING;
        updatedAt = Instant.now();
        version++;
    }

    public synchronized void markOutlinePending() {
        if (stage != WritingStage.RESEARCHING && stage != WritingStage.OUTLINE_PENDING) {
            throw new IllegalStateException("Outline cannot be generated from stage " + stage);
        }
        stage = WritingStage.OUTLINE_PENDING;
        updatedAt = Instant.now();
        version++;
    }

    public synchronized void confirmOutline(OutlineVersionId outlineVersionId) {
        Objects.requireNonNull(outlineVersionId, "outlineVersionId");
        if (stage == WritingStage.OUTLINE_CONFIRMED) {
            if (outlineVersionId.equals(confirmedOutlineVersionId)) return;
            throw new IllegalStateException("OUTLINE_ALREADY_CONFIRMED");
        }
        if (stage != WritingStage.OUTLINE_PENDING) {
            throw new IllegalStateException("Outline cannot be confirmed from stage " + stage);
        }
        confirmedOutlineVersionId = outlineVersionId;
        stage = WritingStage.OUTLINE_CONFIRMED;
        updatedAt = Instant.now();
        version++;
    }

    public synchronized void startDrafting() {
        if (stage == WritingStage.DRAFTING) return;
        if (stage != WritingStage.OUTLINE_CONFIRMED) {
            throw new IllegalStateException("Drafting cannot start from stage " + stage);
        }
        stage = WritingStage.DRAFTING;
        updatedAt = Instant.now();
        version++;
    }

    public synchronized void completeDraft() {
        if (stage == WritingStage.DRAFT_COMPLETED) return;
        if (stage != WritingStage.DRAFTING) {
            throw new IllegalStateException("Draft cannot complete from stage " + stage);
        }
        stage = WritingStage.DRAFT_COMPLETED;
        updatedAt = Instant.now();
        version++;
    }

    public synchronized void markDocumentReady() {
        if (stage == WritingStage.DOCUMENT_READY) return;
        if (stage != WritingStage.DRAFT_COMPLETED) {
            throw new IllegalStateException("Document cannot be assembled from stage " + stage);
        }
        stage = WritingStage.DOCUMENT_READY;
        updatedAt = Instant.now();
        version++;
    }

    public WritingTaskId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String topic() {
        return topic;
    }

    public String requirements() {
        return requirements;
    }

    public synchronized WritingStage stage() {
        return stage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized Instant updatedAt() {
        return updatedAt;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized OutlineVersionId confirmedOutlineVersionId() {
        return confirmedOutlineVersionId;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
