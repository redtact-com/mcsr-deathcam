package com.redtact.deathcam.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User configuration, stored as JSON in {user.home}/.mcsr-deathcam/config.json.
 * Field names are the JSON keys — keep them stable.
 */
public class AppConfig {

    public String obsHost = "localhost";
    public int obsPort = 4455;
    public String obsPassword = "";

    /** Seconds of gameplay kept BEFORE the death (replay buffer length = pre + post). */
    public int preRollSeconds = 30;
    /** Seconds to keep recording AFTER the death before saving the buffer. */
    public int postRollSeconds = 5;

    /** Automatically start OBS's replay buffer when connected. Off = you start it in OBS. */
    public boolean autoStartReplayBuffer = true;

    /**
     * Clip-only recording resolution, e.g. "1280x720"; empty = leave OBS as-is. Applied to OBS's
     * *recording* rescale (Advanced output mode) only, so streaming is untouched. Downscale-only:
     * ignored if larger than OBS's base canvas.
     */
    public String clipRescaleRes = "";

    /** Auto-delete the oldest clip videos once the library exceeds {@link #maxLibraryGb}. */
    public boolean retentionEnabled = false;
    /** Total size cap for saved clip videos, in GB (metadata rows and .rrf are kept). */
    public double maxLibraryGb = 5.0;

    /** Skip recording when the respawn point is a bed/anchor (intentional hunger reset). */
    public boolean skipHungerReset = true;

    /**
     * Which worlds to record. Ranked (type 2) and private (type 3) are both
     * {@code mcsrranked #XXX} worlds and cannot be told apart until the match is
     * queryable via the API, so a clip of a disabled type is deleted after the fact.
     */
    public boolean recordRanked = true;    // ranked queue matches (API type 2)
    public boolean recordPrivate = true;   // private room matches (API type 3)
    public boolean recordOther = false;    // non-mcsrranked worlds (practice maps, singleplayer)

    /** Override for the library root; null = {user.home}/mcsr-deathcam. */
    public String libraryDir = null;

    /** Override for the instance .minecraft dir; null = auto-detect from latest_world.json. */
    public String instanceDir = null;

    /** Port for the embedded dashboard (localhost only). 0 = pick a free port each start. */
    public int webPort = 8777;

    /** Open the dashboard in the default browser on startup. */
    public boolean openBrowserOnStart = true;

    /** Query api.mcsrranked.com after each match to enrich IGT/seed/opponent/result. */
    public boolean enableRankedApi = true;

    /** MCSR username for API lookups; null = auto-detect from the game logs. */
    public String playerName = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path configDir() {
        return Path.of(System.getProperty("user.home"), ".mcsr-deathcam");
    }

    public static Path configFile() {
        return configDir().resolve("config.json");
    }

    public Path libraryPath() {
        return libraryDir != null ? Path.of(libraryDir)
                : Path.of(System.getProperty("user.home"), "mcsr-deathcam");
    }

    public static AppConfig loadOrCreate() {
        Path file = configFile();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                AppConfig cfg = GSON.fromJson(r, AppConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("config.json unreadable, using defaults: " + e);
            }
        }
        AppConfig cfg = new AppConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(configDir());
            try (Writer w = Files.newBufferedWriter(configFile())) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            System.err.println("failed to save config: " + e);
        }
    }
}
