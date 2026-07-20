package com.redtact.deathcam.core;

import java.time.Instant;

/**
 * A death detected live from latest.log.
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
