package org.example.paperaiagent.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

final class CloudKnowledgeResponseParser {

    private final ObjectMapper objectMapper;

    CloudKnowledgeResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    KnowledgeSearchResult parse(String query, String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception exception) {
            throw malformed("Knowledge service returned invalid JSON", exception);
        }
        if (root == null || !root.isObject()) {
            throw malformed("Knowledge service response must be a JSON object");
        }
        if (root.has("success") && !root.path("success").asBoolean(false)) {
            String message = text(root, "message");
            throw new KnowledgeSearchException(
                    KnowledgeSearchErrorCode.REMOTE_ERROR,
                    message.isBlank() ? "Knowledge service reported an unsuccessful response" : message
            );
        }

        JsonNode data = root.get("data");
        if (data == null || !data.isObject()) {
            throw malformed("Knowledge service response is missing object data");
        }
        JsonNode nodes = data.get("nodes");
        if (nodes == null || !nodes.isArray()) {
            throw malformed("Knowledge service response is missing array data.nodes");
        }

        List<EvidenceCandidate> candidates = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            candidates.add(parseCandidate(nodes.get(index), index));
        }
        int total = data.has("total") && data.path("total").canConvertToInt()
                ? data.path("total").intValue()
                : candidates.size();
        if (total < 0) {
            throw malformed("Knowledge service data.total must not be negative");
        }
        return KnowledgeSearchResult.of(query, total, candidates);
    }

    private EvidenceCandidate parseCandidate(JsonNode node, int index) {
        if (node == null || !node.isObject()) {
            throw malformed("Knowledge result node " + index + " must be an object");
        }
        JsonNode metadata = node.get("metadata");
        if (metadata == null || !metadata.isObject()) {
            throw malformed("Knowledge result node " + index + " is missing metadata");
        }
        JsonNode scoreNode = node.get("score");
        if (scoreNode == null || !scoreNode.isNumber()) {
            throw malformed("Knowledge result node " + index + " is missing numeric score");
        }

        String content = firstText(text(node, "text"), text(metadata, "content"));
        String section = firstText(text(metadata, "title"), text(metadata, "hier_title"));
        try {
            return new EvidenceCandidate(
                    requiredText(metadata, "_id", index),
                    requiredText(metadata, "doc_id", index),
                    requiredText(metadata, "doc_name", index),
                    section,
                    parsePages(metadata.get("page_number"), index),
                    content,
                    scoreNode.doubleValue()
            );
        } catch (IllegalArgumentException exception) {
            throw malformed("Knowledge result node " + index + " is invalid: " + exception.getMessage(), exception);
        }
    }

    private List<Integer> parsePages(JsonNode pageNode, int index) {
        if (pageNode == null || pageNode.isNull()) {
            return List.of();
        }
        if (!pageNode.isArray()) {
            throw malformed("Knowledge result node " + index + " metadata.page_number must be an array");
        }
        List<Integer> pages = new ArrayList<>(pageNode.size());
        for (JsonNode page : pageNode) {
            if (!page.canConvertToInt()) {
                throw malformed("Knowledge result node " + index + " contains a non-integer page number");
            }
            pages.add(page.intValue());
        }
        return pages;
    }

    private String requiredText(JsonNode parent, String field, int index) {
        String value = text(parent, field);
        if (value.isBlank()) {
            throw malformed("Knowledge result node " + index + " is missing metadata." + field);
        }
        return value;
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        return value != null && value.isTextual() ? value.textValue().trim() : "";
    }

    private static String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static KnowledgeSearchException malformed(String message) {
        return new KnowledgeSearchException(KnowledgeSearchErrorCode.MALFORMED_RESPONSE, message);
    }

    private static KnowledgeSearchException malformed(String message, Throwable cause) {
        return new KnowledgeSearchException(KnowledgeSearchErrorCode.MALFORMED_RESPONSE, message, cause);
    }
}
