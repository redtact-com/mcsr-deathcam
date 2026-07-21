package com.redtact.deathcam.pipeline;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.DeathEvent;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;
import com.redtact.deathcam.core.Phase;
import com.redtact.deathcam.core.WorldSession;
import com.redtact.deathcam.detect.DeathMessageParser;
import com.redtact.deathcam.detect.LogTailer;
import com.redtact.deathcam.detect.PlayerNameResolver;
import com.redtact.deathcam.detect.WorldTracker;
import com.redtact.deathcam.meta.EventsLogParser;
import com.redtact.deathcam.meta.HungerResetChecker;
import com.redtact.deathcam.meta.RecordJsonParser;
import com.redtact.deathcam.meta.RrfArchiver;
import com.redtact.deathcam.meta.RrfReader;
import com.redtact.deathcam.obs.ObsController;
import com.redtact.deathcam.store.SqliteDeathStore;
import com.redtact.deathcam.ui.MainWindow;
import com.redtact.deathcam.web.DashboardServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires the whole death pipeline:
 * WorldTracker -> LogTailer -> DeathMessageParser -> (post-roll wait) ->
 * hunger-reset check -> OBS SaveReplayBuffer -> DB insert ->
 * (world exit) -> .rrf archive + record.json -> DB update.
 */
public final class DeathCamApp {

    private static final DateTimeFormatter CLIP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Wait after world exit before harvesting .rrf / record.json (written on save). */
    private static final long FINALIZE_DELAY_SECONDS = 8;
    /** Tolerance when pairing our detected deaths with .rrf timeline deaths. */
    private static final long PAIR_TOLERANCE_MILLIS = 30_000;

    private final AppConfig config;
    private final DeathStore store;
    private final ObsController obs;
    private final MainWindow window;
    private final WorldTracker worldTracker;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "deathcam-pipeline");
                t.setDaemon(true);
                return t;
            });

    private volatile WorldSession session;
    private volatile long sessionStartMillis;
    private volatile String playerName;
    private volatile LogTailer tailer;
    private volatile Path tailedLog;
    private final List<DeathRecord> currentWorldRecords = new ArrayList<>();

    public DeathCamApp(AppConfig config) {
        this.config = config;
        this.store = new SqliteDeathStore(config.libraryPath().resolve("deathcam.db"));
        this.obs = new ObsController(config);
        this.window = new MainWindow(config, store);
        this.worldTracker = new WorldTracker(WorldTracker.defaultLatestWorldJson(), this::onWorldChange);
    }

    public void start() {
        try {
            Files.createDirectories(clipsDir());
            Files.createDirectories(rrfDir());
        } catch (IOException e) {
            System.err.println("[app] cannot create library dirs: " + e);
        }
        obs.setStatusListener(window::setObsStatus);
        obs.start();
        worldTracker.start();
        startDashboard();
        window.setWorldStatus("ワールド待機中 (latest_world.json 監視)");
        window.refreshRecords();
        window.setVisible(true);
    }

    private void startDashboard() {
        try {
            DashboardServer server = new DashboardServer(store, config.webPort);
            server.start();
            String url = server.url();
            window.setDashboardOpener(() -> browse(url));
            System.out.println("[app] dashboard at " + url);
            if (config.openBrowserOnStart) {
                browse(url);
            }
        } catch (IOException e) {
            // Port taken (another instance?) — retry on an ephemeral port before giving up.
            try {
                DashboardServer server = new DashboardServer(store, 0);
                server.start();
                String url = server.url();
                window.setDashboardOpener(() -> browse(url));
                System.err.println("[app] port " + config.webPort + " busy, dashboard at " + url);
                if (config.openBrowserOnStart) {
                    browse(url);
                }
            } catch (IOException e2) {
                System.err.println("[app] dashboard failed to start: " + e2);
            }
        }
    }

    private static void browse(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
            }
        } catch (Exception e) {
            System.err.println("[app] cannot open browser: " + e);
        }
    }

    private Path clipsDir() {
        return config.libraryPath().resolve("clips");
    }

    private Path rrfDir() {
        return config.libraryPath().resolve("rrf");
    }

    private synchronized void onWorldChange(WorldSession next) {
        WorldSession prev = session;
        List<DeathRecord> prevRecords = List.copyOf(currentWorldRecords);
        long prevStart = sessionStartMillis;
        if (prev != null && !prevRecords.isEmpty()) {
            scheduler.schedule(() -> finalizeWorld(prev, prevRecords, prevStart),
                    FINALIZE_DELAY_SECONDS, TimeUnit.SECONDS);
        }
        currentWorldRecords.clear();
        session = next;
        sessionStartMillis = System.currentTimeMillis();

        boolean record = !config.rankedOnly || next.isRanked();
        window.setWorldStatus(next.worldName() + (record ? "" : " (非ranked: 記録オフ)"));
        if (!record) {
            return;
        }

        PlayerNameResolver.resolve(next.instanceDir()).ifPresent(n -> playerName = n);
        retargetTailer(next);
        obs.setBufferSeconds(config.preRollSeconds + config.postRollSeconds);
        obs.ensureReplayBufferStarted();
    }

    private void retargetTailer(WorldSession s) {
        Path log = s.latestLog();
        if (log.equals(tailedLog) && tailer != null) {
            return;
        }
        if (tailer != null) {
            tailer.stop();
        }
        tailedLog = log;
        tailer = new LogTailer(log, this::onLogLine);
        tailer.start();
    }

    private void onLogLine(String line) {
        String name = playerName;
        if (name == null) {
            return;
        }
        DeathMessageParser.parse(line, name).ifPresent(this::onDeath);
    }

    private void onDeath(DeathEvent event) {
        WorldSession s = session;
        if (s == null) {
            return;
        }
        window.setBufferStatus("死亡検知: " + event.rawMessage());
        // Post-roll: keep recording a bit past the death, then snapshot the buffer.
        scheduler.schedule(() -> captureDeath(s, event), config.postRollSeconds, TimeUnit.SECONDS);
    }

    private void captureDeath(WorldSession s, DeathEvent event) {
        boolean hungerReset = HungerResetChecker.isBedOrAnchorRespawn(s.worldPath());
        if (hungerReset && config.skipHungerReset) {
            window.setBufferStatus("ハンガーリセット死亡のためスキップ (" + event.rawMessage() + ")");
            return;
        }

        DeathRecord rec = new DeathRecord();
        rec.worldName = s.worldName();
        rec.rankedTag = s.rankedTag();
        rec.detectedAtMillis = event.detectedAt().toEpochMilli();
        rec.cause = event.cause().name();
        rec.killer = event.killer();
        rec.rawMessage = event.rawMessage();
        rec.hungerReset = hungerReset;
        Phase phase = EventsLogParser.parse(s.eventsLog()).phaseAt(Long.MAX_VALUE);
        rec.phase = phase.name();

        store.insert(rec);
        synchronized (this) {
            if (s.equals(session)) {
                currentWorldRecords.add(rec);
            }
        }
        window.refreshRecords();

        obs.saveReplayBuffer().whenComplete((savedPath, err) -> {
            if (err != null) {
                System.err.println("[app] replay save failed: " + err);
                window.setBufferStatus("クリップ保存失敗: " + err.getMessage());
                return;
            }
            try {
                String base = CLIP_TS.format(LocalDateTime.now())
                        + "_" + (rec.rankedTag != null ? rec.rankedTag : sanitize(rec.worldName))
                        + "_" + rec.cause.toLowerCase(Locale.ROOT);
                String ext = fileExtension(savedPath);
                Path dest = clipsDir().resolve(base + ext);
                Files.createDirectories(clipsDir());
                try {
                    Files.move(savedPath, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveFailed) {
                    // OBS may still hold the file or it is on another volume: copy instead.
                    Files.copy(savedPath, dest, StandardCopyOption.REPLACE_EXISTING);
                }
                rec.clipPath = dest.toString();
                store.update(rec);
                window.setBufferStatus("クリップ保存: " + dest.getFileName());
                window.refreshRecords();
            } catch (IOException e) {
                System.err.println("[app] clip move failed: " + e);
                rec.clipPath = savedPath.toString();
                store.update(rec);
                window.refreshRecords();
            }
        });
    }

    /** After the world is left: harvest .rrf + record.json and enrich the rows. */
    private void finalizeWorld(WorldSession s, List<DeathRecord> records, long startMillis) {
        try {
            RecordJsonParser.parse(s.recordJson()).ifPresent(info -> {
                for (DeathRecord rec : records) {
                    rec.finalIgtMillis = info.finalIgt();
                    rec.finalRtaMillis = info.finalRta();
                }
            });

            Optional<Path> rrfSource = RrfArchiver.findNewestRrf(s.mcsrrankedDir(), startMillis);
            if (rrfSource.isPresent()) {
                Path archived = RrfArchiver.archive(rrfSource.get(), rrfDir());
                RrfReader.read(archived).ifPresent(rrf -> {
                    String me = playerName;
                    String myUuid = rrf.playerByName(me).map(RrfReader.RrfPlayer::uuid).orElse(null);
                    for (DeathRecord rec : records) {
                        rec.matchId = rrf.matchId();
                        rec.seedOverworld = rrf.overworldSeed();
                        rec.seedNether = rrf.netherSeed();
                        rec.seedEnd = rrf.theEndSeed();
                        rec.rrfPath = archived.toString();
                        rrf.opponentOf(me).ifPresent(op -> {
                            rec.opponentName = op.nickname();
                            rec.opponentElo = op.eloRate();
                        });
                    }
                    if (myUuid != null) {
                        pairDeathTimes(records, rrf.deathsOf(myUuid), startMillis);
                    }
                });
            }
            for (DeathRecord rec : records) {
                store.update(rec);
            }
            window.refreshRecords();
        } catch (Throwable t) {
            System.err.println("[app] finalize failed for " + s.worldName() + ": " + t);
        }
    }

    /**
     * Pair our wall-clock detections with the .rrf death timeline (match-relative ms)
     * by nearest offset; skipped hunger-reset deaths simply consume no timeline entry.
     */
    private void pairDeathTimes(List<DeathRecord> records,
                                List<RrfReader.RrfTimelineEvent> deaths, long startMillis) {
        boolean[] used = new boolean[deaths.size()];
        for (DeathRecord rec : records) {
            long offset = rec.detectedAtMillis - startMillis;
            int best = -1;
            long bestDiff = PAIR_TOLERANCE_MILLIS;
            for (int i = 0; i < deaths.size(); i++) {
                if (used[i]) {
                    continue;
                }
                long diff = Math.abs(deaths.get(i).time() - offset);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = i;
                }
            }
            if (best >= 0) {
                used[best] = true;
                RrfReader.RrfTimelineEvent ev = deaths.get(best);
                rec.igtAtDeathMillis = ev.time();
                int[] pos = ev.data();
                if (pos != null && pos.length >= 3) {
                    rec.deathX = pos[0];
                    rec.deathY = pos[1];
                    rec.deathZ = pos[2];
                }
            }
        }
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String fileExtension(Path p) {
        String name = p.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".mkv";
    }
}
