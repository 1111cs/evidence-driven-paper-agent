package org.example.paperaiagent.writing.citation;

public class CitationValidationException extends RuntimeException {
    private final String code;

    public CitationValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
