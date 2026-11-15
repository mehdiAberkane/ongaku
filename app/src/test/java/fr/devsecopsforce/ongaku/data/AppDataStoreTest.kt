package fr.devsecopsforce.ongaku.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppDataStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val file = File(context.filesDir, "ongaku.json")

    @Test
    fun `sans fichier, les données sont vides`() {
        assertEquals(AppData(), AppDataStore(context).load())
    }

    @Test
    fun `ce qui est enregistré est relu à l'identique`() {
        val data = AppData(
            favorites = setOf(5, 7),
            playlists = listOf(Playlist("p1", "Route", listOf(7, 5)), Playlist("p2", "Vide")),
            appBackground = "fond.img",
            playerBackground = null,
            trackBackgrounds = mapOf(5L to "cinq.img"),
        )

        AppDataStore(context).save(data)

        assertEquals(data, AppDataStore(context).load())
    }

    @Test
    fun `un second enregistrement remplace bien le premier`() {
        AppDataStore(context).save(AppData(favorites = setOf(1)))
        AppDataStore(context).save(AppData(favorites = setOf(2)))

        assertEquals(setOf(2L), AppDataStore(context).load().favorites)
        assertFalse(File(context.filesDir, "ongaku.json.tmp").exists())
    }

    @Test
    fun `une copie laissée par l'ancienne écriture est récupérée`() {
        File(context.filesDir, "ongaku.json.bak").writeText(
            """{"favorites":[4],"playlists":[],"appBackground":null,"playerBackground":null,"trackBackgrounds":{}}""",
        )

        assertEquals(setOf(4L), AppDataStore(context).load().favorites)
    }

    @Test
    fun `un fichier abîmé est mis de côté au lieu d'être écrasé`() {
        file.writeText("{ pas du json")

        val data = AppDataStore(context).load()

        assertEquals(AppData(), data)
        assertFalse(file.exists())
        val corrupt = File(context.filesDir, "ongaku.json.corrupt")
        assertTrue(corrupt.exists())
        assertEquals("{ pas du json", corrupt.readText())
    }
}
