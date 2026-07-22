package com.redtact.deathcam.meta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Read-only client for the public MCSR Ranked API (api.mcsrranked.com).
 * Fetches match metadata to enrich local death records. No auth required.
 *
 * <p>All public methods are failure-tolerant: on any error (network, non-200,
 * malformed JSON) they log to {@code System.err} and return an empty
 * {@link List} / {@link Optional} rather than throwing.
 */
public final class RankedApiClient {

    private static final String DEFAULT_BASE_URL = "https://api.mcsrranked.com";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(8);

    private final String baseUrl;
    private final Duration timeout;
    private final HttpClient http;

    /** Production client: real base URL, 8s timeout. */
    public RankedApiClient() {
        this(DEFAULT_BASE_URL, DEFAULT_TIMEOUT);
    }

    /** Test-friendly client: point {@code baseUrl} at a local server. */
    public RankedApiClient(String baseUrl, Duration timeout) {
        this.baseUrl = stripTrailingSlash(baseUrl == null ? DEFAULT_BASE_URL : baseUrl);
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        // One HttpClient reused across all calls.
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** GET /users/{username}/matches?count=count ; empty list on any error. */
    public List<ApiMatch> recentMatches(String username, int count) {
        try {
            String path = "/users/" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "/matches?count=" + count;
            JsonElement data = fetchData(path);
            if (data == null || !data.isJsonArray()) {
                return List.of();
            }
            return parseMatchArray(data);
        } catch (Exception e) {
            System.err.println("[deathcam] recentMatches(" + username + ") failed: " + e);
            return List.of();
        }
    }

    /** GET /matches/{id} ; empty on any error. */
    public Optional<ApiMatch> matchDetail(long id) {
        try {
            JsonElement data = fetchData("/matches/" + id);
            if (data == null || !data.isJsonObject()) {
                return Optional.empty();
            }
            return Optional.of(parseMatch(data.getAsJsonObject()));
        } catch (Exception e) {
            System.err.println("[deathcam] matchDetail(" + id + ") failed: " + e);
            return Optional.empty();
        }
    }

    /**
     * Performs the GET, checks HTTP status and the {@code {"status","data"}}
     * envelope, and returns the {@code data} element (array or object), or
     * {@code null} on any problem.
     */
    private JsonElement fetchData(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json")
                // Identify ourselves politely (courtesy for the public API).
                .header("User-Agent", "mcsr-deathcam (+https://github.com/redtact-com/mcsr-deathcam)")
                .GET()
                .build();
        HttpResponse<String> resp =
                http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            System.err.println("[deathcam] GET " + path + " -> HTTP " + resp.statusCode());
            return null;
        }
        JsonObject env = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonElement status = env.get("status");
        JsonElement data = env.get("data");
        // Proceed only when status=="success" or data is present.
        boolean statusOk = status != null && !status.isJsonNull()
                && "success".equals(status.getAsString());
        boolean hasData = data != null && !data.isJsonNull();
        if (!statusOk && !hasData) {
            System.err.println("[deathcam] GET " + path + " -> non-success envelope");
            return null;
        }
        return hasData ? data : null;
    }

    // ------------------------------------------------------------------
    // Parsing (package-private; no network involved, used directly by tests)
    // ------------------------------------------------------------------

    /** Parses a list of match summaries from the {@code data} array. */
    static List<ApiMatch> parseMatchArray(JsonElement dataArray) {
        List<ApiMatch> out = new ArrayList<>();
        if (dataArray == null || !dataArray.isJsonArray()) {
            return out;
        }
        for (JsonElement el : dataArray.getAsJsonArray()) {
            if (el != null && el.isJsonObject()) {
                out.add(parseMatch(el.getAsJsonObject()));
            }
        }
        return out;
    }

    /** Parses one match object; tolerates missing / null fields. */
    static ApiMatch parseMatch(JsonObject o) {
        long id = optLong(o, "id", 0L);
        int type = intOr(optInt(o, "type"), 0);
        long dateSeconds = optLong(o, "date", 0L);

        // result: { uuid, time } — both may be null / absent.
        String resultUuid = null;
        Long resultTimeMs = null;
        JsonObject result = optObject(o, "result");
        if (result != null) {
            resultUuid = optString(result, "uuid");
            resultTimeMs = optLongBoxed(result, "time");
        }

        boolean forfeited = optBoolean(o, "forfeited", false);
        ApiSeed seed = parseSeed(optObject(o, "seed"));
        String seedType = optString(o, "seedType");
        String bastionType = optString(o, "bastionType");
        List<ApiPlayer> players = parsePlayers(optArray(o, "players"));
        List<ApiChange> changes = parseChanges(optArray(o, "changes"));
        List<ApiTimeline> timelines = parseTimelines(optArray(o, "timelines"));

        return new ApiMatch(id, type, dateSeconds, resultUuid, resultTimeMs,
                forfeited, seed, seedType, bastionType, players, changes, timelines);
    }

    private static ApiSeed parseSeed(JsonObject s) {
        if (s == null) {
            return null;
        }
        return new ApiSeed(
                optString(s, "id"),
                optString(s, "overworld"),
                optString(s, "nether"),
                parseIntArray(optArray(s, "endTowers")),
                parseStringArray(optArray(s, "variations")));
    }

    private static List<ApiPlayer> parsePlayers(JsonArray arr) {
        List<ApiPlayer> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject p = el.getAsJsonObject();
            out.add(new ApiPlayer(
                    optString(p, "uuid"),
                    optString(p, "nickname"),
                    optInt(p, "eloRate"),
                    optString(p, "country")));
        }
        return out;
    }

    private static List<ApiChange> parseChanges(JsonArray arr) {
        List<ApiChange> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject c = el.getAsJsonObject();
            out.add(new ApiChange(
                    optString(c, "uuid"),
                    intOr(optInt(c, "change"), 0),
                    optInt(c, "eloRate")));
        }
        return out;
    }

    private static List<ApiTimeline> parseTimelines(JsonArray arr) {
        List<ApiTimeline> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject t = el.getAsJsonObject();
            out.add(new ApiTimeline(
                    optString(t, "uuid"),
                    optLong(t, "time", 0L),
                    optString(t, "type")));
        }
        return out;
    }

    private static int[] parseIntArray(JsonArray arr) {
        if (arr == null) {
            return new int[0];
        }
        int[] out = new int[arr.size()];
        for (int i = 0; i < out.length; i++) {
            JsonElement el = arr.get(i);
            out[i] = (el == null || el.isJsonNull()) ? 0 : el.getAsInt();
        }
        return out;
    }

    private static List<String> parseStringArray(JsonArray arr) {
        List<String> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (JsonElement el : arr) {
            if (el != null && !el.isJsonNull()) {
                out.add(el.getAsString());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // JSON field helpers (null-safe)
    // ------------------------------------------------------------------

    private static JsonElement get(JsonObject o, String key) {
        if (o == null) {
            return null;
        }
        JsonElement e = o.get(key);
        return (e == null || e.isJsonNull()) ? null : e;
    }

    static String optString(JsonObject o, String key) {
        JsonElement e = get(o, key);
        return e == null ? null : e.getAsString();
    }

    static Integer optInt(JsonObject o, String key) {
        JsonElement e = get(o, key);
        return e == null ? null : Integer.valueOf(e.getAsInt());
    }

    static long optLong(JsonObject o, String key, long def) {
        JsonElement e = get(o, key);
        return e == null ? def : e.getAsLong();
    }

    private static Long optLongBoxed(JsonObject o, String key) {
        JsonElement e = get(o, key);
        return e == null ? null : Long.valueOf(e.getAsLong());
    }

    private static boolean optBoolean(JsonObject o, String key, boolean def) {
        JsonElement e = get(o, key);
        return e == null ? def : e.getAsBoolean();
    }

    private static JsonObject optObject(JsonObject o, String key) {
        JsonElement e = get(o, key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }

    private static JsonArray optArray(JsonObject o, String key) {
        JsonElement e = get(o, key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : null;
    }

    private static int intOr(Integer boxed, int def) {
        return boxed == null ? def : boxed;
    }

    // ------------------------------------------------------------------
    // Data records
    // ------------------------------------------------------------------

    public record ApiPlayer(String uuid, String nickname, Integer eloRate, String country) {
    }

    public record ApiChange(String uuid, int change, Integer eloRate) {
    }

    public record ApiTimeline(String uuid, long time, String type) {
    }

    public record ApiSeed(String id, String overworld, String nether,
                          int[] endTowers, List<String> variations) {
    }

    public record ApiMatch(long id, int type, long dateSeconds, String resultUuid, Long resultTimeMs,
                           boolean forfeited, ApiSeed seed, String seedType, String bastionType,
                           List<ApiPlayer> players, List<ApiChange> changes, List<ApiTimeline> timelines) {

        /** Approximate wall-clock start: match date minus the RTA duration. */
        public long startMillis() {
            return dateSeconds * 1000L - (resultTimeMs == null ? 0L : resultTimeMs);
        }

        /** Wall-clock end: the match date in millis. */
        public long endMillis() {
            return dateSeconds * 1000L;
        }

        /** True if {@code wallMs} falls within [start-grace, end+grace]. */
        public boolean containsWallClock(long wallMs, long graceMs) {
            return wallMs >= startMillis() - graceMs && wallMs <= endMillis() + graceMs;
        }

        /** Case-insensitive nickname lookup. */
        public Optional<ApiPlayer> player(String nickname) {
            if (nickname == null) {
                return Optional.empty();
            }
            for (ApiPlayer p : players) {
                if (p.nickname() != null && p.nickname().equalsIgnoreCase(nickname)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }

        /** First player whose nickname differs from mine; empty if solo. */
        public Optional<ApiPlayer> opponent(String myNickname) {
            for (ApiPlayer p : players) {
                if (p.nickname() == null) {
                    continue;
                }
                if (myNickname == null || !p.nickname().equalsIgnoreCase(myNickname)) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }

        /** Elo change entry for a given player uuid. */
        public Optional<ApiChange> changeForUuid(String uuid) {
            if (uuid == null) {
                return Optional.empty();
            }
            for (ApiChange c : changes) {
                if (uuid.equals(c.uuid())) {
                    return Optional.of(c);
                }
            }
            return Optional.empty();
        }

        /** Death timeline events for a uuid (death / death_spawnpoint), sorted by time asc. */
        public List<ApiTimeline> deathsOf(String uuid) {
            List<ApiTimeline> out = new ArrayList<>();
            for (ApiTimeline t : timelines) {
                boolean sameUuid = (uuid == null) ? t.uuid() == null : uuid.equals(t.uuid());
                if (sameUuid && isDeath(t.type())) {
                    out.add(t);
                }
            }
            out.sort(Comparator.comparingLong(ApiTimeline::time));
            return out;
        }

        private static boolean isDeath(String type) {
            return "projectelo.timeline.death".equals(type)
                    || "projectelo.timeline.death_spawnpoint".equals(type);
        }
    }
}
