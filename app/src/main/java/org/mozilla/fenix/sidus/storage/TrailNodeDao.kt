/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for Sidus trail node persistence.
 *
 * All suspend functions are safe to call from any coroutine dispatcher; Room
 * dispatches the actual I/O to its own thread pool.
 */
@Dao
interface TrailNodeDao {

    /**
     * Inserts a new [TrailNode]. Replaces on conflict (e.g. duplicate nodeId, which
     * should never happen with UUIDs but provides a safe fallback for tests).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: TrailNode)

    /**
     * Returns all nodes belonging to [sessionGroupId], ordered by creation time.
     * Used to populate the full Sidus trail graph for the current session.
     */
    @Query("SELECT * FROM $TRAIL_NODES_TABLE_NAME WHERE sessionGroupId = :sessionGroupId ORDER BY timestamp ASC")
    fun observeNodesForSession(sessionGroupId: String): Flow<List<TrailNode>>

    /**
     * Snapshot query — returns nodes for [sessionGroupId] without Flow subscription.
     */
    @Query("SELECT * FROM $TRAIL_NODES_TABLE_NAME WHERE sessionGroupId = :sessionGroupId ORDER BY timestamp ASC")
    suspend fun getNodesForSession(sessionGroupId: String): List<TrailNode>

    /**
     * Returns direct children of [parentNodeId], i.e. navigations that branched from it.
     * Used to render the branching edges in the trail graph.
     */
    @Query("SELECT * FROM $TRAIL_NODES_TABLE_NAME WHERE parentNodeId = :parentNodeId ORDER BY timestamp ASC")
    suspend fun getChildrenOf(parentNodeId: String): List<TrailNode>

    /**
     * Finds the most recent node for [tabId] with [url] within [sessionGroupId].
     * Used during redirect collapsing to locate an existing pending node to update
     * rather than inserting a duplicate.
     */
    @Query(
        """
        SELECT * FROM $TRAIL_NODES_TABLE_NAME
        WHERE tabId = :tabId AND url = :url AND sessionGroupId = :sessionGroupId
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestNodeForTabAndUrl(tabId: String, url: String, sessionGroupId: String): TrailNode?

    /**
     * Returns the most recently inserted node for [tabId] within [sessionGroupId].
     * Used as the [TrailNode.parentNodeId] for the next node in this tab's trail.
     */
    @Query(
        """
        SELECT * FROM $TRAIL_NODES_TABLE_NAME
        WHERE tabId = :tabId AND sessionGroupId = :sessionGroupId
        ORDER BY timestamp DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestNodeForTab(tabId: String, sessionGroupId: String): TrailNode?

    /**
     * Updates the [title] of an existing node identified by [nodeId].
     * Called when [ContentDelegate.onTitleChange] fires after a node was already recorded.
     */
    @Query("UPDATE $TRAIL_NODES_TABLE_NAME SET title = :title WHERE nodeId = :nodeId")
    suspend fun updateTitle(nodeId: String, title: String)

    /**
     * Updates the [url] of an existing node identified by [nodeId].
     * Used to collapse redirect chains: the redirect-source node is updated
     * to the final redirect-target URL, avoiding a separate node per hop.
     */
    @Query("UPDATE $TRAIL_NODES_TABLE_NAME SET url = :url WHERE nodeId = :nodeId")
    suspend fun updateUrl(nodeId: String, url: String)

    /**
     * Deletes all nodes for [sessionGroupId]. Called as a cleanup hook (optional).
     */
    @Query("DELETE FROM $TRAIL_NODES_TABLE_NAME WHERE sessionGroupId = :sessionGroupId")
    suspend fun deleteAllForSession(sessionGroupId: String)

    /**
     * Deletes all trail nodes. Used in debug tooling or full data wipe scenarios.
     */
    @Query("DELETE FROM $TRAIL_NODES_TABLE_NAME")
    suspend fun deleteAll()
}
