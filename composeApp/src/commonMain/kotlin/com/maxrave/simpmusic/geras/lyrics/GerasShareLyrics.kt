package com.maxrave.simpmusic.geras.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kmpalette.rememberPaletteState
import com.maxrave.simpmusic.extension.rememberPaletteColor
import com.maxrave.simpmusic.extension.toImmersiveBackground
import com.maxrave.simpmusic.ui.component.lyrics.ShareLyricsSheet
import com.maxrave.simpmusic.ui.component.lyrics.toShareLyricsLines
import com.maxrave.simpmusic.viewModel.SharedViewModel

private val ShareSeedFallback = Color(0xFF3A241C)

/**
 * Share card for the playing track's lyrics: upstream's sheet (pick lines, pick a background,
 * save a PNG), opened on the line being sung and seeded with the cover's colour.
 */
@Composable
fun GerasShareLyrics(
    sharedViewModel: SharedViewModel,
    onDismiss: () -> Unit,
) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val lyrics = screenData.lyricsData
    if (lyrics == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val paletteState = rememberPaletteState()
    LaunchedEffect(screenData.bitmap) { screenData.bitmap?.let { paletteState.generate(it) } }
    val seed = rememberPaletteColor(paletteState.palette, ShareSeedFallback) { it.toImmersiveBackground() }

    // Where the song is right now, in the sheet's own (source) line indices.
    val initialIndex =
        remember(lyrics) {
            val now = sharedViewModel.timeline.value.current
            lyrics.lyrics.lines
                .orEmpty()
                .indexOfLast { line -> line.startTimeMs.toLongOrNull()?.let { it <= now } == true }
                .coerceAtLeast(0)
        }

    ShareLyricsSheet(
        lines = lyrics.toShareLyricsLines(),
        songTitle = screenData.nowPlayingTitle,
        artistName = screenData.artistName,
        artwork = screenData.bitmap,
        seedColor = seed,
        initialLineIndex = initialIndex,
        onDismiss = onDismiss,
    )
}
