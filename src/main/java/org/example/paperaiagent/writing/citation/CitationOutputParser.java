package org.example.paperaiagent.writing.citation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CitationOutputParser {
    private static final Pattern JSON_FENCE = Pattern.compile(
            "(?is)^\\s*```(?:json)?\\s*\\R(.*)\\R```\\s*$");
    private static final Set<String> ROOT_FIELDS = Set.of("bodyMarkdown", "claims");
    private static final Set<String> CLAIM_FIELDS = Set.of("claimKey", "claimText", "citations");
    private static final Set<String> CITATION_FIELDS = Set.of("evidenceAlias", "supportingQuote");

    private final ObjectMapper objectMapper;

    public CitationOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CitationOutput parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("Model returned an empty citation output");
        }
        String json = unwrapFence(raw);
        try {
            JsonNode root = objectMapper.readTree(json);
            requireObject(root, "root");
            requireExactFields(root, ROOT_FIELDS, "root");
            String body = text(root, "bodyMarkdown", "root").strip();
            if (body.isBlank()) throw invalid("bodyMarkdown must not be blank");
            JsonNode claimNodes = root.get("claims");
            if (claimNodes == null || !claimNodes.isArray() || claimNodes.isEmpty()) {
                throw invalid("claims must be a non-empty array");
            }
            List<CitationOutput.ClaimOutput> claims = new ArrayList<>();
            for (int index = 0; index < claimNodes.size(); index++) {
                JsonNode claim = claimNodes.get(index);
                String path = "claims[" + index + "]";
                requireObject(claim, path);
                requireExactFields(claim, CLAIM_FIELDS, path);
                String claimKey = text(claim, "claimKey", path).trim();
                String claimText = text(claim, "claimText", path);
                JsonNode citationNodes = claim.get("citations");
                if (citationNodes == null || !citationNodes.isArray() || citationNodes.isEmpty()) {
                    throw invalid(path + ".citations must be a non-empty array");
                }
                List<CitationOutput.CitationOutputItem> citations = new ArrayList<>();
                for (int citationIndex = 0; citationIndex < citationNodes.size(); citationIndex++) {
                    JsonNode citation = citationNodes.get(citationIndex);
                    String citationPath = path + ".citations[" + citationIndex + "]";
                    requireObject(citation, citationPath);
                    requireExactFields(citation, CITATION_FIELDS, citationPath);
                    citations.add(new CitationOutput.CitationOutputItem(
                            text(citation, "evidenceAlias", citationPath).trim(),
                            text(citation, "supportingQuote", citationPath)));
                }
                claims.add(new CitationOutput.ClaimOutput(claimKey, claimText, List.copyOf(citations)));
            }
            return new CitationOutput(body, List.copyOf(claims));
        } catch (CitationValidationException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw invalid("Model output is not one complete JSON object: " + exception.getOriginalMessage());
        }
    }

    private static String unwrapFence(String raw) {
        String stripped = raw.strip();
        if (!stripped.startsWith("```")) return stripped;
        Matcher matcher = JSON_FENCE.matcher(stripped);
        if (!matcher.matches() || matcher.group(1).contains("```")) {
            throw invalid("Only one standard JSON code fence is accepted");
        }
        return matcher.group(1).strip();
    }

    private static void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) throw invalid(path + " must be an object");
    }

    private static void requireExactFields(JsonNode node, Set<String> expected, String path) {
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid(path + " fields must be exactly " + expected + ", found " + actual);
        }
    }

    private static String text(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw invalid(path + "." + field + " must be non-blank text");
        }
        return value.asText();
    }

    private static CitationValidationException invalid(String message) {
        return new CitationValidationException("CITATION_OUTPUT_INVALID", message);
    }
}
