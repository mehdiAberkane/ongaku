package fr.devsecopsforce.ongaku.widget

import fr.devsecopsforce.ongaku.data.AppData
import fr.devsecopsforce.ongaku.widget.WidgetImage.Artwork
import fr.devsecopsforce.ongaku.widget.WidgetImage.Background
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetImageTest {

    private val full = AppData(
        appBackground = "app.img",
        playerBackground = "player.img",
        trackBackgrounds = mapOf(7L to "morceau.img"),
        widgetBackground = "widget.img",
    )

    @Test
    fun `l'image du widget passe avant tout le reste`() {
        assertEquals(
            listOf(Background("widget.img"), Background("morceau.img"), Artwork("content://pochette"), Background("player.img"), Background("app.img")),
            WidgetImage.candidates(full, trackId = 7, artworkUri = "content://pochette"),
        )
    }

    @Test
    fun `sans image du widget, l'image du morceau puis la pochette`() {
        val data = full.copy(widgetBackground = null)

        assertEquals(Background("morceau.img"), WidgetImage.candidates(data, 7, "content://pochette").first())
        assertEquals(Artwork("content://pochette"), WidgetImage.candidates(data, 8, "content://pochette").first())
    }

    @Test
    fun `sans pochette, l'image par défaut de l'écran de lecture puis celle de l'application`() {
        val data = full.copy(widgetBackground = null, trackBackgrounds = emptyMap())

        assertEquals(listOf(Background("player.img"), Background("app.img")), WidgetImage.candidates(data, 7, null))
        assertEquals(listOf(Background("app.img")), WidgetImage.candidates(data.copy(playerBackground = null), 7, null))
    }

    @Test
    fun `rien de choisi et pas de pochette, aucune image`() {
        assertEquals(emptyList<WidgetImage>(), WidgetImage.candidates(AppData(), trackId = null, artworkUri = null))
    }

    @Test
    fun `la dernière image n'est pas redécodée`() {
        val cache = WidgetImageCache<String>()
        val decoded = mutableListOf<WidgetImage>()
        val load = { image: WidgetImage -> decoded += image; image.toString() }
        val cover = WidgetImage.Artwork("content://pochette/1")

        cache.pick(listOf(cover), load)
        cache.pick(listOf(cover), load)

        assertEquals(listOf<WidgetImage>(cover), decoded)
    }

    @Test
    fun `une image prioritaire passe devant celle gardée`() {
        val cache = WidgetImageCache<String>()
        val cover = WidgetImage.Artwork("content://pochette/1")
        val chosen = WidgetImage.Background("widget.img")
        cache.pick(listOf(cover)) { "pochette" }

        assertEquals("widget", cache.pick(listOf(chosen, cover)) { if (it == chosen) "widget" else "pochette" })
    }

    @Test
    fun `une image illisible laisse passer la suivante`() {
        val cache = WidgetImageCache<String>()
        val missing = WidgetImage.Background("effacee.img")
        val cover = WidgetImage.Artwork("content://pochette/1")

        assertEquals("pochette", cache.pick(listOf(missing, cover)) { if (it == missing) null else "pochette" })
        assertEquals(null, cache.pick(listOf(missing)) { null })
    }
}