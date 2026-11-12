package com.maxrave.common

/**
 * Fork branding: one place for the on-disk identity of the app.
 * Every desktop path under the user's home resolves through [DATA_DIR_NAME],
 * so a rebrand never shares settings, the database or downloads with an upstream install.
 */
object AppDirs {
    const val DATA_DIR_NAME = ".geras"
    const val DISPLAY_NAME = "Geras"
}
