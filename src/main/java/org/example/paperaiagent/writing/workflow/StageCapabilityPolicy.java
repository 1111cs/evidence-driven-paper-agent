package org.example.paperaiagent.writing.workflow;

import org.example.paperaiagent.agent.runtime.PaperToolNames;
import org.example.paperaiagent.writing.task.WritingStage;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class StageCapabilityPolicy {

    public Set<String> allowedTools(WritingStage stage) {
        return switch (stage) {
            case CREATED, OUTLINE_PENDING, OUTLINE_CONFIRMED, DRAFTING, DRAFT_COMPLETED, DOCUMENT_READY -> Set.of();
            case RESEARCHING -> PaperToolNames.RESEARCH_TOOLS;
        };
    }
}
