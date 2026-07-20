package com.redtact.deathcam.detect;

import com.google.gson.JsonObject;
import com.redtact.deathcam.core.WorldSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTrackerTest {

    @TempDir
    Path tmp;

    private final List<WorldSession> sessions = new CopyOnWriteArrayList<>();
    private WorldTracker tracker;

    @AfterEach
    void tearDown() {
        if (tracker != null) {
            tracker.stop();
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within 3s");
    }

    private static void writeLatestWorld(Path file, String worldPath) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("world_path", worldPath);
        o.addProperty("version", "1.16.1");
        o.addProperty("category", "ANY");
        Files.writeString(file, o.toString());
    }

    @Test
    void emitsOnPathChangeButNotOnRewriteOfSamePath() throws Exception {
        Path json = tmp.resolve("latest_world.json");
        Path saves = tmp.resolve("instance").resolve("minecraft").resolve("saves");
        Path worldA = saves.resolve("mcsrranked #AAAA");
        Path worldB = saves.resolve("mcsrranked #BBBB");

        writeLatestWorld(json, worldA.toString());
        tracker = new WorldTracker(json, sessions::add);
        tracker.start();

        // initial read emits the current world
        waitUntil(() -> sessions.size() >= 1);
        WorldSession first = sessions.get(0);
        assertEquals(worldA, first.worldPath());
        assertEquals(saves.getParent(), first.instanceDir()); // world/../.. = .minecraft
        assertEquals("AAAA", first.rankedTag());

        // same path rewritten: no new emission
        writeLatestWorld(json, worldA.toString());
        Thread.sleep(1200);
        assertEquals(1, sessions.size());

        // changed path: emitted exactly once
        writeLatestWorld(json, worldB.toString());
        waitUntil(() -> sessions.size() >= 2);
        Thread.sleep(1200);
        assertEquals(2, sessions.size());
        assertEquals(worldB, sessions.get(1).worldPath());
    }

    @Test
    void toleratesTornWritesAndRecovers() throws Exception {
        Path json = tmp.resolve("latest_world.json");
        Files.writeString(json, "{\"world_path\":\"C:/torn"); // invalid JSON (torn write)

        tracker = new WorldTracker(json, sessions::add);
        tracker.start();

        Thread.sleep(1200);
        assertTrue(sessions.isEmpty(), "torn write must not emit");

        Path world = tmp.resolve("inst").resolve("mc").resolve("saves").resolve("New World");
        writeLatestWorld(json, world.toString());
        waitUntil(() -> sessions.size() >= 1);
        assertEquals(world, sessions.get(0).worldPath());
    }

    @Test
    void toleratesMissingFileUntilCreated() throws Exception {
        Path json = tmp.resolve("latest_world.json"); // does not exist yet

        tracker = new WorldTracker(json, sessions::add);
        tracker.start();

        Thread.sleep(700);
        assertTrue(sessions.isEmpty());

        Path world = tmp.resolve("inst").resolve("mc").resolve("saves").resolve("mcsrranked #CCCC");
        writeLatestWorld(json, world.toString());
        waitUntil(() -> sessions.size() >= 1);
        assertEquals("mcsrranked #CCCC", sessions.get(0).worldName());
    }
}
