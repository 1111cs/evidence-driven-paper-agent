package org.example.paperaiagent.writing.application;

public class TaskRunNotFoundException extends RuntimeException {

    public TaskRunNotFoundException(String runId) {
        super("Task run not found: " + runId);
    }
}
