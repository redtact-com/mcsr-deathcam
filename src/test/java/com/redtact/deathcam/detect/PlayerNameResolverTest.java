package com.redtact.deathcam.detect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerNameResolverTest {

    @TempDir
    Path tmp;

    private void writeLatestLog(String content) throws IOException {
        Path logs = tmp.resolve("logs");
        Files.createDirectories(logs);
        Files.writeString(logs.resolve("latest.log"), content);
    }

    @Test
    void lastSettingUserWins() throws Exception {
        writeLatestLog(
                "[00:50:59] [main/INFO]: Setting user: OldName\n"
                        + "[00:51:10] [main/INFO]: Something else entirely\n"
                        + "[00:52:00] [main/INFO]: Setting user: Taku128n64\n"
                        + "[00:52:01] [main/INFO]: Loading resources\n");
        assertEquals(Optional.of("Taku128n64"), PlayerNameResolver.resolve(tmp));
    }

    @Test
    void usercacheFallbackPicksNewestByExpiresOn() throws Exception {
        // no logs/latest.log at all
        Files.writeString(tmp.resolve("usercache.json"),
                "[{\"name\":\"Newest\",\"uuid\":\"u2\",\"expiresOn\":\"2026-07-20 10:00:00 +0900\"},"
                        + "{\"name\":\"Older\",\"uuid\":\"u1\",\"expiresOn\":\"2026-07-01 10:00:00 +0900\"}]");
        assertEquals(Optional.of("Newest"), PlayerNameResolver.resolve(tmp));
    }

    @Test
    void logWithoutMarkerFallsBackToUsercache() throws Exception {
        writeLatestLog("[00:50:59] [main/INFO]: No user line here\n");
        Files.writeString(tmp.resolve("usercache.json"),
                "[{\"name\":\"CacheName\",\"uuid\":\"u1\",\"expiresOn\":\"2026-07-01 10:00:00 +0900\"}]");
        assertEquals(Optional.of("CacheName"), PlayerNameResolver.resolve(tmp));
    }

    @Test
    void malformedUsercacheYieldsEmpty() throws Exception {
        Files.writeString(tmp.resolve("usercache.json"), "not json at all");
        assertTrue(PlayerNameResolver.resolve(tmp).isEmpty());
    }

    @Test
    void noSourcesYieldsEmpty() {
        assertTrue(PlayerNameResolver.resolve(tmp).isEmpty());
    }
}
