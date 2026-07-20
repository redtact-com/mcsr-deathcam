package com.redtact.deathcam.meta;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Minimal big-endian NBT parser (tag ids 0-12) for level.dat / playerdata files.
 * Compounds become {@code Map<String,Object>}, lists become {@code List<Object>},
 * arrays stay primitive arrays, strings use modified UTF-8.
 */
public final class NbtReader {

    private NbtReader() {
    }

    /** Reads the file (auto-detecting gzip by the 0x1f8b magic) and returns the root compound's value map. */
    public static Map<String, Object> read(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             PushbackInputStream pb = new PushbackInputStream(new BufferedInputStream(raw), 2)) {
            byte[] magic = pb.readNBytes(2);
            pb.unread(magic);
            InputStream in = (magic.length == 2 && (magic[0] & 0xFF) == 0x1f && (magic[1] & 0xFF) == 0x8b)
                    ? new GZIPInputStream(pb)
                    : pb;
            DataInputStream din = new DataInputStream(in);
            int type = din.readUnsignedByte();
            if (type != 10) {
                throw new IOException("NBT root is not a compound (tag id " + type + ")");
            }
            din.readUTF(); // root name, ignored
            return readCompound(din);
        }
    }

    /** Dotted-path lookup into nested compounds, e.g. "Data.WorldGenSettings.seed". */
    public static Optional<Object> path(Map<String, Object> root, String dottedPath) {
        Object current = root;
        for (String part : dottedPath.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return Optional.empty();
            }
            current = map.get(part);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) { // TAG_End
                return map;
            }
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1:
                return in.readByte();
            case 2:
                return in.readShort();
            case 3:
                return in.readInt();
            case 4:
                return in.readLong();
            case 5:
                return in.readFloat();
            case 6:
                return in.readDouble();
            case 7: {
                byte[] a = new byte[in.readInt()];
                in.readFully(a);
                return a;
            }
            case 8:
                return in.readUTF();
            case 9: {
                int elemType = in.readUnsignedByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) {
                    list.add(readPayload(in, elemType));
                }
                return list;
            }
            case 10:
                return readCompound(in);
            case 11: {
                int len = in.readInt();
                int[] a = new int[len];
                for (int i = 0; i < len; i++) {
                    a[i] = in.readInt();
                }
                return a;
            }
            case 12: {
                int len = in.readInt();
                long[] a = new long[len];
                for (int i = 0; i < len; i++) {
                    a[i] = in.readLong();
                }
                return a;
            }
            default:
                throw new IOException("Unknown or misplaced NBT tag id: " + type);
        }
    }
}
