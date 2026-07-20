package com.redtact.deathcam.meta;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * Locates and archives mcsrranked .rrf replay files.
 */
public final class RrfArchiver {

    /** Subdirectories of the instance mcsrranked dir that may hold .rrf files. */
    private static final String[] REPLAY_SUBDIRS = {"temp-replay", "replay", "replay-cached"};

    private RrfArchiver() {
    }

    /**
     * Newest *.rrf under mcsrrankedDir's replay subdirs with mtime strictly after
     * modifiedAfterEpochMs; Optional.empty when none qualify.
     */
    public static Optional<Path> findNewestRrf(Path mcsrrankedDir, long modifiedAfterEpochMs) {
        Path best = null;
        long bestMtime = Long.MIN_VALUE;
        for (String sub : REPLAY_SUBDIRS) {
            Path dir = mcsrrankedDir.resolve(sub);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.rrf")) {
                for (Path f : ds) {
                    if (!Files.isRegularFile(f)) {
                        continue;
                    }
                    long mtime = Files.getLastModifiedTime(f).toMillis();
                    if (mtime > modifiedAfterEpochMs && mtime > bestMtime) {
                        best = f;
                        bestMtime = mtime;
                    }
                }
            } catch (IOException e) {
                System.err.println("[deathcam] failed to scan " + dir + ": " + e);
            }
        }
        return Optional.ofNullable(best);
    }

    /** Copies the rrf into destDir (creating it), keeping the filename; returns the destination path. */
    public static Path archive(Path rrf, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(rrf.getFileName().toString());
        Files.copy(rrf, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }
}
