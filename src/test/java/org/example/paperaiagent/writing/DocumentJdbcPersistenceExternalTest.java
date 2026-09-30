package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.writing.application.*;
import org.example.paperaiagent.writing.application.dto.GenerateDocumentRequest;
import org.example.paperaiagent.writing.citation.*;
import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.evidence.*;
import org.example.paperaiagent.writing.outline.*;
import org.example.paperaiagent.writing.run.*;
import org.example.paperaiagent.writing.section.*;
import org.example.paperaiagent.writing.task.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Tag("jdbc")
@SpringBootTest(properties = {
        "paper.writing.repository-type=jdbc",
        "paper.agent.runtime=agentscope",
        "paper.knowledge.enabled=false"
})
class DocumentJdbcPersistenceExternalTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WritingTaskRepository tasks;
    @Autowired TaskRunRepository runs;
    @Autowired TaskEvidenceRepository evidence;
    @Autowired OutlineVersionRepository outlines;
    @Autowired OutlineSectionRepository outlineSections;
    @Autowired SectionVersionRepository sections;
    @Autowired SectionClaimRepository claims;
    @Autowired ClaimCitationRepository citations;
    @Autowired DocumentVersionRepository documents;
    @Autowired DocumentAssemblyRequestRepository requests;
    @Autowired DocumentVersionSectionRepository documentSections;
    @Autowired DocumentVersionClaimRepository documentClaims;
    @Autowired DocumentAssembler assembler;
    @Autowired WritingTransactionOperations transactions;

    @Test
    void persistsDocumentMappingsHandlesMultiInstanceRaceAndRollsBackInvalidChain() throws Exception {
        WritingTask task = buildTask();
        try {
            DocumentAssemblyApplicationService firstService = service();
            DocumentAssemblyApplicationService secondService = service();
            var executor = Executors.newFixedThreadPool(2);
            String firstId;
            String secondId;
            try {
                var first = executor.submit(() -> firstService.assemble(task.id().toString(),
                        new GenerateDocumentRequest("jdbc-request-A", DocumentFormat.MARKDOWN)));
                var second = executor.submit(() -> secondService.assemble(task.id().toString(),
                        new GenerateDocumentRequest("jdbc-request-B", DocumentFormat.MARKDOWN)));
                firstId = first.get().documentVersionId();
                secondId = second.get().documentVersionId();
            } finally {
                executor.shutdownNow();
            }
            assertEquals(firstId, secondId);
            DocumentVersionId documentId = DocumentVersionId.from(firstId);
            assertEquals(1, documents.findByTaskId(task.id()).size());
            assertEquals(2, requests.findByDocumentVersionId(documentId).size());
            assertEquals(2, documentSections.findByDocumentVersionId(documentId).size());
            assertEquals(2, documentClaims.findByDocumentVersionId(documentId).size());
            assertEquals(WritingStage.DOCUMENT_READY, tasks.findById(task.id()).orElseThrow().stage());

            DocumentVersion restored = new org.example.paperaiagent.writing.infrastructure.persistence
                    .JdbcDocumentVersionRepository(jdbc).findById(documentId).orElseThrow();
            assertEquals(documents.findById(documentId).orElseThrow().contentSnapshot(), restored.contentSnapshot());
            assertEquals(documents.findById(documentId).orElseThrow().contentHash(), restored.contentHash());
            assertEquals(documents.findById(documentId).orElseThrow().sourceFingerprint(), restored.sourceFingerprint());

            DocumentVersion rollback = DocumentVersion.create(task.id(), task.confirmedOutlineVersionId(), 2,
                    DocumentFormat.MARKDOWN, DocumentAssembler.ASSEMBLER_VERSION, "rollback\n",
                    WritingHashes.sha256("rollback\n"), WritingHashes.sha256("rollback-source-" + task.id()));
            assertThrows(DataIntegrityViolationException.class, () -> transactions.required(() -> {
                documents.save(rollback);
                documentSections.saveAll(List.of(new DocumentVersionSection(rollback.id(), task.id(),
                        task.confirmedOutlineVersionId(), OutlineSectionId.newId(), SectionVersionId.newId(), 1)));
                return null;
            }));
            assertTrue(documents.findById(rollback.id()).isEmpty());

            DocumentVersion persisted = documents.findById(documentId).orElseThrow();
            DocumentVersionSection validMapping = documentSections.findByDocumentVersionId(documentId).getFirst();
            assertThrows(DataIntegrityViolationException.class, () -> documentSections.saveAll(List.of(
                    new DocumentVersionSection(persisted.id(), task.id(), persisted.outlineVersionId(),
                            OutlineSectionId.newId(), validMapping.sectionVersionId(), 99))));
        } finally {
            cleanup(task.id());
        }
    }

    private DocumentAssemblyApplicationService service() {
        return new DocumentAssemblyApplicationService(tasks, outlines, outlineSections, sections, claims,
                citations, evidence, documents, requests, documentSections, documentClaims, assembler, transactions);
    }

    private WritingTask buildTask() {
        WritingTask task = WritingTask.create("Document JDBC", "Topic", "Requirements");
        tasks.save(task);
        task.startResearch();
        tasks.save(task);
        TaskRun research = run(task, TaskRunAction.RESEARCH, "research");
        TaskEvidence source = new TaskEvidence(TaskEvidenceId.newId(), task.id(), research.id(),
                EvidenceSourceType.CLOUD_KNOWLEDGE, "doc", "Source", "Section", List.of(1),
                PageNumberingScheme.BAILIAN_RAW, "1", "support quote", WritingHashes.sha256("support quote"),
                "candidate", WritingHashes.sha256("stable-" + task.id()), 0.9, Instant.now());
        evidence.saveAll(List.of(source));
        research.succeed("research", 1);
        runs.save(research);

        TaskRun outlineRun = run(task, TaskRunAction.GENERATE_OUTLINE, "outline");
        String outlineBody = "# Small\n## First\n## Second";
        OutlineVersion outline = OutlineVersion.draft(task.id(), outlineRun.id(), 1, "Small", outlineBody,
                WritingHashes.sha256(outlineBody), List.of(new OutlineEvidenceReference(source.id(), 1)));
        outlines.save(outline);
        List<OutlineSection> tree = outlineSections.saveAll(new OutlineStructureExtractor().extract(outline));
        outline.confirm();
        outlines.save(outline);
        outlineRun.succeed(outlineBody, 0);
        runs.save(outlineRun);
        task.markOutlinePending();
        tasks.save(task);
        task.confirmOutline(outline.id());
        tasks.save(task);
        task.startDrafting();
        tasks.save(task);

        for (OutlineSection section : tree.stream().filter(OutlineSection::writable).toList()) {
            TaskRun sectionRun = run(task, TaskRunAction.GENERATE_SECTION, "section-" + section.sectionKey());
            String claimText = "Claim for " + section.sectionKey() + ".";
            String body = claimText + "[C1]";
            SectionVersion version = SectionVersion.citationValidatedDraft(task.id(), outline.id(), section.id(),
                    sectionRun.id(), 1, section.title(), body, WritingHashes.sha256(body),
                    List.of(new SectionEvidenceReference(source.id(), 1)));
            sections.save(version);
            SectionClaim claim = SectionClaim.validated(task.id(), version.id(), "C1", claimText, 0, claimText.length());
            claims.saveAll(List.of(claim));
            citations.saveAll(List.of(ClaimCitation.validated(task.id(), version.id(), claim.id(), source.id(),
                    1, "support quote", 0, "support quote".length())));
            version.confirm();
            sections.save(version);
            sectionRun.succeed(body, 0);
            runs.save(sectionRun);
        }
        task.completeDraft();
        tasks.save(task);
        return task;
    }

    private TaskRun run(WritingTask task, TaskRunAction action, String key) {
        TaskRun run = TaskRun.create(task.id(), action, key + "-" + task.id(), WritingHashes.sha256(key + task.id()),
                task.id() + "-" + key, key);
        run.start();
        return runs.save(run);
    }

    private void cleanup(WritingTaskId taskId) {
        jdbc.update("DELETE FROM document_version_claims WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM document_version_sections WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM document_assembly_requests WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM document_versions WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM claim_citations WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM section_claims WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM section_version_evidence WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM section_versions WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM outline_sections WHERE outline_version_id IN (SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", taskId.value());
        jdbc.update("DELETE FROM outline_version_evidence WHERE outline_version_id IN (SELECT outline_version_id FROM outline_versions WHERE task_id = ?)", taskId.value());
        jdbc.update("UPDATE writing_tasks SET confirmed_outline_version_id = NULL WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM outline_versions WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM writing_task_evidence WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM writing_task_runs WHERE task_id = ?", taskId.value());
        jdbc.update("DELETE FROM writing_tasks WHERE task_id = ?", taskId.value());
    }
}
