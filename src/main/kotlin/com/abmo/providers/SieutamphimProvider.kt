package com.abmo.providers

import com.abmo.util.fetchDocument
import com.abmo.util.toJsoupDocument
import org.jsoup.nodes.Document
import java.nio.charset.Charset

class SieutamphimProvider: Provider {

    override fun getVideoID(url: String): String? {
        val splits = url.split("--")
        val originalUrl = splits[0]
        val episodeInput = (splits.getOrNull(1)?.toIntOrNull()?.minus(1)) ?: 0
        val document = originalUrl.fetchDocument()
        return extractVideoId(document, episodeInput)
    }

    internal fun extractVideoId(html: String, episodeInput: Int = 0): String? {
        val document = html.toJsoupDocument()
        return extractVideoId(document, episodeInput)
    }

    internal fun extractVideoId(document: Document, episodeInput: Int = 0): String? {
        val encryptedEpisodes = document.selectFirst("div#mytick div.episodeGroup[data-episodes]")
            ?.attr("data-episodes")
            ?.let(::extractEpisodesFromDataAttribute)

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
