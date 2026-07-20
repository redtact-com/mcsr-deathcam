package com.redtact.deathcam.store;

import com.redtact.deathcam.core.DeathRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDeathStoreTest {

    @TempDir
    Path tempDir;

    private static DeathRecord minimal(String worldName, long detectedAtMillis) {
        DeathRecord r = new DeathRecord();
        r.worldName = worldName;
        r.detectedAtMillis = detectedAtMillis;
        r.cause = "SLAIN";
        r.rawMessage = "Taku128n64 was slain by Blaze";
        r.phase = "FORTRESS";
        return r;
    }

    private static DeathRecord full() {
        DeathRecord r = new DeathRecord();
        r.worldName = "mcsrranked #PZNbqLvSs";
        r.rankedTag = "PZNbqLvSs";
        r.matchId = 11493506L;
        r.detectedAtMillis = 1783140992277L;
        r.cause = "SLAIN";
        r.killer = "Blaze";
        r.rawMessage = "Taku128n64 was slain by Blaze";
        r.phase = "FORTRESS";
        r.igtAtDeathMillis = 730058L;
        r.finalIgtMillis = 776195L;
        r.finalRtaMillis = 780000L;
        r.seedOverworld = "3549039247045008464";
        r.seedNether = "213243711178923";
        r.seedEnd = "3549039247045008464";
        r.deathX = -1512;
        r.deathY = 20;
        r.deathZ = 85;
        r.opponentName = "opponent";
        r.opponentElo = 1227;
        r.hungerReset = true;
        r.clipPath = "/clips/death1.mkv";
        r.rrfPath = "/library/11493506.rrf";
        r.notes = "test note";
        return r;
    }

    private static void assertFullFields(DeathRecord expected, DeathRecord actual) {
        assertEquals(expected.worldName, actual.worldName);
        assertEquals(expected.rankedTag, actual.rankedTag);
        assertEquals(expected.matchId, actual.matchId);
        assertEquals(expected.detectedAtMillis, actual.detectedAtMillis);
        assertEquals(expected.cause, actual.cause);
        assertEquals(expected.killer, actual.killer);
        assertEquals(expected.rawMessage, actual.rawMessage);
        assertEquals(expected.phase, actual.phase);
        assertEquals(expected.igtAtDeathMillis, actual.igtAtDeathMillis);
        assertEquals(expected.finalIgtMillis, actual.finalIgtMillis);
        assertEquals(expected.finalRtaMillis, actual.finalRtaMillis);
        assertEquals(expected.seedOverworld, actual.seedOverworld);
        assertEquals(expected.seedNether, actual.seedNether);
        assertEquals(expected.seedEnd, actual.seedEnd);
        assertEquals(expected.deathX, actual.deathX);
        assertEquals(expected.deathY, actual.deathY);
        assertEquals(expected.deathZ, actual.deathZ);
        assertEquals(expected.opponentName, actual.opponentName);
        assertEquals(expected.opponentElo, actual.opponentElo);
        assertEquals(expected.hungerReset, actual.hungerReset);
        assertEquals(expected.clipPath, actual.clipPath);
        assertEquals(expected.rrfPath, actual.rrfPath);
        assertEquals(expected.notes, actual.notes);
    }

    @Test
    void insertFullRecordRoundTrips() {
        try (SqliteDeathStore store = new SqliteDeathStore(tempDir.resolve("deaths.db"))) {
            DeathRecord inserted = store.insert(full());
            assertTrue(inserted.id > 0);

            List<DeathRecord> recent = store.listRecent(10);
            assertEquals(1, recent.size());
            DeathRecord read = recent.get(0);
            assertEquals(inserted.id, read.id);
            assertFullFields(full(), read);
        }
    }

    @Test
    void insertMinimalRecordKeepsNulls() {
        try (SqliteDeathStore store = new SqliteDeathStore(tempDir.resolve("deaths.db"))) {
            DeathRecord inserted = store.insert(minimal("mcsrranked #abc", 1000L));
            assertTrue(inserted.id > 0);

            DeathRecord read = store.listRecent(10).get(0);
            assertEquals("mcsrranked #abc", read.worldName);
            assertEquals(1000L, read.detectedAtMillis);
            assertEquals("SLAIN", read.cause);
            assertNull(read.rankedTag);
            assertNull(read.matchId);
            assertNull(read.killer);
            assertNull(read.igtAtDeathMillis);
            assertNull(read.finalIgtMillis);
            assertNull(read.finalRtaMillis);
            assertNull(read.seedOverworld);
            assertNull(read.seedNether);
            assertNull(read.seedEnd);
            assertNull(read.deathX);
            assertNull(read.deathY);
            assertNull(read.deathZ);
            assertNull(read.opponentName);
            assertNull(read.opponentElo);
            assertFalse(read.hungerReset);
            assertNull(read.clipPath);
            assertNull(read.rrfPath);
            assertNull(read.notes);
        }
    }

    @Test
    void listRecentOrdersNewestFirstAndHonorsLimit() {
        try (SqliteDeathStore store = new SqliteDeathStore(tempDir.resolve("deaths.db"))) {
            store.insert(minimal("w1", 100L));
            store.insert(minimal("w2", 300L));
            store.insert(minimal("w3", 200L));

            List<DeathRecord> recent = store.listRecent(10);
            assertEquals(3, recent.size());
            assertEquals(300L, recent.get(0).detectedAtMillis);
            assertEquals(200L, recent.get(1).detectedAtMillis);
            assertEquals(100L, recent.get(2).detectedAtMillis);

            assertEquals(2, store.listRecent(2).size());
        }
    }

    @Test
    void updateLateFieldsPersists() {
        try (SqliteDeathStore store = new SqliteDeathStore(tempDir.resolve("deaths.db"))) {
            DeathRecord r = store.insert(minimal("mcsrranked #late", 5000L));

            r.rankedTag = "late";
            r.matchId = 42L;
            r.killer = "Zombie";
            r.igtAtDeathMillis = 123456L;
            r.finalIgtMillis = 200000L;
            r.finalRtaMillis = 210000L;
            r.seedOverworld = "111";
            r.seedNether = "222";
            r.seedEnd = "333";
            r.deathX = 10;
            r.deathY = 64;
            r.deathZ = -20;
            r.opponentName = "rival";
            r.opponentElo = 1500;
            r.hungerReset = true;
            r.clipPath = "/clips/x.mkv";
            r.rrfPath = "/lib/42.rrf";
            r.notes = "updated";
            store.update(r);

            List<DeathRecord> all = store.listByWorld("mcsrranked #late");
            assertEquals(1, all.size());
            DeathRecord read = all.get(0);
            assertEquals(r.id, read.id);
            assertFullFields(r, read);
        }
    }

    @Test
    void listByWorldFiltersAndOrdersAscending() {
        try (SqliteDeathStore store = new SqliteDeathStore(tempDir.resolve("deaths.db"))) {
            store.insert(minimal("worldA", 300L));
            store.insert(minimal("worldA", 100L));
            store.insert(minimal("worldB", 200L));

            List<DeathRecord> a = store.listByWorld("worldA");
            assertEquals(2, a.size());
            assertEquals(100L, a.get(0).detectedAtMillis);
            assertEquals(300L, a.get(1).detectedAtMillis);

            assertEquals(1, store.listByWorld("worldB").size());
            assertTrue(store.listByWorld("nope").isEmpty());
        }
    }

    @Test
    void reopenSameFileKeepsData() {
        Path db = tempDir.resolve("deaths.db");
        long id;
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            id = store.insert(full()).id;
        }
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            List<DeathRecord> recent = store.listRecent(10);
            assertEquals(1, recent.size());
            assertEquals(id, recent.get(0).id);
            assertFullFields(full(), recent.get(0));
        }
    }

    @Test
    void createsParentDirectories() {
        Path db = tempDir.resolve("nested/dir/deaths.db");
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            store.insert(minimal("w", 1L));
            assertEquals(1, store.listRecent(1).size());
        }
    }
}
