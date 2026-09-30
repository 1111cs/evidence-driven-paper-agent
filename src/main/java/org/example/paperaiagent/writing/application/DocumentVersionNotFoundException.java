package org.example.paperaiagent.writing.application;

public class DocumentVersionNotFoundException extends RuntimeException {
    public DocumentVersionNotFoundException(String id) {
        super("Document version not found: " + id);
    }
}
