package com.maxrave.simpmusic.geras.lyrics

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.em
import com.maxrave.domain.data.model.metadata.Line
import com.maxrave.domain.data.model.metadata.Lyrics
import com.maxrave.simpmusic.extension.parseRichSyncWords
import kotlin.math.abs

/** A timed chunk of a line: characters [start, end) of the line text, sung over [startMs, endMs). */
internal class GerasWord(
    val start: Int,
    val end: Int,
    val startMs: Long,
    val endMs: Long,
    /** True when the timing was spread over the line by word length, not read from the source. */
    val estimated: Boolean = false,
) {
    /** Held notes get the glow and the swell; short words only get the wipe. */
    val sustained: Boolean get() = !estimated && endMs - startMs >= SUSTAIN_MIN_MS
}

internal sealed interface GerasLine {
    val startMs: Long
    val endMs: Long
}

internal class GerasTextLine(
    override val startMs: Long,
    override val endMs: Long,
    /** Display text; ad-libs (parenthesised parts) already carry their italic span. */
    val text: AnnotatedString,
    /** Null when the source has no per-word timing (line-synced or unsynced). */
    val words: List<GerasWord>?,
    val translation: String?,
) : GerasLine

/** A silence long enough to be worth showing: rendered as three dots that fill up. */
internal class GerasGapLine(
    override val startMs: Long,
    override val endMs: Long,
) : GerasLine

internal class GerasLyrics(
    val lines: List<GerasLine>,
    /** False for plain text: no line is ever "the sung one", nothing scrolls or seeks. */
    val synced: Boolean,
) {
    /** Index of the line being sung at [nowMs], or -1 before the first one. */
    fun activeIndexAt(nowMs: Long): Int {
        if (!synced || lines.isEmpty() || nowMs < lines[0].startMs) return -1
        var lo = 0
        var hi = lines.lastIndex
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].startMs <= nowMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}

private const val SUSTAIN_MIN_MS = 800L
private const val GAP_MIN_MS = 4000L
private const val INTRO_MIN_MS = 4000L

/** How long the last sung word stays "the line" before the dots take over. */
private const val GAP_LEAD_IN_MS = 400L
private const val LAST_WORD_FALLBACK_MS = 500L
private const val LAST_WORD_MAX_MS = 4000L
private const val TRANSLATION_MATCH_MS = 1000L

private val AdLibSpan = SpanStyle(fontStyle = FontStyle.Italic, fontSize = 0.82.em)

/** Marks every parenthesised run — "(ooh, yeah)" — as an ad-lib: italic and a touch smaller. */
internal fun styleAdLibs(text: String): AnnotatedString =
    buildAnnotatedString {
        append(text)
        var open = -1
        text.forEachIndexed { i, c ->
            when {
                (c == '(' || c == '（') && open < 0 -> open = i
                (c == ')' || c == '）') && open >= 0 -> {
                    addStyle(AdLibSpan, open, i + 1)
                    open = -1
                }
            }
        }
        // An unclosed bracket still reads as an ad-lib to the end of the line.
        if (open >= 0) addStyle(AdLibSpan, open, text.length)
    }

internal fun buildGerasLyrics(
    lyrics: Lyrics,
    translated: Lyrics?,
): GerasLyrics {
    val source = lyrics.lines.orEmpty()
    val starts = source.map { it.startTimeMs.toLongOrNull() }
    // Unsynced sheets still carry a startTimeMs on every line (a literal "0"); one distinct value
    // cannot order anything, whatever the sheet calls itself.
    val synced =
        (lyrics.syncType == "LINE_SYNCED" || lyrics.syncType == "RICH_SYNCED") &&
            starts.filterNotNull().distinct().size > 1
    if (!synced) {
        val translatedLines = translated?.lines.orEmpty()
        return GerasLyrics(
            lines =
                source.mapIndexed { i, line ->
                    GerasTextLine(0L, 0L, styleAdLibs(line.words), null, translatedLines.getOrNull(i)?.words?.takeIf { it.isNotBlank() })
                },
            synced = false,
        )
    }

    val rich = lyrics.syncType == "RICH_SYNCED"
    val translations = translated?.lines.orEmpty().mapNotNull { l -> l.startTimeMs.toLongOrNull()?.let { it to l.words } }
    val out = ArrayList<GerasLine>(source.size + 8)

    source.forEachIndexed { index, line ->
        val start = starts[index] ?: return@forEachIndexed
        val nextStart = (index + 1 until source.size).firstNotNullOfOrNull { starts[it] }
        val built = if (rich) buildRichLine(line, start, nextStart) else null
        val text = built?.first ?: line.words.trim()
        // Most sheets are only line-synced. The wipe still runs on them, paced by word length
        // across the line; it is an estimate, so held-note effects stay off for those words.
        val words = built?.second ?: estimateWords(text, start, nextStart)
        val sungEnd = built?.second?.lastOrNull()?.endMs ?: (nextStart ?: (start + LAST_WORD_MAX_MS))

        if (out.isEmpty() && start >= INTRO_MIN_MS) out += GerasGapLine(0L, start)

        if (text.isBlank()) {
            // LRC marks an instrumental break with an empty line; show it only if it is long.
            if (nextStart != null && nextStart - start >= GAP_MIN_MS) out += GerasGapLine(start, nextStart)
            return@forEachIndexed
        }

        out +=
            GerasTextLine(
                startMs = start,
                endMs = sungEnd,
                text = styleAdLibs(text),
                words = words,
                translation = translations.closestTo(start),
            )
        // Only rich sync records where a line STOPS, so only it can tell a pause from a long line.
        if (built != null && nextStart != null && nextStart - sungEnd >= GAP_MIN_MS) {
            out += GerasGapLine(sungEnd + GAP_LEAD_IN_MS, nextStart)
        }
    }
    return GerasLyrics(out, synced = true)
}

private fun List<Pair<Long, String>>.closestTo(startMs: Long): String? =
    minByOrNull { abs(it.first - startMs) }
        ?.takeIf { abs(it.first - startMs) < TRANSLATION_MATCH_MS }
        ?.second
        ?.takeIf { it.isNotBlank() }

private fun buildRichLine(
    line: Line,
    lineStart: Long,
    nextStart: Long?,
): Pair<String, List<GerasWord>>? {
    val parsed = parseRichSyncWordsQuiet(line) ?: return null
    val sb = StringBuilder()
    val words = ArrayList<GerasWord>(parsed.size)
    parsed.forEachIndexed { i, (wordText, wordStart) ->
        if (sb.isNotEmpty()) sb.append(' ')
        val from = sb.length
        sb.append(wordText)
        val end =
            if (i < parsed.lastIndex) {
                parsed[i + 1].second
            } else {
                val lineEnd = line.endTimeMs.toLongOrNull()?.takeIf { it > wordStart && it != Long.MAX_VALUE }
                val fallback =
                    wordStart + if (i > 0) (wordStart - parsed[i - 1].second).coerceAtLeast(LAST_WORD_FALLBACK_MS) else LAST_WORD_FALLBACK_MS
                var e = (lineEnd ?: fallback).coerceAtMost(wordStart + LAST_WORD_MAX_MS)
                if (nextStart != null && nextStart > wordStart) e = e.coerceAtMost(nextStart)
                e
            }
        words += GerasWord(from, sb.length, wordStart, end.coerceAtLeast(wordStart + 1))
    }
    return sb.toString() to words
}

private const val EST_MS_PER_CHAR = 85L
private const val EST_MIN_LINE_MS = 900L
private const val EST_TAIL_MS = 200L

/** Word timings for a line that only has a start: its duration shared out by word length. */
private fun estimateWords(
    text: String,
    start: Long,
    nextStart: Long?,
): List<GerasWord>? {
    val ranges = Regex("\\S+").findAll(text).map { it.range }.toList()
    if (ranges.isEmpty()) return null
    val chars = ranges.sumOf { it.last - it.first + 1 }
    var duration = (chars * EST_MS_PER_CHAR).coerceAtLeast(EST_MIN_LINE_MS)
    if (nextStart != null) duration = duration.coerceAtMost((nextStart - start - EST_TAIL_MS).coerceAtLeast(1L))
    var at = start
    return ranges.map { r ->
        val len = r.last - r.first + 1
        val end = at + (duration * len / chars).coerceAtLeast(1L)
        GerasWord(r.first, r.last + 1, at, end, estimated = true).also { at = end }
    }
}

private fun parseRichSyncWordsQuiet(line: Line): List<Pair<String, Long>>? =
    parseRichSyncWords(line.words, line.startTimeMs, line.endTimeMs)
        ?.words
        ?.map { it.text to it.startTimeMs }
        ?.takeIf { it.isNotEmpty() }
