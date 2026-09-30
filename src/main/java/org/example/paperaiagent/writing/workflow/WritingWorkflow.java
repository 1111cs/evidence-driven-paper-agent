package org.example.paperaiagent.writing.workflow;

import org.example.paperaiagent.writing.task.WritingStage;
import org.example.paperaiagent.writing.task.WritingTask;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class WritingWorkflow {

    private final StageCapabilityPolicy capabilityPolicy;

    public WritingWorkflow(StageCapabilityPolicy capabilityPolicy) {
        this.capabilityPolicy = capabilityPolicy;
    }

    public void requireResearchAllowed(WritingTask task) {
        WritingStage stage = task.stage();
        if (stage != WritingStage.CREATED && stage != WritingStage.RESEARCHING) {
            throw new IllegalStateException("Research is not allowed in stage " + stage);
        }
    }

    public Set<String> startResearch(WritingTask task) {
        requireResearchAllowed(task);
        task.startResearch();
        return capabilityPolicy.allowedTools(task.stage());
    }

    public Set<String> requireOutlineGenerationAllowed(WritingTask task) {
        WritingStage stage = task.stage();
        if (stage != WritingStage.RESEARCHING && stage != WritingStage.OUTLINE_PENDING) {
            throw new IllegalStateException("Outline generation is not allowed in stage " + stage);
        }
        return Set.of();
    }

    public Set<String> requireSectionGenerationAllowed(WritingTask task) {
        WritingStage stage = task.stage();
        if (stage != WritingStage.OUTLINE_CONFIRMED && stage != WritingStage.DRAFTING) {
            throw new IllegalStateException("Section generation is not allowed in stage " + stage);
        }
        return Set.of();
    }
}
