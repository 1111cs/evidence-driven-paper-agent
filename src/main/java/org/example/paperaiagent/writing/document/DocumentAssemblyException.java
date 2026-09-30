package org.example.paperaiagent.writing.document;

public class DocumentAssemblyException extends RuntimeException {
    private final String code;

    public DocumentAssemblyException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
