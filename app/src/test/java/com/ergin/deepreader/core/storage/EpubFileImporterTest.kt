package com.ergin.deepreader.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubFileImporterTest {

    private lateinit var tempDir: File
    private lateinit var fakeStorageGateway: FakeFileStorageGateway
    private lateinit var importer: EpubFileImporter

    @Before
    fun setUp() {
        tempDir = File.createTempFile("epub_test_", "_dir")
        tempDir.delete()
        tempDir.mkdirs()

        fakeStorageGateway = FakeFileStorageGateway(tempDir)
        importer = EpubFileImporter(fakeStorageGateway)
    }

    @Test
    fun copyAndHash_validEpubStream_writesStagingFileAndReturnsExpectedSha256() {
        val validEpubBytes = createValidEpubZipBytes(
            title = "Test Book",
            author = "Test Author"
        )
        val expectedSha256 = calculateSha256(validEpubBytes)

        val result = importer.copyAndHash(
            inputStream = ByteArrayInputStream(validEpubBytes),
            fallbackFileName = "test.epub"
        )

        assertEquals(expectedSha256, result.sha256)
        assertEquals("Test Book", result.title)
        assertEquals("Test Author", result.author)
        assertTrue(File(result.stagingPath).exists())
    }

    @Test
    fun copyAndHash_missingContainerXmlOrOpf_deletesStagingFileAndThrowsInvalidFormat() {
        val invalidZipBytes = createZipBytesWithoutContainerXml()

        try {
            importer.copyAndHash(
                inputStream = ByteArrayInputStream(invalidZipBytes),
                fallbackFileName = "invalid.epub"
            )
            fail("Expected EpubImportException.InvalidFormat to be thrown")
        } catch (e: EpubImportException.InvalidFormat) {
            assertTrue(e.message?.contains("container.xml") == true || e.message?.contains("EPUB") == true)
        }

        val stagingFiles = tempDir.listFiles() ?: emptyArray()
        assertEquals("Staging files should be cleaned up on failure", 0, stagingFiles.size)
    }

    @Test
    fun copyAndHash_ioExceptionDuringRead_cleansUpPartialStagingFile() {
        val throwingStream = object : InputStream() {
            private var bytesRead = 0
            override fun read(): Int {
                if (bytesRead++ > 10) throw IOException("Stream read error")
                return 'A'.code
            }
        }

        try {
            importer.copyAndHash(
                inputStream = throwingStream,
                fallbackFileName = "throwing.epub"
            )
            fail("Expected EpubImportException.IoError to be thrown")
        } catch (e: EpubImportException.IoError) {
            assertNotNull(e)
        }

        val stagingFiles = tempDir.listFiles() ?: emptyArray()
        assertEquals("Partial staging file should be deleted on IO error", 0, stagingFiles.size)
    }

    // Helper functions
    private fun createValidEpubZipBytes(title: String, author: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. mimetype
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 2. META-INF/container.xml
            val containerXml = """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent()
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            // 3. OEBPS/content.opf
            val opfXml = """
                <?xml version="1.0" encoding="utf-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:creator>$author</dc:creator>
                  </metadata>
                </package>
            """.trimIndent()
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun createZipBytesWithoutContainerXml(): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("dummy.txt"))
            zos.write("not an epub".toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}

class FakeFileStorageGateway(private val baseDir: File) : FileStorageGateway {
    private var counter = 0

    override fun createStagingFile(): File {
        val file = File(baseDir, "staging_${System.currentTimeMillis()}_${counter++}.tmp")
        file.createNewFile()
        return file
    }

    override fun moveToBooksDirectory(stagingFile: File, targetFileName: String): File {
        val targetFile = File(baseDir, targetFileName)
        stagingFile.renameTo(targetFile)
        return targetFile
    }

    override fun deleteFile(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }

    override fun fileExists(file: File): Boolean {
        return file.exists()
    }
}