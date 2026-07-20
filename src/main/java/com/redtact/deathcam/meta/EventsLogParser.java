package com.redtact.deathcam.meta;

import com.redtact.deathcam.core.Phase;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Parsed view of world/speedrunigt/events.log ("&lt;event_id&gt; &lt;rta_ms&gt; &lt;igt_ms&gt;" per line).
 */
public final class EventsLogParser {

    /** One line of events.log. */
    public record IgtEvent(String id, long rta, long igt) {
    }

    private final List<IgtEvent> events;

    private EventsLogParser(List<IgtEvent> events) {
        this.events = events;
    }

    /** Missing file or read failure yields an empty parser; malformed lines are skipped. */
    public static EventsLogParser parse(Path file) {
        List<IgtEvent> events = new ArrayList<>();
        if (file != null && Files.isRegularFile(file)) {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] parts = line.split("\\s+");
                    if (parts.length < 3) {
                        continue;
                    }
                    try {
                        events.add(new IgtEvent(parts[0], Long.parseLong(parts[1]), Long.parseLong(parts[2])));
                    } catch (NumberFormatException ignored) {
                        // skip malformed line
                    }
                }
            } catch (IOException e) {
                System.err.println("[deathcam] failed to read events.log " + file + ": " + e);
            }
        }
        return new EventsLogParser(Collections.unmodifiableList(events));
    }

    public List<IgtEvent> events() {
        return events;
    }

    /** Phase of the last phase-changing event with igt &lt;= timeMs; OVERWORLD when none. */
    public Phase phaseAt(long timeMs) {
        Phase phase = Phase.OVERWORLD;
        for (IgtEvent e : events) {
            if (e.igt() <= timeMs) {
                Phase p = Phase.fromEventId(e.id());
                if (p != null) {
                    phase = p;
                }
            }
        }
        return phase;
    }

    /** Last event (any id) with igt at or before timeMs. */
    public Optional<IgtEvent> lastEventBefore(long timeMs) {
        IgtEvent last = null;
        for (IgtEvent e : events) {
            if (e.igt() <= timeMs) {
                last = e;
            }
        }
        return Optional.ofNullable(last);
    }
}
