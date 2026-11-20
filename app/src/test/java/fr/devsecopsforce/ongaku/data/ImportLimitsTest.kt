package fr.devsecopsforce.ongaku.data

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportLimitsTest {

    @Test
    fun `un fichier sous le plafond est lu entièrement`() {
        val bytes = ByteArray(200_000) { it.toByte() }
        assertArrayEquals(bytes, ImportLimits.readLimited(ByteArrayInputStream(bytes), 200_000))
    }

    @Test
    fun `un fichier trop gros est refusé sans être lu jusqu'au bout`() {
        var read = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { read++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int = len.also { read += it }
        }
        assertNull(ImportLimits.readLimited(endless, 1_000_000))
        assertTrue("lecture arrêtée peu après le plafond", read < 1_100_000)
    }

    @Test
    fun `une photo est réduite pour que son petit côté couvre l'écran`() {
        assertEquals(3219 to 2414, ImportLimits.fitSize(4000, 3000, 2414))
        assertEquals(2414 to 3219, ImportLimits.fitSize(3000, 4000, 2414))
    }

    @Test
    fun `une petite image n'est jamais agrandie`() {
        assertEquals(800 to 600, ImportLimits.fitSize(800, 600, 2414))
    }

    @Test
    fun `le sous-échantillonnage reste au-dessus de la taille voulue`() {
        assertEquals(1, ImportLimits.sampleSize(4000, 3000, 2414))
        assertEquals(4, ImportLimits.sampleSize(12000, 10000, 2414))
        val sample = ImportLimits.sampleSize(12000, 10000, 2414)
        assertTrue(minOf(12000, 10000) / sample >= 2414)
    }

    private val data = AppData(
        appBackground = "app.img",
        playerBackground = "lecteur.img",
        widgetBackground = "widget.img",
        trackBackgrounds = mapOf(1L to "m1.img", -7L to "introuvable.img"),
    )

    @Test
    fun `seuls les fichiers que rien ne désigne et assez anciens sont des restes`() {
        val hour = 3_600_000L
        val files = listOf(
            "app.img" to 0L,
            "m1.img" to 0L,
            "introuvable.img" to 0L,
            "reste.img" to 0L,
            "en-cours.img" to 10 * hour - 1_000,
        )
        assertEquals(listOf("reste.img"), orphanBackgroundFiles(data, files, nowMs = 10 * hour, minAgeMs = hour))
    }

    @Test
    fun `les images des morceaux introuvables sont repérées`() {
        assertEquals(mapOf(-7L to "introuvable.img"), data.missingTrackBackgrounds(existing = setOf(1L, 2L)))
    }
}
