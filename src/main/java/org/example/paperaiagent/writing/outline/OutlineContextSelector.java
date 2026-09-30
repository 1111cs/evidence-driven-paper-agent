package org.example.paperaiagent.writing.outline;

import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutlineContextSelector {
    private final int maxEvidenceCount;
    private final int maxEvidenceCharacters;

    public OutlineContextSelector(
            @Value("${paper.writing.outline.max-evidence-count:20}") int maxEvidenceCount,
            @Value("${paper.writing.outline.max-evidence-characters:30000}") int maxEvidenceCharacters
    ) {
        if (maxEvidenceCount < 1 || maxEvidenceCharacters < 1) {
            throw new IllegalArgumentException("Outline evidence limits must be positive");
        }
        this.maxEvidenceCount = maxEvidenceCount;
        this.maxEvidenceCharacters = maxEvidenceCharacters;
    }

    public OutlineContext select(List<TaskEvidence> source) {
        Map<String, TaskEvidence> unique = new LinkedHashMap<>();
        source.stream()
                .sorted(Comparator.comparingDouble(TaskEvidence::score).reversed()
                        .thenComparing(TaskEvidence::adoptedAt)
                        .thenComparing(item -> item.id().toString()))
                .forEach(item -> unique.putIfAbsent(item.stableKey(), item));

        List<TaskEvidence> selected = new ArrayList<>();
        StringBuilder rendered = new StringBuilder();
        for (TaskEvidence item : unique.values()) {
            if (selected.size() >= maxEvidenceCount || rendered.length() >= maxEvidenceCharacters) break;
            String block = render(item, selected.size() + 1);
            int remaining = maxEvidenceCharacters - rendered.length();
            if (block.length() > remaining) {
                if (selected.isEmpty() && remaining > 0) {
                    rendered.append(block, 0, remaining);
                    selected.add(item);
                }
                break;
            }
            rendered.append(block);
            selected.add(item);
        }
        return new OutlineContext(selected, rendered.toString());
    }

    private static String render(TaskEvidence item, int sequence) {
        return "\n[证据 " + sequence + "]\n"
                + "evidenceId: " + item.id() + "\n"
                + "文档: " + item.documentName() + "\n"
                + "章节: " + (item.section().isBlank() ? "未标注" : item.section()) + "\n"
                + "源页标记: " + item.sourcePages() + "\n"
                + "页码方案: " + item.pageNumberingScheme() + "\n"
                + "内容: " + item.contentSnapshot() + "\n";
    }
}
