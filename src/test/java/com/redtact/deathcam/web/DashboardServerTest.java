package com.redtact.deathcam.web;

import com.google.gson.Gson;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.store.SqliteDeathStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises DashboardServer over real loopback HTTP with a real SQLite store. */
class DashboardServerTest {

    private static final byte[] CLIP_BYTES = buildClipBytes();

    private static byte[] buildClipBytes() {
        // 1000 known bytes; deterministic non-trivial pattern so slice
        // assertions catch off-by-one errors.
        byte[] b = new byte[1000];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (i * 31 + 7);
        }
        return b;
    }

    @TempDir
    Path tempDir;

    private SqliteDeathStore store;
    private DashboardServer server;
    private HttpClient client;
    private long clipId;
    private long noClipId;
    private String clipPath;

    @BeforeEach
    void setUp() throws Exception {
        Path clipFile = tempDir.resolve("death-clip.mp4");
        Files.write(clipFile, CLIP_BYTES);
        clipPath = clipFile.toString();

        store = new SqliteDeathStore(tempDir.resolve("deaths.db"));

        DeathRecord withClip = new DeathRecord();
        withClip.worldName = "mcsrranked #PZNbqLvSs";
        withClip.detectedAtMillis = 1783140992277L;
        withClip.cause = "SLAIN";
        withClip.killer = "Blaze";
        withClip.rawMessage = "Taku128n64 was slain by Blaze";
        withClip.phase = "FORTRESS";
        withClip.hungerReset = true;
        withClip.clipPath = clipPath;
        clipId = store.insert(withClip).id;

        DeathRecord noClip = new DeathRecord();
        noClip.worldName = "mcsrranked #other";
        noClip.detectedAtMillis = 1783140999999L;
        noClip.cause = "FALL";
        noClip.rawMessage = "Taku128n64 fell from a high place";
        noClip.phase = "OVERWORLD";
        noClipId = store.insert(noClip).id;

        server = new DashboardServer(store, 0);
        server.start();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (store != null) {
            store.close();
        }
    }

    /** GET {@code server.url() + pathAndQuery} with optional header pairs. */
    private HttpResponse<byte[]> get(String pathAndQuery, String... headerPairs) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(server.url() + pathAndQuery)).GET();
        for (int i = 0; i < headerPairs.length; i += 2) {
            b.header(headerPairs[i], headerPairs[i + 1]);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    // ------------------------------------------------------------------
    // /api/records
    // ------------------------------------------------------------------

    @Test
    void apiRecordsReturnsAllRecordsAsJson() throws Exception {
        HttpResponse<byte[]> resp = get("api/records");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.headers().firstValue("Content-Type").orElse("")
                .startsWith("application/json"));
        assertEquals("no-store", resp.headers().firstValue("Cache-Control").orElse(""));

        String json = new String(resp.body(), StandardCharsets.UTF_8);
        DeathRecord[] records = new Gson().fromJson(json, DeathRecord[].class);
        assertEquals(2, records.length);

        DeathRecord withClip = Arrays.stream(records)
                .filter(r -> r.id == clipId).findFirst().orElseThrow();
        assertEquals("SLAIN", withClip.cause);
        assertEquals("Blaze", withClip.killer);
        assertEquals("FORTRESS", withClip.phase);
        assertTrue(withClip.hungerReset);
        assertEquals(clipPath, withClip.clipPath);

        DeathRecord noClip = Arrays.stream(records)
                .filter(r -> r.id == noClipId).findFirst().orElseThrow();
        assertEquals("FALL", noClip.cause);
        assertFalse(noClip.hungerReset);
        assertNull(noClip.clipPath);
    }

    @Test
    void postApiRecordsIsRejectedWith405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(server.url() + "api/records"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(405, resp.statusCode());
    }

    // ------------------------------------------------------------------
    // /media/clip/{id}
    // ------------------------------------------------------------------

    @Test
    void fullClipDownloadReturnsWholeFile() throws Exception {
        HttpResponse<byte[]> resp = get("media/clip/" + clipId);
        assertEquals(200, resp.statusCode());
        assertEquals("bytes", resp.headers().firstValue("Accept-Ranges").orElse(""));
        assertEquals("video/mp4", resp.headers().firstValue("Content-Type").orElse(""));
        assertArrayEquals(CLIP_BYTES, resp.body());
    }

    @Test
    void rangeMiddleSliceReturns206() throws Exception {
        HttpResponse<byte[]> resp = get("media/clip/" + clipId, "Range", "bytes=100-199");
        assertEquals(206, resp.statusCode());
        assertEquals("bytes 100-199/1000",
                resp.headers().firstValue("Content-Range").orElse(""));
        assertEquals(100, resp.body().length);
        assertArrayEquals(Arrays.copyOfRange(CLIP_BYTES, 100, 200), resp.body());
    }

    @Test
    void rangeOpenEndedReturnsTail() throws Exception {
        HttpResponse<byte[]> resp = get("media/clip/" + clipId, "Range", "bytes=900-");
        assertEquals(206, resp.statusCode());
        assertEquals("bytes 900-999/1000",
                resp.headers().firstValue("Content-Range").orElse(""));
        assertEquals(100, resp.body().length);
        assertArrayEquals(Arrays.copyOfRange(CLIP_BYTES, 900, 1000), resp.body());
    }

    @Test
    void rangeSuffixReturnsLastBytes() throws Exception {
        HttpResponse<byte[]> resp = get("media/clip/" + clipId, "Range", "bytes=-50");
        assertEquals(206, resp.statusCode());
        assertEquals("bytes 950-999/1000",
                resp.headers().firstValue("Content-Range").orElse(""));
        assertArrayEquals(Arrays.copyOfRange(CLIP_BYTES, 950, 1000), resp.body());
    }

    @Test
    void rangeBeyondEndOfFileReturns416() throws Exception {
        HttpResponse<byte[]> resp = get("media/clip/" + clipId, "Range", "bytes=2000-");
        assertEquals(416, resp.statusCode());
        assertEquals("bytes */1000",
                resp.headers().firstValue("Content-Range").orElse(""));
    }

    @Test
    void unknownClipIdReturns404() throws Exception {
        assertEquals(404, get("media/clip/999999").statusCode());
    }

    @Test
    void recordWithoutClipReturns404() throws Exception {
        assertEquals(404, get("media/clip/" + noClipId).statusCode());
    }

    // ------------------------------------------------------------------
    // static assets
    // ------------------------------------------------------------------

    @Test
    void missingStaticAssetReturns404() throws Exception {
        assertEquals(404, get("nope.js").statusCode());
    }

    @Test
    void encodedPathTraversalIsRejectedWith404() throws Exception {
        // %2e%2e decodes to ".." server-side; must never escape /web/.
        assertEquals(404, get("%2e%2e/secret").statusCode());
    }
}
