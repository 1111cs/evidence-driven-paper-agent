package org.example.paperaiagent.writing.application.dto;

import org.example.paperaiagent.writing.document.DocumentFormat;

public record GenerateDocumentRequest(String requestId, DocumentFormat format) { }
