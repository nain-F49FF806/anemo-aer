/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.documents.receiver

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentResolver
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import alt.nainapps.aer.R
import alt.nainapps.aer.documents.home.HomeEnvironment
import alt.nainapps.aer.task.TaskExecutor
import exe.bbllw8.either.Try
import java.util.Optional
import java.util.concurrent.atomic.AtomicReference

class ReceiverActivity : Activity() {
    private val taskExecutor = TaskExecutor()
    private val dialogRef = AtomicReference(
        Optional.empty<Dialog>()
    )
    private val importRef = AtomicReference<Optional<Uri>>(
        Optional.empty()
    )


    private var contentResolver: ContentResolver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contentResolver = getContentResolver()

        val intent = intent
        if (intent == null || Intent.ACTION_SEND != intent.action) {
            Log.e(TAG, "Nothing to do")
            finish()
            return
        }

        val type = intent.type
        if (type == null) {
            Log.e(TAG, "Can't determine type of sent content")
            finish()
            return
        }

        val source = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        importRef.set(Optional.of(source))

        val pickerIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type)
            .putExtra(
                Intent.EXTRA_TITLE,
                getFileName(source).orElse(getString(R.string.receiver_default_file_name))
            )
            .putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootsUri(HomeEnvironment.AUTHORITY)
            )
        startActivityForResult(pickerIntent, DOCUMENT_PICKER_REQ_CODE)
    }

    override fun onDestroy() {
        dialogRef.getAndSet(null)!!.ifPresent { obj: Dialog -> obj.dismiss() }
        importRef.set(null)
        taskExecutor.terminate()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == DOCUMENT_PICKER_REQ_CODE) {
            if (resultCode == RESULT_OK) {
                doImport(data.data)
            } else {
                Log.d(TAG, "Action canceled")
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun doImport(destination: Uri?) {
        val sourceOpt = importRef.get()
        // No Optional#isEmpty() in android
        // noinspection SimplifyOptionalCallChains
        if (!sourceOpt!!.isPresent) {
            Log.e(TAG, "Nothing to import")
            return
        }
        val source = sourceOpt.get()

        onImportStarted()
        taskExecutor.runTask({ copyUriToUri(source, destination) }, { result: Try<Void?> ->
            result
                .forEach(
                    { success: Void? -> onImportSucceeded() },
                    { failure: Throwable? -> onImportFailed() })
        })
    }

    private fun onImportStarted() {
        val dialog: Dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setMessage(getString(R.string.receiver_importing_message))
            .setCancelable(false)
            .create()
        dialogRef.getAndSet(Optional.of(dialog))!!
            .ifPresent { obj: Dialog -> obj.dismiss() }
    }

    private fun onImportSucceeded() {
        val dialog: Dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setMessage(getString(R.string.receiver_importing_done_ok))
            .setPositiveButton(android.R.string.ok) { d: DialogInterface, which: Int -> d.dismiss() }
            .setOnDismissListener { d: DialogInterface? -> finish() }
            .create()
        dialogRef.getAndSet(Optional.of(dialog))!!
            .ifPresent { obj: Dialog -> obj.dismiss() }
        dialog.show()
    }

    private fun onImportFailed() {
        val dialog: Dialog = AlertDialog.Builder(this, R.style.DialogTheme)
            .setMessage(getString(R.string.receiver_importing_done_fail))
            .setPositiveButton(android.R.string.ok) { d: DialogInterface, which: Int -> d.dismiss() }
            .setOnDismissListener { d: DialogInterface? -> finish() }
            .create()
        dialogRef.getAndSet(Optional.of(dialog))!!
            .ifPresent { obj: Dialog -> obj.dismiss() }
        dialog.show()
    }

    private fun copyUriToUri(source: Uri, destination: Uri?): Try<Void?> {
        return Try.from {
            contentResolver!!.openInputStream(source).use { iStream ->
                contentResolver!!.openOutputStream(
                    destination!!
                ).use { oStream ->
                    val buffer = ByteArray(4096)
                    assert(iStream != null)
                    var read = iStream!!.read(buffer)
                    while (read > 0) {
                        assert(oStream != null)
                        oStream!!.write(buffer, 0, read)
                        read = iStream.read(buffer)
                    }
                }
            }
            null
        }
    }

    private fun getFileName(uri: Uri?): Optional<String> {
        contentResolver!!.query(uri!!, NAME_PROJECTION, null, null, null).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                return Optional.empty()
            } else {
                val nameIndex = cursor.getColumnIndex(NAME_PROJECTION[0])
                if (nameIndex >= 0) {
                    val name = cursor.getString(nameIndex)
                    return Optional.ofNullable(name)
                } else {
                    return Optional.empty()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ReceiverActivity"
        private const val DOCUMENT_PICKER_REQ_CODE = 7
        private val NAME_PROJECTION = arrayOf(OpenableColumns.DISPLAY_NAME)
    }
}
