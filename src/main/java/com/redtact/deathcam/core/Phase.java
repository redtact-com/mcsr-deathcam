package com.redtact.deathcam.core;

/**
 * Run phase at the moment of death, derived from the last SpeedrunIGT event
 * (world/speedrunigt/events.log) whose timestamp precedes the death.
 */
public enum Phase {
    OVERWORLD,   // before entering the nether
    NETHER,      // after rsg.enter_nether, before any structure
    BASTION,     // after rsg.enter_bastion
    FORTRESS,    // after rsg.enter_fortress
    BLIND,       // after rsg.first_portal (blind travel / stronghold search in OW)
    STRONGHOLD,  // after rsg.enter_stronghold
    END,         // after rsg.enter_end
    UNKNOWN;

    /** SpeedrunIGT event id -> the phase that STARTS at that event (null = no phase change). */
    public static Phase fromEventId(String eventId) {
        return switch (eventId) {
            case "rsg.enter_nether" -> NETHER;
            case "rsg.enter_bastion" -> BASTION;
            case "rsg.enter_fortress" -> FORTRESS;
            case "rsg.first_portal" -> BLIND;
            case "rsg.enter_stronghold" -> STRONGHOLD;
            case "rsg.enter_end" -> END;
            default -> null;
        };
    }
}
