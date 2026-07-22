package com.redtact.deathcam.meta;

import com.redtact.deathcam.core.DeathCause;
import com.redtact.deathcam.core.DeathEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathLogReaderTest {

    @TempDir
    Path tmp;

    @Test
    void extractsOnlyThePlayersDeathsInOrder() throws Exception {
        Path log = tmp.resolve("latest.log");
        Files.writeString(log, String.join("\n",
                "[13:00:01] [Server thread/INFO]: Taku128 joined the game",
                "[13:01:02] [Server thread/INFO]: Taku128 was slain by Zombie",
                "[13:02:03] [Server thread/INFO]: Rival was slain by Blaze", // another player
                "[13:03:04] [Server thread/INFO]: Taku128 fell out of the world",
                ""));
        List<DeathEvent> deaths = DeathLogReader.read(log, "Taku128");
        assertEquals(2, deaths.size());
        assertEquals(DeathCause.SLAIN, deaths.get(0).cause());
        assertEquals("Zombie", deaths.get(0).killer());
        assertEquals(DeathCause.VOID, deaths.get(1).cause());
        assertNull(deaths.get(1).killer());
    }

    @Test
    void missingFileIsEmpty() {
        assertTrue(DeathLogReader.read(tmp.resolve("nope.log"), "Taku128").isEmpty());
    }

    @Test
    void blankPlayerIsEmpty() throws Exception {
        Path log = tmp.resolve("l.log");
        Files.writeString(log, "[13:00:00] [Server thread/INFO]: X died\n");
        assertTrue(DeathLogReader.read(log, "").isEmpty());
    }
}
