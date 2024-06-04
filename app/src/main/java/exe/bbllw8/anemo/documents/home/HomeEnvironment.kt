/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package exe.bbllw8.anemo.documents.home

import android.content.Context
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.Volatile

class HomeEnvironment private constructor(context: Context) {
    val baseDir: Path

    init {
        baseDir = context.filesDir.toPath().resolve(ROOT)
        if (!Files.exists(baseDir)) {
            Files.createDirectory(baseDir)
        } else if (!Files.isDirectory(baseDir)) {
            throw IOException("$baseDir is not a directory")
        }
    }

    fun isRoot(path: Path): Boolean {
        return baseDir == path
    }

    companion object {
        const val AUTHORITY: String = "exe.bbllw8.anemo.documents"

        const val ROOT: String = "anemo"
        const val ROOT_DOC_ID: String = "root"

        @Volatile
        private var instance: HomeEnvironment? = null

        @Throws(IOException::class)
        fun getInstance(context: Context): HomeEnvironment? {
            if (instance == null) {
                synchronized(HomeEnvironment::class.java) {
                    if (instance == null) {
                        instance = HomeEnvironment(context.applicationContext)
                    }
                }
            }
            return instance
        }
    }
}
