package com.redtact.deathcam;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.pipeline.DeathCamApp;
import com.redtact.deathcam.ui.MainWindow;

import javax.swing.SwingUtilities;

/** Entry point. */
public final class Main {

    public static void main(String[] args) {
        MainWindow.applyLookAndFeel();
        AppConfig config = AppConfig.loadOrCreate();
        SwingUtilities.invokeLater(() -> new DeathCamApp(config).start());
    }

    private Main() {
    }
}
