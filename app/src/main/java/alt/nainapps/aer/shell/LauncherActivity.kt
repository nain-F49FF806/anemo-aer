/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.shell

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import alt.nainapps.aer.R
import alt.nainapps.aer.documents.home.HomeEnvironment
import alt.nainapps.aer.lock.LockStore
import alt.nainapps.aer.lock.UnlockActivity
import java.util.Arrays

class LauncherActivity : Activity() {
    private val LAUNCHER_INTENTS = arrayOf(
        // AOSP, up to Android 11
        Intent(Intent.ACTION_VIEW, ANEMO_URI).setClassName(
            DOCUMENTS_UI_PACKAGE,
            DOCUMENTS_UI_ACTIVITY
        ),
        Intent(Intent.ACTION_VIEW, ANEMO_URI).setClassName(
            DOCUMENTS_UI_PACKAGE,
            DOCUMENTS_UI_ALIAS_ACTIVITY
        ),  // Pixels, Android 12+
        Intent(Intent.ACTION_VIEW, ANEMO_URI).setClassName(
            GOOGLE_DOCUMENTS_UI_PACKAGE,
            DOCUMENTS_UI_ACTIVITY
        ),  // Android 13+
        Intent(Intent.ACTION_VIEW, ANEMO_URI).setType(TYPE_DOCS_DIRECTORY)
            .setFlags(
                Intent.FLAG_ACTIVITY_FORWARD_RESULT
                        or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP
            ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LockStore.getInstance(this)?.isLocked == true) {
            startActivity(
                Intent(this, UnlockActivity::class.java)
                    .putExtra(UnlockActivity.OPEN_AFTER_UNLOCK, true)
            )
        } else {
            val pm = packageManager
            val fileIntent = Arrays.stream(LAUNCHER_INTENTS)
                .filter { intent: Intent -> canHandle(pm, intent) }
                .findAny()
            if (fileIntent.isPresent) {
                startActivity(fileIntent.get())
                overridePendingTransition(0, 0)
            } else {
                Toast.makeText(this, R.string.launcher_no_activity, Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun canHandle(pm: PackageManager, intent: Intent): Boolean {
        return pm.resolveActivity(intent, PackageManager.MATCH_ALL) != null
    }

    companion object {
        // https://cs.android.com/android/platform/superproject/+/master:packages/apps/DocumentsUI/AndroidManifest.xml
        private const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"
        private const val DOCUMENTS_UI_ACTIVITY = (DOCUMENTS_UI_PACKAGE
                + ".files.FilesActivity")
        private const val DOCUMENTS_UI_ALIAS_ACTIVITY = (DOCUMENTS_UI_PACKAGE
                + ".FilesActivity")
        private const val GOOGLE_DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
        private const val TYPE_DOCS_DIRECTORY = "vnd.android.document/directory"
        private val ANEMO_URI: Uri = DocumentsContract.buildRootsUri(HomeEnvironment.AUTHORITY)
    }
}
