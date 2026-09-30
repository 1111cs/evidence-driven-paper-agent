package org.example.paperaiagent.writing.application;

public class OutlineSectionNotFoundException extends RuntimeException {
    public OutlineSectionNotFoundException(String sectionKey) {
        super("Outline section not found: " + sectionKey);
    }
}
