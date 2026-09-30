package org.example.paperaiagent.writing.infrastructure;

import org.example.paperaiagent.writing.citation.ClaimCitationRepository;
import org.example.paperaiagent.writing.citation.SectionClaimRepository;
import org.example.paperaiagent.writing.document.DocumentAssemblyRequestRepository;
import org.example.paperaiagent.writing.document.DocumentVersionClaimRepository;
import org.example.paperaiagent.writing.document.DocumentVersionRepository;
import org.example.paperaiagent.writing.document.DocumentVersionSectionRepository;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.outline.OutlineVersionRepository;
import org.example.paperaiagent.writing.outline.OutlineSectionRepository;
import org.example.paperaiagent.writing.section.SectionVersionRepository;
import org.example.paperaiagent.writing.run.TaskRunRepository;
import org.example.paperaiagent.writing.task.WritingTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Emits non-secret startup diagnostics so operators can verify that the intended
 * runtime and persistence implementations are active.
 */
@Component
public class WritingRuntimeDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WritingRuntimeDiagnostics.class);

    private final WritingTaskRepository taskRepository;
    private final TaskRunRepository runRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final OutlineVersionRepository outlineRepository;
    private final OutlineSectionRepository outlineSectionRepository;
    private final SectionVersionRepository sectionVersionRepository;
    private final SectionClaimRepository sectionClaimRepository;
    private final ClaimCitationRepository claimCitationRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentAssemblyRequestRepository documentAssemblyRequestRepository;
    private final DocumentVersionSectionRepository documentVersionSectionRepository;
    private final DocumentVersionClaimRepository documentVersionClaimRepository;
    private final String repositoryMode;
    private final boolean knowledgeSearchEnabled;
    private final String agentRuntime;
    private final int maxEvidenceCount;
    private final int maxEvidenceCharacters;
    private final int maxSectionEvidenceCount;
    private final int maxSectionEvidenceCharacters;
    private final int maxPreviousSectionCharacters;

    public WritingRuntimeDiagnostics(
            WritingTaskRepository taskRepository,
            TaskRunRepository runRepository,
            TaskEvidenceRepository evidenceRepository,
            OutlineVersionRepository outlineRepository,
            OutlineSectionRepository outlineSectionRepository,
            SectionVersionRepository sectionVersionRepository,
            SectionClaimRepository sectionClaimRepository,
            ClaimCitationRepository claimCitationRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentAssemblyRequestRepository documentAssemblyRequestRepository,
            DocumentVersionSectionRepository documentVersionSectionRepository,
            DocumentVersionClaimRepository documentVersionClaimRepository,
            @Value("${paper.writing.repository-type:memory}") String repositoryMode,
            @Value("${paper.knowledge.enabled:false}") boolean knowledgeSearchEnabled,
            @Value("${paper.agent.runtime:agentscope}") String agentRuntime,
            @Value("${paper.writing.outline.max-evidence-count:20}") int maxEvidenceCount,
            @Value("${paper.writing.outline.max-evidence-characters:30000}") int maxEvidenceCharacters,
            @Value("${paper.writing.section.max-evidence-count:12}") int maxSectionEvidenceCount,
            @Value("${paper.writing.section.max-evidence-characters:20000}") int maxSectionEvidenceCharacters,
            @Value("${paper.writing.section.max-previous-section-characters:8000}") int maxPreviousSectionCharacters) {
        this.taskRepository = taskRepository;
        this.runRepository = runRepository;
        this.evidenceRepository = evidenceRepository;
        this.outlineRepository = outlineRepository;
        this.outlineSectionRepository = outlineSectionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.sectionClaimRepository = sectionClaimRepository;
        this.claimCitationRepository = claimCitationRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentAssemblyRequestRepository = documentAssemblyRequestRepository;
        this.documentVersionSectionRepository = documentVersionSectionRepository;
        this.documentVersionClaimRepository = documentVersionClaimRepository;
        this.repositoryMode = repositoryMode;
        this.knowledgeSearchEnabled = knowledgeSearchEnabled;
        this.agentRuntime = agentRuntime;
        this.maxEvidenceCount = maxEvidenceCount;
        this.maxEvidenceCharacters = maxEvidenceCharacters;
        this.maxSectionEvidenceCount = maxSectionEvidenceCount;
        this.maxSectionEvidenceCharacters = maxSectionEvidenceCharacters;
        this.maxPreviousSectionCharacters = maxPreviousSectionCharacters;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Writing repository mode: {}", repositoryMode);
        log.info("WritingTaskRepository: {}", implementationName(taskRepository));
        log.info("TaskRunRepository: {}", implementationName(runRepository));
        log.info("TaskEvidenceRepository: {}", implementationName(evidenceRepository));
        log.info("OutlineVersionRepository: {}", implementationName(outlineRepository));
        log.info("OutlineSectionRepository: {}", implementationName(outlineSectionRepository));
        log.info("SectionVersionRepository: {}", implementationName(sectionVersionRepository));
        log.info("SectionClaimRepository: {}", implementationName(sectionClaimRepository));
        log.info("ClaimCitationRepository: {}", implementationName(claimCitationRepository));
        log.info("DocumentVersionRepository: {}", implementationName(documentVersionRepository));
        log.info("DocumentAssemblyRequestRepository: {}", implementationName(documentAssemblyRequestRepository));
        log.info("DocumentVersionSectionRepository: {}", implementationName(documentVersionSectionRepository));
        log.info("DocumentVersionClaimRepository: {}", implementationName(documentVersionClaimRepository));
        log.info("Knowledge search enabled: {}", knowledgeSearchEnabled);
        log.info("Agent runtime: {}", agentRuntime);
        log.info("Outline evidence limits: count={}, characters={}", maxEvidenceCount, maxEvidenceCharacters);
        log.info("Section context limits: evidenceCount={}, evidenceCharacters={}, previousSectionCharacters={}",
                maxSectionEvidenceCount, maxSectionEvidenceCharacters, maxPreviousSectionCharacters);
    }

    private String implementationName(Object repository) {
        return AopUtils.getTargetClass(repository).getSimpleName();
    }
}
