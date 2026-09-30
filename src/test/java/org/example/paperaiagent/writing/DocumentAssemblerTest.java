package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.citation.*;
import org.example.paperaiagent.writing.document.*;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.outline.*;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.section.*;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentAssemblerTest {
    private final DocumentAssembler assembler = new DocumentAssembler(new ObjectMapper());

    @Test
    void assemblesAllHeadingsButMapsOnlyWritableSectionsAndRenumbersClaims() {
        Fixture fixture = fixture();
        AssembledDocument result = assembler.assemble(fixture.source(), DocumentFormat.MARKDOWN);

        assertTrue(result.contentSnapshot().startsWith("# Paper\n\n## Introduction"));
        assertTrue(result.contentSnapshot().contains("Alpha.[1] Ten.[2]"));
        assertTrue(result.contentSnapshot().contains("## Method\n\n### Encoder\n\nSecond.[3]"));
        assertFalse(result.contentSnapshot().contains("[C1]"));
        assertFalse(result.contentSnapshot().contains("[C10]"));
        assertFalse(result.contentSnapshot().contains("planning-only"));
        assertEquals(4, result.outlineSectionCount());
        assertEquals(2, result.writableSectionCount());
        assertEquals(3, result.claimCount());
        assertEquals(2, result.sectionMappings().size());
        assertEquals(List.of(1, 2, 3), result.claimMappings().stream()
                .map(DocumentVersionClaimDraft::displayNumber).toList());
        assertEquals(WritingHashes.sha256(result.contentSnapshot()), result.contentHash());
    }

    @Test
    void canonicalFingerprintIsStableAndTracksObservableInputs() {
        Fixture fixture = fixture();
        AssembledDocument first = assembler.assemble(fixture.source(), DocumentFormat.MARKDOWN);
        AssembledDocument second = assembler.assemble(fixture.source(), DocumentFormat.MARKDOWN);
        assertEquals(first.sourceFingerprint(), second.sourceFingerprint());

        List<OutlineSection> changedSections = fixture.source().outlineSections().stream().map(section ->
                section.sectionKey().equals("s1.1")
                        ? new OutlineSection(section.id(), section.outlineVersionId(), section.sectionKey(),
                        section.parentSectionKey(), section.sequenceNumber(), section.depth(),
                        "Changed Introduction", section.objective(), section.writable())
                        : section).toList();
        DocumentAssemblySource changed = new DocumentAssemblySource(fixture.source().task(), fixture.source().outline(),
                changedSections, fixture.source().confirmedSections(), fixture.source().claims(), fixture.source().citations());
        assertNotEquals(first.sourceFingerprint(), assembler.assemble(changed, DocumentFormat.MARKDOWN).sourceFingerprint());

        SectionClaim firstClaim = fixture.claims().getFirst();
        ClaimCitation original = fixture.source().citations().get(firstClaim.id()).getFirst();
        ClaimCitation modified = new ClaimCitation(original.id(), original.taskId(), original.sectionVersionId(),
                original.claimId(), original.evidenceId(), original.sequenceNumber(), "changed quote",
                1, 14, original.createdAt());
        Map<SectionClaimId, List<ClaimCitation>> citations = new LinkedHashMap<>(fixture.source().citations());
        citations.put(firstClaim.id(), List.of(modified));
        DocumentAssemblySource changedCitation = new DocumentAssemblySource(fixture.source().task(),
                fixture.source().outline(), fixture.source().outlineSections(), fixture.source().confirmedSections(),
                fixture.source().claims(), citations);
        assertNotEquals(first.sourceFingerprint(),
                assembler.assemble(changedCitation, DocumentFormat.MARKDOWN).sourceFingerprint());
    }

    @Test
    void markerMustBeLocatedFromClaimEndOffset() {
        Fixture fixture = fixture();
        SectionClaim original = fixture.claims().getFirst();
        SectionClaim wrong = new SectionClaim(original.id(), original.taskId(), original.sectionVersionId(),
                original.claimKey(), original.claimText(), 1, original.endOffset() + 1,
                original.validationStatus(), original.createdAt());
        Map<SectionVersionId, List<SectionClaim>> claims = new LinkedHashMap<>(fixture.source().claims());
        List<SectionClaim> changed = new java.util.ArrayList<>(claims.get(original.sectionVersionId()));
        changed.set(0, wrong);
        claims.put(original.sectionVersionId(), changed);
        DocumentAssemblySource source = new DocumentAssemblySource(fixture.source().task(), fixture.source().outline(),
                fixture.source().outlineSections(), fixture.source().confirmedSections(), claims,
                fixture.source().citations());
        assertCode("DOCUMENT_CLAIM_MARKER_INVALID", () -> assembler.assemble(source, DocumentFormat.MARKDOWN));
    }

    @Test
    void missingOrLegacySectionIsRejected() {
        Fixture fixture = fixture();
        Map<OutlineSectionId, SectionVersion> missing = new LinkedHashMap<>(fixture.source().confirmedSections());
        missing.remove(fixture.secondSection().id());
        assertCode("DOCUMENT_SECTION_MISSING", () -> assembler.assemble(new DocumentAssemblySource(
                fixture.source().task(), fixture.source().outline(), fixture.source().outlineSections(), missing,
                fixture.source().claims(), fixture.source().citations()), DocumentFormat.MARKDOWN));

        SectionVersion valid = fixture.source().confirmedSections().get(fixture.secondSection().id());
        SectionVersion legacy = SectionVersion.restore(valid.id(), valid.taskId(), valid.outlineVersionId(),
                valid.outlineSectionId(), valid.generatedByRunId(), valid.versionNumber(), valid.lockVersion(),
                SectionVersionStatus.CONFIRMED, valid.sectionTitle(), valid.contentSnapshot(), valid.contentHash(),
                0, CitationValidationStatus.LEGACY_UNVALIDATED, valid.evidenceReferences(), valid.createdAt(), Instant.now());
        Map<OutlineSectionId, SectionVersion> legacyMap = new LinkedHashMap<>(fixture.source().confirmedSections());
        legacyMap.put(fixture.secondSection().id(), legacy);
        assertCode("DOCUMENT_CITATION_VALIDATION_REQUIRED", () -> assembler.assemble(new DocumentAssemblySource(
                fixture.source().task(), fixture.source().outline(), fixture.source().outlineSections(), legacyMap,
                fixture.source().claims(), fixture.source().citations()), DocumentFormat.MARKDOWN));
    }

    private Fixture fixture() {
        TaskEvidenceId evidenceId = TaskEvidenceId.newId();
        WritingTask task = WritingTask.create("Small paper", "Topic", "Requirements");
        task.startResearch();
        task.markOutlinePending();
        OutlineVersion outline = OutlineVersion.draft(task.id(), TaskRunId.newId(), 1, "Paper",
                "# Paper\n## Introduction\n## Method\n### Encoder", WritingHashes.sha256("outline"),
                List.of(new OutlineEvidenceReference(evidenceId, 1)));
        outline.confirm();
        task.confirmOutline(outline.id());
        task.startDrafting();
        task.completeDraft();
        OutlineSection root = section(outline, "s1", null, 1, 1, "Paper", null, false);
        OutlineSection intro = section(outline, "s1.1", "s1", 2, 2, "Introduction", "planning-only", true);
        OutlineSection method = section(outline, "s1.2", "s1", 3, 2, "Method", null, false);
        OutlineSection encoder = section(outline, "s1.2.1", "s1.2", 4, 3, "Encoder", null, true);
        SectionVersion first = confirmed(task, outline, intro, "Alpha.[C1] Ten.[C10]");
        SectionVersion second = confirmed(task, outline, encoder, "Second.[C1]");
        SectionClaim c1 = claim(task, first, "C1", "Alpha.");
        SectionClaim c10 = claim(task, first, "C10", "Ten.");
        SectionClaim c2 = claim(task, second, "C1", "Second.");
        List<SectionClaim> allClaims = List.of(c1, c10, c2);
        Map<SectionVersionId, List<SectionClaim>> claims = Map.of(first.id(), List.of(c1, c10), second.id(), List.of(c2));
        Map<SectionClaimId, List<ClaimCitation>> citations = new LinkedHashMap<>();
        allClaims.forEach(claim -> citations.put(claim.id(), List.of(ClaimCitation.validated(task.id(),
                claim.sectionVersionId(), claim.id(), evidenceId, 1, "support " + claim.claimKey(), 0, 5))));
        return new Fixture(new DocumentAssemblySource(task, outline, List.of(root, intro, method, encoder),
                Map.of(intro.id(), first, encoder.id(), second), claims, citations), intro, encoder, allClaims);
    }

    private static OutlineSection section(OutlineVersion outline, String key, String parent, int sequence,
                                          int depth, String title, String objective, boolean writable) {
        return new OutlineSection(OutlineSectionId.newId(), outline.id(), key, parent, sequence, depth,
                title, objective, writable);
    }
    private static SectionVersion confirmed(WritingTask task, OutlineVersion outline,
                                            OutlineSection section, String body) {
        SectionVersion version = SectionVersion.citationValidatedDraft(task.id(), outline.id(), section.id(),
                TaskRunId.newId(), 1, section.title(), body, WritingHashes.sha256(body), List.of());
        version.confirm();
        return version;
    }
    private static SectionClaim claim(WritingTask task, SectionVersion version, String key, String text) {
        int start = version.contentSnapshot().indexOf(text);
        return SectionClaim.validated(task.id(), version.id(), key, text, start, start + text.length());
    }
    private static void assertCode(String code, Runnable action) {
        DocumentAssemblyException exception = assertThrows(DocumentAssemblyException.class, action::run);
        assertEquals(code, exception.code());
    }
    private record Fixture(DocumentAssemblySource source, OutlineSection firstSection,
                           OutlineSection secondSection, List<SectionClaim> claims) { }
}
