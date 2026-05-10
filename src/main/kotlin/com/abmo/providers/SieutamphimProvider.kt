package com.abmo.providers

import com.abmo.util.fetchDocument
import com.abmo.util.sanitizeFileName
import com.abmo.util.toJsoupDocument
import org.jsoup.nodes.Document
import java.nio.charset.Charset

class SieutamphimProvider: Provider {

    override fun getVideoID(url: String): String? {
        return getDownloadTargets(url).firstOrNull()?.videoId
    }

    override fun getDownloadTargets(url: String): List<DownloadTarget> {
        val splits = url.split("--")
        val originalUrl = splits[0]
        val episodeInput = (splits.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: 0
        val document = originalUrl.fetchDocument()
        val seriesTitle = extractSeriesTitle(document)
        val episodeNames = extractEpisodeNames(document)
        val episodeIds = extractEpisodeIds(document)

        if (episodeIds.isEmpty()) return emptyList()

        if (splits.size > 1) {
            val selectedVideoId = episodeIds.getOrNull(episodeInput) ?: return emptyList()
            val fileStem = buildEpisodeFileStem(seriesTitle, episodeInput, episodeNames.getOrNull(episodeInput))
            return listOf(DownloadTarget(selectedVideoId, fileStem))
        }

        return episodeIds.mapIndexed { index, videoId ->
            DownloadTarget(
                videoId = videoId,
                fileStem = buildEpisodeFileStem(seriesTitle, index, episodeNames.getOrNull(index))
            )
        }
    }

    internal fun extractVideoId(html: String, episodeInput: Int = 0): String? {
        val document = html.toJsoupDocument()
        return extractVideoId(document, episodeInput)
    }

    internal fun extractVideoId(document: Document, episodeInput: Int = 0): String? {
        val encryptedEpisodes = extractEncryptedEpisodes(document)

        if (encryptedEpisodes != null) {
            if (episodeInput >= encryptedEpisodes.size) return null

            val encryptedUrl = encryptedEpisodes[episodeInput]
            val key = extractXorKey(document.html(), encryptedUrl) ?: return null

            return decodeXor(encryptedUrl, key).substringAfterLast("/")
        }

        val episodes = document.select("div#mytick span.server-hx").nextAll()
            .select("button span")
        if (episodes.isEmpty()) return null

        if (episodeInput >= episodes.size) return null

        val encryptedUrl = episodes[episodeInput].attr("data-src")
        val regex = """const\s+key\s*=\s*(\d+);""".toRegex()
        val key = regex.find(document.html())?.groupValues?.getOrNull(1)
            ?.toIntOrNull() ?: return null

        return decodeXor(encryptedUrl, key).substringAfterLast("/")
    }

    internal fun extractEpisodeIds(document: Document): List<String> {
        val encryptedEpisodes = extractEncryptedEpisodes(document)
        if (encryptedEpisodes != null) {
            return encryptedEpisodes.mapNotNull { encryptedUrl ->
                val key = extractXorKey(document.html(), encryptedUrl) ?: return@mapNotNull null
                decodeXor(encryptedUrl, key).substringAfterLast("/").takeIf { it.isNotBlank() }
            }
        }

        val episodes = document.select("div#mytick span.server-hx").nextAll()
            .select("button span")

        if (episodes.isEmpty()) return emptyList()

        val key = """const\s+key\s*=\s*(\d+);""".toRegex()
            .find(document.html())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return emptyList()

        return episodes.mapNotNull { episode ->
            episode.attr("data-src")
                .takeIf { it.isNotBlank() }
                ?.let { decodeXor(it, key).substringAfterLast("/") }
        }
    }

    internal fun extractEpisodeNames(document: Document): List<String> {
        val rawEpisodeContainer = document.selectFirst(".panelz[data-episode-container]")
            ?.attr("data-episode-container")
            ?: return emptyList()

        return Regex(""""([^"]+)"""")
            .findAll(rawEpisodeContainer)
            .map { it.groupValues[1] }
            .filterNot { it == "br" }
            .toList()
    }

    internal fun extractSeriesTitle(document: Document): String {
        return document.selectFirst("h1.entry-title")
            ?.text()
            ?.substringBefore(" - Status:")
            ?.substringBefore(" – Status:")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "episode"
    }

    private fun extractEncryptedEpisodes(document: Document): List<String>? {
        return document.selectFirst("div#mytick div.episodeGroup[data-episodes]")
            ?.attr("data-episodes")
            ?.let(::extractEpisodesFromDataAttribute)
    }

    private fun buildEpisodeFileStem(seriesTitle: String, index: Int, episodeName: String?): String {
        val normalizedSeriesTitle = sanitizeFileName(seriesTitle)
        val episodeLabel = episodeName
            ?.replace(Regex("""^Tập\s+\d+\.\s*""", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::sanitizeFileName)

        return listOfNotNull(
            normalizedSeriesTitle,
            "E${(index + 1).toString().padStart(2, '0')}",
            episodeLabel
        ).joinToString(" - ")
    }

    private fun extractEpisodesFromDataAttribute(dataEpisodes: String): List<String>? {
        return Regex("""\{\s*"([^"]+)"\s*,\s*"[^"]+"\s*}""")
            .findAll(dataEpisodes)
            .map { it.groupValues[1] }
            .toList()
            .ifEmpty { null }
    }

    private fun extractXorKey(html: String, encryptedUrl: String): Int? {
        Regex("""const\s+key\s*=\s*(\d+);""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.let { return it }

        return (0..255).firstOrNull { key ->
            val decoded = decodeXor(encryptedUrl, key)
            decoded.startsWith("http://") || decoded.startsWith("https://")
        }
    }

    private fun decodeXor(encodedStr: String, key: Int): String {
        val xorEncodedBytes = encodedStr.map { it.code.toByte() }.toByteArray()
        val decodedBytes = xorEncryptDecrypt(xorEncodedBytes, key)
        return String(decodedBytes, Charset.defaultCharset())
    }


    private fun xorEncryptDecrypt(data: ByteArray, key: Int): ByteArray {
        return data.map { it.toInt() xor key }.map { it.toByte() }.toByteArray()
    }

}
