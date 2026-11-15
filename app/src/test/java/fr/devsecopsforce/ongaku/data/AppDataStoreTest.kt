package fr.devsecopsforce.ongaku.data

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AppDataStoreTest {

    private val context = RuntimeEnvironment.getApplication()
    private val dir = context.filesDir
    private val file = File(dir, "ongaku.json")

    private val sample = AppData(
        favorites = setOf(5, 7),
        playlists = listOf(Playlist("p1", "Route", listOf(7, 5)), Playlist("p2", "Vide")),
        appBackground = "fond.img",
        playerBackground = null,
        trackBackgrounds = mapOf(5L to "cinq.img"),
    )

    @Before
    fun setUp() {
        AppDataStore.noticeShown()
    }

    private fun store() = AppDataStore(context)

    @Test
    fun `sans fichier, les données sont vides`() {
        assertEquals(AppData(), store().load())
        assertNull(AppDataStore.pendingNotice.value)
    }

    @Test
    fun `ce qui est enregistré est relu à l'identique, avec le numéro de version`() {
        store().save(sample)

        assertEquals(sample, store().load())
        assertEquals(AppDataStore.VERSION, JSONObject(file.readText()).getInt("version"))
    }

    @Test
    fun `un second enregistrement remplace bien le premier`() {
        store().save(AppData(favorites = setOf(1)))
        store().save(AppData(favorites = setOf(2)))

        assertEquals(setOf(2L), store().load().favorites)
        assertFalse(File(dir, "ongaku.json.tmp").exists())
    }

    @Test
    fun `fichier ancien, sans version et sans certains champs, relu sans perte`() {
        // Format d'avant les images par morceau, sans numéro de version.
        file.writeText("""{"favorites":[3,4],"playlists":[{"id":"p","name":"Ancienne","tracks":[4]}]}""")

        val data = store().load()

        assertEquals(setOf(3L, 4L), data.favorites)
        assertEquals(listOf(Playlist("p", "Ancienne", listOf(4))), data.playlists)
        assertEquals(emptyMap<Long, String>(), data.trackBackgrounds)
        assertNull(data.appBackground)
        assertNull(AppDataStore.pendingNotice.value)
    }

    @Test
    fun `une liste à qui il manque un champ reste lisible`() {
        file.writeText("""{"playlists":[{"tracks":[1]},{"id":"x","name":"Sans morceaux"}]}""")

        val playlists = store().load().playlists

        assertEquals("Liste sans nom", playlists[0].name)
        assertEquals(listOf(1L), playlists[0].trackIds)
        assertEquals(Playlist("x", "Sans morceaux", emptyList()), playlists[1])
    }

    @Test
    fun `un champ en trop est conservé à l'enregistrement`() {
        file.writeText("""{"version":3,"favorites":[1],"humeurs":{"calme":[1]}}""")

        val loaded = store()
        val data = loaded.load()
        loaded.save(data.copy(favorites = setOf(1, 2)))

        val written = JSONObject(file.readText())
        assertEquals("""{"calme":[1]}""", written.getJSONObject("humeurs").toString())
        // Écrit par une version plus récente : son numéro n'est pas rabaissé.
        assertEquals(3, written.getInt("version"))
        assertEquals(setOf(1L, 2L), store().load().favorites)
    }

    @Test
    fun `fichier tronqué, mis de côté sous un nom daté et repris depuis la copie précédente`() {
        store().save(sample)
        store().save(sample.copy(favorites = setOf(5, 7, 9))) // sample devient la copie précédente
        file.writeText("""{"favorites":[5,7,9],"playlists":[{"id":"p1","na""")

        val data = store().load()

        assertEquals(sample, data)
        assertEquals(sample, store().load()) // le fichier lui-même a été rétabli
        val copies = AppDataStore.corruptCopies(dir)
        assertEquals(1, copies.size)
        assertTrue(copies.single().name.matches(Regex("""ongaku\.json\.corrupt-\d{8}-\d{6}-\d{3}""")))
        assertTrue(copies.single().readText().endsWith("\"na"))
        assertEquals(AppDataStore.Notice(copies.single().name, restoredFromCopy = true), AppDataStore.pendingNotice.value)
    }

    @Test
    fun `fichier abîmé sans copie lisible, repart de zéro et prévient`() {
        file.writeText("pas du json")

        assertEquals(AppData(), store().load())
        assertFalse(file.exists())
        assertEquals(false, AppDataStore.pendingNotice.value?.restoredFromCopy)
    }

    @Test
    fun `un champ présent mais illisible ne fait rien perdre en silence`() {
        file.writeText("""{"favorites":"cinq","playlists":[{"id":"p","name":"Route","tracks":[1]}]}""")

        assertEquals(AppData(), store().load())
        // Rien n'est réinterprété : l'original est intact parmi les copies mises de côté.
        assertTrue(AppDataStore.corruptCopies(dir).single().readText().contains("Route"))
    }

    @Test
    fun `plusieurs incidents gardent chacun leur copie, dans la limite des cinq dernières`() {
        repeat(7) { index ->
            file.writeText("abîmé $index")
            store().load()
        }

        val copies = AppDataStore.corruptCopies(dir)
        assertEquals(AppDataStore.MAX_CORRUPT_COPIES, copies.size)
        assertEquals((6 downTo 2).map { "abîmé $it" }, copies.map { it.readText() })
    }

    @Test
    fun `une copie laissée par l'ancienne écriture est récupérée`() {
        File(dir, "ongaku.json.bak").writeText("""{"favorites":[4]}""")

        assertEquals(setOf(4L), store().load().favorites)
    }

    @Test
    fun `un fichier abîmé ne remplace jamais la bonne copie précédente`() {
        store().save(sample)
        store().save(sample.copy(favorites = setOf(1)))
        file.writeText("abîmé")

        store().save(AppData(favorites = setOf(2)))

        // La copie précédente est restée celle de sample, la dernière version lisible avant l'incident.
        val copy = JSONObject(File(dir, "ongaku.json.prev").readText())
        assertEquals("[5,7]", copy.getJSONArray("favorites").toString())
        assertEquals("Route", copy.getJSONArray("playlists").getJSONObject(0).getString("name"))
    }
}
