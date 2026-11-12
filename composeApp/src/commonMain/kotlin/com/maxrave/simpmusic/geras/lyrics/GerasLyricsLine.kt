package com.maxrave.simpmusic.geras.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

internal enum class LinePhase { Past, Active, Upcoming, Plain }

/** Fill style C: soft white wipe, with a glow and a slight swell on held words. */
internal class GerasLyricsStyle(
    val text: TextStyle,
    val translation: TextStyle,
    val fill: Color = Color.White,
    val pending: Color = Color.White.copy(alpha = 0.40f),
    val upcoming: Color = Color.White.copy(alpha = 0.40f),
    val past: Color = Color.White.copy(alpha = 0.22f),
) {
    val centered: Boolean get() = text.textAlign == TextAlign.Center
}

// Soft edge of the wipe, in em. It LEADS the sung position, so a finished word is fully lit.
private const val FEATHER_EM = 0.5f
private const val GLOW_ALPHA = 0.6f
private const val GLOW_BLUR_EM = 0.55f
private const val SWELL = 0.045f
private const val PHASE_FADE_MS = 350

/** One visual-line piece of a word, in the line's own pixel space. */
private class Segment(val visualLine: Int, val left: Float, val right: Float)

/** Layout-derived data, rebuilt only when the text is measured to a new result. */
private class LineLayout(
    val result: TextLayoutResult,
    line: GerasTextLine,
) {
    val rtl: Boolean = result.getParagraphDirection(0) == ResolvedTextDirection.Rtl
    val segments: List<List<Segment>> =
        line.words.orEmpty().map { w ->
            val out = ArrayList<Segment>(1)
            if (w.end > w.start && w.end <= result.layoutInput.text.length) {
                val first = result.getLineForOffset(w.start)
                val last = result.getLineForOffset(w.end - 1)
                for (vl in first..last) {
                    val from = maxOf(w.start, result.getLineStart(vl))
                    val to = minOf(w.end, result.getLineEnd(vl, visibleEnd = true))
                    if (to <= from) continue
                    val a = result.getBoundingBox(from)
                    val b = result.getBoundingBox(to - 1)
                    out += Segment(vl, minOf(a.left, b.left), maxOf(a.right, b.right))
                }
            }
            out
        }
    val wordLayouts = HashMap<Int, TextLayoutResult>()
}

private class LayoutHolder {
    var layout: LineLayout? = null
}

/**
 * A lyric line that lights up word by word. Everything time-driven happens in the DRAW phase:
 * the playhead is read only there, and only by the line being sung, so a frame of the wipe costs
 * one redraw of one line — no recomposition, no relayout.
 */
@Composable
internal fun GerasLyricsTextLine(
    line: GerasTextLine,
    phase: LinePhase,
    playhead: LongState,
    style: GerasLyricsStyle,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val holder = remember(line) { LayoutHolder() }
    val active by animateFloatAsState(if (phase == LinePhase.Active) 1f else 0f, tween(PHASE_FADE_MS), label = "gerasLineActive")
    val idle by animateColorAsState(
        when (phase) {
            LinePhase.Past -> style.past
            LinePhase.Upcoming -> style.upcoming
            LinePhase.Active -> style.pending
            LinePhase.Plain -> style.fill
        },
        tween(PHASE_FADE_MS),
        label = "gerasLineIdle",
    )
    val featherPx = with(LocalDensity.current) { style.text.fontSize.toPx() } * FEATHER_EM
    val fontPx = with(LocalDensity.current) { style.text.fontSize.toPx() }

    Column(modifier.fillMaxWidth()) {
        Layout(
            modifier =
                Modifier.drawBehind {
                    val ll = holder.layout ?: return@drawBehind
                    val words = line.words
                    if (phase != LinePhase.Active || words == null || ll.rtl) {
                        // No per-word timing to follow (or the line is fading out): the whole
                        // line carries one colour.
                        drawText(ll.result, color = lerp(idle, style.fill, active))
                        return@drawBehind
                    }
                    val now = playhead.longValue
                    val wi = words.indexOfLast { it.startMs <= now }
                    if (wi < 0) {
                        drawText(ll.result, color = idle)
                        return@drawBehind
                    }
                    val word = words[wi]
                    val p = ((now - word.startMs).toFloat() / (word.endMs - word.startMs)).coerceIn(0f, 1f)
                    val segs = ll.segments[wi]
                    if (segs.isEmpty()) {
                        drawText(ll.result, color = idle)
                        return@drawBehind
                    }
                    // Where the sung position is: which visual line, and how far along it.
                    val total = segs.sumOf { (it.right - it.left).toDouble() }.toFloat()
                    var remaining = p * total
                    var seg = segs.last()
                    for (s in segs) {
                        val w = s.right - s.left
                        if (remaining <= w) {
                            seg = s
                            break
                        }
                        remaining -= w
                    }
                    val sungX = seg.left + remaining.coerceAtMost(seg.right - seg.left)
                    val wipe =
                        Brush.horizontalGradient(
                            colors = listOf(style.fill, idle),
                            startX = sungX,
                            endX = sungX + featherPx,
                        )
                    val passes: DrawScope.() -> Unit = { drawWipe(ll.result, seg.visualLine, wipe, style.fill, idle) }

                    val swell = if (word.sustained && p > 0f && p < 1f) sin(p * PI).toFloat() else 0f
                    if (swell <= 0.01f) {
                        passes()
                        return@drawBehind
                    }
                    val first = segs.first()
                    val top = ll.result.getLineTop(first.visualLine)
                    val bottom = ll.result.getLineBottom(first.visualLine)
                    val rect = Rect(first.left, top, first.right, bottom)
                    // Glow: the word's own shape as a blurred halo underneath it.
                    val wordLayout =
                        ll.wordLayouts.getOrPut(wi) {
                            measurer.measure(line.text.subSequence(word.start, word.end), style.text.copy(textAlign = TextAlign.Start))
                        }
                    drawText(
                        wordLayout,
                        color = Color.Transparent,
                        topLeft = Offset(rect.left, ll.result.getLineBaseline(first.visualLine) - wordLayout.firstBaseline),
                        shadow =
                            Shadow(
                                color = style.fill.copy(alpha = GLOW_ALPHA * swell),
                                offset = Offset.Zero,
                                blurRadius = fontPx * GLOW_BLUR_EM * swell,
                            ),
                    )
                    // Swell: everything but the word as usual, then the word itself slightly larger.
                    clipRect(rect.left, rect.top, rect.right, rect.bottom, ClipOp.Difference) { passes() }
                    scale(1f + SWELL * swell, pivot = rect.center) {
                        clipRect(rect.left, rect.top - fontPx, rect.right, rect.bottom + fontPx) { passes() }
                    }
                },
            content = {},
        ) { _, constraints ->
            val width = constraints.maxWidth
            val result = measurer.measure(line.text, style.text, constraints = Constraints(minWidth = width, maxWidth = width))
            if (holder.layout?.result !== result) holder.layout = LineLayout(result, line)
            layout(width, result.size.height) {}
        }
        if (line.translation != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                line.translation,
                style = style.translation,
                color = lerp(idle, style.fill.copy(alpha = 0.72f), active),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Visual lines above the sung one are lit, below it pending, and the sung one is the gradient. */
private fun DrawScope.drawWipe(
    result: TextLayoutResult,
    sungLine: Int,
    wipe: Brush,
    fill: Color,
    pending: Color,
) {
    val last = result.lineCount - 1
    if (last == 0) {
        drawText(result, brush = wipe)
        return
    }
    for (vl in 0..last) {
        // Open-ended at the outer edges so ascenders and descenders are never shaved.
        val top = if (vl == 0) -size.height else result.getLineTop(vl)
        val bottom = if (vl == last) size.height * 2 else result.getLineTop(vl + 1)
        clipRect(-size.width, top, size.width * 2, bottom) {
            when {
                vl < sungLine -> drawText(result, color = fill)
                vl > sungLine -> drawText(result, color = pending)
                else -> drawText(result, brush = wipe)
            }
        }
    }
}

/** Three dots for a silence; they fill one after another across the gap. */
@Composable
internal fun GerasGapDots(
    line: GerasGapLine,
    phase: LinePhase,
    playhead: LongState,
    style: GerasLyricsStyle,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontPx = with(density) { style.text.fontSize.toPx() }
    val height = with(density) { (fontPx * 1.2f).toDp() }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val p =
            if (phase == LinePhase.Active) {
                ((playhead.longValue - line.startMs).toFloat() / (line.endMs - line.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
            } else {
                0f
            }
        val r = fontPx * 0.13f
        val step = r * 3.4f
        val startX = if (style.centered) (size.width - step * 2) / 2f else r * 1.4f
        for (k in 0..2) {
            val amount = (p * 3f - k).coerceIn(0f, 1f)
            drawCircle(
                color = style.fill.copy(alpha = 0.28f + 0.72f * amount),
                radius = r * (1f + 0.25f * amount),
                center = Offset(startX + step * k, size.height / 2f),
            )
        }
    }
}
