package com.redtact.deathcam.meta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtReaderTest {

    @TempDir
    Path dir;

    @Test
    void readsAllTagTypesFromPlainFile() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(10);
        out.writeUTF("root");
        out.writeByte(1);
        out.writeUTF("b");
        out.writeByte(7);
        out.writeByte(2);
        out.writeUTF("s");
        out.writeShort(300);
        out.writeByte(3);
        out.writeUTF("i");
        out.writeInt(70000);
        out.writeByte(4);
        out.writeUTF("l");
        out.writeLong(1234567890123L);
        out.writeByte(5);
        out.writeUTF("f");
        out.writeFloat(1.5f);
        out.writeByte(6);
        out.writeUTF("d");
        out.writeDouble(2.25);
        out.writeByte(7);
        out.writeUTF("ba");
        out.writeInt(3);
        out.write(new byte[]{1, 2, 3});
        out.writeByte(8);
        out.writeUTF("str");
        out.writeUTF("hello");
        out.writeByte(9); // list of ints
        out.writeUTF("list");
        out.writeByte(3);
        out.writeInt(2);
        out.writeInt(10);
        out.writeInt(20);
        out.writeByte(9); // empty list with TAG_End element type
        out.writeUTF("empty");
        out.writeByte(0);
        out.writeInt(0);
        out.writeByte(11);
        out.writeUTF("ia");
        out.writeInt(2);
        out.writeInt(5);
        out.writeInt(6);
        out.writeByte(12);
        out.writeUTF("la");
        out.writeInt(1);
        out.writeLong(9L);
        out.writeByte(0); // end root
        out.flush();

        Path file = dir.resolve("plain.nbt");
        Files.write(file, bos.toByteArray());

        Map<String, Object> root = NbtReader.read(file);
        assertEquals((byte) 7, root.get("b"));
        assertEquals((short) 300, root.get("s"));
        assertEquals(70000, root.get("i"));
        assertEquals(1234567890123L, root.get("l"));
        assertEquals(1.5f, root.get("f"));
        assertEquals(2.25, root.get("d"));
        assertArrayEquals(new byte[]{1, 2, 3}, (byte[]) root.get("ba"));
        assertEquals("hello", root.get("str"));
        assertEquals(List.of(10, 20), root.get("list"));
        assertEquals(List.of(), root.get("empty"));
        assertArrayEquals(new int[]{5, 6}, (int[]) root.get("ia"));
        assertArrayEquals(new long[]{9L}, (long[]) root.get("la"));
    }

    @Test
    void readsGzippedLevelDatAndResolvesPaths() throws Exception {
        Path file = dir.resolve("level.dat");
        NbtTestWriter.writeGzip(file, NbtTestWriter.levelDatBytes(3549039247045008464L, true, true));

        Map<String, Object> root = NbtReader.read(file);
        assertEquals(3549039247045008464L, NbtReader.path(root, "Data.WorldGenSettings.seed").orElseThrow());
        assertEquals(-1512, NbtReader.path(root, "Data.Player.SpawnX").orElseThrow());
        assertEquals("mcsrranked #PZNbqLvSs", NbtReader.path(root, "Data.LevelName").orElseThrow());
        assertTrue(NbtReader.path(root, "Data.Player").orElseThrow() instanceof Map);
    }

    @Test
    void readsPlainLevelDatToo() throws Exception {
        Path file = dir.resolve("level_plain.dat");
        Files.write(file, NbtTestWriter.levelDatBytes(42L, false, false));

        Map<String, Object> root = NbtReader.read(file);
        assertEquals(42L, NbtReader.path(root, "Data.WorldGenSettings.seed").orElseThrow());
        assertTrue(NbtReader.path(root, "Data.Player").isEmpty());
    }

    @Test
    void pathReturnsEmptyForMissingOrNonCompoundSegments() throws Exception {
        Path file = dir.resolve("level2.dat");
        NbtTestWriter.writeGzip(file, NbtTestWriter.levelDatBytes(1L, true, false));

        Map<String, Object> root = NbtReader.read(file);
        assertTrue(NbtReader.path(root, "Data.Player.SpawnX").isEmpty());
        assertTrue(NbtReader.path(root, "Data.Nope.seed").isEmpty());
        assertTrue(NbtReader.path(root, "Data.WorldGenSettings.seed.deeper").isEmpty());
    }

    @Test
    void garbageFileThrows() throws Exception {
        Path file = dir.resolve("garbage.dat");
        Files.write(file, new byte[]{99, 98, 97});
        assertThrows(Exception.class, () -> NbtReader.read(file));
    }
}
