package com.redtact.deathcam.detect;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Compliant real-time death detector: polls a world's statistics file (250 ms) and fires
 * whenever {@code minecraft:deaths} increases. A death triggers a world save ("Saving and
 * pausing game..."), which flushes the stat, so an increment is observed within ~1 s — fast
 * enough for a replay-buffer clip, whose window covers the moments before detection.
 *
 * <p>One detector per world. The counter is per-world (each ranked match is a fresh world
 * starting at 0); the first poll establishes a baseline without firing, so a detector armed
 * mid-run does not replay historical deaths. The callback receives the number of new deaths
 * since the last poll (normally 1; &gt;1 when two deaths fall inside one save cycle).
 *
 * <p>Reads only the statistics file — never latest.log or level.dat — so nothing vanilla is
 * read during the run (speedrun.com rules A.3.10.a permits statistics reads).
 */
public final class StatsDeathDetector {

    private static final long POLL_INTERVAL_MS = 250;

    private final Path worldPath;
    private final IntConsumer onDeaths;

    private volatile boolean running;
    private Thread thread;

    // Accessed only from the polling thread. -1 = baseline not yet established.
    private int lastDeaths = -1;

    public StatsDeathDetector(Path worldPath, IntConsumer onDeaths) {
        this.worldPath = Objects.requireNonNull(worldPath, "worldPath");
        this.onDeaths = Objects.requireNonNull(onDeaths, "onDeaths");
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "deathcam-stats-detector");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void runLoop() {
        while (running) {
            try {
                pollOnce();
            } catch (Exception e) {
                System.err.println("[StatsDeathDetector] poll failed for " + worldPath + ": " + e);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * One poll iteration. Package-private so the increment logic can be tested without threads.
     * The first call only establishes the baseline; a later increase fires the callback with the
     * delta; a decrease (a fresh world reusing this detector) silently re-baselines.
     */
    void pollOnce() {
        int deaths = StatsReader.deaths(worldPath);
        if (lastDeaths < 0) {
            lastDeaths = deaths; // baseline: do not fire for pre-existing deaths
            return;
        }
        if (deaths > lastDeaths) {
            int delta = deaths - lastDeaths;
            lastDeaths = deaths;
            onDeaths.accept(delta);
        } else if (deaths < lastDeaths) {
            lastDeaths = deaths; // counter went backwards (new/reset world) — re-baseline
        }
    }
}
