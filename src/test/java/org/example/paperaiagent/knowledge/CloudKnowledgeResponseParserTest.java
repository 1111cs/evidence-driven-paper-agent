package org.example.paperaiagent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudKnowledgeResponseParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CloudKnowledgeResponseParser parser = new CloudKnowledgeResponseParser(objectMapper);

    @Test
    void parsesSavedBailianFixtureIntoStableEvidenceCandidates() throws Exception {
        KnowledgeSearchResult result = parser.parse("ALMT解决了什么问题，核心模块是什么？", fixture());

        assertEquals(KnowledgeSearchResult.Status.RESULTS, result.status());
        assertEquals(58, result.total());
        assertEquals(5, result.candidates().size());

        EvidenceCandidate first = result.candidates().getFirst();
        assertEquals(
                "workspace-demo_index-demo_file-demo_0_2",
                first.chunkId()
        );
        assertEquals("file-demo", first.documentId());
        assertEquals("（4）ALMT", first.documentName());
        assertEquals("1Introduction", first.section());
        assertEquals(List.of(0, 1), first.pages());
        assertTrue(first.content().contains("Adaptive Hyper-modality Learning"));
        assertEquals(0.8418587446212769, first.score());

        String stableJson = objectMapper.writeValueAsString(result);
        assertFalse(stableJson.contains("doc_url"));
        assertFalse(stableJson.contains("file_path"));
        assertFalse(stableJson.contains("_pos_url"));
        assertFalse(stableJson.contains("oss-cn-beijing"));
    }

    @Test
    void treatsEmptyNodesAsSuccessfulNoResults() {
        KnowledgeSearchResult result = parser.parse(
                "not found",
                "{\"success\":true,\"data\":{\"total\":0,\"nodes\":[]}}"
        );

        assertEquals(KnowledgeSearchResult.Status.NO_RESULTS, result.status());
        assertEquals(0, result.total());
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void rejectsInvalidJsonAsMalformedResponse() {
        KnowledgeSearchException exception = assertThrows(
                KnowledgeSearchException.class,
                () -> parser.parse("query", "not-json")
        );

        assertEquals(KnowledgeSearchErrorCode.MALFORMED_RESPONSE, exception.code());
    }

    @Test
    void rejectsMissingNodesAsMalformedResponse() {
        KnowledgeSearchException exception = assertThrows(
                KnowledgeSearchException.class,
                () -> parser.parse("query", "{\"success\":true,\"data\":{\"total\":1}}")
        );

        assertEquals(KnowledgeSearchErrorCode.MALFORMED_RESPONSE, exception.code());
    }

    private String fixture() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/fixtures/bailian/knowledge-search-success.json"
        )) {
            if (input == null) {
                throw new IOException("Fixture was not found");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
