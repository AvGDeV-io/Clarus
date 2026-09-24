/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.support.base.utils.NamedThreadFactory
import java.util.concurrent.Executors

/**
 * Read/write contract for the Sidus trail graph storage.
 *
 * All suspend functions are safe to call from any dispatcher; implementations
 * must ensure I/O is performed off the main thread.
 */
interface SidusRepository {

    /**
     * Inserts a new [TrailNode] into persistent storage.
     *
     * @param node The fully-populated node to persist. The [TrailNode.parentNodeId]
     *   should already point to the prior node for this tab (or be null for the root).
     */
    suspend fun insertNode(node: TrailNode)

    /**
     * Returns a hot [Flow] of all [TrailNode]s for [sessionGroupId], ordered by
     * creation time. Emits a new list whenever any node is inserted or updated.
     *
     * Collect this in the Sidus UI to reactively redraw the trail graph.
     */
    fun observeNodesForSession(sessionGroupId: String): Flow<List<TrailNode>>

    /**
     * Returns all [TrailNode]s for [sessionGroupId] as a one-shot snapshot.
     * Prefer [observeNodesForSession] for UI; use this for middleware logic.
     */
    suspend fun getNodesForSession(sessionGroupId: String): List<TrailNode>

    /**
     * Returns the most recently created node for [tabId] within [sessionGroupId].
     *
     * Used by [SidusMiddleware] to determine the [TrailNode.parentNodeId] for the
     * next node in this tab's trail chain.
     *
     * @return The latest [TrailNode] for this tab, or null if no nodes exist yet.
     */
    suspend fun getLatestNodeForTab(tabId: String, sessionGroupId: String): TrailNode?

    /**
     * Returns the most recent node for [tabId] with a specific [url], within [sessionGroupId].
     *
     * Used during redirect collapsing: if a redirect-source node was already persisted
     * with the pre-redirect URL, this finds it so its URL can be updated to the final
     * destination instead of creating a duplicate node.
     *
     * @return The matching [TrailNode], or null if not found.
     */
    suspend fun getLatestNodeForTabAndUrl(tabId: String, url: String, sessionGroupId: String): TrailNode?

    /**
     * Updates the [title] field of an existing node.
     *
     * Called when a page title arrives after the node was already recorded (common —
     * [onVisited] fires before [onTitleChange]).
     */
    suspend fun updateTitle(nodeId: String, title: String)

    /**
     * Updates the [url] field of an existing node.
     * Used during redirect-chain collapsing.
     */
    suspend fun updateUrl(nodeId: String, newUrl: String)

    /**
     * Deletes all nodes for [sessionGroupId]. Called during debug wipe or future
     * session-cleanup housekeeping.
     */
    suspend fun deleteAllForSession(sessionGroupId: String)

    /**
     * Deletes all trail nodes. Debug / full-wipe use only.
     */
    suspend fun deleteAll()
}

/**
 * Default production implementation of [SidusRepository] backed by [SidusDatabase].
 *
 * Uses a single-threaded executor (same pattern as [DefaultHistoryMetadataService]) to
 * serialize all writes and prevent concurrent modification anomalies.
 *
 * @param dao        The [TrailNodeDao] from the Room database.
 * @param ioDispatcher Dispatcher for I/O operations (injectable for testing).
 */
class DefaultSidusRepository(
    private val dao: TrailNodeDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SidusRepository {

    // Single-threaded writer scope — mirrors the pattern in DefaultHistoryMetadataService.
    private val writerScope = CoroutineScope(
        Executors.newSingleThreadExecutor(
            NamedThreadFactory("SidusRepository"),
        ).asCoroutineDispatcher(),
    )

    override suspend fun insertNode(node: TrailNode) {
        withContext(ioDispatcher) {
            dao.insertNode(node)
        }
    }

    override fun observeNodesForSession(sessionGroupId: String): Flow<List<TrailNode>> =
        dao.observeNodesForSession(sessionGroupId)

    override suspend fun getNodesForSession(sessionGroupId: String): List<TrailNode> =
        withContext(ioDispatcher) {
            dao.getNodesForSession(sessionGroupId)
        }

    override suspend fun getLatestNodeForTab(tabId: String, sessionGroupId: String): TrailNode? =
        withContext(ioDispatcher) {
            dao.getLatestNodeForTab(tabId, sessionGroupId)
        }

    override suspend fun getLatestNodeForTabAndUrl(
        tabId: String,
        url: String,
        sessionGroupId: String,
    ): TrailNode? = withContext(ioDispatcher) {
        dao.getLatestNodeForTabAndUrl(tabId, url, sessionGroupId)
    }

    override suspend fun updateTitle(nodeId: String, title: String) {
        withContext(ioDispatcher) {
            dao.updateTitle(nodeId, title)
        }
    }

    override suspend fun updateUrl(nodeId: String, newUrl: String) {
        withContext(ioDispatcher) {
            dao.updateUrl(nodeId, newUrl)
        }
    }

    override suspend fun deleteAllForSession(sessionGroupId: String) {
        withContext(ioDispatcher) {
            dao.deleteAllForSession(sessionGroupId)
        }
    }

    override suspend fun deleteAll() {
        withContext(ioDispatcher) {
            dao.deleteAll()
        }
    }
}
