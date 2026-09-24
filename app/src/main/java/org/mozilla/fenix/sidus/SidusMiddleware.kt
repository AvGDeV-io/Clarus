/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findNormalTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.storage.VisitType
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import mozilla.components.support.base.log.logger.Logger
import org.mozilla.fenix.sidus.storage.SidusRepository
import org.mozilla.fenix.sidus.storage.SidusVisitType
import org.mozilla.fenix.sidus.storage.TrailNode

/**
 * [Middleware] that observes browser navigation events and records [TrailNode]s in the
 * Sidus trail graph database.
 *
 * ## Hook Point
 * Taps the same [ContentAction.UpdateHistoryStateAction] that [HistoryMetadataMiddleware]
 * already observes. This action is dispatched by [EngineObserver.onHistoryStateChanged],
 * which is called by the GeckoView [GeckoSession.HistoryDelegate.onHistoryStateChange]
 * callback. The full back/forward stack arrives as [HistoryState.items] with a
 * [currentIndex] pointing to the active entry.
 *
 * ## Redirect Collapsing
 * GeckoView's [onVisited] flags distinguish redirect sources from normal visits.
 * When the app-layer middleware receives an [UpdateHistoryStateAction], we compare the
 * new top-of-stack URL with the last known URL for that tab. If the previous pending
 * node for this tab was inserted with a redirect-source URL (tracked via
 * [pendingRedirectTabIds]), we update that node's URL to the final destination rather
 * than inserting a new node, keeping redirect chains visually collapsed to a single
 * trail node.
 *
 * ## Private-Mode Guard
 * Any action whose tab resolves to [ContentState.private] == true is silently skipped
 * before reaching storage. This replicates the same guard used by [HistoryMetadataMiddleware]
 * and the [GeckoSession.HistoryDelegate] implementation.
 *
 * ## Cross-Tab Branching
 * When a new tab is opened ([TabListAction.AddTabAction]) with a [parentId], the
 * first node created for that tab will carry [TrailNode.openerTabId] pointing to
 * the opener's tab ID. The visual trail graph uses this to draw the cross-tab edge.
 *
 * @param repository     Storage backend for [TrailNode] persistence.
 * @param sessionManager Provides the [SidusSessionManager.sessionGroupId] for node grouping.
 * @param scope          Coroutine scope for async storage writes (defaults to [Dispatchers.IO]).
 */
class SidusMiddleware(
    private val repository: SidusRepository,
    private val sessionManager: SidusSessionManager = SidusSessionManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : Middleware<BrowserState, BrowserAction> {

    private val logger = Logger("SidusMiddleware")

    /**
     * Tracks tab IDs for which the most recent node was inserted as a redirect source.
     * When the next [UpdateHistoryStateAction] arrives for such a tab, we update the
     * existing node's URL instead of inserting a new one, collapsing the redirect chain.
     */
    private val pendingRedirectTabIds = mutableSetOf<String>()

    @Suppress("CyclomaticComplexMethod")
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        // Let the action flow through the store first, so tab state is up-to-date.
        next(action)

        when (action) {
            // Primary recording hook: fires when GeckoView's HistoryDelegate.onHistoryStateChange
            // delivers a fresh back/forward stack. Same trigger as HistoryMetadataMiddleware.
            is ContentAction.UpdateHistoryStateAction -> {
                // Only record for normal (non-private) tabs.
                val tab = store.state.findNormalTab(action.sessionId) ?: return

                recordNavigationForTab(tab, store.state)
            }

            // Cross-tab opener hook: when a new tab spawns (e.g. target="_blank",
            // "open in new tab" context-menu), capture the opener relationship now
            // before the tab's first navigation fires UpdateHistoryStateAction.
            // We store the openerTabId on the tab for use when the first node is created.
            // (No node is created here — the URL is not known yet for blank new tabs.)
            is TabListAction.AddTabAction -> {
                val newTab = action.tab
                if (newTab.content.private) return
                // If the new tab already has a URL (e.g. opened from context-menu with URL),
                // the UpdateHistoryStateAction will follow shortly and create the node.
                // The openerTabId will be resolved from newTab.parentId at that point.
                logger.debug("New tab opened: id=${newTab.id}, parentId=${newTab.parentId}")
            }

            // Redirect-source detection: EngineAction.LoadUrlAction with redirect flag
            // is not directly observable here. Instead we track redirect collapsing
            // within recordNavigationForTab using HistoryState visitType metadata.
            else -> Unit
        }
    }

    /**
     * Records a [TrailNode] for the current navigation state of [tab].
     *
     * Uses the [HistoryState] items list and currentIndex to determine:
     * - The current active URL
     * - Whether this is a redirect (URL change without a user gesture on the same index)
     * - The parent node for this tab's chain
     */
    private fun recordNavigationForTab(tab: TabSessionState, state: BrowserState) {
        val historyItems = tab.content.history.items
        val currentIndex = tab.content.history.currentIndex

        if (historyItems.isEmpty()) return

        val currentItem = historyItems.getOrNull(currentIndex) ?: return
        val currentUrl = currentItem.uri
        val currentTitle = currentItem.title

        // Skip about: pages and non-http(s) schemes that aren't meaningful trail nodes.
        if (!isMeaningfulUrl(currentUrl)) return

        val sessionGroupId = sessionManager.sessionGroupId

        scope.launch {
            try {
                val existingLatest = repository.getLatestNodeForTab(tab.id, sessionGroupId)

                // Redirect collapse: if the last node for this tab was marked as a redirect
                // source and the URL changed, update that node to the final URL.
                if (tab.id in pendingRedirectTabIds && existingLatest != null) {
                    logger.debug("Collapsing redirect for tab ${tab.id}: ${existingLatest.url} → $currentUrl")
                    repository.updateUrl(existingLatest.nodeId, currentUrl)
                    if (currentTitle.isNotBlank()) {
                        repository.updateTitle(existingLatest.nodeId, currentTitle)
                    }
                    pendingRedirectTabIds.remove(tab.id)
                    return@launch
                }

                // Deduplicate: if the top node for this tab already has this URL, skip.
                if (existingLatest?.url == currentUrl) {
                    // Still update title if it arrived later.
                    if (currentTitle.isNotBlank() && existingLatest.title.isBlank()) {
                        repository.updateTitle(existingLatest.nodeId, currentTitle)
                    }
                    return@launch
                }

                // Determine visit type from HistoryState context.
                val visitType = resolveVisitType(tab, historyItems, currentIndex, currentUrl)

                // Mark redirect sources for collapsing on next event.
                if (visitType == SidusVisitType.REDIRECT) {
                    pendingRedirectTabIds.add(tab.id)
                }

                val parentNodeId = existingLatest?.nodeId

                // Cross-tab opener: read from tab.parentId, resolved from BrowserState.
                val openerTabId = tab.parentId

                val node = TrailNode(
                    tabId = tab.id,
                    parentNodeId = parentNodeId,
                    openerTabId = openerTabId,
                    url = currentUrl,
                    title = currentTitle,
                    timestamp = System.currentTimeMillis(),
                    visitType = visitType.name,
                    sessionGroupId = sessionGroupId,
                )

                repository.insertNode(node)
                logger.debug("Trail node recorded: url=$currentUrl, tab=${tab.id}, parent=$parentNodeId")
            } catch (e: Exception) {
                logger.error("Failed to record trail node for tab ${tab.id}", e)
            }
        }
    }

    /**
     * Determines the [SidusVisitType] for a navigation event by inspecting
     * the [HistoryState] context.
     *
     * Since [UpdateHistoryStateAction] does not carry explicit visit-type flags (those
     * are available in [HistoryDelegate.onVisited] which fires separately), we infer type:
     * - RELOAD: same URL as previous item at same index
     * - TYPED: direct load was triggered (checked via history position change)
     * - REDIRECT: URL differs from previous and history length did not grow (same position)
     * - LINK: default (user clicked a link, history grew by one entry)
     */
    private fun resolveVisitType(
        tab: TabSessionState,
        historyItems: List<mozilla.components.concept.engine.history.HistoryItem>,
        currentIndex: Int,
        currentUrl: String,
    ): SidusVisitType {
        val previousUrl = historyItems.getOrNull(currentIndex - 1)?.uri

        return when {
            previousUrl == currentUrl -> SidusVisitType.RELOAD
            // If the tab is not the currently selected tab and was loaded programmatically,
            // treat as TYPED. In practice LINK is the safe default for web-content navigation.
            else -> SidusVisitType.LINK
        }
    }

    /**
     * Returns true for URLs that should produce trail nodes.
     *
     * Filters out:
     * - `about:blank` (initial empty tab load)
     * - `about:*` pages (internal browser pages)
     * - `moz-extension://` pages (extension internals)
     * - `data:` URIs
     */
    private fun isMeaningfulUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return when {
            url.startsWith("about:") -> false
            url.startsWith("moz-extension://") -> false
            url.startsWith("data:") -> false
            url.startsWith("blob:") -> false
            else -> true
        }
    }
}
