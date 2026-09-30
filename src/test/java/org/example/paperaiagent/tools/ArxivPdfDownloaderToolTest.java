package org.example.paperaiagent.tools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArxivPdfDownloaderToolTest {

    private static final byte[] VALID_PDF = "%PDF-1.7\nlocal test pdf".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path tempDirectory;

    private HttpServer server;
    private ExecutorService executor;
    private URI baseUri;
    private final AtomicInteger successRequests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);

        server.createContext("/pdf/2005.00001", exchange -> {
            successRequests.incrementAndGet();
            respond(exchange, 200, "application/pdf", VALID_PDF);
        });
        server.createContext("/pdf/2005.00002", exchange -> redirect(exchange, "/pdf/2005.00001"));
        server.createContext("/pdf/2005.00003", exchange -> respond(exchange, 404, "text/plain", "missing".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/pdf/2005.00004", exchange -> respond(exchange, 200, "text/html", "<html>not a pdf</html>".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/pdf/2005.00005", exchange -> respond(exchange, 200, "application/pdf", oversizedPayload()));
        server.createContext("/pdf/2005.00006", exchange -> respondChunked(exchange, "application/pdf", oversizedPayload()));
        server.createContext("/pdf/2005.00007", exchange -> respond(exchange, 200, "application/pdf", "definitely-not-pdf".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/pdf/2005.00008", this::respondSlowly);
        server.createContext("/pdf/2005.00009v2", exchange -> respond(exchange, 404, "text/plain", new byte[0]));
        server.createContext("/pdf/2005.00009", exchange -> respond(exchange, 200, "application/pdf", VALID_PDF));
        server.createContext("/pdf/2005.00010", exchange -> redirect(exchange, "http://example.com/paper.pdf"));
        server.createContext("/pdf/2005.00011", exchange -> redirect(exchange, "/pdf/2005.00011"));

        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/pdf/");
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        Thread.interrupted();
    }

    @Test
    void streamsPdfToTemporaryFileAndAtomicallyPublishesIt() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("arXiv:2005.00001");

        Path downloaded = Path.of(result);
        assertTrue(Files.isRegularFile(downloaded));
        assertArrayEquals(VALID_PDF, Files.readAllBytes(downloaded));
        assertFalse(hasPartFiles());
    }

    @Test
    void acceptsFullPdfUrlAndFollowsSameHostRedirect() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5)
                .downloadArxivPaper("https://arxiv.org/pdf/2005.00002.pdf");

        assertTrue(Files.isRegularFile(Path.of(result)));
        assertFalse(hasPartFiles());
    }

    @Test
    void reusesValidExistingPdfWithoutDownloadingAgain() {
        ArxivPdfDownloaderTool tool = tool(64, Duration.ofSeconds(1), 5);

        String first = tool.downloadArxivPaper("2005.00001");
        String second = tool.downloadArxivPaper("2005.00001");

        assertEquals(first, second);
        assertEquals(1, successRequests.get());
    }

    @Test
    void reportsNotFoundSeparately() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00003");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.NOT_FOUND);
        assertFalse(hasPartFiles());
    }

    @Test
    void rejectsUnexpectedContentType() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00004");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.INVALID_CONTENT_TYPE);
        assertFalse(hasPartFiles());
    }

    @Test
    void rejectsDeclaredPdfLargerThanConfiguredLimit() throws IOException {
        String result = tool(32, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00005");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.TOO_LARGE);
        assertFalse(hasPartFiles());
    }

    @Test
    void stopsChunkedDownloadWhenActualBytesExceedLimitAndCleansPartFile() throws IOException {
        String result = tool(32, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00006");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.TOO_LARGE);
        assertFalse(hasPartFiles());
    }

    @Test
    void rejectsBodyThatClaimsToBePdfButHasNoPdfSignature() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00007");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.INVALID_PDF);
        assertFalse(hasPartFiles());
    }

    @Test
    void reportsReadTimeoutAndCleansPartFile() throws IOException {
        String result = tool(64, Duration.ofMillis(50), 5).downloadArxivPaper("2005.00008");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.TIMEOUT);
        assertFalse(hasPartFiles());
    }

    @Test
    void retriesVersionlessIdOnlyAfterVersionReturns404() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00009v2");

        assertTrue(Files.isRegularFile(Path.of(result)));
        assertFalse(hasPartFiles());
    }

    @Test
    void rejectsRedirectToDifferentHost() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00010");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.UNSAFE_REDIRECT);
        assertFalse(hasPartFiles());
    }

    @Test
    void stopsAfterConfiguredRedirectLimit() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 2).downloadArxivPaper("2005.00011");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.TOO_MANY_REDIRECTS);
        assertFalse(hasPartFiles());
    }

    @Test
    void returnsCancelledWhenCallerThreadIsInterrupted() throws IOException {
        Thread.currentThread().interrupt();
        String result;
        try {
            result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("2005.00001");
        } finally {
            Thread.interrupted();
        }

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.CANCELLED);
        assertFalse(hasPartFiles());
    }

    @Test
    void rejectsInvalidArxivIdBeforeOpeningConnection() throws IOException {
        String result = tool(64, Duration.ofSeconds(1), 5).downloadArxivPaper("../../secrets");

        assertErrorCode(result, ArxivPdfDownloaderTool.DownloadErrorCode.INVALID_ARXIV_ID);
        assertFalse(hasPartFiles());
    }

    private ArxivPdfDownloaderTool tool(long maxBytes, Duration readTimeout, int maxRedirects) {
        return new ArxivPdfDownloaderTool(
                baseUri,
                tempDirectory.resolve("pdf"),
                Duration.ofSeconds(1),
                readTimeout,
                maxBytes,
                maxRedirects
        );
    }

    private byte[] oversizedPayload() {
        byte[] payload = new byte[80];
        System.arraycopy(VALID_PDF, 0, payload, 0, VALID_PDF.length);
        return payload;
    }

    private boolean hasPartFiles() throws IOException {
        Path directory = tempDirectory.resolve("pdf");
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.anyMatch(path -> path.getFileName().toString().endsWith(".part"));
        }
    }

    private void respondSlowly(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.sendResponseHeaders(200, 0);
        try {
            Thread.sleep(300);
            exchange.getResponseBody().write(VALID_PDF);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void respondChunked(HttpExchange exchange, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void assertErrorCode(String result, ArxivPdfDownloaderTool.DownloadErrorCode code) {
        assertTrue(result.startsWith("Error downloading PDF [" + code + "]"), result);
    }
}
