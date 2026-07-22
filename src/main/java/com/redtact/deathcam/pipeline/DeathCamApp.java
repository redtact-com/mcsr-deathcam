package com.redtact.deathcam.pipeline;

import com.redtact.deathcam.config.AppConfig;
import com.redtact.deathcam.core.DeathCause;
import com.redtact.deathcam.core.DeathEvent;
import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;
import com.redtact.deathcam.core.Phase;
import com.redtact.deathcam.core.WorldSession;
import com.redtact.deathcam.detect.PlayerNameResolver;
import com.redtact.deathcam.detect.StatsDeathDetector;
import com.redtact.deathcam.detect.WorldTracker;
import com.redtact.deathcam.meta.DeathLogReader;
import com.redtact.deathcam.meta.EventsLogParser;
import com.redtact.deathcam.meta.HungerResetChecker;
import com.redtact.deathcam.meta.RankedApiClient;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Wires the whole death pipeline:
 * WorldTracker -> StatsDeathDetector (stats/&lt;uuid&gt;.json deaths counter) ->
 * (post-roll wait) -> OBS SaveReplayBuffer -> DB insert (cause UNKNOWN) ->
 * (world exit) -> latest.log cause + level.dat hunger-reset + .rrf/record.json/API ->
 * DB update -> prune hunger-reset / wrong-type clips.
 *
 * <p>Detection reads only the statistics file during a run; latest.log (cause) and level.dat
 * (hunger reset) are read after the match, keeping the tool within speedrun.com rule A.3.10.
 */
public final class DeathCamApp {

    private static final DateTimeFormatter CLIP_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Wait after world exit before harvesting .rrf / record.json (written on save). */
    private static final long FINALIZE_DELAY_SECONDS = 8;
    /** Tolerance when pairing our detected deaths with .rrf timeline deaths. */
    private static final long PAIR_TOLERANCE_MILLIS = 30_000;

    /** Records older than this are no longer re-tried by the enrichment sweep. */
    private static final long ENRICH_WINDOW_MILLIS = 3 * 60 * 60 * 1000L;

    private final AppConfig config;
    private final DeathStore store;
    private final ObsController obs;
    private final MainWindow window;
    private final WorldTracker worldTracker;
    private final RankedApiClient api;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "deathcam-pipeline");
                t.setDaemon(true);
                return t;
            });

    private volatile WorldSession session;
    private volatile long sessionStartMillis;
    private volatile String playerName;
    private volatile StatsDeathDetector detector;
    private final List<DeathRecord> currentWorldRecords = new ArrayList<>();

    public DeathCamApp(AppConfig config) {
        this.config = config;
        this.store = new SqliteDeathStore(config.libraryPath().resolve("deathcam.db"));
        this.obs = new ObsController(config);
        this.window = new MainWindow(config, store);
        this.worldTracker = new WorldTracker(WorldTracker.defaultLatestWorldJson(), this::onWorldChange);
        this.api = config.enableRankedApi ? new RankedApiClient() : null;
        if (config.playerName != null && !config.playerName.isBlank()) {
            this.playerName = config.playerName;
        }
    }

    public void start() {
        try {
            Files.createDirectories(clipsDir());
            Files.createDirectories(rrfDir());
        } catch (IOException e) {
            System.err.println("[app] cannot create library dirs: " + e);
        }
        obs.setStatusListener(window::setObsStatus);
        obs.setBufferSecondsListener(secs -> {
            window.setObsBufferSeconds(secs);
            warnIfBufferShort(secs);
        });
        obs.setResolutionListener(window::setBufferStatus);
        window.setObsBufferSupplier(obs::obsBufferSeconds);
        window.setObsBaseResSupplier(obs::obsBaseResolution);
        window.setObsClipResStatusSupplier(obs::obsClipResStatus);
        window.setOnSettingsSaved(this::onSettingsChanged);
        obs.start();
        worldTracker.start();
        startDashboard();
        // Retry API enrichment for any recent records still missing match data
        // (covers app restart, the last world of a session, and API indexing delay).
        if (api != null) {
            scheduler.scheduleWithFixedDelay(this::enrichPending, 25, 60, TimeUnit.SECONDS);
        }
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
        stopDetector(); // previous world's detector no longer applies

        // Ranked vs private is unknown until the match is queried, so arm recording if the
        // world could belong to any enabled category; a wrong-type clip is pruned post-match.
        boolean record = next.isRanked()
                ? (config.recordRanked || config.recordPrivate)
                : config.recordOther;
        window.setWorldStatus(next.worldName() + (record ? "" : " (記録オフ)"));
        if (!record) {
            return;
        }

        // Player name is NOT resolved here: it needs latest.log/usercache and is only used for
        // post-match enrichment, so it is resolved in finalizeWorld to keep the run free of
        // vanilla-file reads. Detection (stats file) needs no name.
        startDetector(next);
        // Do not force OBS's RecRBTime — the user sizes their own buffer (larger than
        // pre+post for timing headroom); we only read it and warn if it's too short.
        obs.ensureReplayBufferStarted();
    }

    private void startDetector(WorldSession s) {
        StatsDeathDetector d = new StatsDeathDetector(s.worldPath(), delta -> onStatsDeaths(s, delta));
        detector = d;
        d.start();
    }

    private void stopDetector() {
        StatsDeathDetector d = detector;
        if (d != null) {
            d.stop();
            detector = null;
        }
    }

    /**
     * Death -> detection latency: an in-game death only flushes {@code minecraft:deaths} to the
     * stats file when the death screen saves, which measured ~3-4 s on a real 1.16.1 ranked match
     * (E2E 2026-07-22: latest.log death line 13:51:37 -> stats flush 13:51:40.4 -> fire +60 ms).
     * The clip is unaffected (the buffer holds the past), but OBS's buffer must lead the app window
     * (pre+post) by at least this much plus a little OBS headroom or the pre-roll gets clipped.
     */
    private static final int DETECTION_LATENCY_SECONDS = 4;
    private static final int OBS_HEADROOM_SECONDS = 5;
    private static final int BUFFER_MARGIN_SECONDS = DETECTION_LATENCY_SECONDS + OBS_HEADROOM_SECONDS;

    /**
     * The saved clip is always [saveTime - RecRBTime, saveTime]; detection+save latency shifts
     * that window forward, so if OBS's buffer isn't comfortably longer than pre+post the lead-in
     * gets clipped. Warn the user to enlarge the OBS buffer.
     */
    /** React to a settings change: re-apply OBS clip resolution, re-validate, enforce retention. */
    private void onSettingsChanged() {
        obs.applyClipResolution();
        warnIfBufferShort(obs.obsBufferSeconds());
        scheduler.execute(this::enforceRetention);
    }

    /**
     * Keep the clips folder under the configured size cap by deleting the oldest videos.
     * Only the video files are removed — the death records (metadata) and any .rrf are kept,
     * so statistics and seed/coord data survive; the row's clipPath is cleared.
     */
    private void enforceRetention() {
        if (!config.retentionEnabled || config.maxLibraryGb <= 0) {
            return;
        }
        try {
            long cap = (long) (config.maxLibraryGb * 1024L * 1024L * 1024L);
            int removed = RetentionPolicy.enforce(store, cap);
            if (removed > 0) {
                System.out.println("[app] retention: removed " + removed
                        + " old clip video(s); metadata kept");
                window.refreshRecords();
            }
        } catch (Exception e) {
            System.err.println("[app] retention failed: " + e);
        }
    }

    private void warnIfBufferShort(int obsBufferSeconds) {
        if (obsBufferSeconds <= 0) {
            return;
        }
        int windowSecs = config.preRollSeconds + config.postRollSeconds;
        int recommended = windowSecs + BUFFER_MARGIN_SECONDS;
        if (obsBufferSeconds < recommended) {
            window.setBufferStatus("⚠ OBS バッファ " + obsBufferSeconds + "s < 設定 " + windowSecs
                    + "s。頭欠けの恐れ: OBS を " + recommended + "s 以上に設定してください");
        }
    }

    /**
     * The statistics counter jumped by {@code delta} (normally 1). We do not yet know the cause
     * (that comes from latest.log after the match) or whether it was a hunger reset (level.dat,
     * also post-match), so every death is captured now and pruned later if the config excludes it.
     */
    private void onStatsDeaths(WorldSession s, int delta) {
        if (!s.equals(session)) {
            return; // detector for a world we already left
        }
        long at = System.currentTimeMillis();
        window.setBufferStatus("死亡検知 (stats): +" + delta);
        // Post-roll: keep recording a bit past the death, then snapshot the buffer.
        scheduler.schedule(() -> captureDeaths(s, delta, at), config.postRollSeconds, TimeUnit.SECONDS);
    }

    private void captureDeaths(WorldSession s, int delta, long detectedAtMillis) {
        Phase phase = EventsLogParser.parse(s.eventsLog()).phaseAt(Long.MAX_VALUE);
        DeathRecord newest = null;
        for (int i = 0; i < delta; i++) {
            DeathRecord rec = new DeathRecord();
            rec.worldName = s.worldName();
            rec.rankedTag = s.rankedTag();
            rec.detectedAtMillis = detectedAtMillis;
            rec.cause = DeathCause.UNKNOWN.name(); // resolved from latest.log at finalize
            rec.phase = phase.name();
            store.insert(rec);
            synchronized (this) {
                if (s.equals(session)) {
                    currentWorldRecords.add(rec);
                }
            }
            newest = rec;
        }
        window.refreshRecords();
        // One buffer snapshot covers the death window; attach it to the most recent death.
        if (newest != null) {
            saveClipFor(newest);
        }
    }

    private void saveClipFor(DeathRecord rec) {
        obs.saveReplayBuffer().whenComplete((savedPath, err) -> {
            if (err != null) {
                System.err.println("[app] replay save failed: " + err);
                window.setBufferStatus("クリップ保存失敗: " + err.getMessage());
                return;
            }
            try {
                String base = CLIP_TS.format(LocalDateTime.now())
                        + "_" + (rec.rankedTag != null ? rec.rankedTag : sanitize(rec.worldName))
                        + "_" + rec.phase.toLowerCase(Locale.ROOT);
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
                enforceRetention();
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
            // Resolve the player name now (post-match) if not configured: it reads latest.log /
            // usercache and enrichment needs it, so it is deferred out of the run.
            if (playerName == null || playerName.isBlank()) {
                PlayerNameResolver.resolve(s.instanceDir()).ifPresent(n -> playerName = n);
            }
            // Death cause: read latest.log now that the run is over (A.3.10 forbids reading
            // vanilla logs before/during a run, not after). Pair the newest log deaths to our
            // stats-detected deaths in chronological order.
            enrichCausesFromLog(s, records);
            // Hunger reset: the level.dat spawn point, also read only after the run.
            if (HungerResetChecker.isBedOrAnchorRespawn(s.worldPath())) {
                for (DeathRecord rec : records) {
                    rec.hungerReset = true;
                }
            }

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
                        // Opponent name / Elo are intentionally NOT stored (self-focused tool).
                    }
                    if (myUuid != null) {
                        pairDeathTimes(records, rrf.deathsOf(myUuid), startMillis);
                    }
                });
            }

            // Ranked API: seed type / bastion / end towers / result + death IGT (no opponent/elo).
            enrichFromApi(records);

            // Offline fallback: approximate the final death IGT from events.log (leave_world),
            // for the case where neither .rrf nor the API produced a time.
            fillIgtFromEventsLog(s, records);

            // Persist survivors; drop hunger-reset / wrong-type clips the config excludes.
            for (DeathRecord rec : records) {
                if (shouldPrune(rec)) {
                    prune(rec);
                } else {
                    store.update(rec);
                }
            }
            window.refreshRecords();
        } catch (Throwable t) {
            System.err.println("[app] finalize failed for " + s.worldName() + ": " + t);
        }
    }

    /**
     * Fill cause / killer / raw message from latest.log (post-match). The log holds every death
     * this session; our K stats-detected deaths for this world are its most recent K death lines,
     * so the last-K log deaths pair to our records in chronological order.
     */
    private void enrichCausesFromLog(WorldSession s, List<DeathRecord> records) {
        String me = playerName;
        if (me == null || me.isBlank() || records.isEmpty()) {
            return;
        }
        List<DeathEvent> logDeaths = DeathLogReader.read(s.latestLog(), me);
        if (logDeaths.isEmpty()) {
            return;
        }
        int k = records.size();
        int offset = Math.max(0, logDeaths.size() - k);
        for (int i = 0; i < k && offset + i < logDeaths.size(); i++) {
            DeathEvent ev = logDeaths.get(offset + i);
            DeathRecord rec = records.get(i);
            rec.cause = ev.cause().name();
            rec.killer = ev.killer();
            rec.rawMessage = ev.rawMessage();
        }
    }

    /**
     * Enrich a group of same-match deaths from the Ranked API. Correlates the deaths to a match
     * by wall-clock time window, then fills seed/result and pairs the per-death IGT
     * from the match timeline. No-op when the API is disabled, the player is unknown, or the match
     * is not yet indexed (the periodic sweep retries later).
     */
    private void enrichFromApi(List<DeathRecord> records) {
        if (api == null || records.isEmpty()) {
            return;
        }
        String me = playerName;
        if (me == null || me.isBlank()) {
            return;
        }
        long probe = records.get(0).detectedAtMillis;
        RankedApiClient.ApiMatch match = null;
        for (RankedApiClient.ApiMatch m : api.recentMatches(me, 15)) {
            if (m.containsWallClock(probe, PAIR_TOLERANCE_MILLIS)) {
                match = m;
                break;
            }
        }
        if (match == null) {
            return;
        }
        RankedApiClient.ApiMatch detail = api.matchDetail(match.id()).orElse(match);
        String myUuid = detail.player(me).map(RankedApiClient.ApiPlayer::uuid).orElse(null);
        for (DeathRecord rec : records) {
            rec.matchId = detail.id();
            rec.matchType = detail.type();
            if (detail.resultTimeMs() != null) {
                rec.finalRtaMillis = detail.resultTimeMs();
            }
            rec.seedType = detail.seedType();
            rec.bastionType = detail.bastionType();
            RankedApiClient.ApiSeed seed = detail.seed();
            if (seed != null) {
                rec.seedId = seed.id();
                rec.endTowers = joinInts(seed.endTowers());
                if (seed.variations() != null && !seed.variations().isEmpty()) {
                    rec.seedVariations = String.join(",", seed.variations());
                }
            }
            rec.resultKind = resultKind(detail, myUuid);
            // Opponent name and Elo (other players' data) are intentionally NOT stored — this tool
            // only keeps the player's own death (cause / clip / IGT / seed / phase / result).
        }
        if (myUuid != null) {
            pairApiDeaths(records, detail.deathsOf(myUuid), detail.startMillis());
        }
    }

    /** Coarse offline IGT fallback: give the latest un-timed death the leave_world IGT. */
    private void fillIgtFromEventsLog(WorldSession s, List<DeathRecord> records) {
        EventsLogParser ev = EventsLogParser.parse(s.eventsLog());
        Long igt = ev.leaveWorldIgt().or(ev::lastIgt).orElse(null);
        if (igt == null) {
            return;
        }
        DeathRecord latest = null;
        for (DeathRecord rec : records) {
            if (rec.igtAtDeathMillis == null
                    && (latest == null || rec.detectedAtMillis > latest.detectedAtMillis)) {
                latest = rec;
            }
        }
        if (latest != null) {
            latest.igtAtDeathMillis = igt;
        }
    }

    /**
     * Background sweep: re-try API enrichment for recent records that still lack match data.
     * Groups by world (one world = one match) and enriches each group.
     */
    private void enrichPending() {
        if (api == null) {
            return;
        }
        try {
            long cutoff = System.currentTimeMillis() - ENRICH_WINDOW_MILLIS;
            Map<String, List<DeathRecord>> byWorld = new LinkedHashMap<>();
            for (DeathRecord r : store.listRecent(100)) {
                if (r.matchType != null || r.detectedAtMillis < cutoff || r.worldName == null) {
                    continue;
                }
                byWorld.computeIfAbsent(r.worldName, k -> new ArrayList<>()).add(r);
            }
            boolean changed = false;
            for (List<DeathRecord> group : byWorld.values()) {
                enrichFromApi(group);
                for (DeathRecord rec : group) {
                    if (rec.matchType != null) {
                        store.update(rec);
                        changed = true;
                    }
                }
            }
            // Prune clips whose confirmed type the user isn't recording.
            for (DeathRecord r : store.listRecent(100)) {
                if (r.detectedAtMillis >= cutoff && shouldPrune(r)) {
                    prune(r);
                    changed = true;
                }
            }
            if (changed) {
                window.refreshRecords();
            }
            enforceRetention();
        } catch (Throwable t) {
            System.err.println("[app] enrichPending failed: " + t);
        }
    }

    /** A recorded death the config excludes: an intentional hunger reset, or a now-known match type. */
    private boolean shouldPrune(DeathRecord rec) {
        if (config.skipHungerReset && rec.hungerReset) {
            return true;    // intentional hunger-reset death (bed/anchor spawn)
        }
        if (rec.matchType == null) {
            return false;   // unknown type (practice/non-ranked) — keep
        }
        if (rec.matchType == 2 && !config.recordRanked) {
            return true;
        }
        return rec.matchType == 3 && !config.recordPrivate;
    }

    private String pruneReason(DeathRecord rec) {
        if (config.skipHungerReset && rec.hungerReset) {
            return "hunger reset";
        }
        return (rec.matchType != null && rec.matchType == 2 ? "ranked" : "private") + " recording off";
    }

    /** Remove a death entirely: clip file, archived replay, and DB row. */
    private void prune(DeathRecord rec) {
        deleteFileQuietly(rec.clipPath);
        deleteFileQuietly(rec.rrfPath);
        if (rec.id != 0) {
            store.delete(rec.id);
        }
        synchronized (this) {
            currentWorldRecords.removeIf(r -> r.id == rec.id);
        }
        System.out.println("[app] pruned " + rec.cause + " clip (" + pruneReason(rec) + ")");
    }

    private static void deleteFileQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (Exception e) {
            System.err.println("[app] could not delete " + path + ": " + e);
        }
    }

    /** Pair wall-clock deaths to the API match timeline (match-relative ms); IGT only. */
    private void pairApiDeaths(List<DeathRecord> records,
                               List<RankedApiClient.ApiTimeline> deaths, long startMillis) {
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
                if (rec.igtAtDeathMillis == null) {
                    rec.igtAtDeathMillis = deaths.get(best).time();
                }
            }
        }
    }

    private static String resultKind(RankedApiClient.ApiMatch m, String myUuid) {
        String winner = m.resultUuid();
        if (winner == null) {
            return m.forfeited() ? "FORFEIT" : "DRAW";
        }
        if (myUuid != null && winner.equals(myUuid)) {
            return "WIN";
        }
        return "LOSS";
    }

    private static String joinInts(int[] values) {
        if (values == null || values.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.toString();
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
