package com.redtact.deathcam.meta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads an mcsrranked .rrf replay (a ZIP with meta.json + timelines.json + encrypted replay.rpd).
 * Only the JSON entries are parsed; replay.rpd is ignored.
 */
public final class RrfReader {

    private static final String TYPE_DEATH = "projectelo.timeline.death";
    private static final String TYPE_DEATH_SPAWNPOINT = "projectelo.timeline.death_spawnpoint";

    /** One entry of meta.json "players". */
    public record RrfPlayer(String uuid, String nickname, Integer eloRate, String country, int roleType) {
    }

    /** One entry of timelines.json; data holds block coordinates when present. */
    public record RrfTimelineEvent(String uuid, String type, long time, int[] data) {
    }

    /** Parsed replay contents. */
    public record RrfData(long matchId, long date, String overworldSeed, String netherSeed, String theEndSeed,
                          String resultUuid, Long resultTime,
                          List<RrfPlayer> players, List<RrfTimelineEvent> timelines) {

        /** Death events of the given player uuid, sorted by time. */
        public List<RrfTimelineEvent> deathsOf(String uuid) {
            return timelines.stream()
                    .filter(t -> t.uuid() != null && t.uuid().equals(uuid))
                    .filter(t -> TYPE_DEATH.equals(t.type()) || TYPE_DEATH_SPAWNPOINT.equals(t.type()))
                    .sorted(Comparator.comparingLong(RrfTimelineEvent::time))
                    .toList();
        }

        public Optional<RrfPlayer> playerByName(String nickname) {
            return players.stream()
                    .filter(p -> p.nickname() != null && p.nickname().equals(nickname))
                    .findFirst();
        }

        public Optional<RrfPlayer> opponentOf(String nickname) {
            return players.stream()
                    .filter(p -> p.nickname() != null && !p.nickname().equals(nickname))
                    .findFirst();
        }
    }

    private RrfReader() {
    }

    /** Absent, corrupt, or non-zip file yields Optional.empty. */
    public static Optional<RrfData> read(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry metaEntry = zip.getEntry("meta.json");
            if (metaEntry == null) {
                return Optional.empty();
            }
            JsonObject meta;
            try (Reader r = new InputStreamReader(zip.getInputStream(metaEntry), StandardCharsets.UTF_8)) {
                meta = JsonParser.parseReader(r).getAsJsonObject();
            }

            List<RrfPlayer> players = new ArrayList<>();
            JsonElement playersEl = meta.get("players");
            if (playersEl != null && playersEl.isJsonArray()) {
                for (JsonElement el : playersEl.getAsJsonArray()) {
                    if (!el.isJsonObject()) {
                        continue;
                    }
                    JsonObject p = el.getAsJsonObject();
                    players.add(new RrfPlayer(
                            asString(p.get("uuid")),
                            asString(p.get("nickname")),
                            asInteger(p.get("eloRate")),
                            asString(p.get("country")),
                            (int) asLong(p.get("roleType"), 0L)));
                }
            }

            String resultUuid = null;
            Long resultTime = null;
            JsonElement resultEl = meta.get("result");
            if (resultEl != null && resultEl.isJsonObject()) {
                JsonObject result = resultEl.getAsJsonObject();
                resultUuid = asString(result.get("uuid"));
                resultTime = asLongObj(result.get("time"));
            }

            List<RrfTimelineEvent> timelines = new ArrayList<>();
            ZipEntry tlEntry = zip.getEntry("timelines.json");
            if (tlEntry != null) {
                try (Reader r = new InputStreamReader(zip.getInputStream(tlEntry), StandardCharsets.UTF_8)) {
                    JsonElement tlRoot = JsonParser.parseReader(r);
                    if (tlRoot.isJsonArray()) {
                        for (JsonElement el : tlRoot.getAsJsonArray()) {
                            if (!el.isJsonObject()) {
                                continue;
                            }
                            JsonObject t = el.getAsJsonObject();
                            timelines.add(new RrfTimelineEvent(
                                    asString(t.get("uuid")),
                                    asString(t.get("type")),
                                    asLong(t.get("time"), 0L),
                                    asIntArray(t.get("data"))));
                        }
                    }
                }
            }

            return Optional.of(new RrfData(
                    asLong(meta.get("matchId"), 0L),
                    asLong(meta.get("date"), 0L),
                    asString(meta.get("overworldSeed")),
                    asString(meta.get("netherSeed")),
                    asString(meta.get("theEndSeed")),
                    resultUuid, resultTime,
                    List.copyOf(players), List.copyOf(timelines)));
        } catch (Exception e) {
            System.err.println("[deathcam] failed to read rrf " + file + ": " + e);
            return Optional.empty();
        }
    }

    private static String asString(JsonElement e) {
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    private static Integer asInteger(JsonElement e) {
        return (e == null || e.isJsonNull()) ? null : e.getAsInt();
    }

    private static Long asLongObj(JsonElement e) {
        return (e == null || e.isJsonNull()) ? null : e.getAsLong();
    }

    private static long asLong(JsonElement e, long def) {
        return (e == null || e.isJsonNull()) ? def : e.getAsLong();
    }

    private static int[] asIntArray(JsonElement e) {
        if (e == null || !e.isJsonArray()) {
            return new int[0];
        }
        JsonArray arr = e.getAsJsonArray();
        int[] out = new int[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            out[i] = arr.get(i).getAsInt();
        }
        return out;
    }
}
