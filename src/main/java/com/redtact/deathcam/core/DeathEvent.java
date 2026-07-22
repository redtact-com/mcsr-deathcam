package com.redtact.deathcam.core;

import java.time.Instant;

/**
 * A vanilla death message (cause + killer) parsed from a latest.log line. Deaths are detected
 * from the statistics file; this carries the cause text, harvested from latest.log after the match.
 *
 * @param detectedAt   wall-clock time the log line was observed
 * @param logTime      the HH:mm:ss timestamp printed in the log line
 * @param playerName   subject of the death message
 * @param rawMessage   full vanilla death message ("Taku128n64 was slain by Blaze")
 * @param cause        categorised cause
 * @param killer       killer entity/player name if the message names one, else null
 */
public record DeathEvent(
        Instant detectedAt,
        String logTime,
        String playerName,
        String rawMessage,
        DeathCause cause,
        String killer) {
}
