package com.redtact.deathcam.meta;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Parses world/speedrunigt/record.json (written by SpeedrunIGT at world exit).
 */
public final class RecordJsonParser {

    /** Subset of record.json relevant to death metadata. */
    public record RecordInfo(String worldName, long date, long finalIgt, long finalRta, boolean completed) {
    }

    private RecordJsonParser() {
    }

    /** Missing / unreadable / malformed file yields Optional.empty. */
    public static Optional<RecordInfo> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            String worldName = asString(o.get("world_name"));
            long date = asLong(o.get("date"));
            long finalIgt = asLong(o.get("final_igt"));
            long finalRta = asLong(o.get("final_rta"));
            JsonElement completed = o.get("is_completed");
            boolean isCompleted = completed != null && !completed.isJsonNull() && completed.getAsBoolean();
            return Optional.of(new RecordInfo(worldName, date, finalIgt, finalRta, isCompleted));
        } catch (Exception e) {
            System.err.println("[deathcam] failed to parse record.json " + file + ": " + e);
            return Optional.empty();
        }
    }

    private static String asString(JsonElement e) {
        return (e == null || e.isJsonNull()) ? null : e.getAsString();
    }

    private static long asLong(JsonElement e) {
        return (e == null || e.isJsonNull()) ? 0L : e.getAsLong();
    }
}
