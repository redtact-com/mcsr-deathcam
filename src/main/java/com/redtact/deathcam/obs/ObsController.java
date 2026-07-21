package com.redtact.deathcam.obs;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.ObsGateway;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.message.event.outputs.ReplayBufferSavedEvent;
import io.obswebsocket.community.client.message.event.outputs.ReplayBufferStateChangedEvent;
import io.obswebsocket.community.client.message.response.config.GetProfileParameterResponse;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * obs-websocket v5 gateway. Reconnects on its own; a lost connection degrades to
 * failed save futures instead of exceptions in the pipeline.
 */
public final class ObsController implements ObsGateway {

    private static final long SAVE_TIMEOUT_SECONDS = 15;

    private final AppConfig config;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "obs-controller");
                t.setDaemon(true);
                return t;
            });
    private final ConcurrentLinkedQueue<CompletableFuture<Path>> pendingSaves = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private volatile OBSRemoteController controller;
    private volatile boolean connected;
    private volatile boolean bufferActive;
    private volatile int bufferSeconds = -1;   // OBS RecRBTime; -1 = unknown
    private volatile Consumer<String> statusListener = s -> { };
    private volatile IntConsumer bufferSecondsListener = s -> { };

    public ObsController(AppConfig config) {
        this.config = config;
    }

    public void setStatusListener(Consumer<String> listener) {
        this.statusListener = listener;
    }

    /** Notified with OBS's replay-buffer length (seconds) whenever it is (re)read. */
    public void setBufferSecondsListener(IntConsumer listener) {
        this.bufferSecondsListener = listener;
    }

    /** Last-read OBS replay-buffer length in seconds, or -1 if unknown. */
    public int obsBufferSeconds() {
        return bufferSeconds;
    }

    @Override
    public void start() {
        scheduler.scheduleWithFixedDelay(this::connectIfNeeded, 0, 10, TimeUnit.SECONDS);
        // Re-read the buffer length periodically in case it's changed in OBS.
        scheduler.scheduleWithFixedDelay(this::refreshBufferSeconds, 12, 30, TimeUnit.SECONDS);
    }

    private void connectIfNeeded() {
        if (connected || !connecting.compareAndSet(false, true)) {
            return;
        }
        try {
            OBSRemoteController old = controller;
            if (old != null) {
                try {
                    old.stop();
                } catch (Throwable ignored) {
                }
            }
            status("接続中 " + config.obsHost + ":" + config.obsPort);
            controller = OBSRemoteController.builder()
                    .host(config.obsHost)
                    .port(config.obsPort)
                    .password(config.obsPassword)
                    .autoConnect(false)
                    .connectionTimeout(5)
                    .lifecycle()
                    // Suppress the library's own stack-trace logging; we surface a clean
                    // status line instead (OBS being off simply means "not connected").
                    .withControllerDefaultLogging(false)
                    .withCommunicatorDefaultLogging(false)
                    .onReady(this::onReady)
                    .onDisconnect(this::onDisconnect)
                    .onControllerError(err -> status("未接続 (OBS 起動待ち)"))
                    .onCommunicatorError(err -> status("未接続 (OBS 起動待ち)"))
                    .and()
                    .registerEventListener(ReplayBufferSavedEvent.class, this::onReplaySaved)
                    .registerEventListener(ReplayBufferStateChangedEvent.class, ev -> {
                        Boolean active = ev.getOutputActive();
                        if (active != null) {
                            bufferActive = active;
                        }
                    })
                    .build();
            controller.connect();
        } catch (Throwable t) {
            System.err.println("[obs] connect failed: " + t);
            status("未接続 (再試行中)");
        } finally {
            connecting.set(false);
        }
    }

    private void onReady() {
        connected = true;
        status("接続済み");
        refreshBufferSeconds();
        ensureReplayBufferStarted();
    }

    /** Read OBS's active-mode RecRBTime and publish it to the listener. */
    private void refreshBufferSeconds() {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        c.getProfileParameter("Output", "Mode", modeResp -> {
            String mode = modeResp != null ? effectiveValue(modeResp) : null;
            String category = "Advanced".equalsIgnoreCase(mode) ? "AdvOut" : "SimpleOutput";
            c.getProfileParameter(category, "RecRBTime", rbResp -> {
                if (rbResp == null) {
                    return;
                }
                try {
                    String v = effectiveValue(rbResp);
                    if (v != null && !v.isBlank()) {
                        int secs = Integer.parseInt(v.trim());
                        if (secs != bufferSeconds) {
                            bufferSeconds = secs;
                            try {
                                bufferSecondsListener.accept(secs);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (NumberFormatException ignored) {
                    // leave bufferSeconds as-is
                }
            });
        });
    }

    private static String effectiveValue(GetProfileParameterResponse resp) {
        String v = resp.getParameterValue();
        return (v != null && !v.isEmpty()) ? v : resp.getDefaultParameterValue();
    }

    private void onDisconnect() {
        connected = false;
        bufferActive = false;
        status("切断 (再接続待ち)");
        failPending("OBS disconnected");
    }

    private void onReplaySaved(ReplayBufferSavedEvent ev) {
        CompletableFuture<Path> f = pendingSaves.poll();
        String path = ev.getSavedReplayPath();
        if (f != null && path != null) {
            f.complete(Path.of(path));
        }
    }

    private void failPending(String reason) {
        CompletableFuture<Path> f;
        while ((f = pendingSaves.poll()) != null) {
            f.completeExceptionally(new IllegalStateException(reason));
        }
    }

    @Override
    public void shutdown() {
        scheduler.shutdownNow();
        OBSRemoteController c = controller;
        if (c != null) {
            try {
                c.stop();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public boolean isReplayBufferActive() {
        return bufferActive;
    }

    @Override
    public void ensureReplayBufferStarted() {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        c.getReplayBufferStatus(resp -> {
            if (resp != null && resp.isSuccessful() && Boolean.TRUE.equals(resp.getOutputActive())) {
                bufferActive = true;
                status("接続済み / リプレイバッファ稼働中");
                return;
            }
            if (!config.autoStartReplayBuffer) {
                // Leave it to the user; warn so a death isn't silently missed.
                status("接続済み / リプレイバッファ停止中 (自動起動オフ)");
                return;
            }
            c.startReplayBuffer(startResp -> {
                if (startResp != null && startResp.isSuccessful()) {
                    bufferActive = true;
                    status("接続済み / リプレイバッファ開始");
                } else {
                    // 604 InvalidResourceState: replay buffer not enabled in OBS settings
                    status("接続済み / リプレイバッファ未有効 (OBS設定で有効化が必要)");
                }
            });
        });
    }

    @Override
    public CompletableFuture<Path> saveReplayBuffer() {
        OBSRemoteController c = controller;
        CompletableFuture<Path> future = new CompletableFuture<>();
        if (c == null || !connected) {
            future.completeExceptionally(new IllegalStateException("OBS not connected"));
            return future;
        }
        pendingSaves.add(future);
        c.saveReplayBuffer(resp -> {
            if (resp == null || !resp.isSuccessful()) {
                if (pendingSaves.remove(future)) {
                    future.completeExceptionally(new IllegalStateException("SaveReplayBuffer request failed"));
                }
            }
        });
        scheduler.schedule(() -> {
            if (pendingSaves.remove(future)) {
                future.completeExceptionally(new IllegalStateException("SaveReplayBuffer timeout"));
            }
        }, SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return future;
    }

    @Override
    public void setBufferSeconds(int seconds) {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        String value = Integer.toString(seconds);
        // Simple and Advanced output modes store the value under different categories.
        c.setProfileParameter("SimpleOutput", "RecRBTime", value, resp -> { });
        c.setProfileParameter("AdvOut", "RecRBTime", value, resp -> {
            // OBS re-reads RecRBTime when the buffer starts, so restart to apply.
            if (bufferActive) {
                c.stopReplayBuffer(stopResp -> scheduler.schedule(
                        this::ensureReplayBufferStarted, 2, TimeUnit.SECONDS));
            }
        });
    }

    private void status(String s) {
        try {
            statusListener.accept(s);
        } catch (Throwable ignored) {
        }
    }
}
