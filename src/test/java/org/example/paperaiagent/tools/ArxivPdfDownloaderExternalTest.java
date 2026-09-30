package org.example.paperaiagent.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("external")
class ArxivPdfDownloaderExternalTest {

    @TempDir
    Path tempDirectory;

    @Test
    void downloadsRealPdfFromArxiv() {
        ArxivPdfDownloaderTool tool = new ArxivPdfDownloaderTool(
                URI.create("https://arxiv.org/pdf/"),
                tempDirectory,
                Duration.ofSeconds(15),
                Duration.ofSeconds(60),
                50L * 1024 * 1024,
                5
        );

        String result = tool.downloadArxivPaper("2208.13944");

        assertFalse(result.startsWith("Error downloading PDF"), result);
        assertTrue(Files.isRegularFile(Path.of(result)), result);
    }
}
