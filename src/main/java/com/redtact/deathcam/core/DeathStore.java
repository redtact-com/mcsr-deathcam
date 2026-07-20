package com.redtact.deathcam.core;

import java.util.List;

/** Persistence for death records (SQLite). */
public interface DeathStore extends AutoCloseable {

    /** Insert and return the record with {@link DeathRecord#id} populated. */
    DeathRecord insert(DeathRecord record);

    /** Update all nullable/late fields of an existing record by id. */
    void update(DeathRecord record);

    /** Most recent records, newest first. */
    List<DeathRecord> listRecent(int limit);

    /** All records for one world (a match can contain several deaths). */
    List<DeathRecord> listByWorld(String worldName);

    @Override
    void close();
}
