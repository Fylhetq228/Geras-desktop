package com.maxrave.simpmusic.geras.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.geras.window.LocalWindowCaption
import com.maxrave.simpmusic.ui.icon.Search
import com.maxrave.simpmusic.ui.icon.SimpIcons
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.geras_search_placeholder

/**
 * Layout B top strip: the search capsule, centred over the whole window. Where the app draws its
 * own caption (Windows) the strip IS the title bar: it drags the window and carries the caption
 * buttons; [captionEnabled] = false hands the strip back to whatever is drawn over it.
 */
@Composable
fun GerasTopBar(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    captionEnabled: Boolean = true,
) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val caption = LocalWindowCaption.current
    val stripModifier =
        if (caption != null) with(caption) { Modifier.captionArea(captionEnabled) } else Modifier
    val searchModifier =
        if (caption != null) with(caption) { Modifier.captionInteractive() } else Modifier
    Box(modifier.fillMaxWidth().height(48.dp).then(stripModifier), contentAlignment = Alignment.Center) {
        Row(
            searchModifier
                .widthIn(min = 320.dp, max = 520.dp)
                .fillMaxWidth(0.45f)
                .height(36.dp)
                .clip(CircleShape)
                .background(
                    if (hovered) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                ).hoverable(hover)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(SimpIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text(
                stringResource(Res.string.geras_search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("Ctrl+K", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
        caption?.CaptionButtons(Modifier.align(Alignment.TopEnd))
    }
}
