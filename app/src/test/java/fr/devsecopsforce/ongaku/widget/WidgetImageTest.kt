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
}
