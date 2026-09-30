package org.example.paperaiagent.writing.application;

public class OutlineNotFoundException extends RuntimeException {
    public OutlineNotFoundException(String id) {
        super("Outline version not found: " + id);
    }
}
