package com.ergin.deepreader.core.storage

import java.io.File

interface FileStorageGateway {
    fun createStagingFile(): File
    fun moveToBooksDirectory(stagingFile: File, targetFileName: String): File
    fun deleteFile(file: File): Boolean
    fun fileExists(file: File): Boolean
}