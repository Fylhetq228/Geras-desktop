package com.maxrave.simpmusic.geras.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * A window caption drawn by the app itself (Windows custom title bar). The native side does the
 * hit testing — drag, double-click maximize, snap layouts flyout on the maximize button — from
 * the regions the UI registers through these modifiers.
 *
 * Null [LocalWindowCaption] means the platform keeps its own title bar.
 */
interface WindowCaption {
    /** The draggable strip. Everything inside it is caption unless marked [captionInteractive]. */
    @Composable
    fun Modifier.captionArea(enabled: Boolean = true): Modifier

    /** An island inside [captionArea] that keeps receiving mouse input (search capsule, buttons). */
    @Composable
    fun Modifier.captionInteractive(): Modifier

    /** Minimize, maximize/restore and close, sized to the caption height. */
    @Composable
    fun CaptionButtons(modifier: Modifier = Modifier)
}

val LocalWindowCaption = staticCompositionLocalOf<WindowCaption?> { null }
