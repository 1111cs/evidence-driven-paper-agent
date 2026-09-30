package org.example.paperaiagent.controller;

import org.example.paperaiagent.writing.application.DocumentAssemblyApplicationService;
import org.example.paperaiagent.writing.application.dto.DocumentCitationResponse;
import org.example.paperaiagent.writing.application.dto.DocumentVersionResponse;
import org.example.paperaiagent.writing.application.dto.GenerateDocumentRequest;
import org.example.paperaiagent.writing.document.DocumentVersion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/writing")
public class WritingDocumentController {
    private static final MediaType MARKDOWN = MediaType.parseMediaType("text/markdown;charset=UTF-8");
    private final DocumentAssemblyApplicationService service;

    public WritingDocumentController(DocumentAssemblyApplicationService service) { this.service = service; }

    @PostMapping("/tasks/{taskId}/document-versions")
    public DocumentVersionResponse assemble(
            @PathVariable String taskId, @RequestBody GenerateDocumentRequest request) {
        return service.assemble(taskId, request);
    }

    @GetMapping("/tasks/{taskId}/document-versions")
    public List<DocumentVersionResponse> list(@PathVariable String taskId) { return service.list(taskId); }

    @GetMapping("/document-versions/{documentVersionId}")
    public DocumentVersionResponse get(@PathVariable String documentVersionId) { return service.get(documentVersionId); }

    @GetMapping("/document-versions/{documentVersionId}/citations")
    public List<DocumentCitationResponse> citations(@PathVariable String documentVersionId) {
        return service.citations(documentVersionId);
    }

    @GetMapping("/document-versions/{documentVersionId}/markdown")
    public ResponseEntity<byte[]> markdown(@PathVariable String documentVersionId) {
        DocumentVersion document = service.getDocument(documentVersionId);
        String filename = "document-v" + document.versionNumber() + ".md";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MARKDOWN);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers)
                .body(document.contentSnapshot().getBytes(StandardCharsets.UTF_8));
    }
}
