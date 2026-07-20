package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HungerResetCheckerTest {

    @TempDir
    Path world;

    @Test
    void trueWhenLevelDatPlayerHasSpawnX() throws Exception {
        NbtTestWriter.writeGzip(world.resolve("level.dat"), NbtTestWriter.levelDatBytes(1L, true, true));
        assertTrue(HungerResetChecker.isBedOrAnchorRespawn(world));
    }

    @Test
    void falseWhenLevelDatPlayerHasNoSpawnX() throws Exception {
        NbtTestWriter.writeGzip(world.resolve("level.dat"), NbtTestWriter.levelDatBytes(1L, true, false));
        assertFalse(HungerResetChecker.isBedOrAnchorRespawn(world));
    }

    @Test
    void fallsBackToNewestPlayerdataWhenLevelDatHasNoPlayer() throws Exception {
        NbtTestWriter.writeGzip(world.resolve("level.dat"), NbtTestWriter.levelDatBytes(1L, false, false));
        Path playerdata = world.resolve("playerdata");
        Files.createDirectories(playerdata);

        Path older = playerdata.resolve("11111111-1111-1111-1111-111111111111.dat");
        Path newer = playerdata.resolve("22222222-2222-2222-2222-222222222222.dat");
        NbtTestWriter.writeGzip(older, NbtTestWriter.playerDataBytes(false));
        NbtTestWriter.writeGzip(newer, NbtTestWriter.playerDataBytes(true));
        long now = System.currentTimeMillis();
        Files.setLastModifiedTime(older, FileTime.fromMillis(now - 60_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(now));

        assertTrue(HungerResetChecker.isBedOrAnchorRespawn(world));

        // flip mtimes: the spawn-less file becomes the newest -> false
        Files.setLastModifiedTime(older, FileTime.fromMillis(now + 60_000));
        assertFalse(HungerResetChecker.isBedOrAnchorRespawn(world));
    }

    @Test
    void worksWithoutLevelDatUsingPlayerdataOnly() throws Exception {
        Path playerdata = world.resolve("playerdata");
        Files.createDirectories(playerdata);
        NbtTestWriter.writeGzip(playerdata.resolve("33333333-3333-3333-3333-333333333333.dat"),
                NbtTestWriter.playerDataBytes(true));
        assertTrue(HungerResetChecker.isBedOrAnchorRespawn(world));
    }

    @Test
    void failsOpenToFalse() throws Exception {
        // no level.dat, no playerdata
        assertFalse(HungerResetChecker.isBedOrAnchorRespawn(world));

        // corrupt level.dat, nothing else
        Files.write(world.resolve("level.dat"), new byte[]{42, 42, 42});
        assertFalse(HungerResetChecker.isBedOrAnchorRespawn(world));

        // nonexistent world dir
        assertFalse(HungerResetChecker.isBedOrAnchorRespawn(world.resolve("no_such_world")));
    }
}
