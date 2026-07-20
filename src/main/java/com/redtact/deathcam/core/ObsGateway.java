package com.redtact.deathcam.core;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal surface of the OBS integration used by the death pipeline.
 * Implemented over obs-websocket v5. All methods must be non-blocking or fast;
 * connection loss must not throw out of the pipeline (return failed futures instead).
 */
public interface ObsGateway {

    /** Connect (async, with retry). Safe to call once at startup. */
    void start();

    void shutdown();

    boolean isConnected();

    /** Whether the replay buffer is currently active in OBS. */
    boolean isReplayBufferActive();

    /**
     * Start the replay buffer if OBS has it enabled in settings.
     * Never throws; logs and reports status via {@link #isReplayBufferActive()}.
     */
    void ensureReplayBufferStarted();

    /**
     * Ask OBS to persist the replay buffer now.
     *
     * @return future completed with the absolute path of the saved clip
     *         (from the ReplayBufferSaved event), or completed exceptionally
     *         on timeout / disconnect.
     */
    CompletableFuture<Path> saveReplayBuffer();

    /**
     * Set the replay buffer length (RecRBTime) in seconds and restart the buffer
     * so it takes effect. Best-effort.
     */
    void setBufferSeconds(int seconds);
}
