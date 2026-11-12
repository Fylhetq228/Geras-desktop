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
