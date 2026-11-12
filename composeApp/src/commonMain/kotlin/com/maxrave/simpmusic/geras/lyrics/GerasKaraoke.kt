package com.maxrave.simpmusic.geras.lyrics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The detached karaoke window, as seen from shared UI. Only desktop hosts the window; it flips
 * [available] on start, and everywhere else the entry point simply never shows.
 */
object GerasKaraoke {
    var available by mutableStateOf(false)
    var isOpen by mutableStateOf(false)
}

/**
 * View options of the Geras lyrics, shared by the panel, the fullscreen player and the karaoke
 * window. Held here rather than in DataStore so the fork adds no keys to upstream's settings;
 * desktop loads and saves it (see DesktopApp).
 */
object GerasLyricsPrefs {
    /** Show the translation line under each lyric when the track has one. */
    var showTranslation by mutableStateOf(true)
}
