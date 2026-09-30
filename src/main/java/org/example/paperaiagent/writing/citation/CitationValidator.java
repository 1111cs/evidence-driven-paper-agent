package org.example.paperaiagent.writing.citation;

import org.example.paperaiagent.writing.outline.OutlineSection;
import org.example.paperaiagent.writing.section.EvidenceContext;
import org.example.paperaiagent.writing.section.WritingContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CitationValidator {
    private static final Pattern CLAIM_KEY = Pattern.compile("C[1-9]\\d*");
    private static final Pattern MARKER = Pattern.compile("\\[(C[1-9]\\d*)]");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+?)[ \\t]*#*[ \\t]*$");

    public ValidatedCitationOutput validate(
            CitationOutput output, WritingContext context, OutlineSection targetSection) {
        String body = output.bodyMarkdown();
        ScanResult scan = scanBody(body, targetSection);

        Map<String, CitationOutput.ClaimOutput> claimsByKey = new LinkedHashMap<>();
        for (CitationOutput.ClaimOutput claim : output.claims()) {
            if (!CLAIM_KEY.matcher(claim.claimKey()).matches()) {
                throw error("CITATION_OUTPUT_INVALID", "Invalid claimKey: " + claim.claimKey());
            }
            if (claimsByKey.putIfAbsent(claim.claimKey(), claim) != null) {
                throw error("CITATION_CLAIM_DUPLICATED", "Duplicate claimKey: " + claim.claimKey());
            }
        }

        for (String markerKey : scan.markersByKey().keySet()) {
            if (!claimsByKey.containsKey(markerKey)) {
                throw error("CITATION_MARKER_ORPHANED", "Marker has no matching claim: " + markerKey);
            }
        }

        Map<String, EvidenceContext> evidenceByAlias = new HashMap<>();
        for (EvidenceContext item : context.evidence()) {
            evidenceByAlias.put(item.alias(), item);
        }

        List<ValidatedCitationOutput.ValidatedClaim> validatedClaims = new ArrayList<>();
        int citationCount = 0;
        for (CitationOutput.ClaimOutput claim : output.claims()) {
            List<MarkerLocation> markers = scan.markersByKey().getOrDefault(claim.claimKey(), List.of());
            if (markers.isEmpty()) {
                throw error("CITATION_MARKER_MISSING", "Missing marker [" + claim.claimKey() + "]");
            }
            if (markers.size() > 1) {
                throw error("CITATION_MARKER_DUPLICATED", "Marker appears more than once: " + claim.claimKey());
            }
            int occurrences = occurrenceCount(body, claim.claimText());
            if (occurrences == 0) {
                throw error("CITATION_CLAIM_NOT_FOUND", "claimText is not present in bodyMarkdown: " + claim.claimKey());
            }
            if (occurrences > 1) {
                throw error("CITATION_CLAIM_AMBIGUOUS", "claimText appears more than once: " + claim.claimKey());
            }
            MarkerLocation marker = markers.getFirst();
            int claimEnd = skipHorizontalWhitespaceBackward(body, marker.startOffset());
            int claimStart = claimEnd - claim.claimText().length();
            if (claimStart < 0 || !body.regionMatches(claimStart, claim.claimText(), 0, claim.claimText().length())) {
                throw error("CITATION_MARKER_NOT_ADJACENT",
                        "Marker must immediately follow claimText: " + claim.claimKey());
            }

            List<ValidatedCitationOutput.ValidatedCitation> citations = new ArrayList<>();
            int sequence = 1;
            for (CitationOutput.CitationOutputItem citation : claim.citations()) {
                EvidenceContext evidence = evidenceByAlias.get(citation.evidenceAlias());
                if (evidence == null) {
                    throw error("CITATION_EVIDENCE_ALIAS_UNKNOWN",
                            "Unknown evidenceAlias: " + citation.evidenceAlias());
                }
                if (!evidence.taskId().equals(context.task().id())) {
                    throw error("CITATION_EVIDENCE_TASK_MISMATCH",
                            "Evidence does not belong to the current task: " + citation.evidenceAlias());
                }
                int quoteOccurrences = occurrenceCount(evidence.contentSnapshot(), citation.supportingQuote());
                if (quoteOccurrences == 0) {
                    throw error("CITATION_SUPPORT_QUOTE_NOT_FOUND",
                            "supportingQuote is not in evidence " + citation.evidenceAlias());
                }
                if (quoteOccurrences > 1) {
                    throw error("CITATION_SUPPORT_QUOTE_AMBIGUOUS",
                            "supportingQuote occurs more than once in evidence " + citation.evidenceAlias());
                }
                int quoteStart = evidence.contentSnapshot().indexOf(citation.supportingQuote());
                citations.add(new ValidatedCitationOutput.ValidatedCitation(
                        evidence.evidenceId(), sequence++, citation.supportingQuote(), quoteStart,
                        quoteStart + citation.supportingQuote().length()));
                citationCount++;
            }
            validatedClaims.add(new ValidatedCitationOutput.ValidatedClaim(
                    claim.claimKey(), claim.claimText(), claimStart, claimEnd, List.copyOf(citations)));
        }
        if (validatedClaims.isEmpty() || citationCount == 0) {
            throw error("CITATION_OUTPUT_INVALID", "At least one claim and citation are required");
        }
        return new ValidatedCitationOutput(body, List.copyOf(validatedClaims));
    }

    private static ScanResult scanBody(String body, OutlineSection target) {
        Map<String, List<MarkerLocation>> markers = new LinkedHashMap<>();
        boolean inFence = false;
        char fenceCharacter = 0;
        int position = 0;
        while (position <= body.length()) {
            int newline = body.indexOf('\n', position);
            int lineEnd = newline < 0 ? body.length() : newline;
            String line = body.substring(position, lineEnd);
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
            String trimmed = line.stripLeading();
            boolean fenceLine = trimmed.startsWith("```") || trimmed.startsWith("~~~");
            if (fenceLine) {
                char current = trimmed.charAt(0);
                if (!inFence) {
                    inFence = true;
                    fenceCharacter = current;
                } else if (current == fenceCharacter) {
                    inFence = false;
                    fenceCharacter = 0;
                }
            } else {
                Matcher markerMatcher = MARKER.matcher(line);
                if (inFence && markerMatcher.find()) {
                    throw error("CITATION_MARKER_IN_CODE_BLOCK", "Citation marker is not allowed in a code block");
                }
                if (!inFence) {
                    Matcher headingMatcher = HEADING.matcher(line);
                    if (headingMatcher.matches()) {
                        int level = headingMatcher.group(1).length();
                        String title = headingMatcher.group(2).trim();
                        if (title.equals(target.title().trim())) {
                            throw error("SECTION_BODY_CONTAINS_OWN_HEADING",
                                    "bodyMarkdown repeats the current section heading");
                        }
                        if (level <= target.depth()) {
                            throw error("SECTION_BODY_HEADING_LEVEL_INVALID",
                                    "body heading level " + level + " must be deeper than section depth " + target.depth());
                        }
                    }
                    markerMatcher.reset();
                    while (markerMatcher.find()) {
                        String key = markerMatcher.group(1);
                        markers.computeIfAbsent(key, ignored -> new ArrayList<>())
                                .add(new MarkerLocation(key, position + markerMatcher.start(),
                                        position + markerMatcher.end()));
                    }
                }
            }
            if (newline < 0) break;
            position = newline + 1;
        }
        if (inFence) throw error("CITATION_OUTPUT_INVALID", "bodyMarkdown contains an unclosed code fence");
        return new ScanResult(markers);
    }

    private static int occurrenceCount(String text, String fragment) {
        if (fragment == null || fragment.isEmpty()) return 0;
        int count = 0;
        int from = 0;
        while (from <= text.length() - fragment.length()) {
            int found = text.indexOf(fragment, from);
            if (found < 0) break;
            count++;
            from = found + 1;
        }
        return count;
    }

    private static int skipHorizontalWhitespaceBackward(String body, int offset) {
        int current = offset;
        while (current > 0) {
            char character = body.charAt(current - 1);
            if (character != ' ' && character != '\t') break;
            current--;
        }
        return current;
    }

    private static CitationValidationException error(String code, String message) {
        return new CitationValidationException(code, message);
    }

    private record MarkerLocation(String key, int startOffset, int endOffset) { }
    private record ScanResult(Map<String, List<MarkerLocation>> markersByKey) { }
}
