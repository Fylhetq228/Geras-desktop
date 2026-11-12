package com.maxrave.simpmusic.geras.window

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.simpmusic.geras.lyrics.GerasLyricsView
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.circle_app_icon
import simpmusic.composeapp.generated.resources.geras_karaoke_hint
import simpmusic.composeapp.generated.resources.geras_no_lyrics
import java.awt.Dimension
import java.util.prefs.Preferences

private val KaraokeTop = Color(0xFF1D1613)
private val KaraokeBottom = Color(0xFF0C0A09)
private val KaraokeMuted = Color.White.copy(alpha = 0.45f)

/**
 * Karaoke window: the animated lyrics on their own, always on top of other windows.
 * F11 toggles fullscreen, Esc leaves fullscreen (or closes the window), Space plays/pauses.
 * Keeps the native frame so moving, resizing and snapping are the system's own.
 */
@Composable
fun KaraokeWindow(
    sharedViewModel: SharedViewModel,
    onCloseRequest: () -> Unit,
) {
    val prefs = remember { Preferences.userRoot().node("Geras/Karaoke") }
    val state =
        remember {
            val x = prefs.getFloat("x", Float.NaN)
            val y = prefs.getFloat("y", Float.NaN)
            WindowState(
                placement = WindowPlacement.Floating,
                position = if (x.isNaN() || y.isNaN()) WindowPosition(Alignment.Center) else WindowPosition(x.coerceAtLeast(0f).dp, y.coerceAtLeast(0f).dp),
                size = DpSize(prefs.getFloat("w", 760f).coerceAtLeast(360f).dp, prefs.getFloat("h", 440f).coerceAtLeast(220f).dp),
            )
        }
    LaunchedEffect(state.position, state.size, state.placement) {
        // Only the windowed geometry is worth remembering; fullscreen reports the monitor's.
        if (state.placement != WindowPlacement.Floating) return@LaunchedEffect
        (state.position as? WindowPosition.Absolute)?.let {
            prefs.putFloat("x", it.x.value)
            prefs.putFloat("y", it.y.value)
        }
        prefs.putFloat("w", state.size.width.value)
        prefs.putFloat("h", state.size.height.value)
    }

    Window(
        onCloseRequest = onCloseRequest,
        title = "Geras — Karaoke",
        icon = painterResource(Res.drawable.circle_app_icon),
        alwaysOnTop = true,
        state = state,
        onKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            when (e.key) {
                Key.F11 -> {
                    state.placement =
                        if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    true
                }
                Key.Escape -> {
                    if (state.placement == WindowPlacement.Fullscreen) state.placement = WindowPlacement.Floating else onCloseRequest()
                    true
                }
                Key.Spacebar -> {
                    sharedViewModel.onUIEvent(UIEvent.PlayPause)
                    true
                }
                else -> false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            val scale = window.graphicsConfiguration.defaultTransform
            window.minimumSize = Dimension((360 * scale.scaleX).toInt(), (220 * scale.scaleY).toInt())
            useDarkTitleBar(window.windowHandle)
        }
        val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
        val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
        val lyrics = screenData.lyricsData

        BoxWithConstraints(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(KaraokeTop, KaraokeBottom))),
            contentAlignment = Alignment.TopCenter,
        ) {
            // Type scales with the window: readable as a small overlay, large across a TV.
            val fontSize = minOf(maxHeight.value / 10f, maxWidth.value / 22f).coerceIn(20f, 72f).sp
            Column(Modifier.fillMaxSize().widthIn(max = 1400.dp).padding(horizontal = 32.dp)) {
                val title = listOf(screenData.nowPlayingTitle, screenData.artistName).filter { it.isNotBlank() }.joinToString(" — ")
                Text(
                    title,
                    color = KaraokeMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (lyrics == null) {
                        Text(stringResource(Res.string.geras_no_lyrics), color = KaraokeMuted, fontSize = 16.sp)
                    } else {
                        GerasLyricsView(
                            lyricsData = lyrics,
                            timeLine = sharedViewModel.timeline,
                            isPlaying = controller.isPlaying,
                            onSeekPercent = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it)) },
                            fontSize = fontSize,
                            centered = true,
                            anchor = 0.34f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    stringResource(Res.string.geras_karaoke_hint),
                    color = KaraokeMuted.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
        }
    }
}
