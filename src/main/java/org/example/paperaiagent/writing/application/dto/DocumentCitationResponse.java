package org.example.paperaiagent.writing.application.dto;

import java.util.List;

public record DocumentCitationResponse(
        int displayNumber,
        String claimId,
        String claimKey,
        String claimText,
        List<DocumentCitationSourceResponse> sources
) {
    public DocumentCitationResponse { sources = List.copyOf(sources); }
}
