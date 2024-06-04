/*
 * Copyright (c) 2022 2bllw8
 * SPDX-License-Identifier: GPL-3.0-only
 */
package alt.nainapps.aer.documents.provider

import android.provider.DocumentsContract
import android.text.TextUtils
import android.webkit.MimeTypeMap
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale

object PathUtils {
    private const val MIME_TYPE_DEFAULT = "application/octet-stream"

    /**
     * Mutate the given filename to make it valid for a FAT filesystem, replacing any invalid
     * characters with "_".
     *
     *
     * Based on `android.os.FileUtils#buildUniqueFile#buildValidFilename`
     */
    @JvmStatic
    fun buildValidFileName(name: String): String {
        if (TextUtils.isEmpty(name) || "." == name) {
            return "(invalid)"
        }

        val res = StringBuilder(name.length)
        for (i in 0 until name.length) {
            val c = name[i]
            if (isValidFatFilenameChar(c.code)) {
                res.append(c)
            } else {
                res.append('_')
            }
        }
        return res.toString()
    }

    /**
     * Generates a unique file name under the given parent directory, keeping any extension intact.
     *
     *
     * Based on `android.os.FileUtils#buildUniqueFile`
     */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun buildUniquePath(parent: Path, displayName: String): Path {
        val name: String
        val ext: String?

        // Extract requested extension from display name
        val lastDot = displayName.lastIndexOf('.')
        if (lastDot >= 0) {
            name = displayName.substring(0, lastDot)
            ext = displayName.substring(lastDot + 1)
        } else {
            name = displayName
            ext = null
        }

        return buildUniquePathWithExtension(parent, name, ext)
    }

    /**
     * Generates a unique file name under the given parent directory. If the display name doesn't
     * have an extension that matches the requested MIME type, the default extension for that MIME
     * type is appended. If a file already exists, the name is appended with a numerical value to
     * make it unique.
     *
     *
     * For example, the display name 'example' with 'text/plain' MIME might produce 'example.txt' or
     * 'example (1).txt', etc.
     *
     *
     * Based on `android.os.FileUtils#buildUniqueFile#buildUniqueFile`
     */
    @JvmStatic
    @Throws(FileNotFoundException::class)
    fun buildUniquePath(parent: Path, mimeType: String, displayName: String): Path {
        val parts = splitFileName(mimeType, displayName)
        return buildUniquePathWithExtension(parent, parts[0], parts[1])
    }

    /**
     * Splits file name into base name and extension. If the display name doesn't have an extension
     * that matches the requested MIME type, the extension is regarded as a part of filename and
     * default extension for that MIME type is appended.
     *
     *
     * Based on `android.os.FileUtils#buildUniqueFile#splitFileName`
     */
    fun splitFileName(mimeType: String, displayName: String): Array<String> {
        var name: String
        var ext: String?

        if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
            name = displayName
            ext = null
        } else {
            var mimeTypeFromExt: String?

            // Extract requested extension from display name
            val lastDot = displayName.lastIndexOf('.')
            if (lastDot >= 0) {
                name = displayName.substring(0, lastDot)
                ext = displayName.substring(lastDot + 1)
                mimeTypeFromExt = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(ext.lowercase(Locale.getDefault()))
            } else {
                name = displayName
                ext = null
                mimeTypeFromExt = null
            }

            if (mimeTypeFromExt == null) {
                mimeTypeFromExt = MIME_TYPE_DEFAULT
            }
            val extFromMimeType =
                if (MIME_TYPE_DEFAULT == mimeType) {
                    null
                } else {
                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                }

            if (!(mimeType == mimeTypeFromExt || ext == extFromMimeType)) {
                // No match; insist that create file matches requested MIME
                name = displayName
                ext = extFromMimeType
            }
        }

        if (ext == null) {
            ext = ""
        }

        return arrayOf(name, ext)
    }

    /**
     * Recursively delete a directory.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun deleteContents(path: Path?) {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            @Throws(IOException::class)
            override fun postVisitDirectory(dir: Path, exc: IOException): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }

            @Throws(IOException::class)
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
        })
    }

    /**
     * Get the mime-type of a given path document.
     */
    @JvmStatic
    fun getDocumentType(documentId: String, path: Path?): String {
        if (Files.isDirectory(path)) {
            return DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val lastDot = documentId.lastIndexOf('.')
            if (lastDot >= 0) {
                val extension = documentId.substring(lastDot + 1).lowercase(Locale.getDefault())
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                if (mime != null) {
                    return mime
                }
            }
            return MIME_TYPE_DEFAULT
        }
    }

    private fun isValidFatFilenameChar(c: Int): Boolean {
        if (c in 0x00..0x1f) {
            return false
        }
        return when (c) {
            '"'.code, '*'.code, '/'.code, ':'.code, '<'.code, '>'.code, '?'.code, '\\'.code, '|'.code, 0x7F -> false
            else -> true
        }
    }

    @Throws(FileNotFoundException::class)
    private fun buildUniquePathWithExtension(parent: Path, name: String, ext: String?): Path {
        var path = buildPath(parent, name, ext)

        // If conflicting path, try adding counter suffix
        var n = 0
        while (Files.exists(path)) {
            if (n++ >= 32) {
                throw FileNotFoundException("Failed to create unique file")
            }
            path = buildPath(parent, "$name ($n)", ext)
        }

        return path
    }

    private fun buildPath(parent: Path, name: String, ext: String?): Path {
        return if (TextUtils.isEmpty(ext)) {
            parent.resolve(name)
        } else {
            parent.resolve("$name.$ext")
        }
    }
}
