package com.redtact.deathcam.detect;

import com.redtact.deathcam.core.DeathCause;
import com.redtact.deathcam.core.DeathEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses vanilla 1.16.1 English death messages out of latest.log lines.
 *
 * <p>Only "[HH:mm:ss] [Server thread/INFO]: &lt;msg&gt;" lines are considered (the
 * integrated server always prints the English message regardless of client language;
 * the "[CHAT]" duplicate on the main thread is ignored). The template table below is
 * the complete set of death.attack.* / death.fell.* entries from the official
 * 1.16.1 en_us.json.
 */
public final class DeathMessageParser {

    private DeathMessageParser() {
    }

    private static final Pattern LINE =
            Pattern.compile("^\\[(\\d{2}:\\d{2}:\\d{2})\\] \\[Server thread/INFO\\]: (.+)$");

    private record Template(String key, String text, DeathCause cause) {
    }

    /** All 1.16.1 death message templates (badRespawnPoint.link and message_too_long excluded: not death lines). */
    private static final List<Template> TEMPLATES = List.of(
            new Template("death.attack.anvil", "%1$s was squashed by a falling anvil", DeathCause.ANVIL),
            new Template("death.attack.anvil.player", "%1$s was squashed by a falling anvil whilst fighting %2$s", DeathCause.ANVIL),
            new Template("death.attack.arrow", "%1$s was shot by %2$s", DeathCause.SHOT),
            new Template("death.attack.arrow.item", "%1$s was shot by %2$s using %3$s", DeathCause.SHOT),
            new Template("death.attack.badRespawnPoint.message", "%1$s was killed by %2$s", DeathCause.INTENTIONAL_GAME_DESIGN),
            new Template("death.attack.cactus", "%1$s was pricked to death", DeathCause.CACTUS),
            new Template("death.attack.cactus.player", "%1$s walked into a cactus whilst trying to escape %2$s", DeathCause.CACTUS),
            new Template("death.attack.cramming", "%1$s was squished too much", DeathCause.SUFFOCATE),
            new Template("death.attack.cramming.player", "%1$s was squashed by %2$s", DeathCause.SUFFOCATE),
            new Template("death.attack.dragonBreath", "%1$s was roasted in dragon breath", DeathCause.DRAGON_BREATH),
            new Template("death.attack.dragonBreath.player", "%1$s was roasted in dragon breath by %2$s", DeathCause.DRAGON_BREATH),
            new Template("death.attack.drown", "%1$s drowned", DeathCause.DROWN),
            new Template("death.attack.drown.player", "%1$s drowned whilst trying to escape %2$s", DeathCause.DROWN),
            new Template("death.attack.even_more_magic", "%1$s was killed by even more magic", DeathCause.MAGIC),
            new Template("death.attack.explosion", "%1$s blew up", DeathCause.BLOWN_UP),
            new Template("death.attack.explosion.player", "%1$s was blown up by %2$s", DeathCause.BLOWN_UP),
            new Template("death.attack.explosion.player.item", "%1$s was blown up by %2$s using %3$s", DeathCause.BLOWN_UP),
            new Template("death.attack.fall", "%1$s hit the ground too hard", DeathCause.FALL),
            new Template("death.attack.fall.player", "%1$s hit the ground too hard whilst trying to escape %2$s", DeathCause.FALL),
            new Template("death.attack.fallingBlock", "%1$s was squashed by a falling block", DeathCause.FALLING_BLOCK),
            new Template("death.attack.fallingBlock.player", "%1$s was squashed by a falling block whilst fighting %2$s", DeathCause.FALLING_BLOCK),
            new Template("death.attack.fireball", "%1$s was fireballed by %2$s", DeathCause.FIREBALLED),
            new Template("death.attack.fireball.item", "%1$s was fireballed by %2$s using %3$s", DeathCause.FIREBALLED),
            new Template("death.attack.fireworks", "%1$s went off with a bang", DeathCause.BLOWN_UP),
            new Template("death.attack.fireworks.item", "%1$s went off with a bang due to a firework fired from %3$s by %2$s", DeathCause.BLOWN_UP),
            new Template("death.attack.fireworks.player", "%1$s went off with a bang whilst fighting %2$s", DeathCause.BLOWN_UP),
            new Template("death.attack.flyIntoWall", "%1$s experienced kinetic energy", DeathCause.KINETIC),
            new Template("death.attack.flyIntoWall.player", "%1$s experienced kinetic energy whilst trying to escape %2$s", DeathCause.KINETIC),
            new Template("death.attack.generic", "%1$s died", DeathCause.GENERIC),
            new Template("death.attack.generic.player", "%1$s died because of %2$s", DeathCause.GENERIC),
            new Template("death.attack.hotFloor", "%1$s discovered the floor was lava", DeathCause.MAGMA),
            new Template("death.attack.hotFloor.player", "%1$s walked into danger zone due to %2$s", DeathCause.MAGMA),
            new Template("death.attack.inFire", "%1$s went up in flames", DeathCause.FIRE),
            new Template("death.attack.inFire.player", "%1$s walked into fire whilst fighting %2$s", DeathCause.FIRE),
            new Template("death.attack.inWall", "%1$s suffocated in a wall", DeathCause.SUFFOCATE),
            new Template("death.attack.inWall.player", "%1$s suffocated in a wall whilst fighting %2$s", DeathCause.SUFFOCATE),
            new Template("death.attack.indirectMagic", "%1$s was killed by %2$s using magic", DeathCause.MAGIC),
            new Template("death.attack.indirectMagic.item", "%1$s was killed by %2$s using %3$s", DeathCause.MAGIC),
            new Template("death.attack.lava", "%1$s tried to swim in lava", DeathCause.LAVA),
            new Template("death.attack.lava.player", "%1$s tried to swim in lava to escape %2$s", DeathCause.LAVA),
            new Template("death.attack.lightningBolt", "%1$s was struck by lightning", DeathCause.LIGHTNING),
            new Template("death.attack.lightningBolt.player", "%1$s was struck by lightning whilst fighting %2$s", DeathCause.LIGHTNING),
            new Template("death.attack.magic", "%1$s was killed by magic", DeathCause.MAGIC),
            new Template("death.attack.magic.player", "%1$s was killed by magic whilst trying to escape %2$s", DeathCause.MAGIC),
            new Template("death.attack.mob", "%1$s was slain by %2$s", DeathCause.SLAIN),
            new Template("death.attack.mob.item", "%1$s was slain by %2$s using %3$s", DeathCause.SLAIN),
            new Template("death.attack.onFire", "%1$s burned to death", DeathCause.FIRE),
            new Template("death.attack.onFire.player", "%1$s was burnt to a crisp whilst fighting %2$s", DeathCause.FIRE),
            new Template("death.attack.outOfWorld", "%1$s fell out of the world", DeathCause.VOID),
            new Template("death.attack.outOfWorld.player", "%1$s didn't want to live in the same world as %2$s", DeathCause.VOID),
            new Template("death.attack.player", "%1$s was slain by %2$s", DeathCause.SLAIN),
            new Template("death.attack.player.item", "%1$s was slain by %2$s using %3$s", DeathCause.SLAIN),
            new Template("death.attack.starve", "%1$s starved to death", DeathCause.STARVE),
            new Template("death.attack.starve.player", "%1$s starved to death whilst fighting %2$s", DeathCause.STARVE),
            new Template("death.attack.sting", "%1$s was stung to death", DeathCause.STING),
            new Template("death.attack.sting.player", "%1$s was stung to death by %2$s", DeathCause.STING),
            new Template("death.attack.sweetBerryBush", "%1$s was poked to death by a sweet berry bush", DeathCause.BERRY_BUSH),
            new Template("death.attack.sweetBerryBush.player", "%1$s was poked to death by a sweet berry bush whilst trying to escape %2$s", DeathCause.BERRY_BUSH),
            new Template("death.attack.thorns", "%1$s was killed trying to hurt %2$s", DeathCause.THORNS),
            new Template("death.attack.thorns.item", "%1$s was killed by %3$s trying to hurt %2$s", DeathCause.THORNS),
            new Template("death.attack.thrown", "%1$s was pummeled by %2$s", DeathCause.GENERIC),
            new Template("death.attack.thrown.item", "%1$s was pummeled by %2$s using %3$s", DeathCause.GENERIC),
            new Template("death.attack.trident", "%1$s was impaled by %2$s", DeathCause.TRIDENT),
            new Template("death.attack.trident.item", "%1$s was impaled by %2$s with %3$s", DeathCause.TRIDENT),
            new Template("death.attack.wither", "%1$s withered away", DeathCause.WITHER),
            new Template("death.attack.wither.player", "%1$s withered away whilst fighting %2$s", DeathCause.WITHER),
            new Template("death.attack.witherSkull", "%1$s was shot by a %2$s's skull", DeathCause.WITHER),
            new Template("death.fell.accident.generic", "%1$s fell from a high place", DeathCause.FALL),
            new Template("death.fell.accident.ladder", "%1$s fell off a ladder", DeathCause.FALL),
            new Template("death.fell.accident.other_climbable", "%1$s fell while climbing", DeathCause.FALL),
            new Template("death.fell.accident.scaffolding", "%1$s fell off scaffolding", DeathCause.FALL),
            new Template("death.fell.accident.twisting_vines", "%1$s fell off some twisting vines", DeathCause.FALL),
            new Template("death.fell.accident.vines", "%1$s fell off some vines", DeathCause.FALL),
            new Template("death.fell.accident.weeping_vines", "%1$s fell off some weeping vines", DeathCause.FALL),
            new Template("death.fell.assist", "%1$s was doomed to fall by %2$s", DeathCause.FALL),
            new Template("death.fell.assist.item", "%1$s was doomed to fall by %2$s using %3$s", DeathCause.FALL),
            new Template("death.fell.finish", "%1$s fell too far and was finished by %2$s", DeathCause.FALL),
            new Template("death.fell.finish.item", "%1$s fell too far and was finished by %2$s using %3$s", DeathCause.FALL),
            new Template("death.fell.killer", "%1$s was doomed to fall", DeathCause.FALL));

    private record Compiled(Pattern pattern, DeathCause cause, boolean hasKiller) {
    }

    private record CompiledSet(String playerName, List<Compiled> entries) {
    }

    /** Single-entry cache: there is one local player, patterns embed the (quoted) name. */
    private static volatile CompiledSet cache;

    /**
     * @return a DeathEvent when the line is a Server-thread death message whose subject
     *         is {@code playerName}; empty for everything else.
     */
    public static Optional<DeathEvent> parse(String logLine, String playerName) {
        if (logLine == null || playerName == null || playerName.isBlank()) {
            return Optional.empty();
        }
        Matcher line = LINE.matcher(logLine.stripTrailing());
        if (!line.matches()) {
            return Optional.empty();
        }
        String logTime = line.group(1);
        String msg = line.group(2);
        if (!msg.startsWith(playerName)) { // every death template starts with %1$s
            return Optional.empty();
        }
        for (Compiled c : compiledFor(playerName)) {
            Matcher m = c.pattern().matcher(msg);
            if (m.matches()) {
                String killer = c.hasKiller() ? m.group("killer") : null;
                return Optional.of(new DeathEvent(Instant.now(), logTime, playerName, msg, c.cause(), killer));
            }
        }
        return Optional.empty();
    }

    private static List<Compiled> compiledFor(String playerName) {
        CompiledSet cs = cache;
        if (cs == null || !cs.playerName().equals(playerName)) {
            List<Template> sorted = new ArrayList<>(TEMPLATES);
            // Most-literal first so "was slain by X using Y" beats "was slain by X" and
            // the literal "was killed by magic" beats badRespawnPoint's "was killed by %2$s".
            sorted.sort(Comparator
                    .comparingInt((Template t) -> literalLength(t.text())).reversed()
                    .thenComparingInt(t -> placeholderCount(t.text()))
                    .thenComparing(Template::key));
            List<Compiled> entries = new ArrayList<>(sorted.size());
            for (Template t : sorted) {
                entries.add(new Compiled(toPattern(t.text(), playerName), t.cause(), t.text().contains("%2$s")));
            }
            cs = new CompiledSet(playerName, List.copyOf(entries));
            cache = cs;
        }
        return cs.entries();
    }

    private static int literalLength(String template) {
        return template.replace("%1$s", "").replace("%2$s", "").replace("%3$s", "").length();
    }

    private static int placeholderCount(String template) {
        int n = 0;
        for (int i = template.indexOf('%'); i >= 0; i = template.indexOf('%', i + 1)) {
            n++;
        }
        return n;
    }

    /** %1$s -> quoted player name (anchored), %2$s -> named "killer" group, %3$s -> named "item" group. */
    private static Pattern toPattern(String template, String playerName) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < template.length()) {
            int next = nextPlaceholder(template, i);
            if (next < 0) {
                sb.append(Pattern.quote(template.substring(i)));
                break;
            }
            if (next > i) {
                sb.append(Pattern.quote(template.substring(i, next)));
            }
            switch (template.substring(next, next + 4)) {
                case "%1$s" -> sb.append(Pattern.quote(playerName));
                case "%2$s" -> sb.append("(?<killer>.+)");
                default -> sb.append("(?<item>.+)");
            }
            i = next + 4;
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    private static int nextPlaceholder(String template, int from) {
        int min = -1;
        for (String ph : new String[] {"%1$s", "%2$s", "%3$s"}) {
            int p = template.indexOf(ph, from);
            if (p >= 0 && (min < 0 || p < min)) {
                min = p;
            }
        }
        return min;
    }
}
