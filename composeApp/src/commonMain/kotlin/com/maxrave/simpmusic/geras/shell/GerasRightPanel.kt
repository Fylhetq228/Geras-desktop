package com.maxrave.simpmusic.geras.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.extension.formatDuration
import com.maxrave.simpmusic.geras.lyrics.GerasLyricsView
import com.maxrave.simpmusic.ui.icon.Close
import com.maxrave.simpmusic.ui.icon.SimpIcons
import com.maxrave.simpmusic.viewModel.SharedViewModel
import com.maxrave.simpmusic.viewModel.UIEvent
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.geras_about_artist
import simpmusic.composeapp.generated.resources.geras_about_description
import simpmusic.composeapp.generated.resources.geras_about_duration
import simpmusic.composeapp.generated.resources.geras_about_published
import simpmusic.composeapp.generated.resources.geras_about_album
import simpmusic.composeapp.generated.resources.geras_about_views
import simpmusic.composeapp.generated.resources.geras_close_panel
import simpmusic.composeapp.generated.resources.geras_no_lyrics
import simpmusic.composeapp.generated.resources.geras_nothing_playing
import simpmusic.composeapp.generated.resources.geras_queue_empty
import simpmusic.composeapp.generated.resources.geras_tab_about
import simpmusic.composeapp.generated.resources.geras_tab_lyrics
import simpmusic.composeapp.generated.resources.geras_tab_queue

enum class RightPanelTab { Lyrics, Queue, About }

/** Width of the layout B right panel, padding included. */
val GERAS_RIGHT_PANEL_WIDTH = 360.dp

/** Layout B right panel: a floating card with Lyrics / Queue / About tabs. */
@Composable
fun GerasRightPanel(
    tab: RightPanelTab,
    onTabChange: (RightPanelTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    sharedViewModel: SharedViewModel = koinInject(),
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TabChip(stringResource(Res.string.geras_tab_lyrics), tab == RightPanelTab.Lyrics) { onTabChange(RightPanelTab.Lyrics) }
            TabChip(stringResource(Res.string.geras_tab_queue), tab == RightPanelTab.Queue) { onTabChange(RightPanelTab.Queue) }
            TabChip(stringResource(Res.string.geras_tab_about), tab == RightPanelTab.About) { onTabChange(RightPanelTab.About) }
            Box(Modifier.weight(1f))
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    SimpIcons.Close,
                    contentDescription = stringResource(Res.string.geras_close_panel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 4.dp)) {
            when (tab) {
                RightPanelTab.Lyrics -> LyricsTab(sharedViewModel)
                RightPanelTab.Queue -> QueueTab()
                RightPanelTab.About -> AboutTab(sharedViewModel)
            }
        }
    }
}

@Composable
private fun TabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .clip(CircleShape)
                .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LyricsTab(sharedViewModel: SharedViewModel) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val lyrics = screenData.lyricsData
    if (lyrics == null) {
        EmptyHint(stringResource(Res.string.geras_no_lyrics))
        return
    }
    val controller by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    GerasLyricsView(
        lyricsData = lyrics,
        timeLine = sharedViewModel.timeline,
        isPlaying = controller.isPlaying,
        onSeekPercent = { percent -> sharedViewModel.onUIEvent(UIEvent.UpdateProgress(percent)) },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    )
}

@Composable
private fun QueueTab(handler: MediaPlayerHandler = koinInject()) {
    val queueData by handler.queueData.collectAsStateWithLifecycle()
    val currentIndex by handler.currentSongIndex.collectAsStateWithLifecycle()
    val tracks = queueData?.data?.listTracks.orEmpty()
    if (tracks.isEmpty()) {
        EmptyHint(stringResource(Res.string.geras_queue_empty))
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in tracks.indices) listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(tracks, key = { i, t -> "$i-${t.videoId}" }) { index, track ->
            val isCurrent = index == currentIndex
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { handler.playMediaItemInMediaSource(index) }
                    .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AsyncImage(
                    model = track.thumbnails?.lastOrNull()?.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artists?.joinToString(", ") { it.name }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                track.duration?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AboutTab(sharedViewModel: SharedViewModel) {
    val screenData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val nowPlaying by sharedViewModel.nowPlayingState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val song = nowPlaying?.songEntity
    if (song == null) {
        EmptyHint(stringResource(Res.string.geras_nothing_playing))
        return
    }
    val info = screenData.songInfoData
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = song.thumbnails,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        )
        Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        InfoRow(stringResource(Res.string.geras_about_artist), song.artistName?.joinToString(", "))
        InfoRow(stringResource(Res.string.geras_about_album), song.albumName)
        InfoRow(stringResource(Res.string.geras_about_duration), if (timeline.total > 0L) formatDuration(timeline.total) else null)
        InfoRow(stringResource(Res.string.geras_about_views), info?.viewCount?.let { formatCount(it.toLong()) })
        InfoRow(stringResource(Res.string.geras_about_published), info?.uploadDate)
        info?.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                stringResource(Res.string.geras_about_description),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String?,
) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.6f))
    }
}

private fun formatCount(n: Long): String =
    n.toString().reversed().chunked(3).joinToString(" ").reversed()
