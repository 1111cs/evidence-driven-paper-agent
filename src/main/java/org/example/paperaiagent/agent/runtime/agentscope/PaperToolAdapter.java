package org.example.paperaiagent.agent.runtime.agentscope;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.example.paperaiagent.tools.ArxivPdfDownloaderTool;
import org.example.paperaiagent.tools.ArxivSearchTool;
import org.example.paperaiagent.tools.PdfReaderTool;

import java.util.List;

public class PaperToolAdapter {

    private final ArxivSearchTool searchTool;
    private final ArxivPdfDownloaderTool downloaderTool;
    private final PdfReaderTool pdfReaderTool;

    public PaperToolAdapter(
            ArxivSearchTool searchTool,
            ArxivPdfDownloaderTool downloaderTool,
            PdfReaderTool pdfReaderTool
    ) {
        this.searchTool = searchTool;
        this.downloaderTool = downloaderTool;
        this.pdfReaderTool = pdfReaderTool;
    }

    @Tool(
            name = "arxiv_search",
            description = "Search arXiv for academic papers. The query must be in English.",
            readOnly = true
    )
    public List<ArxivSearchTool.ArxivPaperResult> search(
            @ToolParam(name = "query", description = "English arXiv search query") String query
    ) {
        return searchTool.searchPapers(query);
    }

    @Tool(
            name = "arxiv_download",
            description = "Download an arXiv PDF by paper ID and return its verified local path.",
            concurrencySafe = true
    )
    public String download(
            @ToolParam(name = "arxivId", description = "arXiv ID, for example 2005.12872") String arxivId
    ) {
        String result = downloaderTool.downloadArxivPaper(arxivId);
        if (result.startsWith("Error downloading PDF")) {
            throw new PaperToolException(result);
        }
        return result;
    }

    @Tool(
            name = "pdf_read",
            description = "Read and extract text from a local PDF returned by arxiv_download.",
            readOnly = true,
            concurrencySafe = true
    )
    public String readPdf(
            @ToolParam(name = "filePath", description = "Verified local PDF path") String filePath
    ) {
        String result = pdfReaderTool.readPdfContent(filePath);
        if (result.startsWith("读取PDF失败") || result.startsWith("错误：")) {
            throw new PaperToolException(result);
        }
        return result;
    }

    static final class PaperToolException extends RuntimeException {

        private PaperToolException(String message) {
            super(message);
        }
    }
}
