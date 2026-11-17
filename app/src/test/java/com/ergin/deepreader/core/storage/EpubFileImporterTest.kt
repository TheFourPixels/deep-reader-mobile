package com.ergin.deepreader.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubFileImporterTest {

    private lateinit var fakeStorageGateway: FakeFileStorageGateway
    private lateinit var epubFileImporter: EpubFileImporter

    @Before
    fun setUp() {
        fakeStorageGateway = FakeFileStorageGateway()
        epubFileImporter = EpubFileImporter(fakeStorageGateway)
    }

    @Test
    fun copyAndHash_validEpub_returnsCorrectMetadataAndSha256() {
        val epubBytes = createEpubZipStream(
            containerXml = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            opfXml = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata><dc:title>Tom &amp; Jerry</dc:title><dc:creator>Author &lt;Name&gt;</dc:creator></metadata><spine><itemref idref="item1"/></spine></package>"""
        )

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "test_book.epub")

        assertNotNull(result.sha256)
        assertTrue(result.sha256.isNotBlank())
        assertEquals("Tom & Jerry", result.title)
        assertEquals("Author <Name>", result.author)
    }

    @Test
    fun copyAndHash_nonZipFile_throwsInvalidFormatAndDeletesStagingFile() {
        val invalidBytes = "This is not a zip file".toByteArray()

        try {
            epubFileImporter.copyAndHash(ByteArrayInputStream(invalidBytes), "invalid.epub")
            fail("Expected EpubImportException.InvalidFormat")
        } catch (e: EpubImportException.InvalidFormat) {
            assertTrue(fakeStorageGateway.deletedFiles.isNotEmpty())
        }
    }

    @Test
    fun copyAndHash_missingContainerXml_throwsInvalidFormatAndDeletesStagingFile() {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write("some opf".toByteArray())
            zos.closeEntry()
        }
        val bytes = baos.toByteArray()

        try {
            epubFileImporter.copyAndHash(ByteArrayInputStream(bytes), "invalid.epub")
            fail("Expected EpubImportException.InvalidFormat")
        } catch (e: EpubImportException.InvalidFormat) {
            assertTrue(e.message?.contains("container.xml") == true)
            assertTrue(fakeStorageGateway.deletedFiles.isNotEmpty())
        }
    }

    @Test
    fun copyAndHash_emptySpine_throwsInvalidFormatAndDeletesStagingFile() {
        val epubBytes = createEpubZipStream(
            opfXml = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata><dc:title>No Spine Book</dc:title></metadata><spine></spine></package>"""
        )

        try {
            epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "nospine.epub")
            fail("Expected EpubImportException.InvalidFormat")
        } catch (e: EpubImportException.InvalidFormat) {
            assertTrue(e.message?.contains("spine") == true)
            assertTrue(fakeStorageGateway.deletedFiles.isNotEmpty())
        }
    }

    @Test
    fun copyAndHash_missingTitle_fallsBackToFileName() {
        val epubBytes = createEpubZipStream(
            opfXml = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata></metadata><spine><itemref idref="item1"/></spine></package>"""
        )

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "my_fallback_name.epub")

        assertEquals("my_fallback_name", result.title)
    }

    @Test
    fun copyAndHash_epub3CoverImage_savesCover() {
        val epubBytes = createEpubZipStream(
            opfXml = opfWithManifest(
                manifest = """<item id="img" href="images/cover.png" media-type="image/png" properties="cover-image"/>"""
            ),
            extraEntries = mapOf("OEBPS/images/cover.png" to byteArrayOf(1, 2, 3))
        )

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "book.epub")

        assertNotNull(result.coverPath)
        assertTrue(result.coverPath!!.endsWith("${result.sha256}.png"))
        assertEquals(listOf(1.toByte(), 2.toByte(), 3.toByte()), fakeStorageGateway.savedCovers.values.single().toList())
    }

    @Test
    fun copyAndHash_epub2MetaCover_savesCover() {
        val epubBytes = createEpubZipStream(
            opfXml = opfWithManifest(
                meta = """<meta content="cover-id" name="cover"/>""",
                manifest = """<item id="cover-id" href="pic%20one.jpg" media-type="image/jpeg"/>"""
            ),
            extraEntries = mapOf("OEBPS/pic one.jpg" to byteArrayOf(9, 9))
        )

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "book.epub")

        assertNotNull(result.coverPath)
        assertTrue(result.coverPath!!.endsWith("${result.sha256}.jpg"))
    }

    @Test
    fun copyAndHash_coverDeclaredAsPage_usesFirstImageOfPage() {
        val epubBytes = createEpubZipStream(
            opfXml = opfWithManifest(
                meta = """<meta name="cover" content="cover-page"/>""",
                manifest = """<item id="cover-page" href="text/cover.xhtml" media-type="application/xhtml+xml"/>"""
            ),
            extraEntries = mapOf(
                "OEBPS/text/cover.xhtml" to """<html><body><img src="../img/front.jpeg"/></body></html>""".toByteArray(),
                "OEBPS/img/front.jpeg" to byteArrayOf(7)
            )
        )

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "book.epub")

        assertNotNull(result.coverPath)
        assertTrue(result.coverPath!!.endsWith("${result.sha256}.jpg"))
    }

    @Test
    fun copyAndHash_noCover_returnsNullCoverPath() {
        val epubBytes = createEpubZipStream()

        val result = epubFileImporter.copyAndHash(ByteArrayInputStream(epubBytes), "book.epub")

        assertNull(result.coverPath)
        assertTrue(fakeStorageGateway.savedCovers.isEmpty())
    }

    @Test
    fun extractCover_storedEpub_returnsCoverPath() {
        val epubBytes = createEpubZipStream(
            opfXml = opfWithManifest(
                manifest = """<item id="img" href="cover.jpg" media-type="image/jpeg" properties="cover-image"/>"""
            ),
            extraEntries = mapOf("OEBPS/cover.jpg" to byteArrayOf(5, 5))
        )
        val epubFile = File.createTempFile("stored_", ".epub").apply { writeBytes(epubBytes) }

        val coverPath = epubFileImporter.extractCover(epubFile, "abc123")

        assertNotNull(coverPath)
        assertTrue(coverPath!!.endsWith("abc123.jpg"))
        epubFile.delete()
    }

    @Test
    fun extractCover_invalidFile_returnsNull() {
        val epubFile = File.createTempFile("broken_", ".epub").apply { writeText("not a zip") }

        assertNull(epubFileImporter.extractCover(epubFile, "abc123"))
        epubFile.delete()
    }

    private fun opfWithManifest(meta: String = "", manifest: String): String =
        """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata><dc:title>Covered</dc:title>$meta</metadata><manifest>$manifest</manifest><spine><itemref idref="item1"/></spine></package>"""

    private fun createEpubZipStream(
        containerXml: String = """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
        opfXml: String = """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata><dc:title>Test Book</dc:title></metadata><spine><itemref idref="item1"/></spine></package>""",
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zos.write(opfXml.toByteArray())
            zos.closeEntry()

            extraEntries.forEach { (name, content) ->
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}

class FakeFileStorageGateway : FileStorageGateway {
    val deletedFiles = mutableListOf<File>()
    val createdFiles = mutableListOf<File>()
    val savedCovers = mutableMapOf<String, ByteArray>()

    override fun saveCoverFile(targetFileName: String, bytes: ByteArray): File {
        savedCovers[targetFileName] = bytes
        return File(System.getProperty("java.io.tmpdir"), targetFileName)
    }

    override fun createStagingFile(): File {
        val file = File.createTempFile("staging_", ".tmp")
        createdFiles.add(file)
        return file
    }

    override fun moveToBooksDirectory(stagingFile: File, targetFileName: String): File {
        val target = File(stagingFile.parentFile, targetFileName)
        stagingFile.renameTo(target)
        return target
    }

    override fun deleteFile(file: File): Boolean {
        deletedFiles.add(file)
        return file.delete()
    }

    override fun fileExists(file: File): Boolean {
        return file.exists()
    }
}