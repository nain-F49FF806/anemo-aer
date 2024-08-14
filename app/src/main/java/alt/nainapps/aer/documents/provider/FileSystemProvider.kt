/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.documents.provider

import alt.nainapps.aer.documents.provider.PathUtils.buildUniquePath
import alt.nainapps.aer.documents.provider.PathUtils.buildValidFileName
import alt.nainapps.aer.documents.provider.PathUtils.deleteContents
import alt.nainapps.aer.documents.provider.PathUtils.getDocumentType
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MatrixCursor.RowBuilder
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.Int64Ref
import android.text.TextUtils
import android.util.ArrayMap
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import exe.bbllw8.either.Try
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A helper class for [android.provider.DocumentsProvider] to perform file operations on local
 * files.
 *
 *
 * Based on `com.android.internal.content.FileSystemProvider`.
 */
abstract class FileSystemProvider : DocumentsProvider() {
    private val observers = ArrayMap<Path, DirectoryObserver>()

    private var handler: Handler? = null
    protected var cr: ContentResolver? = null

    override fun onCreate(): Boolean {
        handler = Handler(Looper.myLooper()!!)
        cr = context!!.contentResolver
        return true
    }

    override fun getDocumentMetadata(documentId: String): Bundle? {
        return getPathForId(documentId)
            .filter { path: Path? -> Files.exists(path) }
            .filter { path: Path? -> Files.isReadable(path) }
            .map<Bundle?> { path: Path? ->
                if (Build.VERSION.SDK_INT >= 29 && Files.isDirectory(path)) {
                    val treeSize = Int64Ref(0)
                    val treeCount = Int64Ref(0)

                    Files.walkFileTree(
                        path,
                        object : SimpleFileVisitor<Path>() {
                            override fun visitFile(
                                file: Path,
                                attrs: BasicFileAttributes,
                            ): FileVisitResult {
                                treeSize.value += attrs.size()
                                treeCount.value += 1
                                return FileVisitResult.CONTINUE
                            }
                        },
                    )

                    val bundle = Bundle()
                    bundle.putLong(DocumentsContract.METADATA_TREE_SIZE, treeSize.value)
                    bundle.putLong(DocumentsContract.METADATA_TREE_COUNT, treeCount.value)
                    return@map bundle
                } else {
                    return@map null
                }
            }.getOrElse(null)
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val docName = buildValidFileName(displayName)
        val result =
            getPathForId(parentDocumentId)
                .filter { path: Path? -> Files.isDirectory(path) }
                .map { parent: Path? ->
                    val path =
                        buildUniquePath(
                            parent!!,
                            mimeType,
                            docName,
                        )
                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        Files.createDirectory(path)
                    } else {
                        Files.createFile(path)
                    }
                    val childId = getDocIdForPath(path)
                    onDocIdChanged(childId)
                    updateMediaStore(context, path)
                    childId
                }
        if (result.isSuccess) {
            return result.get()
        } else {
            Log.e(TAG, "Failed to create document", result.failed().get())
            throw IllegalStateException()
        }
    }

    @Throws(FileNotFoundException::class)
    override fun copyDocument(
        sourceDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        Log.d(TAG, "${getPathForId(sourceDocumentId)}")
        val result =
            getPathForId(sourceDocumentId)
                .flatMap { source: Path ->
                    getPathForId(targetParentDocumentId).map<String> { parent: Path? ->
                        val fileName = source.fileName.toString()
                        val target =
                            buildUniquePath(
                                parent!!,
                                fileName,
                            )
                        Log.d(TAG, "Copying document: $fileName from $source to $parent")

                        if (Files.isDirectory(source)) {
                            // Recursive copy
                            Files.walkFileTree(
                                source,
                                object : SimpleFileVisitor<Path>() {
                                    @Throws(IOException::class)
                                    override fun preVisitDirectory(
                                        dir: Path,
                                        attrs: BasicFileAttributes,
                                    ): FileVisitResult {
                                        Log.d(TAG, "Creating directories: ${target.resolve(dir.relativize(source))}")
                                        Files.createDirectories(target.resolve(dir.relativize(source)))
                                        return FileVisitResult.CONTINUE
                                    }

                                    @Throws(IOException::class)
                                    override fun visitFile(
                                        file: Path,
                                        attrs: BasicFileAttributes,
                                    ): FileVisitResult {
                                        Files.copy(file, target.resolve(file.relativize(source)))
                                        return FileVisitResult.CONTINUE
                                    }
                                },
                            )
                        } else {
                            // Simple copy
                            Log.d(TAG, "File.copy document:  $source to $target")
                            Files.copy(source, target)
                        }

                        val context = context
                        updateMediaStore(context, target)

                        val targetId = getDocIdForPath(target)
                        onDocIdChanged(targetId)
                        targetId
                    }
                }
        if (result.isSuccess) {
            return result.get()
        } else {
            Log.e(TAG, "Failed to copy document", result.failed().get())
            throw IllegalStateException()
        }
    }

    override fun renameDocument(
        documentId: String,
        displayName: String,
    ): String {
        val docName = buildValidFileName(displayName)
        val result =
            getPathForId(documentId).map { before: Path ->
                val after = buildUniquePath(before.parent, docName)
                Files.move(before, after)

                val context = context
                updateMediaStore(context, before)
                updateMediaStore(context, after)

                onDocIdChanged(documentId)
                onDocIdDeleted(documentId)

                val afterId = getDocIdForPath(after)
                if (TextUtils.equals(documentId, afterId)) {
                    // Null is used when the source and destination are equal
                    // according to the Android API specification
                    return@map null
                } else {
                    onDocIdChanged(afterId)
                    return@map afterId
                }
            }
        if (result.isSuccess) {
            return result.get()!!
        } else {
            Log.e(TAG, "Failed to rename document", result.failed().get())
            throw IllegalStateException()
        }
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        val result =
            getPathForId(sourceDocumentId)
                .flatMap { before: Path ->
                    getPathForId(targetParentDocumentId).map { parent: Path ->
                        val documentName = before.fileName.toString()
                        val after = parent.resolve(documentName)
                        Files.move(before, after)

                        val context = context
                        updateMediaStore(context, before)
                        updateMediaStore(context, after)

                        onDocIdChanged(sourceDocumentId)
                        onDocIdDeleted(sourceDocumentId)

                        val afterId = getDocIdForPath(after)
                        onDocIdChanged(afterId)
                        afterId
                    }
                }
        if (result.isSuccess) {
            return result.get()
        } else {
            Log.e(TAG, "Failed to move document", result.failed().get())
            throw IllegalStateException()
        }
    }

    override fun deleteDocument(documentId: String) {
        getPathForId(documentId)
            .map { path: Path ->
                if (Files.isDirectory(path)) {
                    deleteContents(path)
                } else {
                    Files.deleteIfExists(path)
                }
                path
            }.forEach { path: Path ->
                onDocIdChanged(documentId)
                onDocIdDeleted(documentId)
                updateMediaStore(context, path)
            }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        val result =
            getPathForId(documentId).map { path: Path ->
                val pfdMode = ParcelFileDescriptor.parseMode(mode)
                if (pfdMode == ParcelFileDescriptor.MODE_READ_ONLY) {
                    return@map ParcelFileDescriptor.open(path.toFile(), pfdMode)
                } else {
                    // When finished writing, kick off media scanner
                    return@map ParcelFileDescriptor.open(
                        path.toFile(),
                        pfdMode,
                        handler,
                    ) { failure: IOException? ->
                        onDocIdChanged(documentId)
                        updateMediaStore(
                            context,
                            path,
                        )
                    }
                }
            }
        if (result.isFailure) {
            Log.e(TAG, "Failed to open document $documentId", result.failed().get())
            throw FileNotFoundException("Couldn't open $documentId")
        }
        return result.get()
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(
        documentId: String,
        projection: Array<String?>?,
    ): Cursor {
        val result = MatrixCursor(resolveProjection(projection))
        includePath(result, documentId)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String?>?,
        sortOrder: String?,
    ): Cursor {
        val parentTry = getPathForId(parentDocumentId)
        if (parentTry.isFailure) {
            throw FileNotFoundException("Couldn't find $parentDocumentId")
        }
        val parent = parentTry.get()
        val result: MatrixCursor =
            DirectoryCursor(
                resolveProjection(projection),
                parentDocumentId,
                parent,
            )
        if (Files.isDirectory(parent)) {
            Try.from<Any?> {
                Files.list(parent).forEach { file: Path -> includePath(result, file) }
                null
            }
        } else {
            Log.w(TAG, "$parentDocumentId: not a directory")
        }
        return result
    }

    override fun findDocumentPath(
        parentDocumentId: String?,
        childDocumentId: String,
    ): DocumentsContract.Path {
        val pathStr =
            if (parentDocumentId == null) {
                childDocumentId
            } else {
                childDocumentId.substring(parentDocumentId.length)
            }

        val segments =
            listOf(
                *pathStr
                    .split("/".toRegex())
                    .dropLastWhile { it.isEmpty() }
                    .toTypedArray(),
            )
        return DocumentsContract.Path(parentDocumentId, segments)
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        val pathTry = getPathForId(documentId)
        if (pathTry.isFailure) {
            throw FileNotFoundException("Can't find $documentId")
        }
        return getDocumentType(documentId, pathTry.get())
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        docId: String,
        sizeHint: Point,
        signal: CancellationSignal,
    ): AssetFileDescriptor {
        val pathTry =
            getPathForId(docId)
                .filter { path: Path? -> getDocumentType(docId, path).startsWith("image/") }
                .map { path: Path ->
                    val pfd =
                        ParcelFileDescriptor.open(
                            path.toFile(),
                            ParcelFileDescriptor.MODE_READ_ONLY,
                        )
                    val exif = ExifInterface(path.toFile().absolutePath)

                    val thumb = exif.thumbnailRange
                    if (thumb == null) {
                        // Do full file decoding, we don't need to handle the orientation
                        return@map AssetFileDescriptor(
                            pfd,
                            0,
                            AssetFileDescriptor.UNKNOWN_LENGTH,
                            null,
                        )
                    } else {
                        // If we use thumb to decode, we need to handle the rotation by ourselves.
                        var extras: Bundle? = null
                        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> {
                                extras = Bundle(1)
                                extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 90)
                            }

                            ExifInterface.ORIENTATION_ROTATE_180 -> {
                                extras = Bundle(1)
                                extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 180)
                            }

                            ExifInterface.ORIENTATION_ROTATE_270 -> {
                                extras = Bundle(1)
                                extras.putInt(DocumentsContract.EXTRA_ORIENTATION, 270)
                            }
                        }
                        return@map AssetFileDescriptor(pfd, thumb[0], thumb[1], extras)
                    }
                }
        if (pathTry.isFailure) {
            throw FileNotFoundException("Couldn't open $docId")
        }
        return pathTry.get()
    }

    @SuppressLint("NewApi")
    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        projection: Array<String?>?,
        queryArgs: Bundle,
    ): Cursor? {
        val result =
            getPathForId(rootId)
                .filter { _: Path? -> Build.VERSION.SDK_INT > 29 }
                .map { path: Path? -> querySearchDocuments(path, projection, queryArgs) }
        if (result.isFailure) {
            throw FileNotFoundException()
        }
        return result.get()
    }

    @RequiresApi(29)
    protected fun querySearchDocuments(
        parent: Path?,
        projection: Array<String?>?,
        queryArgs: Bundle?,
    ): Cursor {
        val result = MatrixCursor(resolveProjection(projection))
        val count = AtomicInteger(MAX_QUERY_RESULTS)
        Try.from<Path> {
            Files.walkFileTree(
                parent,
                object : SimpleFileVisitor<Path>() {
                    @Throws(IOException::class)
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (matchSearchQueryArguments(file, queryArgs)) {
                            includePath(result, file)
                        }
                        return if (count.decrementAndGet() == 0
                        ) {
                            FileVisitResult.TERMINATE
                        } else {
                            FileVisitResult.CONTINUE
                        }
                    }

                    @Throws(IOException::class)
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        if (matchSearchQueryArguments(dir, queryArgs)) {
                            includePath(result, dir)
                        }
                        return if (count.decrementAndGet() == 0
                        ) {
                            FileVisitResult.TERMINATE
                        } else {
                            FileVisitResult.CONTINUE
                        }
                    }
                },
            )
        }
        return result
    }

    override fun isChildDocument(
        parentDocumentId: String,
        documentId: String,
    ): Boolean = documentId.contains(parentDocumentId)

    /**
     * Callback indicating that the given document has been modified. This gives the provider a hook
     * to invalidate cached data, such as `sdcardfs`.
     */
    protected abstract fun onDocIdChanged(docId: String?)

    /**
     * Callback indicating that the given document has been deleted or moved. This gives the
     * provider a hook to revoke the uri permissions.
     */
    protected abstract fun onDocIdDeleted(docId: String?)

    protected open fun isNotEssential(path: Path?): Boolean = true

    @Throws(FileNotFoundException::class)
    protected fun includePath(
        result: MatrixCursor,
        docId: String,
    ): RowBuilder {
        val pathTry = getPathForId(docId)
        if (pathTry.isFailure) {
            throw FileNotFoundException("Couldn't find $docId")
        }
        return includePath(result, pathTry.get(), docId)
    }

    protected fun includePath(
        result: MatrixCursor,
        path: Path,
        docId: String? = getDocIdForPath(path),
    ): RowBuilder {
        val columns = result.columnNames
        val row = result.newRow()

        val mimeType =
            getDocumentType(
                docId!!,
                path,
            )
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)

        val flagIndex = indexOf(columns, DocumentsContract.Document.COLUMN_FLAGS)
        if (flagIndex != -1) {
            var flags = 0
            if (Files.isWritable(path)) {
                if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
                    if (isNotEssential(path)) {
                        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_COPY
                        flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                        Log.d(TAG, "Flag $flags for: $path")
                    }
                } else {
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_COPY
                    flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_MOVE
                    Log.d(TAG, "Flag $flags for: $path")
                }
            }

            if (mimeType.startsWith("image/")) {
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
            }
            row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }

        val displayNameIndex = indexOf(columns, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        if (displayNameIndex != -1) {
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, path.fileName.toString())
        }

        val lastModifiedIndex = indexOf(columns, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        if (lastModifiedIndex != -1) {
            Try
                .from { Files.getLastModifiedTime(path) }
                .map { obj: FileTime -> obj.toMillis() } // Only publish dates reasonably after epoch
                .filter { lastModified: Long -> lastModified > 31536000000L }
                .forEach { lastModified: Long? ->
                    row.add(
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        lastModified,
                    )
                }
        }

        val sizeIndex = indexOf(columns, DocumentsContract.Document.COLUMN_SIZE)
        if (sizeIndex != -1) {
            Try
                .from { Files.size(path) }
                .forEach { size: Long? -> row.add(DocumentsContract.Document.COLUMN_SIZE, size) }
        }

        // Return the row builder just in case any subclass want to add more stuff to it.
        return row
    }

    protected abstract fun getPathForId(docId: String?): Try<Path>

    protected abstract fun getDocIdForPath(path: Path?): String

    protected abstract fun buildNotificationUri(docId: String?): Uri

    private fun resolveProjection(projection: Array<String?>?): Array<String?> = ((projection ?: DEFAULT_PROJECTION))

    private fun startObserving(
        path: Path,
        notifyUri: Uri,
        cursor: DirectoryCursor,
    ) {
        synchronized(observers) {
            var observer = observers[path]
            if (observer == null) {
                observer =
                    if (Build.VERSION.SDK_INT >= 29) {
                        DirectoryObserver(path, cr, notifyUri)
                    } else {
                        DirectoryObserver(
                            path.toFile().absolutePath,
                            cr,
                            notifyUri,
                        )
                    }
                observer.startWatching()
                observers[path] = observer
            }
            observer.cursors.add(cursor)
        }
    }

    private fun stopObserving(
        path: Path,
        cursor: DirectoryCursor,
    ) {
        synchronized(observers) {
            val observer = observers[path] ?: return
            observer.cursors.remove(cursor)
            if (observer.cursors.isEmpty()) {
                observers.remove(path)
                observer.stopWatching()
            }
        }
    }

    /**
     * Test if the file matches the query arguments.
     *
     * @param path
     * the file to test
     * @param queryArgs
     * the query arguments
     */
    @RequiresApi(29)
    @Throws(IOException::class)
    private fun matchSearchQueryArguments(
        path: Path,
        queryArgs: Bundle?,
    ): Boolean {
        if (queryArgs == null) {
            return true
        }

        val fileName = path.fileName.toString().lowercase()
        val argDisplayName =
            queryArgs.getString(
                DocumentsContract.QUERY_ARG_DISPLAY_NAME,
                "",
            )
        if (argDisplayName.isNotEmpty()) {
            if (!fileName.contains(argDisplayName.lowercase())) {
                return false
            }
        }

        val argFileSize = queryArgs.getLong(DocumentsContract.QUERY_ARG_FILE_SIZE_OVER, -1)
        if (argFileSize != -1L && Files.size(path) < argFileSize) {
            return false
        }

        val argLastModified =
            queryArgs
                .getLong(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER, -1)
        if (argLastModified != -1L &&
            Files
                .getLastModifiedTime(path)
                .toMillis() < argLastModified
        ) {
            return false
        }

        val argMimeTypes =
            queryArgs
                .getStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES)
        if (!argMimeTypes.isNullOrEmpty()) {
            val fileMimeType: String?
            if (Files.isDirectory(path)) {
                fileMimeType = DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                val dotPos = fileName.lastIndexOf('.')
                if (dotPos < 0) {
                    return false
                }
                val extension = fileName.substring(dotPos + 1)
                fileMimeType =
                    Intent.normalizeMimeType(
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
                    )
            }

            for (type in argMimeTypes) {
                if (mimeTypeMatches(fileMimeType, type)) {
                    return true
                }
            }
            return false
        }
        return true
    }

    private inner class DirectoryCursor(
        columnNames: Array<String?>,
        docId: String?,
        private val path: Path,
    ) : MatrixCursor(columnNames) {
        init {
            val notifyUri = buildNotificationUri(docId)
            setNotificationUri(cr, notifyUri)
            startObserving(path, notifyUri, this)
        }

        fun notifyChanged() {
            onChange(false)
        }

        override fun close() {
            super.close()
            stopObserving(path, this)
        }
    }

    private class DirectoryObserver : FileObserver {
        private val resolver: ContentResolver?
        private val notifyUri: Uri
        val cursors: CopyOnWriteArrayList<DirectoryCursor>

        @Suppress("deprecation")
        constructor(absolutePath: String?, resolver: ContentResolver?, notifyUri: Uri) : super(
            absolutePath,
            NOTIFY_EVENTS,
        ) {
            this.resolver = resolver
            this.notifyUri = notifyUri
            this.cursors = CopyOnWriteArrayList()
        }

        @RequiresApi(29)
        constructor(path: Path, resolver: ContentResolver?, notifyUri: Uri) : super(
            path.toFile(),
            NOTIFY_EVENTS,
        ) {
            this.resolver = resolver
            this.notifyUri = notifyUri
            this.cursors = CopyOnWriteArrayList()
        }

        override fun onEvent(
            event: Int,
            path: String?,
        ) {
            if ((event and NOTIFY_EVENTS) != 0) {
                for (cursor in cursors) {
                    cursor.notifyChanged()
                }
                resolver!!.notifyChange(notifyUri, null, 0)
            }
        }

        companion object {
            private const val NOTIFY_EVENTS = (
                ATTRIB or CLOSE_WRITE or MOVED_FROM or MOVED_TO
                    or CREATE or DELETE or DELETE_SELF or MOVE_SELF
            )
        }
    }

    companion object {
        private const val TAG = "FileSystemProvider"
        private const val MAX_QUERY_RESULTS = 23
        private val DEFAULT_PROJECTION =
            arrayOf<String?>(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE,
            )

        @Suppress("deprecation")
        private fun updateMediaStore(
            context: Context?,
            path: Path,
        ) {
            val intent =
                if (!Files.isDirectory(path) &&
                    path.fileName
                        .toString()
                        .endsWith("nomedia")
                ) {
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(path.parent.toFile()),
                    )
                } else {
                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(path.toFile()))
                }
            context!!.sendBroadcast(intent)
        }

        fun mimeTypeMatches(
            mimeType: String?,
            filter: String,
        ): Boolean {
            if (mimeType == null) {
                return false
            }

            val mimeTypeParts =
                mimeType.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val filterParts =
                filter.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            require(filterParts.size == 2) { "Ill-formatted MIME type filter. Must be type/subtype" }
            require(!(filterParts[0].isEmpty() || filterParts[1].isEmpty())) { "Ill-formatted MIME type filter. Type or subtype empty" }
            if (mimeTypeParts.size != 2) {
                return false
            }
            if ("*" != filterParts[0] && filterParts[0] != mimeTypeParts[0]) {
                return false
            }
            return "*" == filterParts[1] || filterParts[1] == mimeTypeParts[1]
        }

        private fun <T> indexOf(
            array: Array<T>?,
            target: T,
        ): Int {
            if (array == null) {
                return -1
            }
            for (i in array.indices) {
                if (array[i] == target) {
                    return i
                }
            }
            return -1
        }
    }
}
