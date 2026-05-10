import com.abmo.providers.SieutamphimProvider
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
