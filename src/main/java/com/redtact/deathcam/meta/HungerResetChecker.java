package com.redtact.deathcam.meta;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;

/**
 * Detects a bed/anchor respawn ("hunger reset" trick) from the world save written at the
 * death screen: the player compound then contains SpawnX/SpawnY/SpawnZ tags.
 */
public final class HungerResetChecker {

    private HungerResetChecker() {
    }

    /**
     * True when the player compound has a SpawnX tag. Checks level.dat Data.Player first,
     * then the newest playerdata/*.dat (whose root IS the player compound).
     * Any IO/parse failure returns false (fail open: the death gets recorded).
     */
    public static boolean isBedOrAnchorRespawn(Path worldPath) {
        try {
            Path levelDat = worldPath.resolve("level.dat");
            if (Files.isRegularFile(levelDat)) {
                Map<String, Object> root = NbtReader.read(levelDat);
                Optional<Object> player = NbtReader.path(root, "Data.Player");
                if (player.isPresent() && player.get() instanceof Map<?, ?> p) {
                    return p.containsKey("SpawnX");
                }
            }
            Path newest = newestPlayerData(worldPath.resolve("playerdata"));
            if (newest != null) {
                return NbtReader.read(newest).containsKey("SpawnX");
            }
        } catch (Exception e) {
            System.err.println("[deathcam] hunger-reset check failed for " + worldPath + ": " + e);
        }
        return false;
    }

    private static Path newestPlayerData(Path playerdataDir) throws IOException {
        if (!Files.isDirectory(playerdataDir)) {
            return null;
        }
        Path newest = null;
        FileTime newestTime = null;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(playerdataDir, "*.dat")) {
            for (Path f : ds) {
                if (!Files.isRegularFile(f)) {
                    continue;
                }
                FileTime t = Files.getLastModifiedTime(f);
                if (newest == null || t.compareTo(newestTime) > 0) {
                    newest = f;
                    newestTime = t;
                }
            }
        }
        return newest;
    }
}
