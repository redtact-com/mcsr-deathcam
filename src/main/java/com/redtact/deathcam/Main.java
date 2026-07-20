package com.redtact.deathcam;

import com.redtact.deathcam.config.AppConfig;

/** Entry point. Wiring of the death pipeline happens here (see pipeline package). */
public final class Main {

    public static void main(String[] args) {
        AppConfig config = AppConfig.loadOrCreate();
        System.out.println("mcsr-deathcam starting; library=" + config.libraryPath());
        // pipeline wiring added in DeathCamApp (integration step)
    }

    private Main() {
    }
}
