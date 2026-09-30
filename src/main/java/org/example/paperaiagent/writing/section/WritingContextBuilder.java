package org.example.paperaiagent.writing.section;

import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.task.WritingTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WritingContextBuilder {
    private static final String EVIDENCE_TRUNCATED = "\n[Evidence content truncated]";
    private static final String PREVIOUS_TRUNCATED = "\n[Previous section content truncated]";

    private final TaskEvidenceRepository evidenceRepository;
    private final OutlineSectionRepository outlineSectionRepository;
    private final SectionVersionRepository sectionVersionRepository;
    private final int maxEvidenceCount;
    private final int maxEvidenceCharacters;
    private final int maxPreviousSectionCharacters;

    public WritingContextBuilder(
            TaskEvidenceRepository evidenceRepository,
            OutlineSectionRepository outlineSectionRepository,
            SectionVersionRepository sectionVersionRepository,
            @Value("${paper.writing.section.max-evidence-count:12}") int maxEvidenceCount,
            @Value("${paper.writing.section.max-evidence-characters:20000}") int maxEvidenceCharacters,
            @Value("${paper.writing.section.max-previous-section-characters:8000}") int maxPreviousSectionCharacters) {
        this.evidenceRepository = evidenceRepository;
        this.outlineSectionRepository = outlineSectionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.maxEvidenceCount = positive(maxEvidenceCount, "maxEvidenceCount");
        this.maxEvidenceCharacters = positive(maxEvidenceCharacters, "maxEvidenceCharacters");
        this.maxPreviousSectionCharacters = positive(maxPreviousSectionCharacters, "maxPreviousSectionCharacters");
    }

    public WritingContext build(WritingTask task, OutlineVersion outline, OutlineSection target) {
        if (!outline.taskId().equals(task.id()) || !target.outlineVersionId().equals(outline.id())) {
            throw new IllegalArgumentException("Writing context objects do not belong to the same task and outline");
        }
        if (outline.status() != OutlineStatus.CONFIRMED
                || !outline.id().equals(task.confirmedOutlineVersionId())) {
            throw new IllegalStateException("Writing context requires the task's confirmed outline");
        }
        List<EvidenceContext> evidence = selectEvidence(task, outline);
        List<PreviousSectionContext> previous = selectPreviousSections(outline, target);
        return new WritingContext(task, outline, target, evidence, previous,
                evidence.stream().mapToInt(item -> item.renderedText().length()).sum(),
                previous.stream().mapToInt(item -> item.renderedText().length()).sum());
    }

    public String budgetFingerprint() {
        return maxEvidenceCount + ":" + maxEvidenceCharacters + ":" + maxPreviousSectionCharacters;
    }

    private List<EvidenceContext> selectEvidence(WritingTask task, OutlineVersion outline) {
        Map<TaskEvidenceId, TaskEvidence> byId = new HashMap<>();
        evidenceRepository.findByTaskId(task.id()).forEach(item -> byId.put(item.id(), item));
        List<OutlineEvidenceReference> references = outline.evidenceReferences().stream()
                .sorted(Comparator.comparingInt(OutlineEvidenceReference::sequenceNumber)
                        .thenComparing(item -> item.evidenceId().toString()))
                .limit(maxEvidenceCount)
                .toList();
        List<EvidenceContext> selected = new ArrayList<>();
        int remaining = maxEvidenceCharacters;
        for (OutlineEvidenceReference reference : references) {
            TaskEvidence item = byId.get(reference.evidenceId());
            if (item == null || !item.taskId().equals(task.id()) || remaining <= 0) continue;
            int sequenceNumber = selected.size() + 1;
            String alias = "E" + sequenceNumber;
            String rendered = """
                    [evidenceAlias=%s]
                    文档：%s
                    章节：%s
                    源页标记：%s（%s）
                    内容：%s
                    """.formatted(alias, item.documentName(), item.section(), item.sourcePages(),
                    item.pageNumberingScheme(), item.contentSnapshot()).trim();
            String fitted = fit(rendered, remaining, EVIDENCE_TRUNCATED);
            if (fitted.isEmpty()) break;
            selected.add(new EvidenceContext(item.id(), item.taskId(), sequenceNumber, alias,
                    item.contentSnapshot(), fitted));
            remaining -= fitted.length();
        }
        return List.copyOf(selected);
    }

    private List<PreviousSectionContext> selectPreviousSections(OutlineVersion outline, OutlineSection target) {
        Map<OutlineSectionId, OutlineSection> sections = new HashMap<>();
        outlineSectionRepository.findByOutlineVersionId(outline.id()).forEach(item -> sections.put(item.id(), item));
        List<SectionVersion> confirmed = sectionVersionRepository.findConfirmedByOutlineVersionId(outline.id()).stream()
                .filter(version -> {
                    OutlineSection section = sections.get(version.outlineSectionId());
                    return section != null && section.sequenceNumber() < target.sequenceNumber();
                })
                .sorted(Comparator.comparingInt(version -> sections.get(version.outlineSectionId()).sequenceNumber()))
                .toList();
        List<PreviousSectionContext> selected = new ArrayList<>();
        int remaining = maxPreviousSectionCharacters;
        for (SectionVersion version : confirmed) {
            if (remaining <= 0) break;
            OutlineSection section = sections.get(version.outlineSectionId());
            String rendered = """
                    [sectionKey=%s]
                    标题：%s
                    已确认正文：%s
                    """.formatted(section.sectionKey(), section.title(), version.contentSnapshot()).trim();
            String fitted = fit(rendered, remaining, PREVIOUS_TRUNCATED);
            if (fitted.isEmpty()) break;
            selected.add(new PreviousSectionContext(section.id(), section.sectionKey(),
                    section.sequenceNumber(), fitted));
            remaining -= fitted.length();
        }
        return List.copyOf(selected);
    }

    private static String fit(String value, int limit, String marker) {
        if (limit <= 0) return "";
        if (value.length() <= limit) return value;
        if (limit <= marker.length()) return marker.substring(0, limit);
        return value.substring(0, limit - marker.length()) + marker;
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }
}
