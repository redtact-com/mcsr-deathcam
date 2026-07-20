package com.redtact.deathcam.detect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogTailerTest {

    @TempDir
    Path tmp;

    private final List<String> lines = new CopyOnWriteArrayList<>();
    private LogTailer tailer;

    @AfterEach
    void tearDown() {
        if (tailer != null) {
            tailer.stop();
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(condition.getAsBoolean(), "condition not met within 3s");
    }

    private static void append(Path file, String text) throws IOException {
        Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Test
    void appendedLinesAreEmittedAndHistoryIsSkipped() throws Exception {
        Path log = tmp.resolve("latest.log");
        Files.writeString(log, "history line\n");
        tailer = new LogTailer(log, lines::add);
        tailer.start();

        append(log, "line1\nline2\r\n");
        waitUntil(() -> lines.size() >= 2);
        assertEquals(List.of("line1", "line2"), lines); // CR stripped, history not emitted
    }

    @Test
    void truncationResetsOffset() throws Exception {
        Path log = tmp.resolve("latest.log");
        Files.writeString(log, "");
        tailer = new LogTailer(log, lines::add);
        tailer.start();

        append(log, "before restart 1\nbefore restart 2\n");
        waitUntil(() -> lines.size() >= 2);

        // game restart rewrites latest.log from scratch (smaller than the old offset)
        Files.writeString(log, "fresh\n", StandardOpenOption.TRUNCATE_EXISTING);
        waitUntil(() -> lines.contains("fresh"));
        assertEquals(List.of("before restart 1", "before restart 2", "fresh"), lines);
    }

    @Test
    void partialLineIsBufferedUntilNewline() throws Exception {
        Path log = tmp.resolve("latest.log");
        Files.writeString(log, "");
        tailer = new LogTailer(log, lines::add);
        tailer.start();

        append(log, "hello wo");
        Thread.sleep(700); // several poll cycles
        assertTrue(lines.isEmpty(), "incomplete line must not be emitted");

        append(log, "rld\n");
        waitUntil(() -> !lines.isEmpty());
        assertEquals(List.of("hello world"), lines);
    }

    @Test
    void missingFileIsPolledUntilItAppears() throws Exception {
        Path log = tmp.resolve("not-yet.log");
        tailer = new LogTailer(log, lines::add);
        tailer.start();

        Thread.sleep(400);
        assertTrue(lines.isEmpty());

        Files.writeString(log, "first\n");
        waitUntil(() -> lines.contains("first"));
    }
}
