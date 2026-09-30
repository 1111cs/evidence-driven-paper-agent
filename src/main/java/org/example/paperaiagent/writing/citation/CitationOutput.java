package org.example.paperaiagent.writing.citation;

import java.util.List;

public record CitationOutput(String bodyMarkdown, List<ClaimOutput> claims) {
    public record ClaimOutput(String claimKey, String claimText, List<CitationOutputItem> citations) { }
    public record CitationOutputItem(String evidenceAlias, String supportingQuote) { }
}
