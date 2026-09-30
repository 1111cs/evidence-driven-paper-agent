package org.example.paperaiagent.writing.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.agent.runtime.AgentRunResult;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperToolNames;
import org.example.paperaiagent.knowledge.EvidenceCandidate;
import org.example.paperaiagent.knowledge.KnowledgeSearchResult;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.task.WritingTaskId;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class TaskEvidenceAdoptionService {

    private final ObjectMapper objectMapper;

    public TaskEvidenceAdoptionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvidenceAdoptionPlan plan(
            WritingTaskId taskId,
            TaskRunId runId,
            AgentRunResult runResult,
            TaskEvidenceRepository repository
    ) {
        List<TaskEvidence> accepted = new ArrayList<>();
        Set<String> seenInResult = new HashSet<>();
        int rejected = 0;

        for (AgentRuntimeEvent event : runResult.events()) {
            if (event.type() != AgentRuntimeEvent.Type.TOOL_COMPLETED
                    || !PaperToolNames.SEARCH_KNOWLEDGE.equals(event.toolName())) {
                continue;
            }
            Object structuredResult = event.data().get("structuredResult");
            if (structuredResult == null) {
                rejected++;
                continue;
            }

            KnowledgeSearchResult searchResult;
            try {
                searchResult = objectMapper.convertValue(structuredResult, KnowledgeSearchResult.class);
            } catch (IllegalArgumentException exception) {
                rejected++;
                continue;
            }

            for (EvidenceCandidate candidate : searchResult.candidates()) {
                try {
                    TaskEvidence evidence = toEvidence(taskId, runId, candidate);
                    if (seenInResult.add(evidence.stableKey())
                            && !repository.existsByStableKey(evidence.stableKey())) {
                        accepted.add(evidence);
                    }
                } catch (IllegalArgumentException exception) {
                    rejected++;
                }
            }
        }
        return new EvidenceAdoptionPlan(accepted, rejected);
    }

    private TaskEvidence toEvidence(WritingTaskId taskId, TaskRunId runId, EvidenceCandidate candidate) {
        if (candidate.section().isBlank() && candidate.pages().isEmpty()) {
            throw new IllegalArgumentException("Candidate has no section or page locator");
        }
        String contentHash = sha256(candidate.content());
        String stableKey = sha256(String.join(
                "|",
                taskId.toString(),
                EvidenceSourceType.CLOUD_KNOWLEDGE.name(),
                candidate.documentId(),
                candidate.chunkId(),
                contentHash
        ));
        return new TaskEvidence(
                TaskEvidenceId.newId(),
                taskId,
                runId,
                EvidenceSourceType.CLOUD_KNOWLEDGE,
                candidate.documentId(),
                candidate.documentName(),
                candidate.section(),
                candidate.pages(),
                PageNumberingScheme.BAILIAN_RAW,
                "",
                candidate.content(),
                contentHash,
                candidate.chunkId(),
                stableKey,
                candidate.score(),
                Instant.now()
        );
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
