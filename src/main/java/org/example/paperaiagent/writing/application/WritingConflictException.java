package org.example.paperaiagent.writing.application;

public class WritingConflictException extends RuntimeException {
    private final String code;

    public WritingConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
