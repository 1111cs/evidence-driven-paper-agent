package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.writing.application.WritingHashes;
import org.example.paperaiagent.writing.citation.CitationOutput;
import org.example.paperaiagent.writing.citation.CitationOutputParser;
import org.example.paperaiagent.writing.citation.CitationValidationException;
import org.example.paperaiagent.writing.citation.CitationValidator;
import org.example.paperaiagent.writing.citation.ValidatedCitationOutput;
import org.example.paperaiagent.writing.evidence.TaskEvidenceId;
import org.example.paperaiagent.writing.outline.OutlineEvidenceReference;
import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.outline.OutlineSectionId;
import org.example.paperaiagent.writing.outline.OutlineVersion;
import org.example.paperaiagent.writing.run.TaskRunId;
import org.example.paperaiagent.writing.section.EvidenceContext;
import org.example.paperaiagent.writing.section.WritingContext;
import org.example.paperaiagent.writing.task.WritingTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimCitationValidatorTest {
    private CitationOutputParser parser;
    private CitationValidator validator;
    private WritingContext context;
    private OutlineSection section;

    @BeforeEach
    void setUp() {
        parser = new CitationOutputParser(new ObjectMapper());
        validator = new CitationValidator();
        WritingTask task = WritingTask.create("Citation", "ALMT", "Use exact evidence");
        TaskEvidenceId first = TaskEvidenceId.newId();
        TaskEvidenceId second = TaskEvidenceId.newId();
        OutlineVersion outline = OutlineVersion.draft(task.id(), TaskRunId.newId(), 1,
                "Outline", "# Paper\n## Introduction", WritingHashes.sha256("outline"),
                List.of(new OutlineEvidenceReference(first, 1), new OutlineEvidenceReference(second, 2)));
        outline.confirm();
        section = new OutlineSection(OutlineSectionId.newId(), outline.id(), "s1.1", "s1",
                2, 2, "Introduction", "Explain ALMT", true);
        List<EvidenceContext> evidence = List.of(
                new EvidenceContext(first, task.id(), 1, "E1",
                        "alpha unique supporting quote. repeated phrase repeated phrase.",
                        "[evidenceAlias=E1] alpha unique supporting quote."),
                new EvidenceContext(second, task.id(), 2, "E2",
                        "beta unique supporting quote.",
                        "[evidenceAlias=E2] beta unique supporting quote."));
        context = new WritingContext(task, outline, section, evidence, List.of(), 100, 0);
    }

    @Test
    void parserAcceptsOneJsonObjectOrOneStandardFenceOnly() {
        String json = json("A claim.[C1]", "A claim.", "E1", "alpha unique supporting quote");
        assertEquals("A claim.[C1]", parser.parse(json).bodyMarkdown());
        assertEquals("A claim.[C1]", parser.parse("```json\n" + json + "\n```").bodyMarkdown());
        assertCode("CITATION_OUTPUT_INVALID", () -> parser.parse("prefix " + json));
        assertCode("CITATION_OUTPUT_INVALID", () -> parser.parse("{\"bodyMarkdown\":\"x\"}"));
    }

    @Test
    void validatesOffsetsAndAllowsOneClaimToUseMultipleEvidence() {
        CitationOutput output = new CitationOutput("A claim. [C1]", List.of(
                new CitationOutput.ClaimOutput("C1", "A claim.", List.of(
                        new CitationOutput.CitationOutputItem("E1", "alpha unique supporting quote"),
                        new CitationOutput.CitationOutputItem("E2", "beta unique supporting quote")))));

        ValidatedCitationOutput validated = validator.validate(output, context, section);

        assertEquals(0, validated.claims().getFirst().startOffset());
        assertEquals("A claim.".length(), validated.claims().getFirst().endOffset());
        assertEquals(2, validated.claims().getFirst().citations().size());
        assertEquals(0, validated.claims().getFirst().citations().getFirst().evidenceStartOffset());
    }

    @Test
    void allowsSameEvidenceForMultipleClaimsAndUnusedContextEvidence() {
        CitationOutput output = new CitationOutput("First.[C1]\nSecond.[C2]", List.of(
                claim("C1", "First.", "E1", "alpha unique supporting quote"),
                claim("C2", "Second.", "E1", "repeated phrase repeated phrase")));
        ValidatedCitationOutput validated = validator.validate(output, context, section);
        assertEquals(2, validated.claims().size());
        assertTrue(validated.claims().stream().flatMap(item -> item.citations().stream())
                .allMatch(item -> item.evidenceId().equals(context.evidence().getFirst().evidenceId())));
    }

    @Test
    void rejectsDuplicateClaimKeysAndMissingOrDuplicateMarkers() {
        CitationOutput duplicateClaim = new CitationOutput("First.[C1]", List.of(
                claim("C1", "First.", "E1", "alpha unique supporting quote"),
                claim("C1", "First.", "E1", "alpha unique supporting quote")));
        assertCode("CITATION_CLAIM_DUPLICATED", () -> validator.validate(duplicateClaim, context, section));

        assertCode("CITATION_MARKER_MISSING", () -> validator.validate(
                output("First.", "C1", "First.", "E1", "alpha unique supporting quote"), context, section));
        assertCode("CITATION_MARKER_DUPLICATED", () -> validator.validate(
                output("First.[C1] [C1]", "C1", "First.", "E1", "alpha unique supporting quote"),
                context, section));
    }

    @Test
    void rejectsOrphanedMarkerAndMarkerInsideCodeBlock() {
        assertCode("CITATION_MARKER_ORPHANED", () -> validator.validate(
                output("First.[C1]\nOther.[C2]", "C1", "First.", "E1",
                        "alpha unique supporting quote"), context, section));
        assertCode("CITATION_MARKER_IN_CODE_BLOCK", () -> validator.validate(
                output("First.[C1]\n```text\n[C2]\n```", "C1", "First.", "E1",
                        "alpha unique supporting quote"), context, section));
    }

    @Test
    void rejectsMissingAmbiguousOrNonAdjacentClaimText() {
        assertCode("CITATION_CLAIM_NOT_FOUND", () -> validator.validate(
                output("Other.[C1]", "C1", "Missing.", "E1", "alpha unique supporting quote"),
                context, section));
        assertCode("CITATION_CLAIM_AMBIGUOUS", () -> validator.validate(
                output("First. First.[C1]", "C1", "First.", "E1", "alpha unique supporting quote"),
                context, section));
        assertCode("CITATION_MARKER_NOT_ADJACENT", () -> validator.validate(
                output("First.\n\n[C1]", "C1", "First.", "E1", "alpha unique supporting quote"),
                context, section));
    }

    @Test
    void rejectsUnknownAliasAndTaskMismatchedContextEvidence() {
        assertCode("CITATION_EVIDENCE_ALIAS_UNKNOWN", () -> validator.validate(
                output("First.[C1]", "C1", "First.", "E9", "alpha unique supporting quote"),
                context, section));

        EvidenceContext wrongTask = new EvidenceContext(context.evidence().getFirst().evidenceId(),
                WritingTask.create("Other", "Other", "").id(), 1, "E1",
                "alpha unique supporting quote.", "[evidenceAlias=E1] alpha unique supporting quote.");
        WritingContext mismatched = new WritingContext(context.task(), context.confirmedOutline(), section,
                List.of(wrongTask), List.of(), 10, 0);
        assertCode("CITATION_EVIDENCE_TASK_MISMATCH", () -> validator.validate(
                output("First.[C1]", "C1", "First.", "E1", "alpha unique supporting quote"),
                mismatched, section));
    }

    @Test
    void rejectsMissingOrAmbiguousSupportingQuote() {
        assertCode("CITATION_SUPPORT_QUOTE_NOT_FOUND", () -> validator.validate(
                output("First.[C1]", "C1", "First.", "E1", "not present"), context, section));
        assertCode("CITATION_SUPPORT_QUOTE_AMBIGUOUS", () -> validator.validate(
                output("First.[C1]", "C1", "First.", "E1", "repeated phrase"), context, section));
    }

    @Test
    void enforcesSectionHeadingContractAndAllowsDeeperSubheading() {
        assertCode("SECTION_BODY_CONTAINS_OWN_HEADING", () -> validator.validate(
                output("## Introduction\nFirst.[C1]", "C1", "First.", "E1",
                        "alpha unique supporting quote"), context, section));
        assertCode("SECTION_BODY_HEADING_LEVEL_INVALID", () -> validator.validate(
                output("# Other\nFirst.[C1]", "C1", "First.", "E1",
                        "alpha unique supporting quote"), context, section));
        ValidatedCitationOutput valid = validator.validate(
                output("### Child\nFirst.[C1]", "C1", "First.", "E1",
                        "alpha unique supporting quote"), context, section);
        assertTrue(valid.bodyMarkdown().startsWith("### Child"));
    }

    private static CitationOutput output(
            String body, String key, String text, String alias, String quote) {
        return new CitationOutput(body, List.of(claim(key, text, alias, quote)));
    }

    private static CitationOutput.ClaimOutput claim(
            String key, String text, String alias, String quote) {
        return new CitationOutput.ClaimOutput(key, text,
                List.of(new CitationOutput.CitationOutputItem(alias, quote)));
    }

    private static String json(String body, String text, String alias, String quote) {
        return """
                {
                  "bodyMarkdown": "%s",
                  "claims": [{
                    "claimKey": "C1",
                    "claimText": "%s",
                    "citations": [{"evidenceAlias": "%s", "supportingQuote": "%s"}]
                  }]
                }
                """.formatted(body, text, alias, quote);
    }

    private static void assertCode(String code, Runnable action) {
        CitationValidationException exception = assertThrows(CitationValidationException.class, action::run);
        assertEquals(code, exception.code());
    }
}
