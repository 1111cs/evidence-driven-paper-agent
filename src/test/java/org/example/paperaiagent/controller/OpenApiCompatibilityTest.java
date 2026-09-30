package org.example.paperaiagent.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "paper.knowledge.enabled=false")
@AutoConfigureMockMvc
class OpenApiCompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocumentIncludesWritingTaskEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/writing/tasks")))
                .andExpect(content().string(containsString("/writing/tasks/{taskId}/research-runs")))
                .andExpect(content().string(containsString("/writing/tasks/{taskId}/outline-versions")))
                .andExpect(content().string(containsString(
                        "/writing/tasks/{taskId}/outline-versions/{outlineVersionId}/confirm")))
                .andExpect(content().string(containsString(
                        "/writing/tasks/{taskId}/sections/{sectionKey}/versions")))
                .andExpect(content().string(containsString(
                        "/writing/section-versions/{sectionVersionId}/citations")))
                .andExpect(content().string(containsString(
                        "/writing/tasks/{taskId}/document-versions")))
                .andExpect(content().string(containsString(
                        "/writing/document-versions/{documentVersionId}/markdown")));
    }

    @Test
    void knife4jDocumentPageIsAvailable() throws Exception {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk());
    }
}
