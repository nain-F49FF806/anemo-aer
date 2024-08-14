/*
 * Copyright (c) 2021 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.documents.provider

import alt.nainapps.aer.R
import alt.nainapps.aer.documents.home.HomeEnvironment
import alt.nainapps.aer.documents.home.HomeEnvironment.Companion.getInstance
import alt.nainapps.aer.lock.LockStore
import alt.nainapps.aer.lock.UnlockActivity
import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Root
import android.util.Log
import exe.bbllw8.either.Failure
import exe.bbllw8.either.Success
import exe.bbllw8.either.Try
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Consumer

class AnemoDocumentProvider : FileSystemProvider() {
    private var homeEnvironment: HomeEnvironment? = null
    private var lockStore: LockStore? = null

    private var showInfo = true

    override fun onCreate(): Boolean {
        if (!super.onCreate()) {
            return false
        }

        val context = context
        lockStore = context?.let { LockStore.getInstance(it) }
        lockStore!!.addListener(onLockChanged)

        return Try
            .from {
                getInstance(
                    context!!,
                )
            }.fold(
                { failure: Throwable? ->
                    Log.e(TAG, "Failed to setup", failure)
                    false
                },
                { homeEnvironment: HomeEnvironment? ->
                    this.homeEnvironment = homeEnvironment
                    true
                },
            )
    }

    override fun shutdown() {
        lockStore!!.removeListener(onLockChanged)
        super.shutdown()
    }

    override fun queryRoots(projection: Array<String?>?): Cursor {
        if (lockStore!!.isLocked) {
            return EmptyCursor()
        }

        val context = context
        val result = MatrixCursor(resolveRootProjection(projection))
        val row = result.newRow()

        var flags = Root.FLAG_LOCAL_ONLY
        flags = flags or Root.FLAG_SUPPORTS_CREATE
        flags = flags or Root.FLAG_SUPPORTS_IS_CHILD
        flags = flags or Root.FLAG_SUPPORTS_EJECT
        if (Build.VERSION.SDK_INT >= 29) {
            flags = flags or Root.FLAG_SUPPORTS_SEARCH
        }

        row
            .add(Root.COLUMN_ROOT_ID, HomeEnvironment.ROOT)
            .add(Root.COLUMN_DOCUMENT_ID, HomeEnvironment.ROOT_DOC_ID)
            .add(Root.COLUMN_FLAGS, flags)
            .add(Root.COLUMN_ICON, R.drawable.ic_storage)
            .add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
            .add(
                Root.COLUMN_SUMMARY,
                context.getString(R.string.anemo_description),
            )
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String?>?,
        sortOrder: String?,
    ): Cursor {
        if (lockStore!!.isLocked) {
            return EmptyCursor()
        }

        val c = super.queryChildDocuments(parentDocumentId, projection, sortOrder)
        if (showInfo && HomeEnvironment.ROOT_DOC_ID == parentDocumentId) {
            // Hide from now on
            // showInfo = false
            // Show info in root dir
            val extras = Bundle()
            extras.putCharSequence(
                DocumentsContract.EXTRA_INFO,
                context!!.getText(R.string.anemo_info),
            )
            c.extras = extras
        }
        return c
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(
        documentId: String,
        projection: Array<String?>?,
    ): Cursor =
        if (lockStore!!.isLocked) {
            EmptyCursor()
        } else {
            super.queryDocument(documentId, projection)
        }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        projection: Array<String?>?,
        queryArgs: Bundle,
    ): Cursor? =
        if (lockStore!!.isLocked) {
            EmptyCursor()
        } else {
            super.querySearchDocuments(rootId, projection, queryArgs)
        }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path =
        if (lockStore!!.isLocked) {
            DocumentsContract.Path(null, emptyList())
        } else {
            super.findDocumentPath(parentDocumentId, childDocumentId)
        }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        assertUnlocked()
        return super.openDocument(documentId, mode, signal)
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        docId: String,
        sizeHint: Point,
        signal: CancellationSignal,
    ): AssetFileDescriptor {
        assertUnlocked()
        return super.openDocumentThumbnail(docId, sizeHint, signal)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        assertUnlocked()
        return super.createDocument(parentDocumentId, mimeType, displayName)
    }

    override fun deleteDocument(documentId: String) {
        assertUnlocked()
        super.deleteDocument(documentId)
    }

    override fun removeDocument(
        documentId: String,
        parentDocumentId: String,
    ) {
        deleteDocument(documentId)
    }

//    @Throws(FileNotFoundException::class)
    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        assertUnlocked()
        return super.copyDocument(sourceDocumentId, targetParentDocumentId)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        assertUnlocked()
        return super.moveDocument(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        assertUnlocked()
        return super.renameDocument(documentId, displayName)
    }

    override fun ejectRoot(rootId: String) {
        if (HomeEnvironment.ROOT == rootId) {
            lockStore!!.lock()
        }
    }

    override fun buildNotificationUri(docId: String?): Uri = DocumentsContract.buildChildDocumentsUri(HomeEnvironment.AUTHORITY, docId)

    override fun getPathForId(docId: String?): Try<Path> {
        val baseDir = homeEnvironment!!.baseDir
        if (HomeEnvironment.ROOT_DOC_ID == docId) {
            return Success(baseDir)
        } else {
            if (docId == null) {
                return Failure(FileNotFoundException("No root for $docId"))
            }
            val splitIndex = docId.indexOf('/', 1)
            if (splitIndex < 0) {
                return Failure(FileNotFoundException("No root for $docId"))
            } else {
                val targetPath = docId.substring(splitIndex + 1)
                val target = Paths.get(baseDir.toString(), targetPath)
                return if (Files.exists(target)) {
                    Success(target)
                } else {
                    Failure(
                        FileNotFoundException("No path for $docId at $target"),
                    )
                }
            }
        }
    }

    override fun getDocIdForPath(path: Path?): String {
        val rootPath = homeEnvironment!!.baseDir
        return if (rootPath == path) {
            HomeEnvironment.ROOT_DOC_ID
        } else {
            (
                HomeEnvironment.ROOT_DOC_ID +
                    path.toString().replaceFirst(rootPath.toString().toRegex(), "")
            )
        }
    }

    override fun isNotEssential(path: Path?): Boolean = !homeEnvironment!!.isRoot(path!!)

    override fun onDocIdChanged(docId: String?) {
        // no-op
    }

    override fun onDocIdDeleted(docId: String?) {
        // no-op
    }

    /**
     * @throws AuthenticationRequiredException
     * if [LockStore.isLocked] is true.
     */
    private fun assertUnlocked() {
        if (lockStore!!.isLocked) {
            val context = context
            val intent =
                Intent(context, UnlockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            throw AuthenticationRequiredException(
                Throwable("Locked storage"),
                PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        }
    }

    private val onLockChanged =
        Consumer { _: Boolean ->
            cr
                ?.notifyChange(DocumentsContract.buildRootsUri(HomeEnvironment.AUTHORITY), null)
        }

    companion object {
        private const val TAG = "AerDocumentProvider"

        private val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                Root.COLUMN_ROOT_ID,
                Root.COLUMN_FLAGS,
                Root.COLUMN_ICON,
                Root.COLUMN_TITLE,
                Root.COLUMN_DOCUMENT_ID,
            )

        private fun resolveRootProjection(projection: Array<String?>?): Array<out String?> = projection ?: DEFAULT_ROOT_PROJECTION
    }
}
