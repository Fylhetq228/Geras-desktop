package com.maxrave.simpmusic.geras.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.domain.data.entities.DownloadState
import com.maxrave.domain.manager.DataStoreManager
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.expect.ui.toImageBitmap
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.ui.component.PlaybackSpeedPitchBottomSheet
import com.maxrave.simpmusic.ui.component.PlayerControlLayout
import com.maxrave.simpmusic.ui.icon.Download
import com.maxrave.simpmusic.ui.icon.DownloadForOffline
import com.maxrave.simpmusic.ui.icon.Favorite
import com.maxrave.simpmusic.ui.icon.FavoriteBorder
import com.maxrave.simpmusic.ui.icon.Lyrics
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.ui.icon.Speaker
import com.maxrave.simpmusic.ui.icon.Tune
import com.maxrave.simpmusic.ui.icon.VolumeOff
import com.maxrave.simpmusic.ui.icon.VolumeUp
import com.maxrave.simpmusic.ui.screen.home.EqualizerSection
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetUIEvent
import com.maxrave.simpmusic.viewModel.NowPlayingBottomSheetViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.geras_download
import simpmusic.composeapp.generated.resources.geras_equalizer
import simpmusic.composeapp.generated.resources.geras_like
import simpmusic.composeapp.generated.resources.geras_lyrics_panel
import simpmusic.composeapp.generated.resources.geras_nothing_playing
import simpmusic.composeapp.generated.resources.geras_output_device
import simpmusic.composeapp.generated.resources.geras_speed
import simpmusic.composeapp.generated.resources.geras_volume
import kotlin.math.roundToInt

/** Layout knobs for the bottom bar — one place to tune. */
private object BarSpec {
    val Height = 76.dp
    val Artwork = 48.dp
    val TrackWidth = 260.dp
    val ProgressMaxWidth = 560.dp
    val VolumeWidth = 96.dp
    const val SPEED_MIN = 0.5f
    const val SPEED_MAX = 2.0f
    const val SPEED_WHEEL_STEP = 0.05f
    const val VOLUME_WHEEL_STEP = 0.05f
}

/**
 * Layout B bottom bar: track on the left with a like/download capsule, transport and progress
 * in the centre, and a tools capsule on the right (lyrics panel, equalizer, speed chip, volume,
 * output device). Persistent — it stays mounted while anything is queued.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GerasPlayerBar(
    isRightPanelOpen: Boolean,
    isFullscreenOpen: Boolean,
    onToggleLyricsPanel: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    sharedViewModel: SharedViewModel = koinInject(),
    sheetViewModel: NowPlayingBottomSheetViewModel = koinViewModel(),
    dataStoreManager: DataStoreManager = koinInject(),
    handler: MediaPlayerHandler = koinInject(),
) {
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val songEntity = nowPlaying?.songEntity
    val sheetState by sheetViewModel.uiState.collectAsStateWithLifecycle()

    // The sheet view model owns like/download for the current song; keep it pointed at it.
    LaunchedEffect(songEntity?.videoId) { sheetViewModel.setSongEntity(songEntity) }

    val contentColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier
            .fillMaxWidth()
            .height(BarSpec.Height)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: artwork, title/artist, like + download capsule.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Fixed slot: the artwork leaves for the fullscreen player (shared element) and comes back.
            Box(Modifier.size(BarSpec.Artwork)) {
                androidx.compose.animation.AnimatedVisibility(visible = !isFullscreenOpen, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        Modifier
                            .size(BarSpec.Artwork)
                            .gerasSharedArtwork(this)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable(enabled = songEntity != null, onClick = onOpenNowPlaying),
                    ) {
                        AsyncImage(
                            model = songEntity?.thumbnails,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(BarSpec.Artwork),
                            // Feeds the cover colours before the fullscreen player opens.
                            onSuccess = { sharedViewModel.setBitmap(it.result.image.toImageBitmap()) },
                        )
                    }
                }
            }
            Column(Modifier.widthIn(max = BarSpec.TrackWidth)) {
                Text(
                    songEntity?.title ?: stringResource(Res.string.geras_nothing_playing),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    songEntity?.artistName?.joinToString(", ").orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (songEntity != null) {
                Capsule {
                    BarIconButton(
                        icon = if (controllerState.isLiked) SimpIcons.Favorite else SimpIcons.FavoriteBorder,
                        tooltip = stringResource(Res.string.geras_like),
                        tint = if (controllerState.isLiked) MaterialTheme.colorScheme.primary else contentColor,
                    ) { sharedViewModel.onUIEvent(UIEvent.ToggleLike) }
                    DownloadButton(
                        downloadState = sheetState.songUIState.downloadState,
                        tint = contentColor,
                    ) { sheetViewModel.onUIEvent(NowPlayingBottomSheetUIEvent.Download) }
                }
            }
        }

        // Centre: transport + progress with times.
        Column(
            Modifier.weight(1.4f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.width(240.dp)) {
                PlayerControlLayout(
                    controllerState,
                    isSmallSize = true,
                    plainPlayPause = true,
                    horizontalPadding = 0.dp,
                    activeColor = MaterialTheme.colorScheme.primary,
                    contentColor = contentColor,
                ) { sharedViewModel.onUIEvent(it) }
            }
            ProgressRow(
                current = timeline.current,
                total = timeline.total,
                isLive = timeline.isLive,
                textColor = mutedColor,
                modifier = Modifier.widthIn(max = BarSpec.ProgressMaxWidth),
            ) { percent -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(percent)) }
        }

        // Right: tools capsule.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            Capsule {
                BarIconButton(
                    icon = SimpIcons.Lyrics,
                    tooltip = stringResource(Res.string.geras_lyrics_panel),
                    tint = if (isRightPanelOpen) MaterialTheme.colorScheme.primary else contentColor,
                    onClick = onToggleLyricsPanel,
                )
                EqualizerButton(tint = contentColor)
                SpeedChip(dataStoreManager = dataStoreManager, textColor = contentColor)
                VolumeControl(
                    volume = controllerState.volume,
                    tint = contentColor,
                ) { sharedViewModel.onUIEvent(UIEvent.UpdateVolume(it)) }
                OutputDeviceButton(handler = handler, tint = contentColor)
            }
        }
    }
}

@Composable
private fun Capsule(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithTooltip(
    text: String,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

@Composable
private fun BarIconButton(
    icon: ImageVector,
    tooltip: String,
    tint: Color,
    onClick: () -> Unit,
) {
    WithTooltip(tooltip) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, contentDescription = tooltip, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DownloadButton(
    downloadState: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    val label = stringResource(Res.string.geras_download)
    WithTooltip(label) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            when (downloadState) {
                DownloadState.STATE_PREPARING, DownloadState.STATE_DOWNLOADING ->
                    // Indeterminate ring until the downloader reports byte progress (wired in the downloads step).
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = tint)
                DownloadState.STATE_DOWNLOADED ->
                    Icon(SimpIcons.DownloadForOffline, label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                else ->
                    Icon(SimpIcons.Download, label, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ProgressRow(
    current: Long,
    total: Long,
    isLive: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val percent = if (total > 0L) current.toFloat() * 100f / total else 0f
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val thumbScale by animateFloatAsState(if (hovered || dragging) 1f else 0f, label = "barThumb")

    Row(
        modifier.fillMaxWidth().hoverable(hover),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDuration(if (dragging) (dragValue / 100f * total).toLong() else current),
            fontSize = 11.sp,
            color = textColor,
        )
        Slider(
            value = if (dragging) dragValue else percent,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                onSeek(dragValue)
                dragging = false
            },
            enabled = !isLive && total > 0L,
            valueRange = 0f..100f,
            colors =
                SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = thumbScale),
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                ),
            modifier = Modifier.weight(1f).height(20.dp).padding(horizontal = 8.dp),
        )
        Text(if (isLive) "LIVE" else formatDuration(total), fontSize = 11.sp, color = textColor)
    }
}

/** Wheel over [this] reports +1 for scroll up, -1 for scroll down. */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.onWheel(onStep: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Scroll) {
                    val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (dy != 0f) {
                        onStep(if (dy < 0f) 1 else -1)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedChip(
    dataStoreManager: DataStoreManager,
    textColor: Color,
) {
    val speed by dataStoreManager.playbackSpeed.collectAsStateWithLifecycle(1f)
    val pitch by dataStoreManager.pitch.collectAsStateWithLifecycle(0)
    var showSheet by remember { mutableStateOf(false) }
    // The wheel handler is installed once; read the live speed through this.
    val liveSpeed by rememberUpdatedState(speed)
    val label = stringResource(Res.string.geras_speed)

    // The full popover (linked pitch, presets) lands in the sound step; until then the existing sheet edits it.
    if (showSheet) {
        PlaybackSpeedPitchBottomSheet(
            onDismiss = { showSheet = false },
            playbackSpeed = speed,
            pitch = pitch,
        ) { s, p ->
            dataStoreManager.setPlaybackSpeed(s)
            dataStoreManager.setPitch(p)
        }
    }

    WithTooltip(label) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .clip(CircleShape)
                .border(1.dp, textColor.copy(alpha = 0.3f), CircleShape)
                .clickable { showSheet = true }
                .onWheel { dir ->
                    val next = ((liveSpeed + dir * BarSpec.SPEED_WHEEL_STEP) * 100f).roundToInt() / 100f
                    dataStoreManager.setPlaybackSpeed(next.coerceIn(BarSpec.SPEED_MIN, BarSpec.SPEED_MAX))
                }.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(formatSpeed(speed), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
    }
}

internal fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    val whole = hundredths / 100
    val frac = hundredths % 100
    return when {
        frac == 0 -> "$whole.0x"
        frac % 10 == 0 -> "$whole.${frac / 10}x"
        else -> "$whole.${frac.toString().padStart(2, '0')}x"
    }
}

@Composable
private fun EqualizerButton(tint: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIconButton(
            icon = SimpIcons.Tune,
            tooltip = stringResource(Res.string.geras_equalizer),
            tint = if (open) MaterialTheme.colorScheme.primary else tint,
        ) { open = !open }
        if (open) {
            // Interim host for the settings equalizer; the curve editor replaces it in the sound step.
            Popup(
                alignment = Alignment.BottomEnd,
                offset = androidx.compose.ui.unit.IntOffset(0, -56),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    Modifier
                        .width(420.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(12.dp),
                ) { EqualizerSection() }
            }
        }
    }
}

@Composable
private fun VolumeControl(
    volume: Float,
    tint: Color,
    onChange: (Float) -> Unit,
) {
    val liveVolume by rememberUpdatedState(volume)
    val liveOnChange by rememberUpdatedState(onChange)
    var lastNonZero by remember { mutableFloatStateOf(if (volume > 0f) volume else 1f) }
    LaunchedEffect(volume) { if (volume > 0f) lastNonZero = volume }
    val label = stringResource(Res.string.geras_volume)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.onWheel { dir ->
                liveOnChange((liveVolume + dir * BarSpec.VOLUME_WHEEL_STEP).coerceIn(0f, 1f))
            },
    ) {
        BarIconButton(
            icon = if (volume == 0f) SimpIcons.VolumeOff else SimpIcons.VolumeUp,
            tooltip = "$label ${(volume * 100).roundToInt()}%",
            tint = tint,
        ) { onChange(if (volume == 0f) lastNonZero else 0f) }
        Slider(
            value = volume,
            onValueChange = onChange,
            valueRange = 0f..1f,
            colors =
                SliderDefaults.colors(
                    thumbColor = tint,
                    activeTrackColor = tint,
                    inactiveTrackColor = tint.copy(alpha = 0.2f),
                ),
            modifier = Modifier.width(BarSpec.VolumeWidth).height(20.dp),
        )
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun OutputDeviceButton(
    handler: MediaPlayerHandler,
    tint: Color,
) {
    val outputs by handler.player.audioOutputs.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    Box {
        BarIconButton(
            icon = SimpIcons.Speaker,
            tooltip = outputs.firstOrNull { it.isActive }?.name ?: stringResource(Res.string.geras_output_device),
            tint = tint,
        ) {
            handler.player.refreshAudioOutputs()
            open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = {
                        Text(
                            output.name,
                            fontWeight = if (output.isActive) FontWeight.Medium else FontWeight.Normal,
                            color = if (output.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        handler.player.selectAudioOutput(output.id)
                        open = false
                    },
                )
            }
        }
    }
}
