package com.redtact.deathcam.detect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tails a log file on a daemon thread (250 ms poll), emitting complete lines.
 *
 * <p>Starts at the current EOF (history is skipped). If the file shrinks (the game
 * restarted and rotated latest.log) the offset is reset to 0. A missing file is
 * polled quietly until it appears. Partial trailing lines are buffered until the
 * terminating newline arrives.
 */
public final class LogTailer {

    private static final long POLL_INTERVAL_MS = 250;
    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private final Path logFile;
    private final Consumer<String> onLine;

    private volatile boolean running;
    private Thread thread;

    // Accessed only from the polling thread (offset is initialized before Thread.start()).
    private long offset;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    public LogTailer(Path logFile, Consumer<String> onLine) {
        this.logFile = Objects.requireNonNull(logFile, "logFile");
        this.onLine = Objects.requireNonNull(onLine, "onLine");
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            offset = Files.exists(logFile) ? Files.size(logFile) : 0L;
        } catch (IOException e) {
            offset = 0L;
        }
        pending.reset();
        running = true;
        thread = new Thread(this::runLoop, "deathcam-log-tailer");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    private void runLoop() {
        while (running) {
            try {
                poll();
            } catch (Exception e) {
                System.err.println("[LogTailer] poll failed for " + logFile + ": " + e);
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void poll() throws IOException {
        if (!Files.isRegularFile(logFile)) {
            return; // not there yet; keep polling quietly
        }
        try (FileChannel ch = FileChannel.open(logFile, StandardOpenOption.READ)) {
            long size = ch.size();
            if (size < offset) { // log rotated at game restart
                offset = 0L;
                pending.reset();
            }
            if (size == offset) {
                return;
            }
            ch.position(offset);
            ByteBuffer buf = ByteBuffer.allocate(READ_BUFFER_SIZE);
            int n;
            while ((n = ch.read(buf)) > 0) {
                offset += n;
                consume(buf.array(), n);
                buf.clear();
            }
        } catch (NoSuchFileException e) {
            // vanished between the check and the open; retry next poll
        }
    }

    private void consume(byte[] data, int len) {
        for (int i = 0; i < len; i++) {
            if (data[i] == '\n') {
                emit(pending.toByteArray());
                pending.reset();
            } else {
                pending.write(data[i]);
            }
        }
    }

    private void emit(byte[] lineBytes) {
        int len = lineBytes.length;
        if (len > 0 && lineBytes[len - 1] == '\r') {
            len--; // CRLF line ending
        }
        // new String(bytes, UTF_8) replaces malformed sequences (CodingErrorAction.REPLACE
        // semantics) — the log can contain CP932 bytes from Japanese chat; death lines are ASCII.
        String line = new String(lineBytes, 0, len, StandardCharsets.UTF_8);
        try {
            onLine.accept(line);
        } catch (Exception e) {
            System.err.println("[LogTailer] line consumer failed: " + e);
        }
    }
}
