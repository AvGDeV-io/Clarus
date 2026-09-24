/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.storage

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local-only Room database for the Sidus trail graph feature.
 *
 * This database is **separate from Places** (the Mozilla Application Services
 * SQLite database that stores browsing history and bookmarks). Sidus stores
 * session-scoped navigation graph nodes that are not shared, synced, or
 * reconciled across restarts — they are ephemeral to the current app process.
 *
 * DB file: `sidus_trail.db`
 *
 * ## Schema versioning
 * Version 1 — initial schema with [TrailNode] table.
 * Destructive migrations are acceptable for v1; trail nodes have no user-facing
 * persistence guarantees beyond the current session.
 */
@Database(
    entities = [TrailNode::class],
    version = 1,
    exportSchema = false,
)
internal abstract class SidusDatabase : RoomDatabase() {

    abstract val trailNodeDao: TrailNodeDao

    companion object {
        const val DB_NAME = "sidus_trail.db"
    }
}
