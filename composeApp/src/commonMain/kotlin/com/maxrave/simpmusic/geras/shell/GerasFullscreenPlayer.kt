package com.maxrave.simpmusic.geras.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.kmpalette.rememberPaletteState
import com.maxrave.common.Config.MAIN_PLAYER
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.domain.mediaservice.handler.RepeatState
import com.maxrave.simpmusic.expect.ui.MediaPlayerViewWithSubtitle
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.extension.rememberPaletteColor
import com.maxrave.simpmusic.extension.toImmersiveBackground
import com.maxrave.simpmusic.geras.window.LocalWindowCaption
import com.maxrave.simpmusic.geras.lyrics.GerasLyricsView
import com.maxrave.simpmusic.ui.component.NowPlayingBottomSheet
import com.maxrave.simpmusic.ui.icon.KeyboardArrowDown
import com.maxrave.simpmusic.ui.icon.MoreVert
import com.maxrave.simpmusic.ui.icon.Movie
import com.maxrave.simpmusic.ui.icon.MusicNote
import com.maxrave.simpmusic.ui.icon.Pause
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Repeat
import com.maxrave.simpmusic.ui.icon.RepeatOne
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.Shuffle
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.SkipNext
import com.maxrave.simpmusic.ui.icon.SkipPrevious
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.geras_audio
import simpmusic.composeapp.generated.resources.geras_close_player
import simpmusic.composeapp.generated.resources.geras_more
import simpmusic.composeapp.generated.resources.geras_no_lyrics
import simpmusic.composeapp.generated.resources.geras_no_video
import simpmusic.composeapp.generated.resources.geras_search_short
import simpmusic.composeapp.generated.resources.geras_show_cover
import simpmusic.composeapp.generated.resources.geras_show_lyrics
import simpmusic.composeapp.generated.resources.geras_up_next
import simpmusic.composeapp.generated.resources.geras_video
import kotlin.math.roundToInt

/** Tuning for the fullscreen player — one place. */
private object FsSpec {
    val MaxContentWidth = 720.dp
    val ArtworkMax = 440.dp
    val ProgressHeight = 4.dp
    val ProgressHoverHeight = 6.dp
    val PlayButton = 64.dp

    /** Warm, muted base the cover colour is pulled toward. */
    val WarmBase = Color(0xFF2A1A14)
    const val WARM_PULL = 0.3f
    val Fallback = Color(0xFF1E1714)
    val Ink = Color(0xFFF7EEE8)
    val InkMuted = Color(0xFFF7EEE8).copy(alpha = 0.6f)
}

private enum class Middle { Cover, Lyrics }

/**
 * Layout B fullscreen player: everything centred. Top row: audio/video pill, search capsule, menu.
 * Middle: artwork (or the clip) ↔ lyrics. Then title, artist with avatar, a thin progress bar with
 * a hover time preview, times with an "up next" marquee between them, and the controls card.
 */
@Composable
fun GerasFullscreenPlayer(
    visibilityScope: AnimatedVisibilityScope,
    navController: NavController,
    onOpenSearch: () -> Unit,
    onClose: () -> Unit,
    sharedViewModel: SharedViewModel = koinInject(),
    handler: MediaPlayerHandler = koinInject(),
    dataStoreManager: DataStoreManager = koinInject(),
) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val watchVideo by sharedViewModel.getVideo.collectAsStateWithLifecycle()
    val queueData by handler.queueData.collectAsStateWithLifecycle()
    val currentIndex by handler.currentSongIndex.collectAsStateWithLifecycle()
    val song = nowPlaying?.songEntity
    val scope = rememberCoroutineScope()

    // Cover colours: kmpalette off the artwork bitmap, pulled warm and muted, faded between tracks.
    val paletteState = rememberPaletteState()
    LaunchedEffect(Unit) {
        snapshotFlow { screenData.bitmap }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { paletteState.generate(it) }
    }
    val base =
        rememberPaletteColor(paletteState.palette, FsSpec.Fallback) {
            lerp(it.toImmersiveBackground(), FsSpec.WarmBase, FsSpec.WARM_PULL)
        }
    val surface = Color.White.copy(alpha = 0.08f).compositeOver(base)

    var middle by rememberSaveable { mutableStateOf(Middle.Cover) }
    var showMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    if (showMenu && song != null) {
        NowPlayingBottomSheet(
            onDismiss = { showMenu = false },
            navController = navController,
            song = song,
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
            onNavigateToOtherScreen = onClose,
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.55f))))
            // Swallow clicks so nothing underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Escape -> onClose().let { true }
                    Key.Spacebar -> sharedViewModel.onUIEvent(UIEvent.PlayPause).let { true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            TopRow(
                isVideoAvailable = screenData.isVideo,
                watchVideo = watchVideo,
                onToggleVideo = { video -> scope.launch { dataStoreManager.setWatchVideoInsteadOfPlayingAudio(video) } },
                onSearch = {
                    onClose()
                    onOpenSearch()
                },
                onMenu = { showMenu = true },
                onClose = onClose,
            )

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = FsSpec.MaxContentWidth)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth, maxHeight, FsSpec.ArtworkMax)
                    AnimatedContent(
                        targetState = middle,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "fsMiddle",
                    ) { mode ->
                        when (mode) {
                            Middle.Cover ->
                                Box(
                                    Modifier
                                        .size(side)
                                        .gerasSharedArtwork(visibilityScope)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(surface),
                                ) {
                                    if (screenData.isVideo && watchVideo) {
                                        MediaPlayerViewWithSubtitle(
                                            modifier = Modifier.fillMaxSize(),
                                            playerName = MAIN_PLAYER,
                                            shouldShowSubtitle = false,
                                            isInPipMode = false,
                                            timelineState = timeline,
                                            mainTextStyle = TextStyle.Default,
                                            translatedTextStyle = TextStyle.Default,
                                        )
                                    } else {
                                        AsyncImage(
                                            model = song?.thumbnails,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            // The palette reads screenData.bitmap, which only the base
                                            // Now Playing screen used to fill; desktop never opens it.
                                            onSuccess = { sharedViewModel.setBitmap(it.result.image.toImageBitmap()) },
                                        )
                                    }
                                }
                            Middle.Lyrics ->
                                Box(Modifier.fillMaxWidth().fillMaxHeight()) {
                                    val lyrics = screenData.lyricsData
                                    if (lyrics == null) {
                                        Text(
                                            stringResource(Res.string.geras_no_lyrics),
                                            color = FsSpec.InkMuted,
                                            modifier = Modifier.align(Alignment.Center),
                                        )
                                    } else {
                                        val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
                                        GerasLyricsView(
                                            lyricsData = lyrics,
                                            timeLine = sharedViewModel.timeline,
                                            isPlaying = controller.isPlaying,
                                            onSeekPercent = { sharedViewModel.onUIEvent(UIEvent.UpdateProgress(it)) },
                                            fontSize = 32.sp,
                                            centered = true,
                                            anchor = 0.36f,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    song?.title.orEmpty(),
                    color = FsSpec.Ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AsyncImage(
                        model = screenData.songInfoData?.authorThumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(surface),
                    )
                    Text(
                        song?.artistName?.joinToString(", ").orEmpty(),
                        color = FsSpec.InkMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Pill(
                    icon = null,
                    text = stringResource(if (middle == Middle.Cover) Res.string.geras_show_lyrics else Res.string.geras_show_cover),
                    background = surface,
                    modifier = Modifier.padding(top = 10.dp),
                ) { middle = if (middle == Middle.Cover) Middle.Lyrics else Middle.Cover }

                Spacer(Modifier.height(20.dp))
                SeekBar(
                    current = timeline.current,
                    total = timeline.total,
                    enabled = !timeline.isLive && timeline.total > 0L,
                    trackColor = FsSpec.Ink.copy(alpha = 0.18f),
                    fillColor = FsSpec.Ink,
                    tooltipBackground = surface,
                ) { percent -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(percent)) }

                val tracks = queueData?.data?.listTracks.orEmpty()
                val next = tracks.getOrNull(currentIndex + 1)
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(formatDuration(timeline.current), color = FsSpec.InkMuted, fontSize = 12.sp)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (next != null) {
                            UpNext(
                                title = "${next.title} — ${next.artists?.joinToString(", ") { it.name }.orEmpty()}",
                                thumbnail = next.thumbnails?.lastOrNull()?.url,
                                background = surface,
                            ) { handler.playMediaItemInMediaSource(currentIndex + 1) }
                        }
                    }
                    Text(if (timeline.isLive) "LIVE" else formatDuration(timeline.total), color = FsSpec.InkMuted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(18.dp))
                ControlsCard(
                    isPlaying = controller.isPlaying,
                    isShuffle = controller.isShuffle,
                    repeat = controller.repeatState,
                    background = surface,
                ) { sharedViewModel.onUIEvent(it) }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun TopRow(
    isVideoAvailable: Boolean,
    watchVideo: Boolean,
    onToggleVideo: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    onClose: () -> Unit,
) {
    val caption = LocalWindowCaption.current
    val strip = if (caption != null) with(caption) { Modifier.captionArea() } else Modifier
    val island = if (caption != null) with(caption) { Modifier.captionInteractive() } else Modifier
    val island2 = if (caption != null) with(caption) { Modifier.captionInteractive() } else Modifier
    val island3 = if (caption != null) with(caption) { Modifier.captionInteractive() } else Modifier
    val chip = Color.White.copy(alpha = 0.1f)

    Box(Modifier.fillMaxWidth().height(56.dp).then(strip)) {
        Row(
            island.align(Alignment.CenterStart).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(SimpIcons.KeyboardArrowDown, stringResource(Res.string.geras_close_player), tint = FsSpec.Ink)
            }
            // Audio / video segmented pill.
            Row(
                Modifier.clip(CircleShape).background(chip).padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Segment(SimpIcons.MusicNote, stringResource(Res.string.geras_audio), selected = !watchVideo || !isVideoAvailable) {
                    onToggleVideo(false)
                }
                Segment(
                    SimpIcons.Movie,
                    if (isVideoAvailable) stringResource(Res.string.geras_video) else stringResource(Res.string.geras_no_video),
                    selected = watchVideo && isVideoAvailable,
                    enabled = isVideoAvailable,
                ) { onToggleVideo(true) }
            }
        }
        Pill(
            icon = SimpIcons.Search,
            text = stringResource(Res.string.geras_search_short),
            background = chip,
            modifier = island2.align(Alignment.Center).widthIn(min = 220.dp),
            onClick = onSearch,
        )
        Row(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu, modifier = island3) {
                Icon(SimpIcons.MoreVert, stringResource(Res.string.geras_more), tint = FsSpec.Ink)
            }
            caption?.CaptionButtons(Modifier.align(Alignment.Top).height(40.dp))
        }
    }
}

@Composable
private fun Segment(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(CircleShape)
            .then(if (selected) Modifier.background(Color.White.copy(alpha = 0.16f)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (enabled) FsSpec.Ink else FsSpec.InkMuted.copy(alpha = 0.35f)
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        if (selected) Text(label, color = tint, fontSize = 12.sp)
    }
}

@Composable
private fun Pill(
    icon: ImageVector?,
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        icon?.let { Icon(it, null, tint = FsSpec.InkMuted, modifier = Modifier.size(16.dp)) }
        Text(text, color = FsSpec.InkMuted, fontSize = 13.sp)
    }
}

@Composable
private fun UpNext(
    title: String,
    thumbnail: String?,
    background: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .widthIn(max = 360.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(22.dp).clip(CircleShape),
        )
        Text(
            stringResource(Res.string.geras_up_next, title),
            color = FsSpec.InkMuted,
            fontSize = 12.sp,
            maxLines = 1,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}

/**
 * Thin seek bar: thickens on hover and shows the time under the cursor; click or drag seeks.
 * Reports percent 0..100, the unit [UIEvent.UpdateProgress] takes.
 */
@Composable
private fun SeekBar(
    current: Long,
    total: Long,
    enabled: Boolean,
    trackColor: Color,
    fillColor: Color,
    tooltipBackground: Color,
    onSeek: (Float) -> Unit,
) {
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val density = LocalDensity.current
    val fraction = dragFraction ?: if (total > 0L) (current.toFloat() / total).coerceIn(0f, 1f) else 0f
    val thick by animateFloatAsState(if (hoverX != null || dragFraction != null) 1f else 0f, label = "seekThick")
    val barHeight = FsSpec.ProgressHeight + (FsSpec.ProgressHoverHeight - FsSpec.ProgressHeight) * thick

    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                widthPx = size.width.toFloat()
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter ->
                                hoverX = event.changes.firstOrNull()?.position?.x
                            PointerEventType.Exit -> hoverX = null
                        }
                    }
                }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { pos -> onSeek((pos.x / size.width).coerceIn(0f, 1f) * 100f) }
            }.pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragFraction = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { onSeek(it * 100f) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(barHeight).clip(CircleShape).background(trackColor))
        Box(Modifier.fillMaxWidth(fraction).height(barHeight).clip(CircleShape).background(fillColor))
        val x = hoverX
        if (x != null && enabled && total > 0L) {
            val at = ((x / widthPx).coerceIn(0f, 1f) * total).toLong()
            Text(
                formatDuration(at),
                color = FsSpec.Ink,
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .offset { IntOffset((x - with(density) { 22.dp.toPx() }).roundToInt(), -with(density) { 26.dp.roundToPx() }) }
                        .clip(RoundedCornerShape(6.dp))
                        .background(tooltipBackground)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ControlsCard(
    isPlaying: Boolean,
    isShuffle: Boolean,
    repeat: RepeatState,
    background: Color,
    onEvent: (UIEvent) -> Unit,
) {
    val active = Color(0xFFF0997B)
    Row(
        Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconButton(onClick = { onEvent(UIEvent.Repeat) }) {
            Icon(
                if (repeat == RepeatState.One) SimpIcons.RepeatOne else SimpIcons.Repeat,
                null,
                tint = if (repeat == RepeatState.None) FsSpec.InkMuted else active,
            )
        }
        IconButton(onClick = { onEvent(UIEvent.Previous) }) {
            Icon(SimpIcons.SkipPrevious, null, tint = FsSpec.Ink, modifier = Modifier.size(30.dp))
        }
        Box(
            Modifier
                .size(FsSpec.PlayButton)
                .clip(CircleShape)
                .background(FsSpec.Ink)
                .clickable { onEvent(UIEvent.PlayPause) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) SimpIcons.Pause else SimpIcons.PlayArrow,
                null,
                tint = Color(0xFF2A1A14),
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = { onEvent(UIEvent.Next) }) {
            Icon(SimpIcons.SkipNext, null, tint = FsSpec.Ink, modifier = Modifier.size(30.dp))
        }
        IconButton(onClick = { onEvent(UIEvent.Shuffle) }) {
            Icon(SimpIcons.Shuffle, null, tint = if (isShuffle) active else FsSpec.InkMuted)
        }
    }
}
