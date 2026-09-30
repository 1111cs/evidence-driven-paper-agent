package org.example.paperaiagent.knowledge;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public final class KnowledgeToolAdapter {

    private final KnowledgeSearchGateway client;

    public KnowledgeToolAdapter(KnowledgeSearchGateway client) {
        this.client = client;
    }

    @Tool(
            name = "search_knowledge",
            description = "Search the configured paper knowledge base. Returns evidence candidates with stable "
                    + "chunk and document IDs, document name, section, source page indexes, content, and score. "
                    + "Use these source fields when answering from the retrieved evidence.",
            readOnly = true,
            concurrencySafe = true
    )
    public KnowledgeSearchResult searchKnowledge(
            @ToolParam(name = "query", description = "Focused natural-language research query") String query
    ) {
        return client.search(query);
    }
}
