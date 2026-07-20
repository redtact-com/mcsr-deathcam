package com.redtact.deathcam.detect;

import com.redtact.deathcam.core.DeathCause;
import com.redtact.deathcam.core.DeathEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathMessageParserTest {

    private static final String PLAYER = "Taku128n64";

    private static DeathEvent parseDeath(String message) {
        Optional<DeathEvent> e =
                DeathMessageParser.parse("[01:05:57] [Server thread/INFO]: " + message, PLAYER);
        assertTrue(e.isPresent(), "expected a death for: " + message);
        return e.get();
    }

    private static void assertDeath(String message, DeathCause cause, String killer) {
        DeathEvent e = parseDeath(message);
        assertEquals(cause, e.cause(), message);
        assertEquals(killer, e.killer(), message);
        assertEquals(message, e.rawMessage());
        assertEquals(PLAYER, e.playerName());
        assertEquals("01:05:57", e.logTime());
        assertNotNull(e.detectedAt());
    }

    private static void assertNotDeath(String logLine) {
        assertTrue(DeathMessageParser.parse(logLine, PLAYER).isEmpty(), "expected rejection: " + logLine);
    }

    // --- one representative message per cause category ---

    @Test
    void slainWithKiller() {
        assertDeath(PLAYER + " was slain by Blaze", DeathCause.SLAIN, "Blaze");
    }

    @Test
    void slainUsingItemCapturesOnlyKiller() {
        assertDeath(PLAYER + " was slain by Piglin using Golden Sword", DeathCause.SLAIN, "Piglin");
    }

    @Test
    void shot() {
        assertDeath(PLAYER + " was shot by Skeleton", DeathCause.SHOT, "Skeleton");
    }

    @Test
    void shotUsingItem() {
        assertDeath(PLAYER + " was shot by Skeleton using Bow", DeathCause.SHOT, "Skeleton");
    }

    @Test
    void fireballed() {
        assertDeath(PLAYER + " was fireballed by Ghast", DeathCause.FIREBALLED, "Ghast");
    }

    @Test
    void blewUpNoKiller() {
        assertDeath(PLAYER + " blew up", DeathCause.BLOWN_UP, null);
    }

    @Test
    void blownUpBy() {
        assertDeath(PLAYER + " was blown up by Creeper", DeathCause.BLOWN_UP, "Creeper");
    }

    @Test
    void intentionalGameDesign() {
        assertDeath(PLAYER + " was killed by [Intentional Game Design]",
                DeathCause.INTENTIONAL_GAME_DESIGN, "[Intentional Game Design]");
    }

    @Test
    void fallHitGround() {
        assertDeath(PLAYER + " hit the ground too hard", DeathCause.FALL, null);
    }

    @Test
    void fellFromHighPlace() {
        assertDeath(PLAYER + " fell from a high place", DeathCause.FALL, null);
    }

    @Test
    void doomedToFallByKiller() {
        assertDeath(PLAYER + " was doomed to fall by Zombie", DeathCause.FALL, "Zombie");
    }

    @Test
    void lava() {
        assertDeath(PLAYER + " tried to swim in lava", DeathCause.LAVA, null);
    }

    @Test
    void inFire() {
        assertDeath(PLAYER + " went up in flames", DeathCause.FIRE, null);
    }

    @Test
    void onFire() {
        assertDeath(PLAYER + " burned to death", DeathCause.FIRE, null);
    }

    @Test
    void magmaBlock() {
        assertDeath(PLAYER + " discovered the floor was lava", DeathCause.MAGMA, null);
    }

    @Test
    void drown() {
        assertDeath(PLAYER + " drowned", DeathCause.DROWN, null);
    }

    @Test
    void suffocateInWall() {
        assertDeath(PLAYER + " suffocated in a wall", DeathCause.SUFFOCATE, null);
    }

    @Test
    void cramming() {
        assertDeath(PLAYER + " was squished too much", DeathCause.SUFFOCATE, null);
    }

    @Test
    void starve() {
        assertDeath(PLAYER + " starved to death", DeathCause.STARVE, null);
    }

    @Test
    void wither() {
        assertDeath(PLAYER + " withered away", DeathCause.WITHER, null);
    }

    @Test
    void magicLiteralIsNotIntentionalGameDesign() {
        // "was killed by magic" must hit the magic template, not badRespawnPoint's "was killed by %2$s"
        assertDeath(PLAYER + " was killed by magic", DeathCause.MAGIC, null);
    }

    @Test
    void indirectMagic() {
        assertDeath(PLAYER + " was killed by Witch using magic", DeathCause.MAGIC, "Witch");
    }

    @Test
    void lightning() {
        assertDeath(PLAYER + " was struck by lightning", DeathCause.LIGHTNING, null);
    }

    @Test
    void cactus() {
        assertDeath(PLAYER + " was pricked to death", DeathCause.CACTUS, null);
    }

    @Test
    void berryBush() {
        assertDeath(PLAYER + " was poked to death by a sweet berry bush", DeathCause.BERRY_BUSH, null);
    }

    @Test
    void anvilBeatsCrammingCatchAll() {
        // "was squashed by a falling anvil" must not fall through to cramming.player "was squashed by %2$s"
        assertDeath(PLAYER + " was squashed by a falling anvil", DeathCause.ANVIL, null);
    }

    @Test
    void fallingBlock() {
        assertDeath(PLAYER + " was squashed by a falling block", DeathCause.FALLING_BLOCK, null);
    }

    @Test
    void voidDeath() {
        assertDeath(PLAYER + " fell out of the world", DeathCause.VOID, null);
    }

    @Test
    void kinetic() {
        assertDeath(PLAYER + " experienced kinetic energy", DeathCause.KINETIC, null);
    }

    @Test
    void sting() {
        assertDeath(PLAYER + " was stung to death", DeathCause.STING, null);
    }

    @Test
    void trident() {
        assertDeath(PLAYER + " was impaled by Drowned", DeathCause.TRIDENT, "Drowned");
    }

    @Test
    void dragonBreath() {
        assertDeath(PLAYER + " was roasted in dragon breath", DeathCause.DRAGON_BREATH, null);
    }

    @Test
    void thorns() {
        assertDeath(PLAYER + " was killed trying to hurt Guardian", DeathCause.THORNS, "Guardian");
    }

    @Test
    void thornsItemKillerIsTrailingGroup() {
        // template is "%1$s was killed by %3$s trying to hurt %2$s" — killer is the last capture
        assertDeath(PLAYER + " was killed by Thorns trying to hurt Guardian", DeathCause.THORNS, "Guardian");
    }

    @Test
    void generic() {
        assertDeath(PLAYER + " died", DeathCause.GENERIC, null);
    }

    @Test
    void trailingCarriageReturnTolerated() {
        Optional<DeathEvent> e = DeathMessageParser.parse(
                "[01:05:57] [Server thread/INFO]: " + PLAYER + " was slain by Blaze\r", PLAYER);
        assertTrue(e.isPresent());
        assertEquals(DeathCause.SLAIN, e.get().cause());
    }

    // --- rejections ---

    @Test
    void advancementChatLineRejected() {
        assertNotDeath("[01:05:07] [main/INFO]: [CHAT] " + PLAYER
                + " has made the advancement [A Terrible Fortress]");
    }

    @Test
    void advancementServerThreadLineRejected() {
        assertNotDeath("[01:05:07] [Server thread/INFO]: " + PLAYER
                + " has made the advancement [A Terrible Fortress]");
    }

    @Test
    void chatDuplicateRejected() {
        // only the Server thread line counts; the [CHAT] duplicate on main must not double-fire
        assertNotDeath("[01:05:57] [main/INFO]: [CHAT] " + PLAYER + " was slain by Blaze");
    }

    @Test
    void wrongPlayerRejected() {
        assertNotDeath("[01:05:57] [Server thread/INFO]: OtherGuy was slain by Blaze");
    }

    @Test
    void playerNamePrefixRejected() {
        assertNotDeath("[01:05:57] [Server thread/INFO]: " + PLAYER + "xx was slain by Blaze");
    }

    @Test
    void otherServerInfoRejected() {
        assertNotDeath("[01:05:58] [Server thread/INFO]: Saving and pausing game...");
    }

    @Test
    void nonLogLineRejected() {
        assertNotDeath(PLAYER + " was slain by Blaze");
    }
}
