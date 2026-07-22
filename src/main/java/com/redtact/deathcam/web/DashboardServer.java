package com.redtact.deathcam.web;

import com.google.gson.Gson;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embedded local dashboard server. Serves the record list as JSON, clip files
 * with HTTP Range support (for video seeking), and static assets bundled in
 * the fat jar under classpath {@code /web/}. Binds to the loopback interface
 * only and runs entirely on daemon threads. JDK built-in
 * {@link com.sun.net.httpserver.HttpServer} only — no external server deps.
 */
public final class DashboardServer {

    private static final int BUFFER_SIZE = 64 * 1024;
    /** Effectively "all records" for a personal death database. */
    private static final int RECORD_FETCH_LIMIT = 500_000;

    private final DeathStore store;
    private final int requestedPort;
    private final Gson gson = new Gson();

    private HttpServer server;
    private ExecutorService executor;

    /** @param port TCP port to bind on loopback; 0 picks an ephemeral port. */
    public DashboardServer(DeathStore store, int port) {
        this.store = store;
        this.requestedPort = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), requestedPort), 0);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "deathcam-web-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        executor = Executors.newCachedThreadPool(factory);
        server.setExecutor(executor);
        // Single root context; dispatch() routes by path prefix.
        server.createContext("/", this::dispatch);
        server.start();
    }

    /** Actual bound port (differs from the constructor arg when it was 0). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + getPort() + "/";
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    private void dispatch(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/records")) {
                handleRecords(exchange);
            } else if (path.startsWith("/api/records/")) {
                handleDeleteRecord(exchange, path.substring("/api/records/".length()));
            } else if (path.equals("/api/openapi.yaml")) {
                handleStatic(exchange, "/openapi.yaml");     // this app's OpenAPI 3 spec
            } else if (path.equals("/api/mcsrranked.yaml")) {
                handleStatic(exchange, "/mcsrranked.yaml");  // vendored official MCSR Ranked spec
            } else if (path.equals("/api/docs") || path.equals("/api/docs/")) {
                handleStatic(exchange, "/swagger/index.html"); // self-contained Swagger UI
            } else if (path.startsWith("/media/clip/")) {
                handleClip(exchange, path.substring("/media/clip/".length()));
            } else {
                handleStatic(exchange, path);
            }
        } catch (Exception e) {
            // Never let a single bad request kill the server.
            e.printStackTrace();
            trySendError(exchange);
        } finally {
            exchange.close();
        }
    }

    private static void trySendError(HttpExchange exchange) {
        try {
            sendText(exchange, 500, "internal server error");
        } catch (Exception ignored) {
            // Headers were likely already sent; nothing more we can do.
        }
    }

    /**
     * Small text response. For HEAD requests the Content-Length header is set
     * explicitly and no body is sent (JDK honors a pre-set Content-Length when
     * the response length argument is -1).
     */
    private static void sendText(HttpExchange exchange, int status, String message)
            throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", "text/plain; charset=utf-8");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            h.set("Content-Length", Integer.toString(body.length));
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // ------------------------------------------------------------------
    // GET /api/records
    // ------------------------------------------------------------------

    private void handleRecords(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        List<DeathRecord> records = store.listRecent(RECORD_FETCH_LIMIT);
        byte[] body = gson.toJson(records).getBytes(StandardCharsets.UTF_8);
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    // ------------------------------------------------------------------
    // DELETE /api/records/{id}
    // ------------------------------------------------------------------

    /** Delete one death record entirely: its clip video, archived .rrf, and the DB row. */
    private void handleDeleteRecord(HttpExchange exchange, String idPart) throws IOException {
        if (!"DELETE".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "DELETE");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            sendText(exchange, 404, "not found");
            return;
        }
        DeathRecord record = store.listRecent(RECORD_FETCH_LIMIT).stream()
                .filter(r -> r.id == id)
                .findFirst()
                .orElse(null);
        if (record == null) {
            sendText(exchange, 404, "not found");
            return;
        }
        deleteFileQuietly(record.clipPath);
        deleteFileQuietly(record.rrfPath);
        store.delete(id);
        exchange.sendResponseHeaders(204, -1); // No Content; dispatch's finally closes the exchange
    }

    private static void deleteFileQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (Exception e) {
            System.err.println("[dashboard] could not delete " + path + ": " + e);
        }
    }

    // ------------------------------------------------------------------
    // GET/HEAD /media/clip/{id}
    // ------------------------------------------------------------------

    private void handleClip(HttpExchange exchange, String idPart) throws IOException {
        String method = exchange.getRequestMethod();
        boolean head = "HEAD".equals(method);
        if (!head && !"GET".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            sendText(exchange, 405, "method not allowed");
            return;
        }

        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException e) {
            sendText(exchange, 404, "not found");
            return;
        }

        DeathRecord record = store.listRecent(RECORD_FETCH_LIMIT).stream()
                .filter(r -> r.id == id)
                .findFirst()
                .orElse(null);
        if (record == null || record.clipPath == null) {
            sendText(exchange, 404, "not found");
            return;
        }
        Path file = Path.of(record.clipPath);
        if (!Files.isRegularFile(file)) {
            sendText(exchange, 404, "not found");
            return;
        }

        long total = Files.size(file);
        Headers h = exchange.getResponseHeaders();
        h.set("Accept-Ranges", "bytes");
        h.set("Content-Type", videoContentType(record.clipPath));

        String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
        if (rangeHeader == null) {
            if (head) {
                h.set("Content-Length", Long.toString(total));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            // Length 0 would mean "chunked" to HttpServer; use -1 for empty files.
            exchange.sendResponseHeaders(200, total == 0 ? -1 : total);
            if (total > 0) {
                try (OutputStream os = exchange.getResponseBody()) {
                    Files.copy(file, os);
                }
            }
            return;
        }

        long[] range = parseRange(rangeHeader, total);
        if (range == null) {
            h.set("Content-Range", "bytes */" + total);
            sendText(exchange, 416, "range not satisfiable");
            return;
        }
        long start = range[0];
        long end = range[1];
        long length = end - start + 1;
        h.set("Content-Range", "bytes " + start + "-" + end + "/" + total);
        if (head) {
            h.set("Content-Length", Long.toString(length));
            exchange.sendResponseHeaders(206, -1);
            return;
        }
        exchange.sendResponseHeaders(206, length);
        try (SeekableByteChannel ch = Files.newByteChannel(file, StandardOpenOption.READ);
             OutputStream os = exchange.getResponseBody()) {
            ch.position(start);
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE);
            long remaining = length;
            while (remaining > 0) {
                buf.clear();
                if (remaining < buf.capacity()) {
                    buf.limit((int) remaining);
                }
                int n = ch.read(buf);
                if (n < 0) {
                    break; // file truncated concurrently; stop early
                }
                os.write(buf.array(), 0, n);
                remaining -= n;
            }
        }
    }

    /**
     * Parses a Range header against a resource of {@code total} bytes.
     * Returns inclusive {start, end}, or null when invalid/unsatisfiable.
     * Only the first range of a multi-range request is honored.
     */
    private static long[] parseRange(String header, long total) {
        String spec = header.trim();
        if (!spec.toLowerCase(Locale.ROOT).startsWith("bytes=")) {
            return null;
        }
        spec = spec.substring("bytes=".length());
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            spec = spec.substring(0, comma);
        }
        spec = spec.trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (startPart.isEmpty()) {
                // Suffix form bytes=-N: last N bytes.
                if (endPart.isEmpty()) {
                    return null;
                }
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, total - suffix);
                end = total - 1;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? total - 1 : Long.parseLong(endPart);
                if (end > total - 1) {
                    end = total - 1; // clamp per RFC 9110
                }
            }
            if (start < 0 || start >= total || start > end) {
                return null;
            }
            return new long[] {start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String videoContentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".mp4") || p.endsWith(".m4v")) {
            return "video/mp4";
        }
        if (p.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (p.endsWith(".mkv")) {
            return "video/x-matroska";
        }
        if (p.endsWith(".webm")) {
            return "video/webm";
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // GET/HEAD /{static asset} from classpath /web/
    // ------------------------------------------------------------------

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod();
        boolean head = "HEAD".equals(method);
        if (!head && !"GET".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            sendText(exchange, 405, "method not allowed");
            return;
        }
        // getPath() has already percent-decoded, so encoded traversal
        // (%2e%2e, %5c) is caught here as well.
        if (path.contains("..") || path.contains("\\")) {
            sendText(exchange, 404, "not found");
            return;
        }
        String resource = path.equals("/") ? "/web/index.html" : "/web" + path;
        byte[] body;
        try (InputStream in = DashboardServer.class.getResourceAsStream(resource)) {
            if (in == null) {
                sendText(exchange, 404, "not found");
                return;
            }
            body = in.readAllBytes();
        }
        Headers h = exchange.getResponseHeaders();
        h.set("Content-Type", staticContentType(resource));
        h.set("Cache-Control", resource.endsWith(".html") ? "no-cache" : "max-age=3600");
        if (head) {
            h.set("Content-Length", Integer.toString(body.length));
            exchange.sendResponseHeaders(200, -1);
            return;
        }
        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static String staticContentType(String resource) {
        String p = resource.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (p.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (p.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (p.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        if (p.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (p.endsWith(".png")) {
            return "image/png";
        }
        if (p.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (p.endsWith(".woff2")) {
            return "font/woff2";
        }
        if (p.endsWith(".yaml") || p.endsWith(".yml")) {
            return "application/yaml; charset=utf-8";
        }
        return "application/octet-stream";
    }
}
