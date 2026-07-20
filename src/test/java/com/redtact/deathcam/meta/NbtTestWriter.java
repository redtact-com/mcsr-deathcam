package com.redtact.deathcam.meta;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Hand-writes minimal NBT byte structures for tests (big-endian, tag ids as in the spec). */
final class NbtTestWriter {

    private NbtTestWriter() {
    }

    /**
     * level.dat-style bytes: root{} > Data > WorldGenSettings{seed:long} [+ Player compound,
     * optionally with SpawnX/SpawnY/SpawnZ + SpawnForced].
     */
    static byte[] levelDatBytes(long seed, boolean includePlayer, boolean playerHasSpawn) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(10);
        out.writeUTF(""); // root compound with empty name

        out.writeByte(10);
        out.writeUTF("Data");

        out.writeByte(10);
        out.writeUTF("WorldGenSettings");
        out.writeByte(4);
        out.writeUTF("seed");
        out.writeLong(seed);
        out.writeByte(0); // end WorldGenSettings

        if (includePlayer) {
            out.writeByte(10);
            out.writeUTF("Player");
            writePlayerTags(out, playerHasSpawn);
            out.writeByte(0); // end Player
        }

        out.writeByte(8);
        out.writeUTF("LevelName");
        out.writeUTF("mcsrranked #PZNbqLvSs");

        out.writeByte(0); // end Data
        out.writeByte(0); // end root
        out.flush();
        return bos.toByteArray();
    }

    /** playerdata/&lt;uuid&gt;.dat-style bytes: the root compound IS the player compound. */
    static byte[] playerDataBytes(boolean hasSpawn) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeByte(10);
        out.writeUTF("");
        writePlayerTags(out, hasSpawn);
        out.writeByte(0); // end root
        out.flush();
        return bos.toByteArray();
    }

    private static void writePlayerTags(DataOutputStream out, boolean hasSpawn) throws IOException {
        if (hasSpawn) {
            out.writeByte(3);
            out.writeUTF("SpawnX");
            out.writeInt(-1512);
            out.writeByte(3);
            out.writeUTF("SpawnY");
            out.writeInt(70);
            out.writeByte(3);
            out.writeUTF("SpawnZ");
            out.writeInt(85);
            out.writeByte(1);
            out.writeUTF("SpawnForced");
            out.writeByte(0);
        }
        out.writeByte(5);
        out.writeUTF("Health");
        out.writeFloat(0.0f);
    }

    static void writeGzip(Path file, byte[] nbt) throws IOException {
        try (OutputStream os = Files.newOutputStream(file);
             GZIPOutputStream gz = new GZIPOutputStream(os)) {
            gz.write(nbt);
        }
    }
}
