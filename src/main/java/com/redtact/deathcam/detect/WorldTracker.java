package com.redtact.deathcam.detect;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.redtact.deathcam.core.WorldSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Watches {user.home}/speedrunigt/latest_world.json (rewritten by SpeedRunIGT on every
 * world event) on a daemon thread (500 ms poll) and emits a {@link WorldSession}
 * whenever the world_path value changes. The first successful read also emits, so an
 * app started mid-run picks up the current world.
 *
 * <p>Tolerates a missing file and torn writes (invalid JSON is skipped silently).
 * instanceDir is derived as world_path/../.. (saves/&lt;world&gt; → .minecraft).
 */
public final class WorldTracker {

    private static final long POLL_INTERVAL_MS = 500;

    private final Path latestWorldJson;
    private final Consumer<WorldSession> onWorldChange;

    private volatile boolean running;
    private Thread thread;

    // Accessed only from the polling thread.
    private String lastWorldPath;

    public WorldTracker(Path latestWorldJson, Consumer<WorldSession> onWorldChange) {
        this.latestWorldJson = Objects.requireNonNull(latestWorldJson, "latestWorldJson");
        this.onWorldChange = Objects.requireNonNull(onWorldChange, "onWorldChange");
    }

    public static Path defaultLatestWorldJson() {
        return Path.of(System.getProperty("user.home"), "speedrunigt", "latest_world.json");
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "deathcam-world-tracker");
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
                poll();
            } catch (Exception e) {
                System.err.println("[WorldTracker] poll failed for " + latestWorldJson + ": " + e);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void poll() {
        String content;
        try {
            content = Files.readString(latestWorldJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // missing file or unreadable mid-write; retry next poll
        }
        String worldPath = extractWorldPath(content);
        if (worldPath == null || worldPath.isBlank() || worldPath.equals(lastWorldPath)) {
            return;
        }
        lastWorldPath = worldPath; // set before the callback so a throwing consumer is not re-invoked
        Path world = Path.of(worldPath);
        Path saves = world.getParent();
        Path instanceDir = saves != null ? saves.getParent() : null;
        if (instanceDir == null) {
            return;
        }
        try {
            onWorldChange.accept(new WorldSession(world, instanceDir));
        } catch (Exception e) {
            System.err.println("[WorldTracker] world change consumer failed: " + e);
        }
    }

    private static String extractWorldPath(String content) {
        try {
            JsonElement root = JsonParser.parseString(content);
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement wp = root.getAsJsonObject().get("world_path");
            if (wp == null || !wp.isJsonPrimitive() || !wp.getAsJsonPrimitive().isString()) {
                return null;
            }
            return wp.getAsString();
        } catch (JsonSyntaxException e) {
            return null; // torn write
        }
    }
}
