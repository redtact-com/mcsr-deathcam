package com.redtact.deathcam.obs;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.ObsGateway;
import io.obswebsocket.community.client.OBSRemoteController;
import io.obswebsocket.community.client.message.event.outputs.ReplayBufferSavedEvent;
import io.obswebsocket.community.client.message.event.outputs.ReplayBufferStateChangedEvent;
import io.obswebsocket.community.client.message.response.config.GetProfileParameterResponse;
import io.obswebsocket.community.client.message.response.config.GetVideoSettingsResponse;

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
    private volatile int baseWidth = -1;
    private volatile int baseHeight = -1;
    private volatile String clipResStatus = "録画解像度: 未確認";
    private volatile Consumer<String> statusListener = s -> { };
    private volatile IntConsumer bufferSecondsListener = s -> { };
    private volatile Consumer<String> resolutionListener = s -> { };

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

    /** Notified with a human-readable clip-resolution status/warning. */
    public void setResolutionListener(Consumer<String> listener) {
        this.resolutionListener = listener;
    }

    /** OBS base canvas resolution as "WxH", or null if unknown. */
    public String obsBaseResolution() {
        return (baseWidth > 0 && baseHeight > 0) ? baseWidth + "x" + baseHeight : null;
    }

    /** Human-readable status of the last clip-resolution apply (for the settings dialog). */
    public String obsClipResStatus() {
        return clipResStatus;
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
        refreshVideoSettings();
        ensureReplayBufferStarted();
        applyClipResolution();
    }

    /** Read OBS's base canvas resolution for downscale validation. */
    private void refreshVideoSettings() {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        c.getVideoSettings(vs -> {
            if (vs == null) {
                return;
            }
            baseWidth = num(vs.getBaseWidth());
            baseHeight = num(vs.getBaseHeight());
        });
    }

    /**
     * Apply the clip-only recording resolution via OBS's Advanced-mode recording rescale, which
     * affects the recording/replay-buffer output only — streaming is untouched. Downscale-only.
     *
     * <p>Modern OBS gates the rescale on {@code RecRescaleFilter != 0} (not the {@code RecRescale}
     * bool), and that auto-fill only happens on profile load — so over websocket all three keys
     * must be set explicitly. The rescale also can't be applied while an output is active, so the
     * replay buffer is bounced afterward. If the recording encoder is shared with streaming
     * ({@code RecEncoder=none}) the rescale is ignored — we detect and warn.
     */
    public void applyClipResolution() {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        String res = config.clipRescaleRes == null ? "" : config.clipRescaleRes.trim();
        c.getProfileParameter("Output", "Mode", modeResp -> {
            boolean advanced = modeResp != null && "Advanced".equalsIgnoreCase(effectiveValue(modeResp));
            if (res.isEmpty()) {
                if (advanced) {
                    c.setProfileParameter("AdvOut", "RecRescale", "false", r1 ->
                            c.setProfileParameter("AdvOut", "RecRescaleFilter", "0", r2 ->
                                    restartBufferForResolution()));
                }
                setClipRes("録画解像度: OBS のまま");
                return;
            }
            if (!advanced) {
                setClipRes("⚠ OBS を『詳細(Advanced)出力モード』にしてください (現在は Simple)");
                return;
            }
            int[] wh = parseRes(res);
            if (wh == null) {
                setClipRes("解像度の指定が不正: " + res);
                return;
            }
            if (baseWidth > 0 && baseHeight > 0 && (wh[0] > baseWidth || wh[1] > baseHeight)) {
                setClipRes("⚠ アップスケール不可: base " + baseWidth + "x" + baseHeight + " 以下にしてください");
                return;
            }
            // Recording encoder must be a real encoder, not "(Use stream encoder)" (RecEncoder=none).
            c.getProfileParameter("AdvOut", "RecEncoder", encResp -> {
                String enc = encResp != null ? effectiveValue(encResp) : null;
                boolean sharesStream = enc == null || enc.isBlank() || "none".equalsIgnoreCase(enc);
                // Set all three keys; RecRescaleFilter (3 = bilinear) is the real gate in modern OBS.
                c.setProfileParameter("AdvOut", "RecRescale", "true", r1 ->
                        c.setProfileParameter("AdvOut", "RecRescaleFilter", "3", r2 ->
                                c.setProfileParameter("AdvOut", "RecRescaleRes", res, r3 -> {
                                    restartBufferForResolution();
                                    if (sharesStream) {
                                        setClipRes("⚠ " + res + " 設定済だが録画エンコーダが『配信と共有』。"
                                                + "OBS の出力→録画でエンコーダを個別指定してください");
                                    } else {
                                        verifyClipRes(res);
                                    }
                                })));
            });
        });
    }

    /** Read the rescale back so we report what OBS actually stored (and that the filter took). */
    private void verifyClipRes(String requested) {
        OBSRemoteController c = controller;
        if (c == null) {
            return;
        }
        c.getProfileParameter("AdvOut", "RecRescaleRes", resResp -> {
            String stored = resResp != null ? effectiveValue(resResp) : null;
            if (requested.equalsIgnoreCase(stored)) {
                setClipRes("✓ 録画解像度 " + requested + " 適用 (配信は不変・要バッファ再起動反映)");
            } else {
                setClipRes("録画解像度 " + requested + " 設定 (OBS 応答: " + stored + ")");
            }
        });
    }

    /**
     * A resolution change only takes effect on a fresh output that is NOT active, so bounce the
     * replay buffer. (If streaming is also running OBS keeps the old size until it too stops.)
     */
    private void restartBufferForResolution() {
        OBSRemoteController c = controller;
        if (c == null || !connected) {
            return;
        }
        if (bufferActive) {
            c.stopReplayBuffer(s -> scheduler.schedule(this::ensureReplayBufferStarted, 2, TimeUnit.SECONDS));
        } else {
            ensureReplayBufferStarted();
        }
    }

    private void setClipRes(String s) {
        clipResStatus = s;
        try {
            resolutionListener.accept(s);
        } catch (Throwable ignored) {
        }
    }

    private static int num(Number n) {
        return n == null ? -1 : n.intValue();
    }

    private static int[] parseRes(String res) {
        int x = res.toLowerCase().indexOf('x');
        if (x <= 0 || x >= res.length() - 1) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(res.substring(0, x).trim()),
                    Integer.parseInt(res.substring(x + 1).trim()),
            };
        } catch (NumberFormatException e) {
            return null;
        }
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
