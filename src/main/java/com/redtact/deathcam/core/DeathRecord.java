package com.redtact.deathcam.core;

/**
 * One recorded death, as persisted in SQLite. Fields that are only known after
 * the match ends (seed, coordinates, official times from the .rrf) are nullable
 * and filled in by the post-match collector.
 */
public class DeathRecord {
    public long id;                 // DB primary key (0 = not yet inserted)
    public String worldName;        // "mcsrranked #PZNbqLvSs"
    public String rankedTag;        // "PZNbqLvSs" or null
    public Long matchId;            // numeric ranked match id from .rrf meta.json, nullable
    public long detectedAtMillis;   // wall clock of detection
    public String cause;            // DeathCause name
    public String killer;           // nullable
    public String rawMessage;       // full death message
    public String phase;            // Phase name
    public Long igtAtDeathMillis;   // approx from events.log live, refined from .rrf timelines
    public Long finalIgtMillis;     // record.json final_igt, nullable until world exit
    public Long finalRtaMillis;     // record.json final_rta, nullable
    public String seedOverworld;    // nullable until .rrf harvested
    public String seedNether;       // nullable
    public String seedEnd;          // nullable
    public Integer deathX;          // nullable, from .rrf timelines data[]
    public Integer deathY;
    public Integer deathZ;
    public String opponentName;     // nullable
    public Integer opponentElo;     // nullable
    public boolean hungerReset;     // respawn point was bed/anchor at death time
    public String clipPath;         // nullable when clip skipped (hunger reset) or OBS failed
    public String rrfPath;          // archived replay, nullable
    public String notes;            // free-form, nullable
}
