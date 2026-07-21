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

    /** Skip recording when the respawn point is a bed/anchor (intentional hunger reset). */
    public boolean skipHungerReset = true;

    /** Record only ranked worlds (mcsrranked #XXX). When false, any world is recorded. */
    public boolean rankedOnly = true;

    /** Override for the library root; null = {user.home}/mcsr-deathcam. */
    public String libraryDir = null;

    /** Override for the instance .minecraft dir; null = auto-detect from latest_world.json. */
    public String instanceDir = null;

    /** Port for the embedded dashboard (localhost only). 0 = pick a free port each start. */
    public int webPort = 8777;

    /** Open the dashboard in the default browser on startup. */
    public boolean openBrowserOnStart = true;

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
