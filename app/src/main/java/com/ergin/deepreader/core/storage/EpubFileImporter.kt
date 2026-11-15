package com.ergin.deepreader.core.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

class EpubFileImporter @Inject constructor(
    private val storageGateway: FileStorageGateway
) {

    @Throws(EpubImportException::class)
    fun copyAndHash(inputStream: InputStream, fallbackFileName: String): EpubImportResult {
        val stagingFile = try {
            storageGateway.createStagingFile()
        } catch (e: Exception) {
            throw EpubImportException.IoError("Failed to create staging file", e)
        }

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            DigestInputStream(inputStream, digest).use { dis ->
                FileOutputStream(stagingFile).use { fos ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (dis.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }
            }
        } catch (e: IOException) {
            storageGateway.deleteFile(stagingFile)
            throw EpubImportException.IoError("IO error while copying file stream", e)
        } catch (e: Exception) {
            storageGateway.deleteFile(stagingFile)
            throw EpubImportException.IoError("Unexpected error while copying stream", e)
        }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        val metadata = try {
            validateAndExtractMetadata(stagingFile, fallbackFileName)
        } catch (e: EpubImportException.InvalidFormat) {
            storageGateway.deleteFile(stagingFile)
            throw e
        } catch (e: Exception) {
            storageGateway.deleteFile(stagingFile)
            throw EpubImportException.InvalidFormat("File is not a valid EPUB archive: ${e.message}")
        }

        return EpubImportResult(
            sha256 = sha256,
            stagingPath = stagingFile.absolutePath,
            title = metadata.title,
            author = metadata.author
        )
    }

    private fun validateAndExtractMetadata(file: File, fallbackFileName: String): Metadata {
        val zipFile = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw EpubImportException.InvalidFormat("File is not a valid ZIP archive")
        }

        zipFile.use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: throw EpubImportException.InvalidFormat("Missing META-INF/container.xml in EPUB")

            val containerContent = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
            val opfPath = extractOpfPathFromContainer(containerContent)
                ?: throw EpubImportException.InvalidFormat("Could not locate OPF file path in container.xml")

            val opfEntry = zip.getEntry(opfPath)
                ?: throw EpubImportException.InvalidFormat("OPF file not found at path: $opfPath")

            val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val (title, author) = parseOpfMetadata(opfContent, fallbackFileName)

            return Metadata(title = title, author = author)
        }
    }

    private fun extractOpfPathFromContainer(containerXml: String): String? {
        val regex = Regex("""full-path=["']([^"']+)["']""")
        return regex.find(containerXml)?.groupValues?.get(1)
    }

    private fun parseOpfMetadata(opfXml: String, fallbackFileName: String): Pair<String, String?> {
        val titleRegex = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
        val creatorRegex = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)

        val title = titleRegex.find(opfXml)?.groupValues?.get(1)?.trim()
            ?: fallbackFileName.substringBeforeLast(".")
        val author = creatorRegex.find(opfXml)?.groupValues?.get(1)?.trim()

        return Pair(title, author)
    }

    private data class Metadata(
        val title: String,
        val author: String?
    )
}