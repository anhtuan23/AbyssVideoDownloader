import com.abmo.providers.SieutamphimProvider
import com.abmo.util.toJsoupDocument
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
