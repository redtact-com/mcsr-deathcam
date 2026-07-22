package com.redtact.deathcam.detect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatsReaderTest {

    @TempDir
    Path tmp;

    private Path worldWithStats(String worldName, String statsFileName, String json) throws Exception {
        Path world = tmp.resolve(worldName);
        Path stats = Files.createDirectories(world.resolve("stats"));
        Files.writeString(stats.resolve(statsFileName), json);
        return world;
    }

    @Test
    void readsDeathsFrom116StatsJson() throws Exception {
        Path world = worldWithStats("mcsrranked #ABC", "e193ed64-uuid.json",
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:deaths\":3,"
                        + "\"minecraft:time_since_death\":42}},\"DataVersion\":2586}");
        assertEquals(3, StatsReader.deaths(world));
    }

    @Test
    void missingStatsFolderIsZero() {
        assertEquals(0, StatsReader.deaths(tmp.resolve("no-such-world")));
    }

    @Test
    void missingDeathsKeyIsZero() throws Exception {
        Path world = worldWithStats("w", "u.json",
                "{\"stats\":{\"minecraft:custom\":{\"minecraft:jump\":5}},\"DataVersion\":2586}");
        assertEquals(0, StatsReader.deaths(world));
    }

    @Test
    void tornWriteIsZero() throws Exception {
        Path world = worldWithStats("w2", "u.json", "{\"stats\":{\"minecraft:cus");
        assertEquals(0, StatsReader.deaths(world));
    }

    @Test
    void newestOfMultipleStatsFilesWins() throws Exception {
        Path world = tmp.resolve("w3");
        Path stats = Files.createDirectories(world.resolve("stats"));
        Path older = stats.resolve("aaa.json");
        Path newer = stats.resolve("bbb.json");
        Files.writeString(older, "{\"stats\":{\"minecraft:custom\":{\"minecraft:deaths\":1}}}");
        Files.writeString(newer, "{\"stats\":{\"minecraft:custom\":{\"minecraft:deaths\":9}}}");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000_000));
        assertEquals(9, StatsReader.deaths(world));
    }
}
