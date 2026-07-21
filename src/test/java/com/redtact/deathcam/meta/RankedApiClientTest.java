package com.redtact.deathcam.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the JSON parser directly with canned fixtures (no network) and the
 * real HTTP path against a loopback {@link HttpServer}. No Mockito.
 */
class RankedApiClientTest {

    // Full player UUIDs referenced by the fixtures below.
    private static final String TAKU_UUID = "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b";
    private static final String WISH_UUID = "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a";

    // GET /users/Taku128n64/matches?count=2  (summary array: solo #11854600, 2p #11837597).
    private static final String SUMMARY_ENVELOPE = """
            {
              "status": "success",
              "data": [
                {
                  "id": 11854600,
                  "type": 3,
                  "date": 1784598624,
                  "forfeited": true,
                  "result": {"uuid": null, "time": 0},
                  "seed": {"id": "mamhoglteawlqty5", "overworld": "VILLAGE", "nether": "TREASURE", "endTowers": [103,82,100,76], "variations": ["biome:structure:savanna"]},
                  "seedType": "VILLAGE",
                  "bastionType": "TREASURE",
                  "players": [{"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "nickname": "Taku128n64", "eloRate": 1013, "eloRank": 7876, "country": "jp", "roleType": 1}],
                  "changes": []
                },
                {
                  "id": 11837597,
                  "type": 2,
                  "date": 1784500000,
                  "forfeited": true,
                  "result": {"uuid": "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a", "time": 509312},
                  "seed": {"id": "abc", "overworld": "VILLAGE", "nether": "BASTION", "endTowers": [1,2,3,4], "variations": []},
                  "seedType": "VILLAGE",
                  "bastionType": "BASTION",
                  "players": [
                    {"uuid": "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a", "nickname": "wishgant", "eloRate": 1041, "country": "us"},
                    {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "nickname": "Taku128n64", "eloRate": 1013, "country": "jp"}
                  ],
                  "changes": []
                }
              ]
            }
            """;

    // GET /matches/11854582  (solo detail with a mixed timeline).
    private static final String DETAIL_11854582_ENVELOPE = """
            {
              "status": "success",
              "data": {
                "id": 11854582,
                "type": 3,
                "date": 1784598522,
                "forfeited": true,
                "result": {"uuid": null, "time": 133910},
                "seed": {"id": "seedxyz", "overworld": "VILLAGE", "nether": "TREASURE", "endTowers": [10,20,30,40], "variations": []},
                "seedType": "VILLAGE",
                "bastionType": "TREASURE",
                "players": [{"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "nickname": "Taku128n64", "eloRate": 1013, "country": "jp"}],
                "changes": [],
                "timelines": [
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 26674, "type": "story.smelt_iron"},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 71021, "type": "projectelo.timeline.death"},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 112530, "type": "story.root"},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 125976, "type": "projectelo.timeline.death"},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 133910, "type": "projectelo.timeline.forfeit"}
                ]
              }
            }
            """;

    // GET /matches/11837597  (2p detail with elo changes populated).
    private static final String DETAIL_11837597_ENVELOPE = """
            {
              "status": "success",
              "data": {
                "id": 11837597,
                "type": 2,
                "date": 1784500000,
                "forfeited": true,
                "result": {"uuid": "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a", "time": 509312},
                "seed": {"id": "abc", "overworld": "VILLAGE", "nether": "BASTION", "endTowers": [1,2,3,4], "variations": ["biome:x"]},
                "seedType": "VILLAGE",
                "bastionType": "BASTION",
                "players": [
                  {"uuid": "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a", "nickname": "wishgant", "eloRate": 1041, "country": "us"},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "nickname": "Taku128n64", "eloRate": 1013, "country": "jp"}
                ],
                "changes": [
                  {"uuid": "ad86f0e1-2d3c-4b5a-6978-0f1e2d3c4b5a", "change": 19, "eloRate": 1067},
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "change": -19, "eloRate": 1050}
                ],
                "timelines": [
                  {"uuid": "e193b1a2-3c4d-5e6f-7a8b-9c0d1e2f3a4b", "time": 5000, "type": "projectelo.timeline.death"}
                ]
              }
            }
            """;

    /** Returns the "data" element of an envelope text block. */
    private static JsonElement dataOf(String envelope) {
        return JsonParser.parseString(envelope).getAsJsonObject().get("data");
    }

    // ------------------------------------------------------------------
    // parseMatchArray / parseMatch
    // ------------------------------------------------------------------

    @Test
    void parseMatchArrayReadsSummaryFields() {
        List<RankedApiClient.ApiMatch> matches =
                RankedApiClient.parseMatchArray(dataOf(SUMMARY_ENVELOPE));
        assertEquals(2, matches.size());

        RankedApiClient.ApiMatch solo = matches.get(0);
        assertEquals(11854600L, solo.id());
        assertEquals(3, solo.type());
        assertEquals(1784598624L, solo.dateSeconds());
        assertTrue(solo.forfeited());
        assertEquals("VILLAGE", solo.seedType());
        assertEquals("TREASURE", solo.bastionType());
        assertArrayEquals(new int[]{103, 82, 100, 76}, solo.seed().endTowers());
        assertEquals(1, solo.players().size());
        assertEquals("Taku128n64", solo.players().get(0).nickname());
        assertEquals(Integer.valueOf(1013), solo.players().get(0).eloRate());
        assertNull(solo.resultUuid());

        RankedApiClient.ApiMatch duo = matches.get(1);
        assertEquals(11837597L, duo.id());
        assertEquals(2, duo.type());
        assertEquals(2, duo.players().size());
        // changes are empty in the summary payload.
        assertTrue(duo.changes().isEmpty());
        assertEquals(WISH_UUID, duo.resultUuid());
    }

    @Test
    void parseMatchReadsDetailFields() {
        RankedApiClient.ApiMatch m =
                RankedApiClient.parseMatch(dataOf(DETAIL_11854582_ENVELOPE).getAsJsonObject());
        assertEquals(11854582L, m.id());
        assertEquals(3, m.type());
        assertEquals(1784598522L, m.dateSeconds());
        assertEquals(Long.valueOf(133910L), m.resultTimeMs());
        assertTrue(m.forfeited());
        assertEquals("VILLAGE", m.seedType());
        assertEquals("TREASURE", m.bastionType());
        assertEquals(1, m.players().size());
    }

    // ------------------------------------------------------------------
    // deathsOf
    // ------------------------------------------------------------------

    @Test
    void deathsOfReturnsOnlyDeathEventsSorted() {
        RankedApiClient.ApiMatch m =
                RankedApiClient.parseMatch(dataOf(DETAIL_11854582_ENVELOPE).getAsJsonObject());
        List<RankedApiClient.ApiTimeline> deaths = m.deathsOf(TAKU_UUID);
        // Excludes smelt_iron / root / forfeit; sorted ascending.
        assertEquals(2, deaths.size());
        assertEquals(71021L, deaths.get(0).time());
        assertEquals(125976L, deaths.get(1).time());
        assertEquals("projectelo.timeline.death", deaths.get(0).type());
    }

    // ------------------------------------------------------------------
    // startMillis / endMillis / containsWallClock
    // ------------------------------------------------------------------

    @Test
    void wallClockWindowRespectsResultTimeAndGrace() {
        RankedApiClient.ApiMatch m =
                RankedApiClient.parseMatch(dataOf(DETAIL_11854582_ENVELOPE).getAsJsonObject());
        long start = m.startMillis();
        long end = m.endMillis();
        assertEquals(1784598522L * 1000L - 133910L, start);
        assertEquals(1784598522L * 1000L, end);

        // Boundaries and interior are inside the window.
        assertTrue(m.containsWallClock(start, 0));
        assertTrue(m.containsWallClock(end, 0));
        assertTrue(m.containsWallClock((start + end) / 2, 0));

        // Just outside is rejected without grace.
        assertFalse(m.containsWallClock(start - 1, 0));
        assertFalse(m.containsWallClock(end + 1, 0));

        // Grace widens the window in both directions.
        assertTrue(m.containsWallClock(start - 5_000, 10_000));
        assertTrue(m.containsWallClock(end + 5_000, 10_000));
    }

    // ------------------------------------------------------------------
    // opponent / player / changeForUuid
    // ------------------------------------------------------------------

    @Test
    void opponentIsEmptyOnSoloMatch() {
        RankedApiClient.ApiMatch solo =
                RankedApiClient.parseMatchArray(dataOf(SUMMARY_ENVELOPE)).get(0);
        assertTrue(solo.opponent("Taku128n64").isEmpty());
    }

    @Test
    void opponentAndPlayerLookupOnDuoMatch() {
        RankedApiClient.ApiMatch m =
                RankedApiClient.parseMatch(dataOf(DETAIL_11837597_ENVELOPE).getAsJsonObject());
        assertEquals("wishgant", m.opponent("Taku128n64").orElseThrow().nickname());
        // player() is case-insensitive.
        assertTrue(m.player("taku128N64").isPresent());
        assertEquals(Integer.valueOf(1013), m.player("TAKU128N64").orElseThrow().eloRate());
    }

    @Test
    void changeForUuidReturnsEloDelta() {
        RankedApiClient.ApiMatch m =
                RankedApiClient.parseMatch(dataOf(DETAIL_11837597_ENVELOPE).getAsJsonObject());
        RankedApiClient.ApiChange ch = m.changeForUuid(TAKU_UUID).orElseThrow();
        assertEquals(-19, ch.change());
        assertEquals(Integer.valueOf(1050), ch.eloRate());
    }

    // ------------------------------------------------------------------
    // Real HTTP path over a loopback server (no external network)
    // ------------------------------------------------------------------

    @Test
    void httpClientFetchesArrayAndDetail() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/users/x/matches", ex -> send(ex, SUMMARY_ENVELOPE));
        server.createContext("/matches/11854582", ex -> send(ex, DETAIL_11854582_ENVELOPE));
        server.start();
        try {
            int port = server.getAddress().getPort();
            RankedApiClient client =
                    new RankedApiClient("http://127.0.0.1:" + port, Duration.ofSeconds(5));

            List<RankedApiClient.ApiMatch> matches = client.recentMatches("x", 2);
            assertEquals(2, matches.size());
            assertEquals(11854600L, matches.get(0).id());

            Optional<RankedApiClient.ApiMatch> detail = client.matchDetail(11854582);
            assertTrue(detail.isPresent());
            assertEquals(2, detail.get().deathsOf(TAKU_UUID).size());
        } finally {
            server.stop(0);
        }
    }

    /** Writes a UTF-8 JSON body with HTTP 200. */
    private static void send(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
