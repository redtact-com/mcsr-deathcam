package com.redtact.deathcam.pipeline;

import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Size-cap retention for clip videos. When the total size of saved clips exceeds the cap, the
 * oldest videos are deleted — but only the video files: each death record (all metadata) and any
 * archived .rrf are kept, with the row's {@code clipPath} cleared. So statistics, seeds and
 * coordinates survive even after the video is gone.
 */
public final class RetentionPolicy {

    private RetentionPolicy() {
    }

    /**
     * Enforce the cap against the store. Returns the number of clip videos deleted.
     * A record whose clip file is already missing has its stale path cleared (not counted).
     */
    public static int enforce(DeathStore store, long capBytes) throws IOException {
        List<DeathRecord> withClip = new ArrayList<>();
        Map<Long, Long> sizes = new HashMap<>();
        long total = 0;
        for (DeathRecord r : store.listRecent(1_000_000)) {
            if (r.clipPath == null) {
                continue;
            }
            Path p = Path.of(r.clipPath);
            long sz = Files.isRegularFile(p) ? Files.size(p) : 0;
            if (sz == 0) {
                r.clipPath = null;      // stale path — keep metadata
                store.update(r);
                continue;
            }
            sizes.put(r.id, sz);
            withClip.add(r);
            total += sz;
        }
        if (total <= capBytes) {
            return 0;
        }
        withClip.sort(Comparator.comparingLong(r -> r.detectedAtMillis));   // oldest first
        int removed = 0;
        for (DeathRecord r : withClip) {
            if (total <= capBytes) {
                break;
            }
            try {
                Files.deleteIfExists(Path.of(r.clipPath));
            } catch (IOException e) {
                System.err.println("[retention] could not delete " + r.clipPath + ": " + e);
                continue;   // couldn't free this one; try the next
            }
            r.clipPath = null;
            store.update(r);
            total -= sizes.getOrDefault(r.id, 0L);
            removed++;
        }
        return removed;
    }
}
