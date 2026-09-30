package org.example.paperaiagent.writing;

import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.evidence.EvidenceSourceType;
import org.example.paperaiagent.writing.evidence.PageNumberingScheme;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcClaimCitationRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcOutlineSectionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcSectionClaimRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcSectionVersionRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.persistence.JdbcWritingTaskRepository;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.run.TaskRun;
import org.example.paperaiagent.writing.run.TaskRunAction;
import org.example.paperaiagent.writing.section.SectionEvidenceReference;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("jdbc")
@SpringBootTest(properties = {
        "paper.writing.repository-type=jdbc",
        "paper.agent.runtime=agentscope",
        "paper.knowledge.enabled=false"
})
class ClaimCitationJdbcPersistenceExternalTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void persistsClaimsCitationsOffsetsAndRejectsCrossContextRelations() {
        var tasks = new JdbcWritingTaskRepository(jdbc);
        var runs = new JdbcTaskRunRepository(jdbc);
        var evidenceRepository = new JdbcTaskEvidenceRepository(jdbc);
        var outlines = new JdbcOutlineVersionRepository(jdbc);
        var outlineSections = new JdbcOutlineSectionRepository(jdbc);
        var sections = new JdbcSectionVersionRepository(jdbc);
        var claims = new JdbcSectionClaimRepository(jdbc);
        var citations = new JdbcClaimCitationRepository(jdbc);

        WritingTask task = WritingTask.create("Citation JDBC", "ALMT", "Exact citations");
        try {
            tasks.save(task);
            task.startResearch();
            tasks.save(task);
            TaskRun research = run(task, runs, TaskRunAction.RESEARCH, "research");
            TaskEvidence allowed = evidence(task, research, "allowed", "alpha unique supporting quote.");
            TaskEvidence outsideContext = evidence(task, research, "outside", "outside unique quote.");
            evidenceRepository.saveAll(List.of(allowed, outsideContext));
            research.succeed("research", 2);
            runs.save(research);

            TaskRun outlineRun = run(task, runs, TaskRunAction.GENERATE_OUTLINE, "outline");
            String outlineText = "# Paper\n## Introduction";
            OutlineVersion outline = OutlineVersion.draft(task.id(), outlineRun.id(), 1, "Outline",
                    outlineText, WritingHashes.sha256(outlineText),
                    List.of(new OutlineEvidenceReference(allowed.id(), 1)));
            outlines.save(outline);
            List<OutlineSection> tree = outlineSections.saveAll(new OutlineStructureExtractor().extract(outline));
            outline.confirm();
            outlines.save(outline);
            outlineRun.succeed(outlineText, 0);
            runs.save(outlineRun);
            task.markOutlinePending();
            tasks.save(task);
            task.confirmOutline(outline.id());
            tasks.save(task);

            OutlineSection target = tree.get(1);
            TaskRun sectionRun = run(task, runs, TaskRunAction.GENERATE_SECTION, "section-valid");
            String body = "ALMT uses evidence.[C1]";
            SectionVersion version = SectionVersion.citationValidatedDraft(task.id(), outline.id(), target.id(),
                    sectionRun.id(), 1, target.title(), body, WritingHashes.sha256(body),
                    List.of(new SectionEvidenceReference(allowed.id(), 1)));
            sections.save(version);
            SectionClaim claim = SectionClaim.validated(task.id(), version.id(), "C1",
                    "ALMT uses evidence.", 0, "ALMT uses evidence.".length());
            claims.saveAll(List.of(claim));
            ClaimCitation citation = ClaimCitation.validated(task.id(), version.id(), claim.id(), allowed.id(),
                    1, "alpha unique supporting quote", 0, "alpha unique supporting quote".length());
            citations.saveAll(List.of(citation));
            sectionRun.succeed(body, 0);
            runs.save(sectionRun);

            assertThrows(DataIntegrityViolationException.class, () -> citations.saveAll(List.of(
                    ClaimCitation.validated(task.id(), version.id(), claim.id(), outsideContext.id(), 2,
                            "outside unique quote", 0, "outside unique quote".length()))));
            WritingTask otherTask = WritingTask.create("Other", "Other", "");
            assertThrows(DataIntegrityViolationException.class, () -> citations.saveAll(List.of(
                    ClaimCitation.validated(otherTask.id(), version.id(), claim.id(), allowed.id(), 2,
                            "alpha unique supporting quote", 0, "alpha unique supporting quote".length()))));

            var restartedSections = new JdbcSectionVersionRepository(jdbc);
            var restartedClaims = new JdbcSectionClaimRepository(jdbc);
            var restartedCitations = new JdbcClaimCitationRepository(jdbc);
            SectionVersion restored = restartedSections.findById(version.id()).orElseThrow();
            SectionClaim restoredClaim = restartedClaims.findBySectionVersionId(version.id()).getFirst();
            ClaimCitation restoredCitation = restartedCitations.findBySectionVersionId(version.id()).getFirst();
            assertEquals(CitationValidationStatus.STRUCTURE_VALIDATED, restored.citationValidationStatus());
            assertEquals(1, restored.citationSchemaVersion());
            assertEquals(claim.startOffset(), restoredClaim.startOffset());
            assertEquals(claim.endOffset(), restoredClaim.endOffset());
            assertEquals(citation.evidenceId(), restoredCitation.evidenceId());
            assertEquals(citation.supportingQuote(), restoredCitation.supportingQuote());
            assertEquals(citation.evidenceStartOffset(), restoredCitation.evidenceStartOffset());
            assertEquals(citation.evidenceEndOffset(), restoredCitation.evidenceEndOffset());

            TaskRun rollbackRun = run(task, runs, TaskRunAction.GENERATE_SECTION, "section-rollback");
            SectionVersion rollbackVersion = SectionVersion.citationValidatedDraft(task.id(), outline.id(),
                    target.id(), rollbackRun.id(), 2, target.title(), "Rollback.[C1]",
                    WritingHashes.sha256("Rollback.[C1]"),
                    List.of(new SectionEvidenceReference(allowed.id(), 1)));
            SectionClaim rollbackClaim = SectionClaim.validated(task.id(), rollbackVersion.id(), "C1",
                    "Rollback.", 0, "Rollback.".length());
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            assertThrows(DataIntegrityViolationException.class, () -> template.executeWithoutResult(ignored -> {
                sections.save(rollbackVersion);
                claims.saveAll(List.of(rollbackClaim));
                citations.saveAll(List.of(ClaimCitation.validated(task.id(), rollbackVersion.id(),
                        rollbackClaim.id(), outsideContext.id(), 1, "outside unique quote", 0,
                        "outside unique quote".length())));
            }));
            assertFalse(sections.findById(rollbackVersion.id()).isPresent());
            assertEquals(0, claims.countBySectionVersionId(rollbackVersion.id()));
            assertEquals(0, citations.countBySectionVersionId(rollbackVersion.id()));
        } finally {
            cleanup(task);
        }
    }

    private TaskRun run(
            WritingTask task, JdbcTaskRunRepository runs, TaskRunAction action, String request) {
        TaskRun run = TaskRun.create(task.id(), action, request + "-" + task.id(),
                WritingHashes.sha256(request + task.id()), task.id() + "-" + request, request + " input");
        run.start();
        return runs.save(run);
    }

    private TaskEvidence evidence(WritingTask task, TaskRun run, String key, String content) {
        return new TaskEvidence(TaskEvidenceId.newId(), task.id(), run.id(),
                EvidenceSourceType.CLOUD_KNOWLEDGE, "doc-" + key, "ALMT", "3 Method", List.of(2),
                PageNumberingScheme.BAILIAN_RAW, "", content, WritingHashes.sha256(content),
                "chunk-" + key, WritingHashes.sha256(task.id() + key), 0.9, Instant.now());
    }

    private void cleanup(WritingTask task) {
        jdbc.update("DELETE FROM claim_citations WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM section_claims WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM section_version_evidence WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM section_versions WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM outline_sections WHERE outline_version_id IN "
                + "(SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", task.id().value());
        jdbc.update("DELETE FROM outline_version_evidence WHERE outline_version_id IN "
                + "(SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", task.id().value());
        jdbc.update("UPDATE writing_tasks SET confirmed_outline_version_id = NULL WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM outline_versions WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM writing_task_evidence WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM writing_task_runs WHERE task_id = ?", task.id().value());
        jdbc.update("DELETE FROM writing_tasks WHERE task_id = ?", task.id().value());
    }
}
