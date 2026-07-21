package com.redtact.deathcam.pipeline;

import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.store.SqliteDeathStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPolicyTest {

    @TempDir
    Path tmp;

    private DeathRecord withClip(SqliteDeathStore store, String world, long at, Path clip) {
        DeathRecord r = new DeathRecord();
        r.worldName = world;
        r.detectedAtMillis = at;
        r.cause = "SLAIN";
        r.phase = "NETHER";
        r.seedType = "VILLAGE";      // metadata that must survive
        r.clipPath = clip == null ? null : clip.toString();
        return store.insert(r);
    }

    private Path clip(String name, int bytes) throws IOException {
        Path p = tmp.resolve(name);
        Files.write(p, new byte[bytes]);
        return p;
    }

    @Test
    void deletesOldestVideosButKeepsMetadata() throws IOException {
        Path db = tmp.resolve("d.db");
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            Path c1 = clip("old.mp4", 1000);
            Path c2 = clip("mid.mp4", 1000);
            Path c3 = clip("new.mp4", 1000);
            withClip(store, "w1", 100, c1);   // oldest
            withClip(store, "w2", 200, c2);
            long newId = withClip(store, "w3", 300, c3).id;

            // cap = 1500 bytes -> must drop from 3000 to <=1500 by deleting the 2 oldest
            int removed = RetentionPolicy.enforce(store, 1500);
            assertEquals(2, removed);

            assertFalse(Files.exists(c1), "oldest video deleted");
            assertFalse(Files.exists(c2), "second-oldest video deleted");
            assertTrue(Files.exists(c3), "newest video kept");

            // all 3 rows (metadata) still present
            List<DeathRecord> rows = store.listRecent(10);
            assertEquals(3, rows.size());
            for (DeathRecord r : rows) {
                assertEquals("VILLAGE", r.seedType, "metadata preserved");
            }
            // only the newest still has a clip path; the deleted ones are cleared
            DeathRecord newest = rows.stream().filter(r -> r.id == newId).findFirst().orElseThrow();
            assertEquals(c3.toString(), newest.clipPath);
            long withClipRemaining = rows.stream().filter(r -> r.clipPath != null).count();
            assertEquals(1, withClipRemaining);
        }
    }

    @Test
    void underCapDeletesNothing() throws IOException {
        Path db = tmp.resolve("d2.db");
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            Path c = clip("a.mp4", 500);
            withClip(store, "w", 100, c);
            assertEquals(0, RetentionPolicy.enforce(store, 10_000));
            assertTrue(Files.exists(c));
        }
    }

    @Test
    void clearsStalePathWhenFileMissing() throws IOException {
        Path db = tmp.resolve("d3.db");
        try (SqliteDeathStore store = new SqliteDeathStore(db)) {
            withClip(store, "w", 100, tmp.resolve("gone.mp4"));   // file never created
            assertEquals(0, RetentionPolicy.enforce(store, 0));   // cap 0, but no real files
            assertNull(store.listRecent(1).get(0).clipPath, "stale path cleared");
        }
    }
}
