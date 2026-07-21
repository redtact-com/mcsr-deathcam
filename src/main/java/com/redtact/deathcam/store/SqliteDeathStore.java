package com.redtact.deathcam.store;

import com.redtact.deathcam.core.DeathRecord;
import com.redtact.deathcam.core.DeathStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed {@link DeathStore}. One connection, all public methods
 * synchronized — callers may be on any thread.
 */
public final class SqliteDeathStore implements DeathStore {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS deaths (
              id                  INTEGER PRIMARY KEY AUTOINCREMENT,
              world_name          TEXT,
              ranked_tag          TEXT,
              match_id            INTEGER NULL,
              detected_at_millis  INTEGER NOT NULL,
              cause               TEXT,
              killer              TEXT,
              raw_message         TEXT,
              phase               TEXT,
              igt_at_death_millis INTEGER NULL,
              final_igt_millis    INTEGER NULL,
              final_rta_millis    INTEGER NULL,
              seed_overworld      TEXT,
              seed_nether         TEXT,
              seed_end            TEXT,
              death_x             INTEGER NULL,
              death_y             INTEGER NULL,
              death_z             INTEGER NULL,
              opponent_name       TEXT,
              opponent_elo        INTEGER NULL,
              hunger_reset        INTEGER NOT NULL DEFAULT 0,
              clip_path           TEXT,
              rrf_path            TEXT,
              notes               TEXT,
              match_type          INTEGER NULL,
              seed_id             TEXT,
              seed_type           TEXT,
              bastion_type        TEXT,
              end_towers          TEXT,
              seed_variations     TEXT,
              result_kind         TEXT,
              elo_before          INTEGER NULL,
              elo_change          INTEGER NULL
            )""";

    /** Columns added after 0.1.0; applied to pre-existing DBs via ALTER TABLE. */
    private static final String[][] MIGRATIONS = {
            {"match_type", "INTEGER"},
            {"seed_id", "TEXT"},
            {"seed_type", "TEXT"},
            {"bastion_type", "TEXT"},
            {"end_towers", "TEXT"},
            {"seed_variations", "TEXT"},
            {"result_kind", "TEXT"},
            {"elo_before", "INTEGER"},
            {"elo_change", "INTEGER"},
    };

    private static final String INSERT_SQL = """
            INSERT INTO deaths (
              world_name, ranked_tag, match_id, detected_at_millis, cause, killer,
              raw_message, phase, igt_at_death_millis, final_igt_millis, final_rta_millis,
              seed_overworld, seed_nether, seed_end, death_x, death_y, death_z,
              opponent_name, opponent_elo, hunger_reset, clip_path, rrf_path, notes,
              match_type, seed_id, seed_type, bastion_type, end_towers, seed_variations,
              result_kind, elo_before, elo_change
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""";

    private static final String UPDATE_SQL = """
            UPDATE deaths SET
              world_name = ?, ranked_tag = ?, match_id = ?, detected_at_millis = ?,
              cause = ?, killer = ?, raw_message = ?, phase = ?,
              igt_at_death_millis = ?, final_igt_millis = ?, final_rta_millis = ?,
              seed_overworld = ?, seed_nether = ?, seed_end = ?,
              death_x = ?, death_y = ?, death_z = ?,
              opponent_name = ?, opponent_elo = ?, hunger_reset = ?,
              clip_path = ?, rrf_path = ?, notes = ?,
              match_type = ?, seed_id = ?, seed_type = ?, bastion_type = ?, end_towers = ?,
              seed_variations = ?, result_kind = ?, elo_before = ?, elo_change = ?
            WHERE id = ?""";

    private static final String SELECT_COLUMNS = """
            SELECT id, world_name, ranked_tag, match_id, detected_at_millis, cause, killer,
                   raw_message, phase, igt_at_death_millis, final_igt_millis, final_rta_millis,
                   seed_overworld, seed_nether, seed_end, death_x, death_y, death_z,
                   opponent_name, opponent_elo, hunger_reset, clip_path, rrf_path, notes,
                   match_type, seed_id, seed_type, bastion_type, end_towers, seed_variations,
                   result_kind, elo_before, elo_change
            FROM deaths """;

    private final Connection conn;

    public SqliteDeathStore(Path dbFile) {
        try {
            Path parent = dbFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute(CREATE_TABLE);
            }
            migrate();
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("failed to open death store at " + dbFile, e);
        }
    }

    /** Add any columns missing from a DB created by an older version. */
    private void migrate() throws SQLException {
        java.util.Set<String> existing = new java.util.HashSet<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(deaths)")) {
            while (rs.next()) {
                existing.add(rs.getString("name"));
            }
        }
        for (String[] col : MIGRATIONS) {
            if (!existing.contains(col[0])) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE deaths ADD COLUMN " + col[0] + " " + col[1]);
                }
            }
        }
    }

    @Override
    public synchronized DeathRecord insert(DeathRecord record) {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            bindFields(ps, record);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    record.id = keys.getLong(1);
                }
            }
            return record;
        } catch (SQLException e) {
            throw new IllegalStateException("insert failed", e);
        }
    }

    @Override
    public synchronized void update(DeathRecord record) {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
            int next = bindFields(ps, record);
            ps.setLong(next, record.id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("update failed for id=" + record.id, e);
        }
    }

    @Override
    public synchronized List<DeathRecord> listRecent(int limit) {
        String sql = SELECT_COLUMNS + " ORDER BY detected_at_millis DESC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            return readAll(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("listRecent failed", e);
        }
    }

    @Override
    public synchronized List<DeathRecord> listByWorld(String worldName) {
        String sql = SELECT_COLUMNS + " WHERE world_name = ? ORDER BY detected_at_millis ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, worldName);
            return readAll(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("listByWorld failed", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            System.err.println("failed to close death store: " + e);
        }
    }

    /** Binds all non-id fields in table order; returns the next free parameter index. */
    private static int bindFields(PreparedStatement ps, DeathRecord r) throws SQLException {
        int i = 1;
        ps.setString(i++, r.worldName);
        ps.setString(i++, r.rankedTag);
        setNullableLong(ps, i++, r.matchId);
        ps.setLong(i++, r.detectedAtMillis);
        ps.setString(i++, r.cause);
        ps.setString(i++, r.killer);
        ps.setString(i++, r.rawMessage);
        ps.setString(i++, r.phase);
        setNullableLong(ps, i++, r.igtAtDeathMillis);
        setNullableLong(ps, i++, r.finalIgtMillis);
        setNullableLong(ps, i++, r.finalRtaMillis);
        ps.setString(i++, r.seedOverworld);
        ps.setString(i++, r.seedNether);
        ps.setString(i++, r.seedEnd);
        setNullableInt(ps, i++, r.deathX);
        setNullableInt(ps, i++, r.deathY);
        setNullableInt(ps, i++, r.deathZ);
        ps.setString(i++, r.opponentName);
        setNullableInt(ps, i++, r.opponentElo);
        ps.setInt(i++, r.hungerReset ? 1 : 0);
        ps.setString(i++, r.clipPath);
        ps.setString(i++, r.rrfPath);
        ps.setString(i++, r.notes);
        setNullableInt(ps, i++, r.matchType);
        ps.setString(i++, r.seedId);
        ps.setString(i++, r.seedType);
        ps.setString(i++, r.bastionType);
        ps.setString(i++, r.endTowers);
        ps.setString(i++, r.seedVariations);
        ps.setString(i++, r.resultKind);
        setNullableInt(ps, i++, r.eloBefore);
        setNullableInt(ps, i++, r.eloChange);
        return i;
    }

    private static void setNullableLong(PreparedStatement ps, int idx, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setLong(idx, value);
        }
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, value);
        }
    }

    private static List<DeathRecord> readAll(PreparedStatement ps) throws SQLException {
        List<DeathRecord> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(readRecord(rs));
            }
        }
        return out;
    }

    private static DeathRecord readRecord(ResultSet rs) throws SQLException {
        DeathRecord r = new DeathRecord();
        r.id = rs.getLong("id");
        r.worldName = rs.getString("world_name");
        r.rankedTag = rs.getString("ranked_tag");
        r.matchId = getNullableLong(rs, "match_id");
        r.detectedAtMillis = rs.getLong("detected_at_millis");
        r.cause = rs.getString("cause");
        r.killer = rs.getString("killer");
        r.rawMessage = rs.getString("raw_message");
        r.phase = rs.getString("phase");
        r.igtAtDeathMillis = getNullableLong(rs, "igt_at_death_millis");
        r.finalIgtMillis = getNullableLong(rs, "final_igt_millis");
        r.finalRtaMillis = getNullableLong(rs, "final_rta_millis");
        r.seedOverworld = rs.getString("seed_overworld");
        r.seedNether = rs.getString("seed_nether");
        r.seedEnd = rs.getString("seed_end");
        r.deathX = getNullableInt(rs, "death_x");
        r.deathY = getNullableInt(rs, "death_y");
        r.deathZ = getNullableInt(rs, "death_z");
        r.opponentName = rs.getString("opponent_name");
        r.opponentElo = getNullableInt(rs, "opponent_elo");
        r.hungerReset = rs.getInt("hunger_reset") != 0;
        r.clipPath = rs.getString("clip_path");
        r.rrfPath = rs.getString("rrf_path");
        r.notes = rs.getString("notes");
        r.matchType = getNullableInt(rs, "match_type");
        r.seedId = rs.getString("seed_id");
        r.seedType = rs.getString("seed_type");
        r.bastionType = rs.getString("bastion_type");
        r.endTowers = rs.getString("end_towers");
        r.seedVariations = rs.getString("seed_variations");
        r.resultKind = rs.getString("result_kind");
        r.eloBefore = getNullableInt(rs, "elo_before");
        r.eloChange = getNullableInt(rs, "elo_change");
        return r;
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }
}
