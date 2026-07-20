package com.redtact.deathcam.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared display formatting for the UI. */
final class Formats {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private Formats() {
    }

    /** Epoch millis -> "yyyy-MM-dd HH:mm" in the local zone. */
    static String dateTime(long epochMillis) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    /** Millis -> "mm:ss" (minutes not capped at 59); "-" when null. */
    static String mmss(Long millis) {
        if (millis == null) {
            return "-";
        }
        long totalSeconds = millis / 1000;
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
