package com.redtact.deathcam.meta;

import com.redtact.deathcam.core.DeathEvent;
import com.redtact.deathcam.detect.DeathMessageParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads death causes out of latest.log <em>after</em> a match ends. latest.log is a vanilla
 * log file, so speedrun.com rule A.3.10 forbids reading it before/during a run — but reading it
 * once the run is over is outside that scope. The death message (the only place the cause text
 * exists) is harvested here and paired to the deaths already detected from the statistics file.
 *
 * <p>Returns every player death message in the file, in file (chronological) order.
 */
public final class DeathLogReader {

    private DeathLogReader() {
    }

    public static List<DeathEvent> read(Path latestLog, String playerName) {
        List<DeathEvent> out = new ArrayList<>();
        if (playerName == null || playerName.isBlank() || !Files.isRegularFile(latestLog)) {
            return out;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(latestLog);
        } catch (IOException e) {
            return out;
        }
        // Lenient decode: the log may contain non-UTF-8 chat bytes; death lines are ASCII.
        String content = new String(bytes, StandardCharsets.UTF_8);
        for (String line : content.split("\n", -1)) {
            DeathMessageParser.parse(line, playerName).ifPresent(out::add);
        }
        return out;
    }
}
