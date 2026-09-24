/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.sidus.storage

/**
 * Classification of how a navigation event reached a URL.
 *
 * Maps to the visit-flag semantics provided by [GeckoSession.HistoryDelegate.onVisited]
 * and [mozilla.components.concept.storage.VisitType], but scoped to the four cases
 * meaningful for Sidus trail-graph rendering.
 *
 * Stored as a String column in Room so that future variants do not require a DB migration.
 */
enum class SidusVisitType {

    /** User clicked a hyperlink or navigated via web content (default). */
    LINK,

    /** User typed or pasted a URL, or navigated from the toolbar / awesomebar. */
    TYPED,

    /**
     * Server-side HTTP redirect (3xx). Redirect chains are collapsed into a single trail
     * node at the [SidusMiddleware] level; individual hops are not stored.
     */
    REDIRECT,

    /** The same URL was reloaded (e.g. pull-to-refresh, reload button). */
    RELOAD,
}
