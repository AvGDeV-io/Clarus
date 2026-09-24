/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus

import java.util.UUID

/**
 * Provides the session group ID that scopes all Sidus trail nodes to the
 * current app process lifetime.
 *
 * ## Session Boundary Model
 * A "session" in Sidus terms is a single **process lifetime** — i.e. from the
 * time the app is cold-started until the process is killed. This mirrors the
 * existing session-only data model used by [BrowserState] tabs, which are
 * also not guaranteed to survive a process restart without explicit snapshot
 * serialization.
 *
 * No cross-session reconciliation is performed in v1. When the process starts,
 * a fresh UUID is generated. All nodes created during this run carry that ID.
 * Nodes from previous sessions remain in the DB as orphaned rows (queryable
 * by their old sessionGroupId) but are not surfaced in the UI.
 *
 * ## Thread safety
 * [sessionGroupId] is initialized lazily on first access via [lazy(LazyThreadSafetyMode.SYNCHRONIZED)].
 * It is safe to call from any thread.
 */
object SidusSessionManager {

    /**
     * A stable UUID string identifying the current app-process session.
     * Generated once on first access; never changes within a process lifetime.
     */
    val sessionGroupId: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UUID.randomUUID().toString()
    }
}
