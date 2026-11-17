package com.ergin.deepreader.core.storage

import java.net.URLDecoder
import java.util.zip.ZipFile

/** Image bytes of an EPUB cover together with a file extension suitable for storing it. */
class EpubCover(val bytes: ByteArray, val extension: String)

/**
 * Locates and reads a cover image from an EPUB archive using only the OPF package document.
 *
 * Lookup order:
 * 1. manifest item with `properties="cover-image"` (EPUB 3);
 * 2. `<meta name="cover" content="item-id">` (EPUB 2);
 * 3. any image manifest item whose id or href contains "cover".
 *
 * If the resolved item is an (X)HTML page, the first image referenced inside it is used.
 */
internal object EpubCoverExtractor {

    private const val MAX_COVER_BYTES = 10 * 1024 * 1024

    private val itemRegex = Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val metaRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val attributeRegex = Regex("""([\w:.-]+)\s*=\s*("([^"]*)"|'([^']*)')""")
    private val imageRefRegex = Regex("""(?:src|xlink:href|href)\s*=\s*["']([^"']+\.(?:jpe?g|png|gif|webp))["']""", RegexOption.IGNORE_CASE)

    private data class ManifestItem(val id: String, val href: String, val mediaType: String, val properties: String)

    fun extract(zip: ZipFile, opfPath: String, opfContent: String): EpubCover? {
        val items = parseManifest(opfContent)
        val item = findCoverItem(items, opfContent) ?: return null
        val baseDir = opfPath.substringBeforeLast('/', "")
        return readItem(zip, item, baseDir)
    }

    private fun findCoverItem(items: List<ManifestItem>, opfContent: String): ManifestItem? {
        items.firstOrNull { "cover-image" in it.properties.split(' ') }?.let { return it }

        val coverId = metaRegex.findAll(opfContent)
            .map { parseAttributes(it.value) }
            .firstOrNull { it["name"].equals("cover", ignoreCase = true) }
            ?.get("content")
        if (coverId != null) {
            items.firstOrNull { it.id == coverId }?.let { return it }
        }

        return items.firstOrNull {
            it.mediaType.startsWith("image/") &&
                (it.id.contains("cover", ignoreCase = true) || it.href.contains("cover", ignoreCase = true))
        }
    }

    private fun readItem(zip: ZipFile, item: ManifestItem, baseDir: String): EpubCover? {
        val path = resolvePath(baseDir, item.href)
        if (item.mediaType.startsWith("image/")) {
            val bytes = readEntry(zip, path) ?: return null
            return EpubCover(bytes, extensionFor(item.mediaType, path))
        }

        // Cover declared as a page: take the first image referenced inside it.
        val pageContent = readEntry(zip, path)?.toString(Charsets.UTF_8) ?: return null
        val imageRef = imageRefRegex.find(pageContent)?.groupValues?.get(1) ?: return null
        val imagePath = resolvePath(path.substringBeforeLast('/', ""), imageRef)
        val bytes = readEntry(zip, imagePath) ?: return null
        return EpubCover(bytes, extensionFor("", imagePath))
    }

    private fun readEntry(zip: ZipFile, path: String): ByteArray? {
        val entry = zip.getEntry(path) ?: zip.getEntry(decode(path)) ?: return null
        if (entry.isDirectory || entry.size > MAX_COVER_BYTES) return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_COVER_BYTES }
    }

    private fun parseManifest(opfContent: String): List<ManifestItem> =
        itemRegex.findAll(opfContent).mapNotNull { match ->
            val attrs = parseAttributes(match.value)
            val href = attrs["href"] ?: return@mapNotNull null
            ManifestItem(
                id = attrs["id"].orEmpty(),
                href = href,
                mediaType = attrs["media-type"].orEmpty(),
                properties = attrs["properties"].orEmpty()
            )
        }.toList()

    private fun parseAttributes(tag: String): Map<String, String> =
        attributeRegex.findAll(tag).associate { match ->
            val value = match.groups[3]?.value ?: match.groups[4]?.value.orEmpty()
            match.groupValues[1] to value
        }

    private fun resolvePath(baseDir: String, href: String): String {
        val cleanHref = href.substringBefore('#').substringBefore('?')
        val segments = ArrayDeque<String>()
        val combined = if (cleanHref.startsWith("/")) cleanHref.removePrefix("/") else {
            if (baseDir.isEmpty()) cleanHref else "$baseDir/$cleanHref"
        }
        combined.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> segments.removeLastOrNull()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/")
    }

    private fun decode(path: String): String = try {
        URLDecoder.decode(path.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        path
    }

    private fun extensionFor(mediaType: String, path: String): String {
        val fromPath = path.substringAfterLast('.', "").lowercase()
        return when {
            mediaType == "image/png" || fromPath == "png" -> "png"
            mediaType == "image/gif" || fromPath == "gif" -> "gif"
            mediaType == "image/webp" || fromPath == "webp" -> "webp"
            else -> "jpg"
        }
    }
}
