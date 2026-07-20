package com.redtact.deathcam.core;

import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The world currently being played, resolved from {user.home}/speedrunigt/latest_world.json.
 *
 * @param worldPath    absolute path of the world folder
 * @param instanceDir  the .minecraft directory containing the world (worldPath/../..)
 */
public record WorldSession(Path worldPath, Path instanceDir) {

    private static final Pattern RANKED_WORLD = Pattern.compile("^mcsrranked #([a-zA-Z0-9]+)$");

    public String worldName() {
        return worldPath.getFileName().toString();
    }

    /** Ranked world tag ("PZNbqLvSs") or null when this is not a ranked match world. */
    public String rankedTag() {
        Matcher m = RANKED_WORLD.matcher(worldName());
        return m.matches() ? m.group(1) : null;
    }

    public boolean isRanked() {
        return rankedTag() != null;
    }

    public Path eventsLog() {
        return worldPath.resolve("speedrunigt").resolve("events.log");
    }

    public Path recordJson() {
        return worldPath.resolve("speedrunigt").resolve("record.json");
    }

    public Path latestLog() {
        return instanceDir.resolve("logs").resolve("latest.log");
    }

    public Path mcsrrankedDir() {
        return instanceDir.resolve("mcsrranked");
    }
}
