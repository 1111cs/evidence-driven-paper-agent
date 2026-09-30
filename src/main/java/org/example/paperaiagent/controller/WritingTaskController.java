package org.example.paperaiagent.controller;

import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.SectionWritingApplicationService;
import org.example.paperaiagent.writing.application.dto.CreateWritingTaskRequest;
import org.example.paperaiagent.writing.application.dto.GenerateOutlineRequest;
import org.example.paperaiagent.writing.application.dto.OutlineGenerationResponse;
import org.example.paperaiagent.writing.application.dto.OutlineVersionResponse;
import org.example.paperaiagent.writing.application.dto.ResearchRequest;
import org.example.paperaiagent.writing.application.dto.TaskEvidenceResponse;
import org.example.paperaiagent.writing.application.dto.TaskRunResponse;
import org.example.paperaiagent.writing.application.dto.WritingTaskResponse;
import org.example.paperaiagent.writing.application.dto.GenerateSectionRequest;
import org.example.paperaiagent.writing.application.dto.OutlineSectionResponse;
import org.example.paperaiagent.writing.application.dto.SectionGenerationResponse;
import org.example.paperaiagent.writing.application.dto.SectionVersionResponse;
import org.example.paperaiagent.writing.application.dto.SectionClaimResponse;
import org.example.paperaiagent.writing.application.dto.ClaimCitationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/writing")
public class WritingTaskController {

    private final WritingTaskApplicationService applicationService;
    private final SectionWritingApplicationService sectionApplicationService;

    public WritingTaskController(
            WritingTaskApplicationService applicationService,
            SectionWritingApplicationService sectionApplicationService) {
        this.applicationService = applicationService;
        this.sectionApplicationService = sectionApplicationService;
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public WritingTaskResponse createTask(@RequestBody CreateWritingTaskRequest request) {
        return applicationService.createTask(request);
    }

    @PostMapping("/tasks/{taskId}/research-runs")
    public TaskRunResponse startResearch(
            @PathVariable String taskId,
            @RequestBody(required = false) ResearchRequest request
    ) {
        return applicationService.startResearch(taskId, request);
    }

    @GetMapping("/tasks/{taskId}")
    public WritingTaskResponse getTask(@PathVariable String taskId) {
        return applicationService.getTask(taskId);
    }

    @GetMapping("/runs/{runId}")
    public TaskRunResponse getRun(@PathVariable String runId) {
        return applicationService.getRun(runId);
    }

    @GetMapping("/tasks/{taskId}/evidence")
    public List<TaskEvidenceResponse> listEvidence(@PathVariable String taskId) {
        return applicationService.listEvidence(taskId);
    }

    @PostMapping("/tasks/{taskId}/outline-versions")
    public OutlineGenerationResponse generateOutline(
            @PathVariable String taskId,
            @RequestBody GenerateOutlineRequest request
    ) {
        return applicationService.generateOutline(taskId, request);
    }

    @GetMapping("/tasks/{taskId}/outline-versions")
    public List<OutlineVersionResponse> listOutlineVersions(@PathVariable String taskId) {
        return applicationService.listOutlineVersions(taskId);
    }

    @GetMapping("/outline-versions/{outlineVersionId}")
    public OutlineVersionResponse getOutlineVersion(@PathVariable String outlineVersionId) {
        return applicationService.getOutlineVersion(outlineVersionId);
    }

    @PostMapping("/tasks/{taskId}/outline-versions/{outlineVersionId}/confirm")
    public OutlineVersionResponse confirmOutline(
            @PathVariable String taskId,
            @PathVariable String outlineVersionId
    ) {
        return applicationService.confirmOutline(taskId, outlineVersionId);
    }

    @GetMapping("/tasks/{taskId}/outline-sections")
    public List<OutlineSectionResponse> listOutlineSections(@PathVariable String taskId) {
        return sectionApplicationService.listOutlineSections(taskId);
    }

    @PostMapping("/tasks/{taskId}/sections/{sectionKey}/versions")
    public SectionGenerationResponse generateSection(
            @PathVariable String taskId,
            @PathVariable String sectionKey,
            @RequestBody GenerateSectionRequest request) {
        return sectionApplicationService.generateSection(taskId, sectionKey, request);
    }

    @GetMapping("/tasks/{taskId}/sections")
    public List<SectionVersionResponse> listSections(@PathVariable String taskId) {
        return sectionApplicationService.listSections(taskId);
    }

    @GetMapping("/tasks/{taskId}/sections/{sectionKey}/versions")
    public List<SectionVersionResponse> listSectionVersions(
            @PathVariable String taskId, @PathVariable String sectionKey) {
        return sectionApplicationService.listSectionVersions(taskId, sectionKey);
    }

    @GetMapping("/section-versions/{sectionVersionId}")
    public SectionVersionResponse getSectionVersion(@PathVariable String sectionVersionId) {
        return sectionApplicationService.getSectionVersion(sectionVersionId);
    }

    @GetMapping("/section-versions/{sectionVersionId}/claims")
    public List<SectionClaimResponse> listSectionClaims(@PathVariable String sectionVersionId) {
        return sectionApplicationService.listClaims(sectionVersionId);
    }

    @GetMapping("/section-versions/{sectionVersionId}/citations")
    public List<ClaimCitationResponse> listClaimCitations(@PathVariable String sectionVersionId) {
        return sectionApplicationService.listCitations(sectionVersionId);
    }

    @PostMapping("/tasks/{taskId}/sections/{sectionKey}/versions/{sectionVersionId}/confirm")
    public SectionVersionResponse confirmSection(
            @PathVariable String taskId,
            @PathVariable String sectionKey,
            @PathVariable String sectionVersionId) {
        return sectionApplicationService.confirmSection(taskId, sectionKey, sectionVersionId);
    }
}
