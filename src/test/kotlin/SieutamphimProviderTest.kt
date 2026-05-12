package com.abmo

import com.abmo.providers.SieutamphimProvider
import com.abmo.util.toJsoupDocument
import com.abmo.model.video.Mp4
import com.abmo.model.video.FirstData
import com.abmo.model.video.Source
import com.abmo.model.video.toSimpleVideo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SieutamphimProviderTest {

    @Test
    fun `extract video id from data episodes without explicit key`() {
        val provider = SieutamphimProvider()
        val html = """
            <div id="mytick">
              <div class="episodeGroup" data-server="hx" data-episodes='[
                {"B^^ZYYBEX^CDA_n^lNY","1"},
                {"B^^ZYYBEX^CDAYBFAH[l","2"},
              ]'></div>
            </div>
        """.trimIndent()

        assertEquals("4uD-2tFds", provider.extractVideoId(html, 0))
    }

    @Test
    fun `extract all episode targets for series page`() {
        val provider = SieutamphimProvider()
        val html = """
            <html>
            <body>
            <h1 class="entry-title">W - Hai The Gioi - Status: 16 / 16</h1>
            <div id="mytick">
              <div class="episodeGroup" data-server="hx" data-episodes='[
                {"B^^ZYYBEX^CDA_n^lNY","1"},
                {"B^^ZYYBEX^CDAYBFAH[l","2"}
              ]'></div>
            </div>
            <div class="panelz" data-episode-container='[
                "br",
                "Tap 1. Hai the gioi",
                "Tap 2. Nhan vat chinh"
            ]'></div>
            </body>
            </html>
        """.trimIndent().toJsoupDocument()

        val targets = provider.extractEpisodeIds(html)
        val episodeNames = provider.extractEpisodeNames(html)
        val seriesTitle = provider.extractSeriesTitle(html)

        assertEquals(2, targets.size)
        assertEquals("4uD-2tFds", targets.first())
        assertEquals(listOf("Tap 1. Hai the gioi", "Tap 2. Nhan vat chinh"), episodeNames)
        assertTrue(seriesTitle.startsWith("W"))
    }

    @Test
    fun `prefer abyss episode group when page has multiple sources`() {
        val provider = SieutamphimProvider()
        val html = """
            <div id="mytick">
              <div class="episodeGroup" data-server="vc" data-episodes='[
                {"https://vc.example/691640aaf612e3cff99a4efd","1"},
                {"https://vc.example/6916410eba13d3e2d2821230","2"}
              ]'></div>
              <div class="episodeGroup" data-server="hx" data-episodes='[
                {"B^^ZYYBEX^CDA_n^lNY","1"},
                {"B^^ZYYBEX^CDAYBFAH[l","2"}
              ]'></div>
            </div>
        """.trimIndent().toJsoupDocument()

        assertEquals(listOf("4uD-2tFds", "s2hlkb1qF"), provider.extractEpisodeIds(html))
    }

    @Test
    fun `prefer source url when building simple video`() {
        val metadata = Mp4(
            domains = listOf("https://fallback.example.com"),
            sources = listOf(
                Source(
                    label = "1080p",
                    size = 123L,
                    res_id = 1,
                    sub = "ignored",
                    url = "d4vi7e6z20.sssrr.org"
                )
            ),
            slug = "slug",
            md5_id = 1
        )

        val simpleVideo = metadata.toSimpleVideo("1080p")

        assertNotNull(simpleVideo.url)
        assertEquals("https://d4vi7e6z20.sssrr.org", simpleVideo.url)
    }

    @Test
    fun `fallback to first data url when source url is absent`() {
        val metadata = Mp4(
            firstDatas = listOf(
                FirstData(
                    res_id = 1,
                    size = 123L,
                    url = "d4vi7e6z20.sssrr.org"
                )
            ),
            sources = listOf(
                Source(
                    label = "1080p",
                    size = 123L,
                    res_id = 1,
                    sub = "ignored",
                    url = null
                )
            ),
            slug = "slug",
            md5_id = 1
        )

        val simpleVideo = metadata.toSimpleVideo("1080p")

        assertNotNull(simpleVideo.url)
        assertEquals("https://d4vi7e6z20.sssrr.org", simpleVideo.url)
    }

    @Test
    fun `choose indexed domain when source subdomain is absent`() {
        val metadata = Mp4(
            domains = listOf(
                "vsnaiq1323.sssrr.org",
                "0bud01ado11.sssrr.org",
                "qgc1tmer44.sssrr.org"
            ),
            firstDatas = listOf(
                FirstData(
                    codec = "h264",
                    partSize = 16777216,
                    res_id = 5,
                    size = 1183795585L,
                    url = "aytgzfk1k8.sssrr.org/c/2/5/file.1183795585.5.fd"
                )
            ),
            sources = listOf(
                Source(
                    codec = "h264",
                    label = "1080p",
                    size = 1183795585L,
                    res_id = 5,
                    sub = null,
                    url = null
                )
            ),
            slug = "s2hlkb1qF",
            md5_id = 23537279
        )

        val simpleVideo = metadata.toSimpleVideo("1080p")

        assertNotNull(simpleVideo.url)
        assertEquals("https://0bud01ado11.sssrr.org", simpleVideo.url)
        assertEquals(16777216L, simpleVideo.partSize)
        assertEquals(null, simpleVideo.path)
    }

    @Test
    fun `strip media path from fallback first data url before building segment base`() {
        val metadata = Mp4(
            firstDatas = listOf(
                FirstData(
                    res_id = 1,
                    size = 123L,
                    url = "8oekkfci14.sssrr.org/7/1/6/da344e57ab1141b043ec6b3e64a57.558401029.5.fd"
                )
            ),
            sources = listOf(
                Source(
                    label = "1080p",
                    size = 123L,
                    res_id = 1
                )
            ),
            slug = "slug",
            md5_id = 1
        )

        val simpleVideo = metadata.toSimpleVideo("1080p")

        assertNotNull(simpleVideo.url)
        assertEquals("https://8oekkfci14.sssrr.org", simpleVideo.url)
        assertEquals(null, simpleVideo.path)
    }

    @Test
    fun `prefer alternate matching source over zero part size source`() {
        val metadata = Mp4(
            domains = listOf(
                "valid-sub.sssrr.org"
            ),
            firstDatas = listOf(
                FirstData(
                    partSize = 16777216,
                    res_id = 4,
                    size = 334150344L,
                    url = "vdqv2f3va0.sssrr.org/0/9/b/file.334150344.4.fd"
                )
            ),
            sources = listOf(
                Source(
                    label = "720p",
                    partSize = 0,
                    path = "0/9/b/ee19d8d335f3b211a4084dbfd25c2.334150344.4",
                    size = 334150344L,
                    res_id = 4,
                    url = "vdqv2f3va0.sssrr.org"
                ),
                Source(
                    label = "720p",
                    partSize = null,
                    size = 313564130L,
                    res_id = 4,
                    sub = "valid-sub"
                )
            ),
            slug = "slug",
            md5_id = 27452164
        )

        val simpleVideo = metadata.toSimpleVideo("720p")

        assertEquals(null, simpleVideo.partSize)
        assertEquals(null, simpleVideo.path)
        assertEquals(313564130L, simpleVideo.size)
        assertEquals("https://valid-sub.sssrr.org", simpleVideo.url)
    }
}
