/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components.menu.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import mozilla.components.compose.base.BottomSheetHandle

/**
 * The menu dialog bottom sheet.
 *
 * @param modifier [Modifier] to be applied to [BottomSheetHandle].
 * @param onRequestDismiss Invoked when accessibility services or UI automation requests
 * dismissal of the bottom sheet.
 * @param menuHandleState Configuration of the handle to use for the menu layout.
 * @param snackbarHostState The [SnackbarHostState] to display snackbars in.
 * @param cornerShape The shape of the bottom sheet's top corners.
 * @param content The children composable to be laid out.
 */
import org.mozilla.fenix.theme.Theme
import org.mozilla.fenix.theme.getThemeProvider
import org.mozilla.fenix.theme.glass.ClarusGlassSurface
import org.mozilla.fenix.theme.glass.ClarusGlassTokens

/**
 * The menu dialog bottom sheet.
 *
 * @param modifier [Modifier] to be applied to [BottomSheetHandle].
 * @param onRequestDismiss Invoked when accessibility services or UI automation requests
 * dismissal of the bottom sheet.
 * @param menuHandleState Configuration of the handle to use for the menu layout.
 * @param snackbarHostState The [SnackbarHostState] to display snackbars in.
 * @param cornerShape The shape of the bottom sheet's top corners.
 * @param content The children composable to be laid out.
 */
@Composable
fun MenuDialogBottomSheet(
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit,
    menuHandleState: MenuHandleState,
    snackbarHostState: SnackbarHostState,
    cornerShape: Shape = ClarusGlassTokens.Shapes.BottomSheet,
    content: @Composable () -> Unit,
) {
    val theme = getThemeProvider().provideTheme()
    val isDark = theme == Theme.Dark || theme == Theme.Private
    val isPrivate = theme == Theme.Private

    Box {
        ClarusGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(rememberNestedScrollInteropConnection()),
            shape = cornerShape,
            elevation = ClarusGlassTokens.Elevation.Level3,
            blurRadius = ClarusGlassTokens.Blur.Heavy,
            isDarkTheme = isDark,
            isPrivate = isPrivate,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (menuHandleState.visible) {
                    MenuBottomSheetHandle(
                        modifier = modifier,
                        onRequestDismiss = onRequestDismiss,
                        contentDescription = menuHandleState.contentDescription,
                        isMenuDragBarDark = menuHandleState.useDarkBackground,
                        cornerShape = cornerShape,
                    )
                }

                content()
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MenuBottomSheetHandle(
    modifier: Modifier = Modifier,
    onRequestDismiss: () -> Unit,
    contentDescription: String,
    isMenuDragBarDark: Boolean = false,
    cornerShape: Shape = ClarusGlassTokens.Shapes.BottomSheet,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color.Transparent,
                shape = cornerShape,
            )
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetHandle(
            onRequestDismiss = onRequestDismiss,
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

/**
 * Configuration of the handle to use for the menu layout.
 *
 * @property contentDescription Custom content description for a11y services.
 * @property useDarkBackground Whether to use a dark background (screen wide) for the handle. Defaults to `false`.
 * @property visible Whether the handle should be visible. Defaults to `true`.
 */
data class MenuHandleState(
    val contentDescription: String,
    val useDarkBackground: Boolean = false,
    val visible: Boolean = true,
)
