package com.maxrave.simpmusic.geras.shell

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** The shell's shared-transition scope: player bar artwork <-> fullscreen player artwork. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalGerasSharedTransition = compositionLocalOf<SharedTransitionScope?> { null }

private const val ARTWORK_KEY = "geras-now-playing-artwork"

/** Marks the now-playing artwork as the shared element; a no-op outside the shell's scope. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.gerasSharedArtwork(visibilityScope: AnimatedVisibilityScope): Modifier {
    val shared = LocalGerasSharedTransition.current ?: return this
    return with(shared) {
        this@gerasSharedArtwork.sharedElement(
            sharedContentState = rememberSharedContentState(ARTWORK_KEY),
            animatedVisibilityScope = visibilityScope,
        )
    }
}
