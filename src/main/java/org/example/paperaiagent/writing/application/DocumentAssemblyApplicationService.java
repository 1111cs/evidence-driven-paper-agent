package org.example.paperaiagent.writing.application;

import org.example.paperaiagent.writing.application.dto.*;
import org.example.paperaiagent.writing.citation.*;
import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.evidence.TaskEvidence;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.evidence.TaskEvidenceRepository;
import org.example.paperaiagent.writing.outline.*;
import org.example.paperaiagent.writing.section.*;
import org.example.paperaiagent.writing.task.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DocumentAssemblyApplicationService {
    private final WritingTaskRepository taskRepository;
    private final OutlineVersionRepository outlineRepository;
    private final OutlineSectionRepository outlineSectionRepository;
    private final SectionVersionRepository sectionVersionRepository;
    private final SectionClaimRepository claimRepository;
    private final ClaimCitationRepository citationRepository;
    private final TaskEvidenceRepository evidenceRepository;
    private final DocumentVersionRepository documentRepository;
    private final DocumentAssemblyRequestRepository requestRepository;
    private final DocumentVersionSectionRepository documentSectionRepository;
    private final DocumentVersionClaimRepository documentClaimRepository;
    private final DocumentAssembler assembler;
    private final WritingTransactionOperations transactions;
    private final ConcurrentMap<WritingTaskId, Object> taskLocks = new ConcurrentHashMap<>();

    public DocumentAssemblyApplicationService(
            WritingTaskRepository taskRepository, OutlineVersionRepository outlineRepository,
            OutlineSectionRepository outlineSectionRepository, SectionVersionRepository sectionVersionRepository,
            SectionClaimRepository claimRepository, ClaimCitationRepository citationRepository,
            TaskEvidenceRepository evidenceRepository, DocumentVersionRepository documentRepository,
            DocumentAssemblyRequestRepository requestRepository,
            DocumentVersionSectionRepository documentSectionRepository,
            DocumentVersionClaimRepository documentClaimRepository, DocumentAssembler assembler,
            WritingTransactionOperations transactions) {
        this.taskRepository = taskRepository;
        this.outlineRepository = outlineRepository;
        this.outlineSectionRepository = outlineSectionRepository;
        this.sectionVersionRepository = sectionVersionRepository;
        this.claimRepository = claimRepository;
        this.citationRepository = citationRepository;
        this.evidenceRepository = evidenceRepository;
        this.documentRepository = documentRepository;
        this.requestRepository = requestRepository;
        this.documentSectionRepository = documentSectionRepository;
        this.documentClaimRepository = documentClaimRepository;
        this.assembler = assembler;
        this.transactions = transactions;
    }

    public DocumentVersionResponse assemble(String taskIdValue, GenerateDocumentRequest request) {
        if (request == null) throw new IllegalArgumentException("request must not be null");
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        String requestId = requireText(request.requestId(), "requestId");
        DocumentFormat format = Objects.requireNonNull(request.format(), "format");
        String requestHash = requestHash(taskId, format);
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            DocumentVersion existingRequest = transactions.required(() -> resolveRequest(taskId, requestId, requestHash));
            if (existingRequest != null) return response(existingRequest);

            DocumentAssemblySource initialSource = transactions.required(() -> loadSource(findTask(taskId)));
            AssembledDocument initialAssembly = assembler.assemble(initialSource, format);
            try {
                DocumentVersion result = transactions.required(() -> commit(
                        taskId, requestId, requestHash, format, initialAssembly));
                return response(result);
            } catch (DataIntegrityViolationException conflict) {
                DocumentVersion recovered = transactions.required(() -> recoverUniqueConflict(
                        taskId, requestId, requestHash, initialAssembly.sourceFingerprint()));
                if (recovered != null) return response(recovered);
                throw conflict;
            }
        }
    }

    public List<DocumentVersionResponse> list(String taskIdValue) {
        WritingTaskId taskId = WritingTaskId.from(taskIdValue);
        findTask(taskId);
        return documentRepository.findByTaskId(taskId).stream().map(this::response).toList();
    }

    public DocumentVersionResponse get(String documentVersionIdValue) {
        return response(findDocument(DocumentVersionId.from(documentVersionIdValue)));
    }

    public DocumentVersion getDocument(String documentVersionIdValue) {
        return findDocument(DocumentVersionId.from(documentVersionIdValue));
    }

    public List<DocumentCitationResponse> citations(String documentVersionIdValue) {
        DocumentVersion document = findDocument(DocumentVersionId.from(documentVersionIdValue));
        List<DocumentVersionClaim> mappings = documentClaimRepository.findByDocumentVersionId(document.id());
        Map<SectionClaimId, SectionClaim> claims = new HashMap<>();
        Map<SectionClaimId, List<ClaimCitation>> citations = new HashMap<>();
        for (DocumentVersionSection section : documentSectionRepository.findByDocumentVersionId(document.id())) {
            for (SectionClaim claim : claimRepository.findBySectionVersionId(section.sectionVersionId())) {
                claims.put(claim.id(), claim);
                citationRepository.findBySectionVersionId(section.sectionVersionId()).stream()
                        .filter(item -> item.claimId().equals(claim.id()))
                        .sorted(Comparator.comparingInt(ClaimCitation::sequenceNumber))
                        .forEach(item -> citations.computeIfAbsent(claim.id(), ignored -> new ArrayList<>()).add(item));
            }
        }
        Map<TaskEvidenceId, TaskEvidence> evidence = new HashMap<>();
        evidenceRepository.findByTaskId(document.taskId()).forEach(item -> evidence.put(item.id(), item));
        List<DocumentCitationResponse> result = new ArrayList<>();
        for (DocumentVersionClaim mapping : mappings) {
            SectionClaim claim = claims.get(mapping.claimId());
            if (claim == null) throw new IllegalStateException("Document claim mapping is incomplete");
            List<DocumentCitationSourceResponse> sources = citations.getOrDefault(claim.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(ClaimCitation::sequenceNumber))
                    .map(item -> sourceResponse(item, evidence.get(item.evidenceId())))
                    .toList();
            result.add(new DocumentCitationResponse(mapping.displayNumber(), claim.id().toString(),
                    claim.claimKey(), claim.claimText(), sources));
        }
        return List.copyOf(result);
    }

    private DocumentVersion commit(
            WritingTaskId taskId, String requestId, String requestHash,
            DocumentFormat format, AssembledDocument initialAssembly) {
        WritingTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new WritingTaskNotFoundException(taskId.toString()));
        DocumentVersion idempotent = resolveRequest(taskId, requestId, requestHash);
        if (idempotent != null) return idempotent;
        requireAssemblyStage(task);
        DocumentAssemblySource currentSource = loadSource(task);
        AssembledDocument current = assembler.assemble(currentSource, format);
        if (!current.sourceFingerprint().equals(initialAssembly.sourceFingerprint())) {
            throw error("DOCUMENT_SOURCE_CONFLICT", "Document sources changed during assembly");
        }
        Optional<DocumentVersion> sameSource = documentRepository.findByTaskIdAndSourceFingerprint(
                taskId, current.sourceFingerprint());
        if (sameSource.isPresent()) {
            DocumentVersion existing = sameSource.get();
            requestRepository.save(DocumentAssemblyRequest.create(taskId, requestId, requestHash, existing.id()));
            moveToDocumentReady(task);
            return existing;
        }
        if (task.stage() == WritingStage.DOCUMENT_READY) {
            throw error("DOCUMENT_SOURCE_CONFLICT", "A different document source is already ready");
        }
        DocumentVersion document = DocumentVersion.create(task.id(), currentSource.outline().id(),
                documentRepository.nextVersionNumber(task.id()), format, DocumentAssembler.ASSEMBLER_VERSION,
                current.contentSnapshot(), current.contentHash(), current.sourceFingerprint());
        documentRepository.save(document);
        documentSectionRepository.saveAll(current.sectionMappings().stream()
                .map(item -> new DocumentVersionSection(document.id(), task.id(), currentSource.outline().id(),
                        item.outlineSectionId(), item.sectionVersionId(), item.sequenceNumber())).toList());
        documentClaimRepository.saveAll(current.claimMappings().stream()
                .map(item -> new DocumentVersionClaim(document.id(), task.id(), item.sectionVersionId(),
                        item.claimId(), item.displayNumber())).toList());
        requestRepository.save(DocumentAssemblyRequest.create(taskId, requestId, requestHash, document.id()));
        moveToDocumentReady(task);
        return document;
    }

    private DocumentVersion recoverUniqueConflict(
            WritingTaskId taskId, String requestId, String requestHash, String fingerprint) {
        DocumentVersion byRequest = resolveRequest(taskId, requestId, requestHash);
        if (byRequest != null) return byRequest;
        Optional<DocumentVersion> bySource = documentRepository.findByTaskIdAndSourceFingerprint(taskId, fingerprint);
        if (bySource.isEmpty()) return null;
        requestRepository.save(DocumentAssemblyRequest.create(taskId, requestId, requestHash, bySource.get().id()));
        return bySource.get();
    }

    private DocumentVersion resolveRequest(WritingTaskId taskId, String requestId, String requestHash) {
        Optional<DocumentAssemblyRequest> existing = requestRepository.findByTaskIdAndRequestId(taskId, requestId);
        if (existing.isEmpty()) return null;
        if (!existing.get().requestHash().equals(requestHash)) {
            throw error("DOCUMENT_IDEMPOTENCY_CONFLICT", "requestId was already used with different options");
        }
        return findDocument(existing.get().documentVersionId());
    }

    private DocumentAssemblySource loadSource(WritingTask task) {
        requireAssemblyStage(task);
        if (task.confirmedOutlineVersionId() == null) {
            throw error("DOCUMENT_OUTLINE_NOT_CONFIRMED", "Writing task has no confirmed outline");
        }
        OutlineVersion outline = outlineRepository.findById(task.confirmedOutlineVersionId())
                .orElseThrow(() -> error("DOCUMENT_OUTLINE_NOT_CONFIRMED", "Confirmed outline does not exist"));
        List<OutlineSection> sections = outlineSectionRepository.findByOutlineVersionId(outline.id());
        Map<OutlineSectionId, List<SectionVersion>> grouped = new HashMap<>();
        sectionVersionRepository.findConfirmedByOutlineVersionId(outline.id())
                .forEach(item -> grouped.computeIfAbsent(item.outlineSectionId(), ignored -> new ArrayList<>()).add(item));
        Map<OutlineSectionId, SectionVersion> confirmed = new LinkedHashMap<>();
        Map<SectionVersionId, List<SectionClaim>> claims = new LinkedHashMap<>();
        Map<SectionClaimId, List<ClaimCitation>> citations = new LinkedHashMap<>();
        for (OutlineSection section : sections.stream()
                .sorted(Comparator.comparingInt(OutlineSection::sequenceNumber)).toList()) {
            if (!section.writable()) continue;
            List<SectionVersion> versions = grouped.getOrDefault(section.id(), List.of());
            if (versions.isEmpty()) throw error("DOCUMENT_SECTION_MISSING", "Missing section " + section.sectionKey());
            if (versions.size() > 1) throw error("DOCUMENT_SECTION_MULTIPLE_CONFIRMED",
                    "Multiple confirmed versions for " + section.sectionKey());
            SectionVersion version = versions.getFirst();
            confirmed.put(section.id(), version);
            List<SectionClaim> sectionClaims = claimRepository.findBySectionVersionId(version.id());
            claims.put(version.id(), sectionClaims);
            List<ClaimCitation> sectionCitations = citationRepository.findBySectionVersionId(version.id());
            for (SectionClaim claim : sectionClaims) {
                citations.put(claim.id(), sectionCitations.stream()
                        .filter(item -> item.claimId().equals(claim.id()))
                        .sorted(Comparator.comparingInt(ClaimCitation::sequenceNumber)).toList());
            }
        }
        return new DocumentAssemblySource(task, outline, sections, confirmed, claims, citations);
    }

    private void moveToDocumentReady(WritingTask task) {
        if (task.stage() == WritingStage.DRAFT_COMPLETED) {
            task.markDocumentReady();
            taskRepository.save(task);
        }
    }

    private DocumentVersionResponse response(DocumentVersion document) {
        int sectionCount = documentSectionRepository.findByDocumentVersionId(document.id()).size();
        int claimCount = documentClaimRepository.findByDocumentVersionId(document.id()).size();
        int outlineCount = outlineSectionRepository.findByOutlineVersionId(document.outlineVersionId()).size();
        return DocumentVersionResponse.from(document, outlineCount, sectionCount, claimCount);
    }

    private static DocumentCitationSourceResponse sourceResponse(ClaimCitation citation, TaskEvidence evidence) {
        if (evidence == null) throw new IllegalStateException("Document citation evidence is missing");
        return new DocumentCitationSourceResponse(citation.id().toString(), evidence.id().toString(),
                evidence.documentName(), evidence.section(), evidence.sourcePages(), evidence.pageDisplayText(),
                citation.sequenceNumber(), citation.supportingQuote(), citation.evidenceStartOffset(),
                citation.evidenceEndOffset());
    }

    private WritingTask findTask(WritingTaskId id) {
        return taskRepository.findById(id).orElseThrow(() -> new WritingTaskNotFoundException(id.toString()));
    }
    private DocumentVersion findDocument(DocumentVersionId id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentVersionNotFoundException(id.toString()));
    }
    private static void requireAssemblyStage(WritingTask task) {
        if (task.stage() != WritingStage.DRAFT_COMPLETED && task.stage() != WritingStage.DOCUMENT_READY) {
            throw error("DOCUMENT_ASSEMBLY_NOT_ALLOWED", "Document assembly is not allowed in stage " + task.stage());
        }
    }
    private static String requestHash(WritingTaskId taskId, DocumentFormat format) {
        return WritingHashes.sha256("{\"format\":\"" + format.name() + "\",\"taskId\":\"" + taskId + "\"}");
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static DocumentAssemblyException error(String code, String message) {
        return new DocumentAssemblyException(code, message);
    }
}
