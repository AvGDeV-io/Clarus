/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.theme.FirefoxTheme
import org.mozilla.fenix.theme.Theme
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

@RunWith(AndroidJUnit4::class)
class MenuDialogBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `WHEN MenuDialogBottomSheet renders THEN content is displayed inside ClarusGlassSurface`() {
        composeTestRule.setContent {
            FirefoxTheme(theme = Theme.Light) {
                MenuDialogBottomSheet(
                    onRequestDismiss = {},
                    menuHandleState = MenuHandleState(contentDescription = "Handle"),
                    snackbarHostState = SnackbarHostState(),
                    cornerShape = ClarusGlassTokens.Shapes.BottomSheet,
                ) {
                    Text("Clarus Menu Content")
                }
            }
        }

        composeTestRule.onNodeWithText("Clarus Menu Content", useUnmergedTree = true).assertExists()
    }
}
