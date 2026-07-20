package com.redtact.deathcam.meta;

import com.redtact.deathcam.core.Phase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsLogParserTest {

    @TempDir
    Path dir;

    private EventsLogParser parseSample() throws Exception {
        Path log = dir.resolve("events.log");
        String content = String.join("\n",
                "common.view_seed 0 0",
                "rsg.obtain_iron_ingot 111211 111211",
                "rsg.enter_nether 254216 254216",
                "rsg.enter_bastion 305067 305067",
                "rsg.enter_fortress 350000 350000",
                "rsg.first_portal 400000 400000",
                "rsg.enter_stronghold 450000 450000",
                "rsg.enter_end 500000 500000",
                "common.leave_world 725370 725370") + "\n";
        Files.writeString(log, content, StandardCharsets.UTF_8);
        return EventsLogParser.parse(log);
    }

    @Test
    void phaseProgression() throws Exception {
        EventsLogParser p = parseSample();
        assertEquals(Phase.OVERWORLD, p.phaseAt(100000));   // before nether
        assertEquals(Phase.NETHER, p.phaseAt(254216));      // igt == timeMs is inclusive
        assertEquals(Phase.NETHER, p.phaseAt(300000));
        assertEquals(Phase.BASTION, p.phaseAt(310000));
        assertEquals(Phase.FORTRESS, p.phaseAt(399999));
        assertEquals(Phase.BLIND, p.phaseAt(420000));       // after rsg.first_portal
        assertEquals(Phase.STRONGHOLD, p.phaseAt(460000));
        assertEquals(Phase.END, p.phaseAt(999999));         // leave_world is not a phase change
    }

    @Test
    void eventsListAndLastEventBefore() throws Exception {
        EventsLogParser p = parseSample();
        assertEquals(9, p.events().size());
        assertEquals(new EventsLogParser.IgtEvent("common.view_seed", 0, 0), p.events().get(0));

        assertEquals("rsg.enter_nether", p.lastEventBefore(300000).orElseThrow().id());
        assertEquals("rsg.enter_nether", p.lastEventBefore(254216).orElseThrow().id());
        assertEquals("common.leave_world", p.lastEventBefore(9999999).orElseThrow().id());
        assertTrue(p.lastEventBefore(-1).isEmpty());
    }

    @Test
    void missingFileYieldsEmptyParser() {
        EventsLogParser p = EventsLogParser.parse(dir.resolve("no_such_dir").resolve("events.log"));
        assertTrue(p.events().isEmpty());
        assertEquals(Phase.OVERWORLD, p.phaseAt(123456));
        assertTrue(p.lastEventBefore(123456).isEmpty());
    }

    @Test
    void malformedLinesAreSkipped() throws Exception {
        Path log = dir.resolve("bad.log");
        Files.writeString(log, "rsg.enter_nether 100 100\nbroken line\nonly_two 5\nxyz abc def\n\n");
        EventsLogParser p = EventsLogParser.parse(log);
        assertEquals(1, p.events().size());
        assertEquals(Phase.NETHER, p.phaseAt(100));
    }
}
