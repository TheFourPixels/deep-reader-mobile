package com.ergin.deepreader.core.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            storageGateway.deleteFile(stagingFile)
            throw EpubImportException.IoError("Unexpected error while copying stream", e)
        }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

        val metadata = try {
            validateAndExtractMetadata(stagingFile, fallbackFileName)
        } catch (e: EpubImportException) {
            storageGateway.deleteFile(stagingFile)
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            storageGateway.deleteFile(stagingFile)
            throw EpubImportException.InvalidFormat("File is not a valid EPUB archive: ${e.message}")
        }

        return EpubImportResult(
            sha256 = sha256,
            stagingPath = stagingFile.absolutePath,
            title = metadata.title,
            author = metadata.author,
            coverPath = metadata.cover?.let { saveCover(sha256, it) }
        )
    }

    /**
     * Extracts the cover from an already stored EPUB and saves it. Returns the cover file path
     * or null when the book has no (readable) cover. Never throws: the cover is optional.
     */
    fun extractCover(epubFile: File, sha256: String): String? {
        return try {
            ZipFile(epubFile).use { zip ->
                val (opfPath, opfContent) = readOpf(zip)
                EpubCoverExtractor.extract(zip, opfPath, opfContent)
            }?.let { saveCover(sha256, it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun saveCover(sha256: String, cover: EpubCover): String? {
        return try {
            storageGateway.saveCoverFile("$sha256.${cover.extension}", cover.bytes).absolutePath
        } catch (e: IOException) {
            null
        }
    }

    private fun validateAndExtractMetadata(file: File, fallbackFileName: String): Metadata {
        val zipFile = try {
            ZipFile(file)
        } catch (e: Exception) {
            throw EpubImportException.InvalidFormat("File is not a valid ZIP archive")
        }

        zipFile.use { zip ->
            val (opfPath, opfContent) = readOpf(zip)

            validateSpine(opfContent)

            val (title, author) = parseOpfMetadata(opfContent, fallbackFileName)

            val cover = try {
                EpubCoverExtractor.extract(zip, opfPath, opfContent)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }

            return Metadata(title = title, author = author, cover = cover)
        }
    }

    /** Returns the resolved OPF entry path together with its text content. */
    private fun readOpf(zip: ZipFile): Pair<String, String> {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: throw EpubImportException.InvalidFormat("Missing META-INF/container.xml in EPUB")

        val containerContent = try {
            zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw EpubImportException.InvalidFormat("Cannot read META-INF/container.xml")
        }

        val opfPath = extractOpfPathFromContainer(containerContent)
            ?: throw EpubImportException.InvalidFormat("Could not locate OPF file path in container.xml")

        val decodedOpfPath = try {
            URLDecoder.decode(opfPath, "UTF-8")
        } catch (e: Exception) {
            opfPath
        }

        val resolvedOpfPath = if (zip.getEntry(opfPath) != null) opfPath else decodedOpfPath
        val opfEntry = zip.getEntry(resolvedOpfPath)
            ?: throw EpubImportException.InvalidFormat("OPF file not found at path: $opfPath")

        val opfContent = try {
            zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            throw EpubImportException.InvalidFormat("Cannot read OPF file at path: $opfPath")
        }

        return resolvedOpfPath to opfContent
    }

    private fun extractOpfPathFromContainer(containerXml: String): String? {
        val regex = Regex("""full-path=["']([^"']+)["']""")
        return regex.find(containerXml)?.groupValues?.get(1)
    }

    private fun validateSpine(opfXml: String) {
        val spineRegex = Regex("""<spine[^>]*>(.*?)</spine>""", RegexOption.DOT_MATCHES_ALL)
        val spineContent = spineRegex.find(opfXml)?.groupValues?.get(1)

        val itemrefRegex = Regex("""<itemref\b""", RegexOption.IGNORE_CASE)
        val hasItemref = if (spineContent != null) {
            itemrefRegex.containsMatchIn(spineContent)
        } else {
            itemrefRegex.containsMatchIn(opfXml)
        }

        if (!hasItemref) {
            throw EpubImportException.InvalidFormat("В EPUB отсутствует содержимое (spine)")
        }
    }

    private fun parseOpfMetadata(opfXml: String, fallbackFileName: String): Pair<String, String?> {
        val titleRegex = Regex("""<dc:title[^>]*>(.*?)</dc:title>""", RegexOption.DOT_MATCHES_ALL)
        val creatorRegex = Regex("""<dc:creator[^>]*>(.*?)</dc:creator>""", RegexOption.DOT_MATCHES_ALL)

        val rawTitle = titleRegex.find(opfXml)?.groupValues?.get(1)?.trim()
        val defaultTitle = fallbackFileName.substringBeforeLast(".")
        val title = if (!rawTitle.isNullOrBlank()) {
            val decoded = decodeXmlEntities(rawTitle)
            if (decoded.isBlank()) defaultTitle else decoded
        } else {
            defaultTitle
        }

        val rawAuthor = creatorRegex.find(opfXml)?.groupValues?.get(1)?.trim()
        val author = if (!rawAuthor.isNullOrBlank()) {
            val decoded = decodeXmlEntities(rawAuthor)
            if (decoded.isBlank()) null else decoded
        } else {
            null
        }

        return Pair(title, author)
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }

    private data class Metadata(
        val title: String,
        val author: String?,
        val cover: EpubCover?
    )
}