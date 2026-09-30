package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.controller.WritingDocumentController;
import org.example.paperaiagent.writing.application.DocumentAssemblyApplicationService;
import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.application.dto.GenerateDocumentRequest;
import org.example.paperaiagent.writing.citation.*;
import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.evidence.*;
import org.example.paperaiagent.writing.infrastructure.memory.*;
import org.example.paperaiagent.writing.outline.*;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.section.*;
import org.example.paperaiagent.writing.task.*;
import org.example.paperaiagent.writing.workflow.StageCapabilityPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DocumentAssemblyApplicationServiceTest {
    private InMemoryWritingTaskRepository tasks;
    private InMemoryOutlineVersionRepository outlines;
    private InMemoryOutlineSectionRepository outlineSections;
    private InMemorySectionVersionRepository sections;
    private InMemorySectionClaimRepository claims;
    private InMemoryClaimCitationRepository citations;
    private InMemoryTaskEvidenceRepository evidence;
    private InMemoryDocumentVersionRepository documents;
    private InMemoryDocumentAssemblyRequestRepository requests;
    private InMemoryDocumentVersionSectionRepository documentSections;
    private InMemoryDocumentVersionClaimRepository documentClaims;
    private DocumentAssemblyApplicationService service;
    private WritingTask task;

    @BeforeEach
    void setUp() {
        tasks = new InMemoryWritingTaskRepository();
        outlines = new InMemoryOutlineVersionRepository();
        outlineSections = new InMemoryOutlineSectionRepository();
        sections = new InMemorySectionVersionRepository();
        claims = new InMemorySectionClaimRepository();
        citations = new InMemoryClaimCitationRepository();
        evidence = new InMemoryTaskEvidenceRepository();
        documents = new InMemoryDocumentVersionRepository();
        requests = new InMemoryDocumentAssemblyRequestRepository();
        documentSections = new InMemoryDocumentVersionSectionRepository();
        documentClaims = new InMemoryDocumentVersionClaimRepository();
        service = new DocumentAssemblyApplicationService(tasks, outlines, outlineSections, sections,
                claims, citations, evidence, documents, requests, documentSections, documentClaims,
                new DocumentAssembler(new ObjectMapper()), new InMemoryWritingTransactionOperations());
        task = persistCompleteTask();
    }

    @Test
    void requestAndSourceIdempotencyCreateOneDocumentAndTwoRequestMappings() {
        var first = service.assemble(task.id().toString(),
                new GenerateDocumentRequest("request-A", DocumentFormat.MARKDOWN));
        var repeated = service.assemble(task.id().toString(),
                new GenerateDocumentRequest("request-A", DocumentFormat.MARKDOWN));
        var secondRequest = service.assemble(task.id().toString(),
                new GenerateDocumentRequest("request-B", DocumentFormat.MARKDOWN));

        assertEquals(first.documentVersionId(), repeated.documentVersionId());
        assertEquals(first.documentVersionId(), secondRequest.documentVersionId());
        assertEquals(1, documents.findByTaskId(task.id()).size());
        assertEquals(2, requests.findByDocumentVersionId(DocumentVersionId.from(first.documentVersionId())).size());
        assertEquals(WritingStage.DOCUMENT_READY, tasks.findById(task.id()).orElseThrow().stage());
        assertEquals(2, first.sectionCount());
        assertEquals(2, first.claimCount());
    }

    @Test
    void existingRequestWithDifferentHashIsRejected() {
        var created = service.assemble(task.id().toString(),
                new GenerateDocumentRequest("request-A", DocumentFormat.MARKDOWN));
        requests.save(new DocumentAssemblyRequest(task.id(), "conflicting", "different-hash",
                DocumentVersionId.from(created.documentVersionId()), Instant.now()));
        DocumentAssemblyException exception = assertThrows(DocumentAssemblyException.class,
                () -> service.assemble(task.id().toString(),
                        new GenerateDocumentRequest("conflicting", DocumentFormat.MARKDOWN)));
        assertEquals("DOCUMENT_IDEMPOTENCY_CONFLICT", exception.code());
    }

    @Test
    void citationViewAndMarkdownDownloadAreDeterministic() {
        var created = service.assemble(task.id().toString(),
                new GenerateDocumentRequest("request-A", DocumentFormat.MARKDOWN));
        var view = service.citations(created.documentVersionId());
        assertEquals(List.of(1, 2), view.stream().map(item -> item.displayNumber()).toList());
        assertEquals("Source", view.getFirst().sources().getFirst().documentName());
        assertEquals("support quote", view.getFirst().sources().getFirst().supportingQuote());

        WritingDocumentController controller = new WritingDocumentController(service);
        var response = controller.markdown(created.documentVersionId());
        DocumentVersion persisted = service.getDocument(created.documentVersionId());
        assertArrayEquals(persisted.contentSnapshot().getBytes(StandardCharsets.UTF_8), response.getBody());
        assertEquals("text/markdown;charset=UTF-8", response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getContentDisposition().isAttachment());
    }

    @Test
    void incompleteTaskCannotBeAssembledAndDocumentReadyHasNoTools() {
        WritingTask incomplete = WritingTask.create("Incomplete", "Topic", "Requirements");
        tasks.save(incomplete);
        DocumentAssemblyException exception = assertThrows(DocumentAssemblyException.class,
                () -> service.assemble(incomplete.id().toString(),
                        new GenerateDocumentRequest("no", DocumentFormat.MARKDOWN)));
        assertEquals("DOCUMENT_ASSEMBLY_NOT_ALLOWED", exception.code());

        service.assemble(task.id().toString(), new GenerateDocumentRequest("yes", DocumentFormat.MARKDOWN));
        assertEquals(Set.of(), new StageCapabilityPolicy().allowedTools(WritingStage.DOCUMENT_READY));
    }

    private WritingTask persistCompleteTask() {
        TaskEvidenceId evidenceId = TaskEvidenceId.newId();
        WritingTask value = WritingTask.create("Small", "Topic", "Requirements");
        value.startResearch();
        value.markOutlinePending();
        OutlineVersion outline = OutlineVersion.draft(value.id(), TaskRunId.newId(), 1, "Small paper",
                "# Small paper\n## First\n## Second", WritingHashes.sha256("outline"),
                List.of(new OutlineEvidenceReference(evidenceId, 1)));
        outline.confirm();
        value.confirmOutline(outline.id());
        value.startDrafting();
        OutlineSection root = new OutlineSection(OutlineSectionId.newId(), outline.id(), "s1", null,
                1, 1, "Small paper", null, false);
        OutlineSection first = new OutlineSection(OutlineSectionId.newId(), outline.id(), "s1.1", "s1",
                2, 2, "First", "objective-one", true);
        OutlineSection second = new OutlineSection(OutlineSectionId.newId(), outline.id(), "s1.2", "s1",
                3, 2, "Second", "objective-two", true);
        SectionVersion firstVersion = confirmed(value, outline, first, "First claim.[C1]");
        SectionVersion secondVersion = confirmed(value, outline, second, "Second claim.[C1]");
        SectionClaim firstClaim = claim(value, firstVersion, "First claim.");
        SectionClaim secondClaim = claim(value, secondVersion, "Second claim.");
        ClaimCitation firstCitation = ClaimCitation.validated(value.id(), firstVersion.id(), firstClaim.id(),
                evidenceId, 1, "support quote", 0, "support quote".length());
        ClaimCitation secondCitation = ClaimCitation.validated(value.id(), secondVersion.id(), secondClaim.id(),
                evidenceId, 1, "support quote", 0, "support quote".length());
        value.completeDraft();
        tasks.save(value);
        outlines.save(outline);
        outlineSections.saveAll(List.of(root, first, second));
        sections.save(firstVersion);
        sections.save(secondVersion);
        claims.saveAll(List.of(firstClaim, secondClaim));
        citations.saveAll(List.of(firstCitation, secondCitation));
        evidence.saveAll(List.of(new TaskEvidence(evidenceId, value.id(), TaskRunId.newId(),
                EvidenceSourceType.CLOUD_KNOWLEDGE, "doc", "Source", "Section", List.of(1),
                PageNumberingScheme.BAILIAN_RAW, "1", "support quote",
                WritingHashes.sha256("support quote"), "candidate", "stable-" + value.id(), 0.9, Instant.now())));
        return value;
    }

    private static SectionVersion confirmed(WritingTask task, OutlineVersion outline,
                                            OutlineSection section, String body) {
        SectionVersion version = SectionVersion.citationValidatedDraft(task.id(), outline.id(), section.id(),
                TaskRunId.newId(), 1, section.title(), body, WritingHashes.sha256(body), List.of());
        version.confirm();
        return version;
    }
    private static SectionClaim claim(WritingTask task, SectionVersion version, String text) {
        int start = version.contentSnapshot().indexOf(text);
        return SectionClaim.validated(task.id(), version.id(), "C1", text, start, start + text.length());
    }
}
