package org.example.paperaiagent.writing.application;

public class WritingTaskNotFoundException extends RuntimeException {

    public WritingTaskNotFoundException(String taskId) {
        super("Writing task not found: " + taskId);
    }
}
