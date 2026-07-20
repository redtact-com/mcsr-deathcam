package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecordJsonParserTest {

    @TempDir
    Path dir;

    @Test
    void parsesRecordJson() throws Exception {
        Path file = dir.resolve("record.json");
        String json = """
                {"mc_version":"1.16.1","category":"ANY","run_type":"random_seed",
                 "is_completed":false,"world_name":"mcsrranked #PZNbqLvSs",
                 "date":1783140992277,"retimed_igt":725370,"final_igt":725370,"final_rta":725370,
                 "timelines":[{"name":"enter_nether","igt":254216,"rta":254216}],
                 "advancements":{}}
                """;
        Files.writeString(file, json, StandardCharsets.UTF_8);

        RecordJsonParser.RecordInfo info = RecordJsonParser.parse(file).orElseThrow();
        assertEquals("mcsrranked #PZNbqLvSs", info.worldName());
        assertEquals(1783140992277L, info.date());
        assertEquals(725370L, info.finalIgt());
        assertEquals(725370L, info.finalRta());
        assertFalse(info.completed());
    }

    @Test
    void parsesCompletedFlag() throws Exception {
        Path file = dir.resolve("record2.json");
        Files.writeString(file,
                "{\"world_name\":\"w\",\"date\":1,\"final_igt\":2,\"final_rta\":3,\"is_completed\":true}");
        RecordJsonParser.RecordInfo info = RecordJsonParser.parse(file).orElseThrow();
        assertTrue(info.completed());
        assertEquals(2L, info.finalIgt());
        assertEquals(3L, info.finalRta());
    }

    @Test
    void missingFileYieldsEmpty() {
        assertTrue(RecordJsonParser.parse(dir.resolve("nope.json")).isEmpty());
    }

    @Test
    void corruptFileYieldsEmpty() throws Exception {
        Path file = dir.resolve("corrupt.json");
        Files.writeString(file, "not json {{{");
        assertTrue(RecordJsonParser.parse(file).isEmpty());
    }
}
