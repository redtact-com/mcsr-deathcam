package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfReaderTest {

    private static final String UUID_ME = "e193ed6435ef45948615ae268cbfe043";
    private static final String UUID_OPP = "0ccb000000000000000000000000cafe";

    private static final String META_JSON = """
            {"version":32,"matchId":11493506,"date":1783140992277,"matchType":2,
             "overworldSeed":"3549039247045008464","netherSeed":"213243711178923",
             "theEndSeed":"3549039247045008464","symmetricKey":"QUFBQQ==",
             "result":{"uuid":"%s","time":776195},
             "players":[
               {"uuid":"%s","nickname":"Taku128n64","roleType":1,"eloRate":1203,"eloRank":null,"country":"jp"},
               {"uuid":"%s","nickname":"opponent","roleType":0,"eloRate":1227,"eloRank":null,"country":"cn"}]}
            """.formatted(UUID_OPP, UUID_ME, UUID_OPP);

    // deaths of UUID_ME deliberately out of chronological order
    private static final String TIMELINES_JSON = """
            [{"uuid":"%s","type":"projectelo.timeline.death_spawnpoint","time":730058,"data":[-1512,20,85]},
             {"uuid":"%s","type":"projectelo.timeline.death","time":300000,"data":[10,64,-5]},
             {"uuid":"%s","type":"story.enter_the_end","time":745852,"data":[-1508,21,76]},
             {"uuid":"%s","type":"projectelo.timeline.death","time":100,"data":[1,2,3]},
             {"uuid":"%s","type":"projectelo.timeline.dragon_death","time":766239,"data":[2,57,1]}]
            """.formatted(UUID_ME, UUID_ME, UUID_ME, UUID_OPP, UUID_OPP);

    @TempDir
    Path dir;

    private Path writeRrf(String metaJson, String timelinesJson) throws Exception {
        Path rrf = dir.resolve("11493506.rrf");
        try (OutputStream os = Files.newOutputStream(rrf);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            if (metaJson != null) {
                zos.putNextEntry(new ZipEntry("meta.json"));
                zos.write(metaJson.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            if (timelinesJson != null) {
                zos.putNextEntry(new ZipEntry("timelines.json"));
                zos.write(timelinesJson.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("replay.rpd"));
            zos.write(new byte[]{9, 9, 9, 9});
            zos.closeEntry();
        }
        return rrf;
    }

    @Test
    void parsesMetaAndTimelines() throws Exception {
        RrfReader.RrfData data = RrfReader.read(writeRrf(META_JSON, TIMELINES_JSON)).orElseThrow();

        assertEquals(11493506L, data.matchId());
        assertEquals(1783140992277L, data.date());
        assertEquals("3549039247045008464", data.overworldSeed());
        assertEquals("213243711178923", data.netherSeed());
        assertEquals("3549039247045008464", data.theEndSeed());
        assertEquals(UUID_OPP, data.resultUuid());
        assertEquals(776195L, data.resultTime());
        assertEquals(2, data.players().size());
        assertEquals(5, data.timelines().size());

        RrfReader.RrfPlayer me = data.playerByName("Taku128n64").orElseThrow();
        assertEquals(UUID_ME, me.uuid());
        assertEquals(1203, me.eloRate());
        assertEquals("jp", me.country());
        assertEquals(1, me.roleType());
    }

    @Test
    void deathsOfIsFilteredAndSortedByTime() throws Exception {
        RrfReader.RrfData data = RrfReader.read(writeRrf(META_JSON, TIMELINES_JSON)).orElseThrow();

        List<RrfReader.RrfTimelineEvent> deaths = data.deathsOf(UUID_ME);
        assertEquals(2, deaths.size());
        assertEquals("projectelo.timeline.death", deaths.get(0).type());
        assertEquals(300000L, deaths.get(0).time());
        assertEquals("projectelo.timeline.death_spawnpoint", deaths.get(1).type());
        assertEquals(730058L, deaths.get(1).time());
        assertArrayEquals(new int[]{-1512, 20, 85}, deaths.get(1).data());

        List<RrfReader.RrfTimelineEvent> oppDeaths = data.deathsOf(UUID_OPP);
        assertEquals(1, oppDeaths.size());
        assertEquals(100L, oppDeaths.get(0).time());
    }

    @Test
    void opponentOfReturnsTheOtherPlayer() throws Exception {
        RrfReader.RrfData data = RrfReader.read(writeRrf(META_JSON, TIMELINES_JSON)).orElseThrow();
        assertEquals("opponent", data.opponentOf("Taku128n64").orElseThrow().nickname());
        assertEquals(1227, data.opponentOf("Taku128n64").orElseThrow().eloRate());
        assertEquals("Taku128n64", data.opponentOf("opponent").orElseThrow().nickname());
        assertTrue(data.playerByName("nobody").isEmpty());
    }

    @Test
    void missingTimelinesEntryStillParses() throws Exception {
        RrfReader.RrfData data = RrfReader.read(writeRrf(META_JSON, null)).orElseThrow();
        assertEquals(11493506L, data.matchId());
        assertTrue(data.timelines().isEmpty());
        assertTrue(data.deathsOf(UUID_ME).isEmpty());
    }

    @Test
    void missingMetaEntryYieldsEmpty() throws Exception {
        assertTrue(RrfReader.read(writeRrf(null, TIMELINES_JSON)).isEmpty());
    }

    @Test
    void absentOrCorruptFileYieldsEmpty() throws Exception {
        assertTrue(RrfReader.read(dir.resolve("no_such.rrf")).isEmpty());

        Path corrupt = dir.resolve("corrupt.rrf");
        Files.write(corrupt, new byte[]{1, 2, 3, 4, 5});
        assertTrue(RrfReader.read(corrupt).isEmpty());
    }
}
