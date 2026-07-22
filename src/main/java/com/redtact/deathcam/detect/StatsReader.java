package com.redtact.deathcam.detect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Reads the vanilla statistics file of a world save. Statistics files are explicitly
 * readable by players and programs at any time (speedrun.com rules A.3.10.a / A.11.1),
 * unlike latest.log / level.dat, so the death counter here drives compliant real-time
 * death detection ({@link StatsDeathDetector}).
 *
 * <p>The 1.16 stats JSON is {@code {"stats":{"minecraft:custom":{"minecraft:deaths":N,...}},...}}.
 * A singleplayer world's {@code stats/} folder holds exactly one {@code <uuid>.json}; if more
 * than one is present (should not happen) the most-recently-modified file is used.
 */
public final class StatsReader {

    private StatsReader() {
    }

    /**
     * The {@code minecraft:custom / minecraft:deaths} counter for a world, or 0 when the
     * stats folder/file is absent or unparseable (a fresh world has no stats file until its
     * first save). Never throws — a read/parse failure reads as "no change" to the detector.
     */
    public static int deaths(Path worldPath) {
        Path statsFile = newestStatsFile(worldPath.resolve("stats"));
        if (statsFile == null) {
            return 0;
        }
        try {
            String content = Files.readString(statsFile, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) {
                return 0;
            }
            JsonObject stats = optObject(root.getAsJsonObject(), "stats");
            JsonObject custom = stats == null ? null : optObject(stats, "minecraft:custom");
            if (custom == null) {
                return 0;
            }
            JsonElement deaths = custom.get("minecraft:deaths");
            if (deaths == null || !deaths.isJsonPrimitive() || !deaths.getAsJsonPrimitive().isNumber()) {
                return 0;
            }
            return deaths.getAsInt();
        } catch (IOException | RuntimeException e) {
            return 0; // missing / torn write / malformed — treat as no reading
        }
    }

    private static JsonObject optObject(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static Path newestStatsFile(Path statsDir) {
        if (!Files.isDirectory(statsDir)) {
            return null;
        }
        Path newest = null;
        FileTime newestTime = null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(statsDir, "*.json")) {
            for (Path f : ds) {
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                FileTime t = Files.getLastModifiedTime(f);
                if (newest == null || t.compareTo(newestTime) > 0) {
                    newest = f;
                    newestTime = t;
                }
            }
        } catch (IOException e) {
            return newest;
        }
        return newest;
    }
}
