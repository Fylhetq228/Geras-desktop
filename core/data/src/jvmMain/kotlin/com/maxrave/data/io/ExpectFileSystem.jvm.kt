package com.maxrave.data.io

import com.maxrave.common.AppDirs
import okio.FileSystem
import java.io.File


actual fun fileSystem(): FileSystem {
    return FileSystem.SYSTEM
}

actual fun fileDir(): String = File(getHomeFolderPath(listOf(AppDirs.DATA_DIR_NAME))).absolutePath