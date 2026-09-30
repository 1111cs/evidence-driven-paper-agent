package org.example.paperaiagent.writing.outline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OutlineStructureExtractor {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern UNSUPPORTED_HEADING = Pattern.compile("^#{4,}\\s+.*$");

    public List<OutlineSection> extract(OutlineVersion outline) {
        List<Node> nodes = parse(outline.contentSnapshot());
        if (nodes.isEmpty()) {
            throw invalid("Outline contains no supported Markdown headings");
        }
        Set<String> parents = new HashSet<>();
        for (Node node : nodes) {
            if (node.parentKey != null) parents.add(node.parentKey);
        }
        List<OutlineSection> result = nodes.stream()
                .map(node -> new OutlineSection(
                        OutlineSectionId.newId(), outline.id(), node.key, node.parentKey,
                        node.sequence, node.depth, node.title, node.objective(), !parents.contains(node.key)))
                .toList();
        if (result.stream().noneMatch(OutlineSection::writable)) {
            throw invalid("Outline contains no writable leaf section");
        }
        return result;
    }

    private List<Node> parse(String markdown) {
        List<Node> nodes = new ArrayList<>();
        Node[] ancestors = new Node[4];
        int[] siblingCounters = new int[4];
        boolean inFence = false;
        String fenceMarker = null;
        Node current = null;
        boolean objectiveClosed = false;

        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
            String line = lines[lineNumber];
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String marker = trimmed.substring(0, 3);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker;
                } else if (marker.equals(fenceMarker)) {
                    inFence = false;
                    fenceMarker = null;
                }
                continue;
            }
            if (inFence) continue;
            if (UNSUPPORTED_HEADING.matcher(trimmed).matches()) {
                throw invalid("Unsupported heading depth at line " + (lineNumber + 1));
            }
            Matcher matcher = HEADING.matcher(trimmed);
            if (matcher.matches()) {
                int depth = matcher.group(1).length();
                String title = matcher.group(2).trim();
                if (title.isEmpty()) throw invalid("Blank heading at line " + (lineNumber + 1));
                if (nodes.isEmpty() && depth != 1) {
                    throw invalid("The first heading must have depth 1");
                }
                if (!nodes.isEmpty() && depth > current.depth + 1) {
                    throw invalid("Heading depth jumps at line " + (lineNumber + 1));
                }
                if (depth > 1 && ancestors[depth - 1] == null) {
                    throw invalid("Heading has no parent at line " + (lineNumber + 1));
                }
                siblingCounters[depth]++;
                for (int level = depth + 1; level < siblingCounters.length; level++) {
                    siblingCounters[level] = 0;
                    ancestors[level] = null;
                }
                StringBuilder key = new StringBuilder("s").append(siblingCounters[1]);
                for (int level = 2; level <= depth; level++) key.append('.').append(siblingCounters[level]);
                String parentKey = depth == 1 ? null : ancestors[depth - 1].key;
                current = new Node(key.toString(), parentKey, nodes.size() + 1, depth, title);
                nodes.add(current);
                ancestors[depth] = current;
                objectiveClosed = false;
                continue;
            }
            if (current == null || objectiveClosed) continue;
            if (trimmed.isEmpty()) {
                if (!current.objectiveLines.isEmpty()) objectiveClosed = true;
            } else {
                current.objectiveLines.add(trimmed);
            }
        }
        if (inFence) throw invalid("Unclosed Markdown code fence");
        return nodes;
    }

    private OutlineStructureInvalidException invalid(String message) {
        return new OutlineStructureInvalidException(message);
    }

    private static final class Node {
        private final String key;
        private final String parentKey;
        private final int sequence;
        private final int depth;
        private final String title;
        private final List<String> objectiveLines = new ArrayList<>();

        private Node(String key, String parentKey, int sequence, int depth, String title) {
            this.key = key;
            this.parentKey = parentKey;
            this.sequence = sequence;
            this.depth = depth;
            this.title = title;
        }

        private String objective() {
            return objectiveLines.isEmpty() ? null : String.join(" ", objectiveLines);
        }
    }
}
