package com.redtact.deathcam.detect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsDeathDetectorTest {

    @TempDir
    Path tmp;

    private static void writeDeaths(Path world, int n) throws Exception {
        Path stats = Files.createDirectories(world.resolve("stats"));
        Files.writeString(stats.resolve("u.json"),
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:deaths\":" + n + "}}}");
    }

    @Test
    void firstPollBaselinesWithoutFiring() throws Exception {
        Path world = tmp.resolve("w");
        writeDeaths(world, 2); // detector armed mid-run with 2 deaths already present
        AtomicInteger total = new AtomicInteger();
        StatsDeathDetector d = new StatsDeathDetector(world, total::addAndGet);
        d.pollOnce();
        assertEquals(0, total.get(), "historical deaths must not fire");
    }

    @Test
    void incrementFiresWithDelta() throws Exception {
        Path world = tmp.resolve("w2");
        writeDeaths(world, 0);
        AtomicInteger total = new AtomicInteger();
        StatsDeathDetector d = new StatsDeathDetector(world, total::addAndGet);
        d.pollOnce();          // baseline 0
        writeDeaths(world, 1);
        d.pollOnce();          // +1
        assertEquals(1, total.get());
        writeDeaths(world, 3);
        d.pollOnce();          // +2 (two deaths inside one save cycle)
        assertEquals(3, total.get());
    }

    @Test
    void freshWorldWithoutStatsFileFiresOnFirstDeath() throws Exception {
        Path world = tmp.resolve("fresh");
        Files.createDirectories(world); // no stats/ folder yet
        AtomicInteger total = new AtomicInteger();
        StatsDeathDetector d = new StatsDeathDetector(world, total::addAndGet);
        d.pollOnce();          // baseline 0 (no file)
        writeDeaths(world, 1); // first save writes stats with deaths=1
        d.pollOnce();
        assertEquals(1, total.get());
    }

    @Test
    void counterGoingBackwardsRebaselines() throws Exception {
        Path world = tmp.resolve("w3");
        writeDeaths(world, 5);
        AtomicInteger total = new AtomicInteger();
        StatsDeathDetector d = new StatsDeathDetector(world, total::addAndGet);
        d.pollOnce();          // baseline 5
        writeDeaths(world, 1);
        d.pollOnce();          // backwards → re-baseline, no fire
        assertEquals(0, total.get());
        writeDeaths(world, 2);
        d.pollOnce();          // +1 from the new baseline
        assertEquals(1, total.get());
    }
}
