/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.documents.home

import android.content.Context
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager.getDefaultSharedPreferences
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.Volatile

class HomeEnvironment private constructor(
    context: Context,
) {
    val baseDir: Path =
            getPreferredStorageFilesDir(context)?.toPath() // manual config
                ?: getSelectExternalFilesDir(context)?.toPath()
                ?: context.filesDir.toPath() // internal

    init {
        if (!Files.exists(baseDir)) {
            Files.createDirectory(baseDir)
        } else if (!Files.isDirectory(baseDir)) {
            throw IOException("$baseDir is not a directory")
        }
    }

    fun isRoot(path: Path): Boolean = baseDir == path

    private fun getPreferredStorageFilesDir(context: Context): File? {
        val sharedPrefs = getDefaultSharedPreferences(context)
        return sharedPrefs.getString("selected_storage_dir",null)?.let {
            File(it)
        }
    }

    private fun getSelectExternalFilesDir(context: Context): File? {
        // Below Android 11 (API 30) we do not prefer external storage
        // as privacy of external filedir is guaranteed from Android 11 only.
        // https://developer.android.com/about/versions/11/privacy/storage#other-app-specific-dirs
        if (Build.VERSION.SDK_INT < 30) {
            return null
        }
        val externalFilesDirs = context.getExternalFilesDirs(null)
        // The first few (in forward order) may be on primary storage,
        // so we traverse in reverse order to find first available externalFilesDir
        for (i in externalFilesDirs.indices.reversed()) {
            if (externalFilesDirs[i] != null) {
                if (Environment.getExternalStorageState(externalFilesDirs[i]) == Environment.MEDIA_MOUNTED) {
                    return externalFilesDirs[i]
                }
            }
        }
        return null
    }

    companion object {
        const val AUTHORITY: String = "alt.nainapps.aer.documents"
        const val ROOT: String = "alt.nainapps.aer.documents.root"
        const val ROOT_DOC_ID: String = "aer_root"

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
