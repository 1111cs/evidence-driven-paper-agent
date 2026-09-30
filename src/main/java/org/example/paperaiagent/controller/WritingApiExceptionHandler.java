package org.example.paperaiagent.controller;

import org.example.paperaiagent.writing.application.ResearchRunConflictException;
import org.example.paperaiagent.writing.application.TaskRunNotFoundException;
import org.example.paperaiagent.writing.application.WritingTaskNotFoundException;
import org.example.paperaiagent.writing.application.OutlineNotFoundException;
import org.example.paperaiagent.writing.application.WritingConflictException;
import org.example.paperaiagent.writing.application.OutlineSectionNotFoundException;
import org.example.paperaiagent.writing.application.SectionVersionNotFoundException;
import org.example.paperaiagent.writing.application.DocumentVersionNotFoundException;
import org.example.paperaiagent.writing.document.DocumentAssemblyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {WritingTaskController.class, WritingDocumentController.class})
public class WritingApiExceptionHandler {

    @ExceptionHandler({WritingTaskNotFoundException.class, TaskRunNotFoundException.class,
            OutlineNotFoundException.class, OutlineSectionNotFoundException.class,
            SectionVersionNotFoundException.class, DocumentVersionNotFoundException.class})
    public ProblemDetail notFound(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({ResearchRunConflictException.class, IllegalStateException.class})
    public ProblemDetail conflict(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(WritingConflictException.class)
    public ProblemDetail writingConflict(WritingConflictException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setProperty("code", exception.code());
        return detail;
    }

    @ExceptionHandler(DocumentAssemblyException.class)
    public ProblemDetail documentConflict(DocumentAssemblyException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        detail.setProperty("code", exception.code());
        return detail;
    }

    @ExceptionHandler({IllegalArgumentException.class})
    public ProblemDetail badRequest(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
