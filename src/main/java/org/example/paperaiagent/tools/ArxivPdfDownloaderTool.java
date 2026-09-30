package org.example.paperaiagent.tools;

import org.example.paperaiagent.constant.FileConstant;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArxivPdfDownloaderTool {

    static final URI DEFAULT_BASE_URI = URI.create("https://arxiv.org/pdf/");
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(60);
    static final long DEFAULT_MAX_PDF_BYTES = 50L * 1024 * 1024;
    static final int DEFAULT_MAX_REDIRECTS = 5;

    private static final Pattern MODERN_ARXIV_ID = Pattern.compile("\\d{4,5}\\.\\d{5}(v\\d+)?");
    private static final Pattern VERSION_SUFFIX = Pattern.compile("v\\d+$", Pattern.CASE_INSENSITIVE);
    private static final byte[] PDF_SIGNATURE = new byte[]{'%', 'P', 'D', 'F', '-'};
    private static final String USER_AGENT =
            "EvidenceDrivenPaperAgent/1.0 (+https://github.com/1111cs/evidence-driven-paper-agent)";

    private final URI baseUri;
    private final Path downloadDirectory;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final long maxPdfBytes;
    private final int maxRedirects;

    public ArxivPdfDownloaderTool() {
        this(
                DEFAULT_BASE_URI,
                Path.of(FileConstant.FILE_SAVE_DIR, "pdf"),
                DEFAULT_CONNECT_TIMEOUT,
                DEFAULT_READ_TIMEOUT,
                DEFAULT_MAX_PDF_BYTES,
                DEFAULT_MAX_REDIRECTS
        );
    }

    ArxivPdfDownloaderTool(
            URI baseUri,
            Path downloadDirectory,
            Duration connectTimeout,
            Duration readTimeout,
            long maxPdfBytes,
            int maxRedirects
    ) {
        this.baseUri = requireHttpUri(baseUri);
        this.downloadDirectory = Objects.requireNonNull(downloadDirectory, "downloadDirectory").toAbsolutePath().normalize();
        this.connectTimeoutMillis = toTimeoutMillis(connectTimeout, "connectTimeout");
        this.readTimeoutMillis = toTimeoutMillis(readTimeout, "readTimeout");
        if (maxPdfBytes <= 0) {
            throw new IllegalArgumentException("maxPdfBytes must be greater than zero");
        }
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects must not be negative");
        }
        this.maxPdfBytes = maxPdfBytes;
        this.maxRedirects = maxRedirects;
    }

    public String downloadArxivPaper(String arxivId) {
        try {
            String paperId = cleanPaperId(arxivId);
            return downloadWithVersionFallback(paperId).toString();
        } catch (PdfDownloadException exception) {
            return errorResult(exception.code(), exception.getMessage());
        } catch (IllegalArgumentException exception) {
            return errorResult(DownloadErrorCode.INVALID_ARXIV_ID, exception.getMessage());
        } catch (Exception exception) {
            return errorResult(DownloadErrorCode.IO_ERROR, safeMessage(exception));
        }
    }

    private Path downloadWithVersionFallback(String paperId) {
        Path target = buildTargetPath(paperId);
        if (isReusablePdf(target)) {
            return target;
        }

        try {
            return download(buildPdfUri(paperId), target);
        } catch (PdfDownloadException exception) {
            Matcher versionMatcher = VERSION_SUFFIX.matcher(paperId);
            if (exception.code() != DownloadErrorCode.NOT_FOUND || !versionMatcher.find()) {
                throw exception;
            }
            String versionlessPaperId = versionMatcher.replaceFirst("");
            return download(buildPdfUri(versionlessPaperId), target);
        }
    }

    private Path download(URI sourceUri, Path target) {
        Path partFile = null;
        HttpURLConnection connection = null;
        try {
            ensureNotCancelled();
            Files.createDirectories(downloadDirectory);
            partFile = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".part");

            URI currentUri = sourceUri;
            for (int redirectCount = 0; ; redirectCount++) {
                ensureAllowedHost(currentUri);
                connection = openConnection(currentUri);
                int status = connection.getResponseCode();

                if (isRedirect(status)) {
                    if (redirectCount >= maxRedirects) {
                        throw failure(DownloadErrorCode.TOO_MANY_REDIRECTS,
                                "Too many redirects while downloading " + sourceUri);
                    }
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        throw failure(DownloadErrorCode.HTTP_ERROR,
                                "HTTP " + status + " response did not contain a Location header");
                    }
                    URI redirectedUri = currentUri.resolve(location);
                    connection.disconnect();
                    connection = null;
                    currentUri = redirectedUri;
                    continue;
                }

                validateStatus(status, currentUri);
                validateContentType(connection.getContentType());
                validateDeclaredSize(connection.getContentLengthLong());

                try (InputStream input = connection.getInputStream()) {
                    streamToPartFile(input, partFile);
                }
                validatePdfSignature(partFile);
                moveIntoPlace(partFile, target);
                partFile = null;
                return target;
            }
        } catch (SocketTimeoutException exception) {
            throw failure(DownloadErrorCode.TIMEOUT, "Timed out while downloading " + sourceUri, exception);
        } catch (PdfDownloadException exception) {
            throw exception;
        } catch (IOException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw failure(DownloadErrorCode.CANCELLED, "Download was cancelled", exception);
            }
            throw failure(DownloadErrorCode.IO_ERROR,
                    "I/O failure while downloading " + sourceUri + ": " + safeMessage(exception), exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            deleteQuietly(partFile);
        }
    }

    private HttpURLConnection openConnection(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/pdf");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private void streamToPartFile(InputStream input, Path partFile) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long totalBytes = 0;
        try (var output = Files.newOutputStream(partFile)) {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                ensureNotCancelled();
                totalBytes += bytesRead;
                if (totalBytes > maxPdfBytes) {
                    throw failure(DownloadErrorCode.TOO_LARGE,
                            "PDF exceeded the configured limit of " + maxPdfBytes + " bytes");
                }
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private void validateStatus(int status, URI uri) {
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            throw failure(DownloadErrorCode.NOT_FOUND, "arXiv PDF was not found: " + uri);
        }
        if (status < 200 || status >= 300) {
            throw failure(DownloadErrorCode.HTTP_ERROR, "HTTP " + status + " while downloading " + uri);
        }
    }

    private void validateContentType(String contentType) {
        String normalized = contentType == null
                ? ""
                : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!"application/pdf".equals(normalized)) {
            String actual = contentType == null || contentType.isBlank() ? "missing" : contentType;
            throw failure(DownloadErrorCode.INVALID_CONTENT_TYPE,
                    "Expected application/pdf but received " + actual);
        }
    }

    private void validateDeclaredSize(long contentLength) {
        if (contentLength > maxPdfBytes) {
            throw failure(DownloadErrorCode.TOO_LARGE,
                    "PDF Content-Length " + contentLength + " exceeded the configured limit of " + maxPdfBytes + " bytes");
        }
    }

    private void validatePdfSignature(Path file) throws IOException {
        if (Files.size(file) < PDF_SIGNATURE.length) {
            throw failure(DownloadErrorCode.INVALID_PDF, "Downloaded response is too short to be a PDF");
        }
        try (InputStream input = Files.newInputStream(file)) {
            for (byte expected : PDF_SIGNATURE) {
                if (input.read() != Byte.toUnsignedInt(expected)) {
                    throw failure(DownloadErrorCode.INVALID_PDF,
                            "Downloaded response does not start with the PDF signature");
                }
            }
        }
    }

    private boolean isReusablePdf(Path target) {
        try {
            if (!Files.isRegularFile(target)) {
                return false;
            }
            long size = Files.size(target);
            if (size < PDF_SIGNATURE.length || size > maxPdfBytes) {
                return false;
            }
            validatePdfSignature(target);
            return true;
        } catch (IOException | PdfDownloadException ignored) {
            return false;
        }
    }

    private void moveIntoPlace(Path partFile, Path target) throws IOException {
        try {
            Files.move(partFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(partFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensureAllowedHost(URI uri) {
        String host = uri.getHost();
        String baseHost = baseUri.getHost();
        boolean allowed = host != null && (host.equalsIgnoreCase(baseHost)
                || ("arxiv.org".equalsIgnoreCase(baseHost)
                && host.toLowerCase(Locale.ROOT).endsWith(".arxiv.org")));
        if (!allowed) {
            throw failure(DownloadErrorCode.UNSAFE_REDIRECT,
                    "Refused to download from redirected host: " + host);
        }
    }

    private void ensureNotCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw failure(DownloadErrorCode.CANCELLED, "Download was cancelled");
        }
    }

    private URI buildPdfUri(String paperId) {
        String base = baseUri.toString();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return URI.create(base + paperId);
    }

    private Path buildTargetPath(String paperId) {
        String safeFileName = paperId.replaceAll("[^a-zA-Z0-9.]", "_") + ".pdf";
        return downloadDirectory.resolve(safeFileName).normalize();
    }

    private String cleanPaperId(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("arXiv ID must not be blank");
        }
        String cleanId = input.trim()
                .replaceFirst("(?i)^arxiv:\\s*", "")
                .replaceFirst("(?i)^https?://(?:www\\.)?arxiv\\.org/(?:abs|pdf)/", "")
                .replaceFirst("(?i)\\.pdf$", "")
                .trim();
        if (!MODERN_ARXIV_ID.matcher(cleanId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid arXiv ID format. Expected format like '1908.05806v2', got: " + input);
        }
        return cleanId;
    }

    private static boolean isRedirect(int status) {
        return status == HttpURLConnection.HTTP_MOVED_PERM
                || status == HttpURLConnection.HTTP_MOVED_TEMP
                || status == HttpURLConnection.HTTP_SEE_OTHER
                || status == 307
                || status == 308;
    }

    private static URI requireHttpUri(URI uri) {
        Objects.requireNonNull(uri, "baseUri");
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("baseUri must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUri must contain a host");
        }
        return uri;
    }

    private static int toTimeoutMillis(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        long millis = duration.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be between 1 ms and " + Integer.MAX_VALUE + " ms");
        }
        return (int) millis;
    }

    private static String errorResult(DownloadErrorCode code, String message) {
        return "Error downloading PDF [" + code + "]: " + message;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static PdfDownloadException failure(DownloadErrorCode code, String message) {
        return new PdfDownloadException(code, message, null);
    }

    private static PdfDownloadException failure(DownloadErrorCode code, String message, Throwable cause) {
        return new PdfDownloadException(code, message, cause);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The original failure is more useful to the agent than a cleanup failure.
        }
    }

    public enum DownloadErrorCode {
        INVALID_ARXIV_ID,
        TIMEOUT,
        NOT_FOUND,
        INVALID_CONTENT_TYPE,
        TOO_LARGE,
        INVALID_PDF,
        TOO_MANY_REDIRECTS,
        UNSAFE_REDIRECT,
        CANCELLED,
        HTTP_ERROR,
        IO_ERROR
    }

    private static final class PdfDownloadException extends RuntimeException {

        private final DownloadErrorCode code;

        private PdfDownloadException(DownloadErrorCode code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        private DownloadErrorCode code() {
            return code;
        }
    }
}
