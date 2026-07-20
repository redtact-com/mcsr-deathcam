package com.redtact.deathcam.detect;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves the local player name for an instance directory:
 * last "Setting user: &lt;name&gt;" in logs/latest.log, falling back to the newest
 * usercache.json entry. Absence of either source yields empty.
 */
public final class PlayerNameResolver {

    private PlayerNameResolver() {
    }

    private static final Pattern SETTING_USER = Pattern.compile("Setting user: (\\S+)");

    public static Optional<String> resolve(Path instanceDir) {
        if (instanceDir == null) {
            return Optional.empty();
        }
        Optional<String> fromLog = fromLatestLog(instanceDir.resolve("logs").resolve("latest.log"));
        if (fromLog.isPresent()) {
            return fromLog;
        }
        return fromUsercache(instanceDir.resolve("usercache.json"));
    }

    private static Optional<String> fromLatestLog(Path latestLog) {
        byte[] bytes;
        try {
            if (!Files.isRegularFile(latestLog)) {
                return Optional.empty();
            }
            bytes = Files.readAllBytes(latestLog);
        } catch (IOException e) {
            return Optional.empty();
        }
        // Lenient decode: the log may contain non-UTF-8 chat bytes; the marker line is ASCII.
        String content = new String(bytes, StandardCharsets.UTF_8);
        Matcher m = SETTING_USER.matcher(content);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return Optional.ofNullable(last);
    }

    private static Optional<String> fromUsercache(Path usercache) {
        String content;
        try {
            if (!Files.isRegularFile(usercache)) {
                return Optional.empty();
            }
            content = Files.readString(usercache, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return Optional.empty();
        }
        try {
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonArray()) {
                return Optional.empty();
            }
            String bestName = null;
            String bestExpires = null;
            for (JsonElement el : root.getAsJsonArray()) {
                if (!el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                JsonElement nameEl = o.get("name");
                if (nameEl == null || !nameEl.isJsonPrimitive()) {
                    continue;
                }
                String name = nameEl.getAsString();
                if (name.isBlank()) {
                    continue;
                }
                JsonElement expEl = o.get("expiresOn");
                String expires = expEl != null && expEl.isJsonPrimitive() ? expEl.getAsString() : "";
                // "yyyy-MM-dd HH:mm:ss ..." sorts lexicographically; ties and missing
                // timestamps fall back to array order (later entry wins).
                if (bestName == null || expires.compareTo(bestExpires) >= 0) {
                    bestName = name;
                    bestExpires = expires;
                }
            }
            return Optional.ofNullable(bestName);
        } catch (RuntimeException e) {
            return Optional.empty(); // malformed usercache.json
        }
    }
}
