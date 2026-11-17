package com.ergin.deepreader.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStorageGatewayImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : FileStorageGateway {

    private val stagingDir: File by lazy {
        File(context.cacheDir, "staging").apply { if (!exists()) mkdirs() }
    }

    private val booksDir: File by lazy {
        File(context.filesDir, "books").apply { if (!exists()) mkdirs() }
    }

    private val coversDir: File by lazy {
        File(context.filesDir, "covers").apply { if (!exists()) mkdirs() }
    }

    override fun createStagingFile(): File {
        return File.createTempFile("import_", ".tmp", stagingDir)
    }

    override fun moveToBooksDirectory(stagingFile: File, targetFileName: String): File {
        val targetFile = File(booksDir, targetFileName)
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = stagingFile.renameTo(targetFile)
        if (!renamed) {
            try {
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()
            } catch (e: IOException) {
                targetFile.delete()
                throw e
            }
        }
        return targetFile
    }

    override fun saveCoverFile(targetFileName: String, bytes: ByteArray): File {
        val targetFile = File(coversDir, targetFileName)
        targetFile.writeBytes(bytes)
        return targetFile
    }

    override fun deleteFile(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }

    override fun fileExists(file: File): Boolean {
        return file.exists()
    }
}
