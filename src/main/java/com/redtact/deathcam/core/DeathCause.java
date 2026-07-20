package com.redtact.deathcam.core;

/**
 * Categorised death cause, derived from the vanilla 1.16 English death message
 * printed by the integrated server ("[Server thread/INFO]: <name> was slain by ...").
 */
public enum DeathCause {
    SLAIN,            // was slain by X
    SHOT,             // was shot by X
    FIREBALLED,       // was fireballed by X (ghast/blaze fireball)
    BLOWN_UP,         // blew up / was blown up by X (creeper, TNT)
    INTENTIONAL_GAME_DESIGN, // bed / respawn anchor explosion
    FALL,             // hit the ground too hard / fell from a high place / doomed to fall
    LAVA,             // tried to swim in lava
    FIRE,             // went up in flames / burned to death / burnt to a crisp
    MAGMA,            // discovered the floor was lava (magma block)
    DROWN,            // drowned
    SUFFOCATE,        // suffocated in a wall / squished too much
    STARVE,           // starved to death
    WITHER,           // withered away
    MAGIC,            // was killed by magic (potions, evoker fangs, guardian)
    LIGHTNING,        // was struck by lightning
    CACTUS,           // was pricked to death / walked into a cactus
    BERRY_BUSH,       // was poked to death by a sweet berry bush
    ANVIL,            // was squashed by a falling anvil
    FALLING_BLOCK,    // was squashed by a falling block
    VOID,             // fell out of the world
    KINETIC,          // experienced kinetic energy (elytra)
    STING,            // was stung to death (bees)
    TRIDENT,          // was impaled by X
    DRAGON_BREATH,    // was roasted in dragon breath
    THORNS,           // was killed trying to hurt X
    GENERIC,          // died / died because of X
    UNKNOWN;
}
