package com.maxrave.simpmusic.geras.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxrave.domain.data.model.streams.TimeLine
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.NowPlayingScreenData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** The player reports its position this often; between reports the playhead is extrapolated. */
private const val TICK_MS = 50.0

/** Never run further than this past the last report — a stalled player must not keep "singing". */
private const val EXTRAPOLATE_CAP_MS = TICK_MS * 3

/** A report further than this from where the playhead is means a seek: jump, do not glide. */
private const val SNAP_MS = 300.0

/** Share of the remaining error corrected per frame; keeps the playhead locked without steps. */
private const val LOCK_GAIN = 0.12

/** Autoscroll comes back this long after the last manual scroll. */
private const val SCROLL_RESUME_MS = 3500L

/**
 * Geras animated lyrics: word-by-word wipe (fill style C), italic ad-libs, dots in long gaps,
 * spring autoscroll that yields to manual scrolling, click a line to seek.
 *
 * @param anchor where the sung line sits, as a fraction of the height from the top.
 */
@Composable
fun GerasLyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    isPlaying: Boolean,
    onSeekPercent: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 24.sp,
    centered: Boolean = false,
    anchor: Float = 0.25f,
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val translated = lyricsData.translatedLyrics?.first.takeIf { GerasLyricsPrefs.showTranslation }
    val lyrics = remember(lyricsData.lyrics, translated) { buildGerasLyrics(lyricsData.lyrics, translated) }
    val base = typo().headlineLarge
    val bodyStyle = typo().bodyMedium
    val style =
        remember(base, bodyStyle, fontSize, centered) {
            val align = if (centered) TextAlign.Center else TextAlign.Start
            GerasLyricsStyle(
                text = base.copy(fontSize = fontSize, lineHeight = fontSize * 1.28f, fontWeight = FontWeight.Bold, textAlign = align),
                translation = bodyStyle.copy(fontSize = fontSize * 0.58f, lineHeight = fontSize * 0.8f, textAlign = align),
            )
        }

    val speed by dataStoreManager.playbackSpeed.collectAsStateWithLifecycle(1f)
    val offsetMs by dataStoreManager.lyricsOffsetMs.collectAsStateWithLifecycle(0)
    val playhead = rememberPlayhead(timeLine, isPlaying, speed, offsetMs)

    val currentIndex by remember(lyrics) { derivedStateOf { lyrics.activeIndexAt(playhead.longValue) } }

    val listState = rememberLazyListState()
    var userScrolledAt by remember { mutableStateOf<TimeMark?>(null) }
    LaunchedEffect(lyrics, currentIndex, userScrolledAt) {
        if (!lyrics.synced) return@LaunchedEffect
        userScrolledAt?.let { mark ->
            val left = SCROLL_RESUME_MS - mark.elapsedNow().inWholeMilliseconds
            if (left > 0) delay(left)
        }
        val index = currentIndex.coerceAtLeast(0)
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (item == null) {
            // Off screen (first show, or a seek across the song): no long glide through the sheet.
            listState.scrollToItem(index)
        } else {
            // The top content padding IS the anchor, so offset 0 is exactly where the line belongs.
            listState.animateScrollBy(item.offset.toFloat(), spring(dampingRatio = 0.82f, stiffness = 90f))
        }
    }

    BoxWithConstraints(modifier) {
        val top = maxHeight * anchor
        val bottom = maxHeight * (1f - anchor)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = top, bottom = bottom),
            modifier =
                Modifier
                    .fillMaxSize()
                    // Lines dissolve into the panel at both ends instead of being cut by its edge.
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.10f to Color.Black,
                                0.78f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val dragging = event.type == PointerEventType.Move && event.changes.any { it.pressed }
                                if (event.type == PointerEventType.Scroll || dragging) {
                                    userScrolledAt = TimeSource.Monotonic.markNow()
                                }
                            }
                        }
                    },
        ) {
            items(lyrics.lines.size) { index ->
                val line = lyrics.lines[index]
                val phase =
                    when {
                        !lyrics.synced -> LinePhase.Plain
                        index == currentIndex -> LinePhase.Active
                        index < currentIndex -> LinePhase.Past
                        else -> LinePhase.Upcoming
                    }
                val seek =
                    if (lyrics.synced) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val total = timeLine.value.total
                                if (total > 0) onSeekPercent(line.startMs.toFloat() * 100f / total)
                                userScrolledAt = null
                            }
                    } else {
                        Modifier
                    }
                when (line) {
                    is GerasTextLine ->
                        GerasLyricsTextLine(
                            line = line,
                            phase = phase,
                            playhead = playhead,
                            style = style,
                            modifier = seek.padding(vertical = 10.dp),
                        )
                    is GerasGapLine ->
                        // A silence that is over leaves no hole in the sheet.
                        AnimatedVisibility(
                            visible = phase != LinePhase.Past,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            GerasGapDots(line, phase, playhead, style, seek.fillMaxWidth().padding(vertical = 10.dp))
                        }
                }
            }
        }
    }
}

/**
 * Lyrics time in ms, advanced every frame. Between the player's 50 ms reports it runs on at the
 * playback speed, so a wipe at 0.5x or 2x moves at the pace of what is actually heard; each report
 * pulls it back onto the truth a little per frame rather than in a visible step.
 */
@Composable
private fun rememberPlayhead(
    timeLine: StateFlow<TimeLine>,
    isPlaying: Boolean,
    speed: Float,
    offsetMs: Int,
): LongState {
    val playhead = remember { mutableLongStateOf(timeLine.value.current - offsetMs) }
    val playing by rememberUpdatedState(isPlaying)
    val rate by rememberUpdatedState(speed)
    val offset by rememberUpdatedState(offsetMs)
    LaunchedEffect(timeLine) {
        var reported = timeLine.value.current
        var fresh = true
        launch {
            timeLine.collect {
                reported = it.current
                fresh = true
            }
        }
        var shown = reported.toDouble()
        var reportedAt = 0L
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (fresh) {
                    reportedAt = now
                    fresh = false
                }
                val dt = if (last < 0) 0.0 else (now - last) / 1e6
                last = now
                val ahead = if (playing) ((now - reportedAt) / 1e6 * rate).coerceAtMost(EXTRAPOLATE_CAP_MS) else 0.0
                val target = reported + ahead
                val error = target - shown
                shown =
                    if (!playing || abs(error) > SNAP_MS) {
                        target
                    } else {
                        (shown + dt * rate + error * LOCK_GAIN).coerceAtMost(reported + EXTRAPOLATE_CAP_MS)
                    }
                playhead.longValue = shown.toLong() - offset
            }
        }
    }
    return playhead
}
