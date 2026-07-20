package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfArchiverTest {

    @TempDir
    Path dir;

    @Test
    void findsNewestRrfAcrossSubdirs() throws Exception {
        Path mcsrranked = dir.resolve("mcsrranked");
        Path tempReplay = mcsrranked.resolve("temp-replay");
        Path replay = mcsrranked.resolve("replay");
        Files.createDirectories(tempReplay);
        Files.createDirectories(replay);

        long base = System.currentTimeMillis() - 1_000_000;
        Path older = tempReplay.resolve("111.rrf");
        Path newer = replay.resolve("222.rrf");
        Path notRrf = tempReplay.resolve("333.txt");
        Files.write(older, new byte[]{1});
        Files.write(newer, new byte[]{2});
        Files.write(notRrf, new byte[]{3});
        Files.setLastModifiedTime(older, FileTime.fromMillis(base + 100_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(base + 200_000));
        Files.setLastModifiedTime(notRrf, FileTime.fromMillis(base + 900_000));

        assertEquals(newer, RrfArchiver.findNewestRrf(mcsrranked, base).orElseThrow());
        // strictly-after threshold: exactly the older file's mtime excludes it
        assertEquals(newer, RrfArchiver.findNewestRrf(mcsrranked, base + 100_000).orElseThrow());
        assertTrue(RrfArchiver.findNewestRrf(mcsrranked, base + 200_000).isEmpty());
    }

    @Test
    void emptyWhenNoReplayDirsExist() {
        assertTrue(RrfArchiver.findNewestRrf(dir.resolve("mcsrranked"), 0).isEmpty());
    }

    @Test
    void archiveCopiesIntoCreatedDirAndReplaces() throws Exception {
        Path src = dir.resolve("11493506.rrf");
        Files.write(src, new byte[]{7, 7, 7});
        Path destDir = dir.resolve("library").resolve("rrf"); // does not exist yet

        Path dest = RrfArchiver.archive(src, destDir);
        assertEquals(destDir.resolve("11493506.rrf"), dest);
        assertArrayEquals(new byte[]{7, 7, 7}, Files.readAllBytes(dest));
        assertTrue(Files.exists(src)); // copy, not move

        // overwrite with new content
        Files.write(src, new byte[]{8, 8});
        Path dest2 = RrfArchiver.archive(src, destDir);
        assertEquals(dest, dest2);
        assertArrayEquals(new byte[]{8, 8}, Files.readAllBytes(dest2));
    }
}
