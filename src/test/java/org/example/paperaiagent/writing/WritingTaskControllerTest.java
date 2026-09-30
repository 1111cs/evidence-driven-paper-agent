package org.example.paperaiagent.writing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.paperaiagent.agent.runtime.AgentRunCommand;
import org.example.paperaiagent.agent.runtime.AgentRuntimeEvent;
import org.example.paperaiagent.agent.runtime.PaperAgentRuntime;
import org.example.paperaiagent.controller.WritingApiExceptionHandler;
import org.example.paperaiagent.controller.WritingTaskController;
import org.example.paperaiagent.writing.application.WritingTaskApplicationService;
import org.example.paperaiagent.writing.application.SectionWritingApplicationService;
import org.example.paperaiagent.writing.evidence.TaskEvidenceAdoptionService;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskEvidenceRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryTaskRunRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTaskRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryOutlineSectionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemorySectionVersionRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemorySectionClaimRepository;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryClaimCitationRepository;
import org.example.paperaiagent.writing.outline.OutlineContextSelector;
import org.example.paperaiagent.writing.outline.OutlineStructureExtractor;
import org.example.paperaiagent.writing.section.WritingContextBuilder;
import org.example.paperaiagent.writing.citation.CitationOutputParser;
import org.example.paperaiagent.writing.citation.CitationValidator;
import org.example.paperaiagent.writing.infrastructure.memory.InMemoryWritingTransactionOperations;
import org.example.paperaiagent.writing.workflow.StageCapabilityPolicy;
import org.example.paperaiagent.writing.workflow.WritingWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WritingTaskControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaperAgentRuntime runtime = new PaperAgentRuntime() {
            @Override
            public Flux<AgentRuntimeEvent> stream(AgentRunCommand command) {
                return Flux.just(new AgentRuntimeEvent(
                        1,
                        Instant.now(),
                        AgentRuntimeEvent.Type.RUN_COMPLETED,
                        null,
                        null,
                        Map.of("finalAnswer", "Research completed")
                ));
            }
        };
        var tasks = new InMemoryWritingTaskRepository();
        var runs = new InMemoryTaskRunRepository();
        var evidence = new InMemoryTaskEvidenceRepository();
        var outlines = new InMemoryOutlineVersionRepository();
        var outlineSections = new InMemoryOutlineSectionRepository();
        var sections = new InMemorySectionVersionRepository();
        var claims = new InMemorySectionClaimRepository();
        var citations = new InMemoryClaimCitationRepository();
        var workflow = new WritingWorkflow(new StageCapabilityPolicy());
        var transactions = new InMemoryWritingTransactionOperations();
        WritingTaskApplicationService service = new WritingTaskApplicationService(
                tasks,
                runs,
                evidence,
                outlines,
                outlineSections,
                new OutlineStructureExtractor(),
                new OutlineContextSelector(20, 30000),
                workflow,
                new TaskEvidenceAdoptionService(objectMapper),
                runtime,
                transactions
        );
        SectionWritingApplicationService sectionService = new SectionWritingApplicationService(
                tasks, runs, outlines, outlineSections, sections,
                claims, citations, evidence,
                new WritingContextBuilder(evidence, outlineSections, sections, 12, 20000, 8000),
                new CitationOutputParser(objectMapper), new CitationValidator(),
                workflow, runtime, transactions);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WritingTaskController(service, sectionService))
                .setControllerAdvice(new WritingApiExceptionHandler())
                .build();
    }

    @Test
    void exposesCreateResearchAndQueryApis() throws Exception {
        String created = mockMvc.perform(post("/writing/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"ALMT study","topic":"ALMT","requirements":"Use sources"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("CREATED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode createdJson = objectMapper.readTree(created);
        String taskId = createdJson.path("taskId").asText();

        String run = mockMvc.perform(post("/writing/tasks/{taskId}/research-runs", taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Research ALMT","requestId":"api-request-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.finalAnswer").value("Research completed"))
                .andReturn().getResponse().getContentAsString();
        String runId = objectMapper.readTree(run).path("runId").asText();

        mockMvc.perform(get("/writing/tasks/{taskId}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("RESEARCHING"));
        mockMvc.perform(get("/writing/runs/{runId}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("api-request-1"));
        mockMvc.perform(get("/writing/tasks/{taskId}/evidence", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
