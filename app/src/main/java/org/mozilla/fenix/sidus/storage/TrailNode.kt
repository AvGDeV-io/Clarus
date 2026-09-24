/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.storage

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

internal const val TRAIL_NODES_TABLE_NAME = "trail_nodes"

/**
 * A single node in the Sidus visual navigation trail graph.
 *
 * Each node represents one "committed" page within a tab's session.
 * The graph is modelled as a self-referencing tree: [parentNodeId] links to the
 * immediately preceding navigation event within the same tab's back-stack,
 * while [openerTabId] captures the cross-tab spawning relationship
 * (mirroring [TabSessionState.parentId]).
 *
 * ## Lifecycle
 * Trail nodes are **session-only** — they share the lifetime of the current
 * process. Nodes are stored in a local Room database ([SidusDatabase]) and are
 * queried by [sessionGroupId]. No cross-session reconciliation is performed;
 * old sessions' nodes are orphaned and can be garbage-collected on next start.
 *
 * ## Privacy
 * This entity is never created for private-mode tabs. The [SidusMiddleware]
 * enforces this guard before reaching storage.
 *
 * @property nodeId       Stable UUID primary key for this trail node.
 * @property tabId        The [TabSessionState.id] of the tab that generated this visit.
 * @property parentNodeId FK to [nodeId] of the immediately preceding node in this tab's
 *                         history stack, or null for the root node of a tab's trail.
 * @property openerTabId  The [TabSessionState.parentId] captured at node-creation time.
 *                         Identifies which tab opened this tab (cross-tab graph edge).
 *                         Null if the tab had no recorded opener.
 * @property url          The committed URL of this navigation.
 * @property title        Page title at the time of the visit. May be empty if the title
 *                         had not yet been received when the node was recorded.
 * @property timestamp    Unix epoch milliseconds when this node was created.
 * @property visitType    Classification of how this URL was reached; stored as the
 *                         [SidusVisitType.name] string.
 * @property sessionGroupId UUID string shared by all trail nodes created in the same
 *                           app process lifetime. Generated once by [SidusSessionManager].
 */
@Entity(
    tableName = TRAIL_NODES_TABLE_NAME,
    foreignKeys = [
        ForeignKey(
            entity = TrailNode::class,
            parentColumns = ["nodeId"],
            childColumns = ["parentNodeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sessionGroupId"]),
        Index(value = ["tabId"]),
        Index(value = ["parentNodeId"]),
    ],
)
data class TrailNode(
    @PrimaryKey val nodeId: String = UUID.randomUUID().toString(),
    val tabId: String,
    val parentNodeId: String? = null,
    val openerTabId: String? = null,
    val url: String,
    val title: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val visitType: String = SidusVisitType.LINK.name,
    val sessionGroupId: String,
)
