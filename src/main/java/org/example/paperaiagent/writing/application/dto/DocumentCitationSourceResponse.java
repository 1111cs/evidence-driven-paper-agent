package org.example.paperaiagent.writing.application.dto;

import java.util.List;

public record DocumentCitationSourceResponse(
        String citationId,
        String evidenceId,
        String documentName,
        String section,
        List<Integer> sourcePages,
        String pageDisplayText,
        int sequenceNumber,
        String supportingQuote,
        int evidenceStartOffset,
        int evidenceEndOffset
) {
    public DocumentCitationSourceResponse { sourcePages = List.copyOf(sourcePages); }
}
