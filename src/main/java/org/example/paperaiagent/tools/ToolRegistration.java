package org.example.paperaiagent.tools;

import org.example.paperaiagent.constant.FileConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class ToolRegistration {

    @Value("${paper.tools.arxiv-download.base-url:https://arxiv.org/pdf/}")
    private URI arxivBaseUri;

    @Value("${paper.tools.arxiv-download.connect-timeout:10s}")
    private Duration arxivConnectTimeout;

    @Value("${paper.tools.arxiv-download.read-timeout:60s}")
    private Duration arxivReadTimeout;

    @Value("${paper.tools.arxiv-download.max-pdf-size:50MB}")
    private DataSize arxivMaxPdfSize;

    @Value("${paper.tools.arxiv-download.max-redirects:5}")
    private int arxivMaxRedirects;

    @Bean
    public ArxivPdfDownloaderTool arxivPdfDownloaderTool() {
        return new ArxivPdfDownloaderTool(
                arxivBaseUri,
                Path.of(FileConstant.FILE_SAVE_DIR, "pdf"),
                arxivConnectTimeout,
                arxivReadTimeout,
                arxivMaxPdfSize.toBytes(),
                arxivMaxRedirects
        );
    }

    @Bean
    public PdfReaderTool pdfReaderTool() {
        return new PdfReaderTool();
    }

}
