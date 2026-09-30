package org.example.paperaiagent.writing.application;

public class SectionVersionNotFoundException extends RuntimeException {
    public SectionVersionNotFoundException(String id) {
        super("Section version not found: " + id);
    }
}
