package org.example.paperaiagent.writing.document;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.citation.CitationValidationStatus;
import org.example.paperaiagent.writing.citation.ClaimCitation;
import org.example.paperaiagent.writing.citation.SectionClaim;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineStatus;
import org.example.paperaiagent.writing.section.SectionVersion;
import org.example.paperaiagent.writing.section.SectionVersionStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocumentAssembler {
    public static final String ASSEMBLER_VERSION = "document-assembler-v1";
    private static final Pattern MARKER = Pattern.compile("\\[(C[1-9]\\d*)]");
    private final ObjectMapper canonicalMapper;

    public DocumentAssembler(ObjectMapper objectMapper) {
        this.canonicalMapper = objectMapper.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public AssembledDocument assemble(DocumentAssemblySource source, DocumentFormat format) {
        requireSourceIdentity(source);
        List<OutlineSection> sections = source.outlineSections().stream()
                .sorted(Comparator.comparingInt(OutlineSection::sequenceNumber)
                        .thenComparing(item -> item.id().toString()))
                .toList();
        List<OutlineSection> writable = sections.stream().filter(OutlineSection::writable).toList();
        if (writable.isEmpty()) throw error("DOCUMENT_SECTION_MISSING", "The outline has no writable sections");

        List<String> blocks = new ArrayList<>();
        List<DocumentVersionSectionDraft> sectionMappings = new ArrayList<>();
        List<DocumentVersionClaimDraft> claimMappings = new ArrayList<>();
        int displayNumber = 1;

        for (OutlineSection section : sections) {
            String heading = "#".repeat(section.depth()) + " " + section.title();
            if (!section.writable()) {
                blocks.add(heading);
                continue;
            }
            SectionVersion version = source.confirmedSections().get(section.id());
            validateSection(source, section, version);
            List<SectionClaim> claims = source.claims().getOrDefault(version.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(SectionClaim::startOffset)
                            .thenComparing(item -> item.id().toString()))
                    .toList();
            if (claims.isEmpty()) {
                throw error("DOCUMENT_CITATION_VALIDATION_REQUIRED",
                        "Confirmed section has no validated claims: " + section.sectionKey());
            }
            for (SectionClaim claim : claims) validateClaimAndCitations(source, version, claim);

            ReplacementResult replaced = replaceMarkers(version.contentSnapshot(), claims, displayNumber);
            blocks.add(heading + "\n\n" + replaced.body());
            sectionMappings.add(new DocumentVersionSectionDraft(
                    section.id(), version.id(), section.sequenceNumber()));
            for (int index = 0; index < claims.size(); index++) {
                SectionClaim claim = claims.get(index);
                claimMappings.add(new DocumentVersionClaimDraft(
                        version.id(), claim.id(), displayNumber + index));
            }
            displayNumber += claims.size();
        }

        String content = String.join("\n\n", blocks) + "\n";
        String canonicalJson = canonicalJson(source, format, sections);
        return new AssembledDocument(content, WritingHashes.sha256(content), WritingHashes.sha256(canonicalJson),
                sectionMappings, claimMappings, sections.size(), writable.size(), claimMappings.size());
    }

    private static void requireSourceIdentity(DocumentAssemblySource source) {
        if (!source.outline().taskId().equals(source.task().id())) {
            throw error("DOCUMENT_OUTLINE_NOT_CONFIRMED", "Outline does not belong to the writing task");
        }
        if (!source.outline().id().equals(source.task().confirmedOutlineVersionId())
                || source.outline().status() != OutlineStatus.CONFIRMED) {
            throw error("DOCUMENT_OUTLINE_NOT_CONFIRMED", "The task outline is not confirmed");
        }
        for (OutlineSection section : source.outlineSections()) {
            if (!section.outlineVersionId().equals(source.outline().id())) {
                throw error("DOCUMENT_SECTION_OUTLINE_MISMATCH", "Outline section belongs to another outline");
            }
        }
    }

    private static void validateSection(
            DocumentAssemblySource source, OutlineSection outlineSection, SectionVersion version) {
        if (version == null || version.status() != SectionVersionStatus.CONFIRMED) {
            throw error("DOCUMENT_SECTION_MISSING", "Missing confirmed section: " + outlineSection.sectionKey());
        }
        if (!version.taskId().equals(source.task().id())) {
            throw error("DOCUMENT_SECTION_TASK_MISMATCH", "Section belongs to another task");
        }
        if (!version.outlineVersionId().equals(source.outline().id())
                || !version.outlineSectionId().equals(outlineSection.id())) {
            throw error("DOCUMENT_SECTION_OUTLINE_MISMATCH", "Section belongs to another outline node");
        }
        if (version.citationSchemaVersion() != 1
                || version.citationValidationStatus() != CitationValidationStatus.STRUCTURE_VALIDATED) {
            throw error("DOCUMENT_CITATION_VALIDATION_REQUIRED", "Section is not ClaimCitation V1 validated");
        }
        if (!WritingHashes.sha256(version.contentSnapshot()).equals(version.contentHash())) {
            throw error("DOCUMENT_CONTENT_HASH_MISMATCH", "Section content hash mismatch: " + version.id());
        }
    }

    private static void validateClaimAndCitations(
            DocumentAssemblySource source, SectionVersion version, SectionClaim claim) {
        if (!claim.taskId().equals(source.task().id()) || !claim.sectionVersionId().equals(version.id())
                || claim.validationStatus() != CitationValidationStatus.STRUCTURE_VALIDATED) {
            throw error("DOCUMENT_CITATION_VALIDATION_REQUIRED", "Claim ownership or status is invalid");
        }
        List<ClaimCitation> citations = source.citations().getOrDefault(claim.id(), List.of());
        if (citations.isEmpty()) {
            throw error("DOCUMENT_CITATION_VALIDATION_REQUIRED", "Claim has no citation: " + claim.claimKey());
        }
        int expectedSequence = 1;
        for (ClaimCitation citation : citations.stream()
                .sorted(Comparator.comparingInt(ClaimCitation::sequenceNumber)
                        .thenComparing(item -> item.id().toString())).toList()) {
            if (!citation.taskId().equals(source.task().id())
                    || !citation.sectionVersionId().equals(version.id())
                    || !citation.claimId().equals(claim.id())
                    || citation.sequenceNumber() != expectedSequence++) {
                throw error("DOCUMENT_CITATION_VALIDATION_REQUIRED", "Citation relation is incomplete");
            }
        }
    }

    private static ReplacementResult replaceMarkers(String body, List<SectionClaim> claims, int firstDisplay) {
        List<Replacement> replacements = new ArrayList<>();
        Set<Integer> expectedMarkerStarts = new HashSet<>();
        for (int index = 0; index < claims.size(); index++) {
            SectionClaim claim = claims.get(index);
            if (claim.startOffset() < 0 || claim.endOffset() > body.length()
                    || !body.substring(claim.startOffset(), claim.endOffset()).equals(claim.claimText())) {
                throw error("DOCUMENT_CLAIM_MARKER_INVALID", "Claim offsets do not match body: " + claim.claimKey());
            }
            int markerStart = claim.endOffset();
            while (markerStart < body.length()
                    && (body.charAt(markerStart) == ' ' || body.charAt(markerStart) == '\t')) markerStart++;
            String marker = "[" + claim.claimKey() + "]";
            if (!body.startsWith(marker, markerStart) || !expectedMarkerStarts.add(markerStart)) {
                throw error("DOCUMENT_CLAIM_MARKER_INVALID", "Claim marker is invalid: " + claim.claimKey());
            }
            replacements.add(new Replacement(markerStart, marker.length(), firstDisplay + index));
        }
        Matcher matcher = MARKER.matcher(body);
        int markerCount = 0;
        while (matcher.find()) {
            markerCount++;
            if (!expectedMarkerStarts.contains(matcher.start())) {
                throw error("DOCUMENT_CLAIM_MARKER_INVALID", "Body contains an orphan or misplaced marker");
            }
        }
        if (markerCount != claims.size()) {
            throw error("DOCUMENT_CLAIM_MARKER_INVALID", "Body marker count does not match claims");
        }
        StringBuilder rewritten = new StringBuilder(body);
        replacements.stream().sorted(Comparator.comparingInt(Replacement::start).reversed())
                .forEach(item -> rewritten.replace(item.start(), item.start() + item.length(),
                        "[" + item.displayNumber() + "]"));
        return new ReplacementResult(rewritten.toString());
    }

    private String canonicalJson(
            DocumentAssemblySource source, DocumentFormat format, List<OutlineSection> sections) {
        List<CanonicalOutlineSection> canonicalSections = sections.stream()
                .map(item -> new CanonicalOutlineSection(item.id().toString(), item.sequenceNumber(),
                        item.depth(), item.title(), item.writable()))
                .toList();
        List<CanonicalSectionVersion> canonicalVersions = new ArrayList<>();
        List<CanonicalClaim> canonicalClaims = new ArrayList<>();
        List<CanonicalCitation> canonicalCitations = new ArrayList<>();
        for (OutlineSection section : sections) {
            if (!section.writable()) continue;
            SectionVersion version = source.confirmedSections().get(section.id());
            if (version == null) continue;
            canonicalVersions.add(new CanonicalSectionVersion(version.id().toString(), section.id().toString(),
                    version.contentHash(), version.citationSchemaVersion(),
                    version.citationValidationStatus().name()));
            List<SectionClaim> claims = source.claims().getOrDefault(version.id(), List.of()).stream()
                    .sorted(Comparator.comparingInt(SectionClaim::startOffset)
                            .thenComparing(item -> item.id().toString())).toList();
            for (SectionClaim claim : claims) {
                canonicalClaims.add(new CanonicalClaim(claim.id().toString(), version.id().toString(),
                        claim.claimKey(), claim.claimText(), claim.startOffset(), claim.endOffset()));
                source.citations().getOrDefault(claim.id(), List.of()).stream()
                        .sorted(Comparator.comparingInt(ClaimCitation::sequenceNumber)
                                .thenComparing(item -> item.id().toString()))
                        .map(item -> new CanonicalCitation(item.id().toString(), claim.id().toString(),
                                item.evidenceId().toString(), item.sequenceNumber(), item.supportingQuote(),
                                item.evidenceStartOffset(), item.evidenceEndOffset()))
                        .forEach(canonicalCitations::add);
            }
        }
        CanonicalSource value = new CanonicalSource(1, ASSEMBLER_VERSION, format.name(),
                source.task().id().toString(), source.outline().id().toString(), canonicalSections,
                canonicalVersions, canonicalClaims, canonicalCitations);
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize canonical document source", exception);
        }
    }

    private static DocumentAssemblyException error(String code, String message) {
        return new DocumentAssemblyException(code, message);
    }

    private record Replacement(int start, int length, int displayNumber) { }
    private record ReplacementResult(String body) { }
    private record CanonicalSource(
            int schemaVersion, String assemblerVersion, String format, String taskId, String outlineVersionId,
            List<CanonicalOutlineSection> outlineSections, List<CanonicalSectionVersion> sectionVersions,
            List<CanonicalClaim> claims, List<CanonicalCitation> citations) { }
    private record CanonicalOutlineSection(
            String outlineSectionId, int sequenceNumber, int depth, String title, boolean writable) { }
    private record CanonicalSectionVersion(
            String sectionVersionId, String outlineSectionId, String contentHash,
            int citationSchemaVersion, String citationValidationStatus) { }
    private record CanonicalClaim(
            String claimId, String sectionVersionId, String claimKey, String claimText,
            int startOffset, int endOffset) { }
    private record CanonicalCitation(
            String citationId, String claimId, String evidenceId, int sequenceNumber,
            String supportingQuote, int evidenceStartOffset, int evidenceEndOffset) { }
}
