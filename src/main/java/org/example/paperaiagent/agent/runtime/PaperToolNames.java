package org.example.paperaiagent.agent.runtime;

import java.util.Set;

public final class PaperToolNames {

    public static final String SEARCH_KNOWLEDGE = "search_knowledge";
    public static final String ARXIV_SEARCH = "arxiv_search";
    public static final String ARXIV_DOWNLOAD = "arxiv_download";
    public static final String PDF_READ = "pdf_read";

    public static final Set<String> RESEARCH_TOOLS = Set.of(
            SEARCH_KNOWLEDGE,
            ARXIV_SEARCH,
            ARXIV_DOWNLOAD,
            PDF_READ
    );

    private PaperToolNames() {
    }
}
