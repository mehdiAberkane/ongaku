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
class RuleListTest {

    private val context = RuntimeEnvironment.getApplication()

    private val library = listOf(
        track(1, artist = "B", year = 1995, durationMs = 200_000, folder = "Music/Rock", genre = "Rock; Grunge"),
        track(2, artist = "A", year = 2005, durationMs = 400_000, folder = "Music/Rock/Live", genre = "rock"),
        track(3, artist = "C", year = 0, durationMs = 100_000, folder = "Music/Jazz", genre = "Jazz"),
        track(4, artist = "D", year = 2015, durationMs = 250_000, folder = "Music2", genre = ""),
    )

    private fun ids(rule: RuleList) = rule.tracks(library).map { it.id }

    @Test
    fun `genre exact, sans casse, parmi plusieurs genres du tag`() {
        assertEquals(listOf(2L, 1L), ids(RuleList("r", "Rock", genre = "ROCK")))
        assertEquals(listOf(1L), ids(RuleList("r", "Grunge", genre = "grunge")))
    }

    @Test
    fun `années bornées des deux côtés, un morceau sans année exclu dès qu'il y a une fin`() {
        assertEquals(listOf(1L), ids(RuleList("r", "90s", yearFrom = 1990, yearTo = 1999)))
        assertEquals(listOf(2L, 4L), ids(RuleList("r", "2000+", yearFrom = 2000)))
        assertEquals(listOf(1L), ids(RuleList("r", "Avant 2000", yearTo = 1999)))
    }

    @Test
    fun `durée en secondes`() {
        assertEquals(listOf(1L, 4L), ids(RuleList("r", "Courts", minSeconds = 150, maxSeconds = 300)))
    }

    @Test
    fun `dossier avec ses sous-dossiers, sans les dossiers au nom proche`() {
        assertEquals(listOf(2L, 1L), ids(RuleList("r", "Rock", folder = "Music/Rock")))
        assertEquals(emptyList<Long>(), ids(RuleList("r", "Music2", folder = "Music/Ro")))
        assertEquals(listOf(4L), ids(RuleList("r", "Music2", folder = "/Music2/")))
    }

    @Test
    fun `toutes les conditions s'additionnent`() {
        assertEquals(listOf(2L), ids(RuleList("r", "x", genre = "rock", yearFrom = 2000, folder = "Music")))
    }

    @Test
    fun `une liste sans condition ou à plage impossible est refusée`() {
        assertFalse(RuleList("r", "Tout").hasRule)
        assertTrue(RuleList("r", "Jazz", genre = "Jazz").hasRule)
        assertFalse(RuleList("r", "x", yearFrom = 2010, yearTo = 2000).rangesValid)
        assertFalse(RuleList("r", "x", minSeconds = 300, maxSeconds = 60).rangesValid)
        assertTrue(RuleList("r", "x", yearFrom = 2000, minSeconds = 300).rangesValid)
    }

    @Test
    fun `les listes par règle sont enregistrées et relues`() {
        val store = AppDataStore(context)
        val rule = RuleList("id1", "Rock 90", genre = "Rock", yearFrom = 1990, yearTo = 1999, minSeconds = 60, maxSeconds = 600, folder = "Music")
        store.save(AppData(favorites = setOf(1), ruleLists = listOf(rule)))

        assertEquals(listOf(rule), AppDataStore(context).load().ruleLists)
    }

    @Test
    fun `un fichier d'avant les listes par règle se relit sans elles`() {
        File(context.filesDir, "ongaku.json").writeText("""{"version":1,"favorites":[4],"playlists":[]}""")

        val data = AppDataStore(context).load()
        assertEquals(setOf(4L), data.favorites)
        assertEquals(emptyList<RuleList>(), data.ruleLists)
    }

    @Test
    fun `une condition manquante vaut sans condition`() {
        File(context.filesDir, "ongaku.json").writeText("""{"version":1,"ruleLists":[{"id":"a","name":"Jazz","genre":"Jazz"}]}""")

        assertEquals(listOf(RuleList("a", "Jazz", genre = "Jazz")), AppDataStore(context).load().ruleLists)
    }

    @Test
    fun `la sauvegarde emporte les listes par règle, sans doublon de nom à l'import`() {
        val rule = RuleList("id1", "Rock 90", genre = "Rock", yearFrom = 1990)
        val text = PlaylistBackup.serialize(AppData(ruleLists = listOf(rule)), library)
        val imported = PlaylistBackup.parse(text, library)
        assertEquals(listOf("Rock 90"), imported.ruleLists.map { it.name })

        var next = 0
        val fresh = PlaylistBackup.merge(AppData(), imported) { "n${next++}" }
        assertEquals(listOf(rule.copy(id = "n0")), fresh.ruleLists)
        // Déjà là sous ce nom : rien n'est ajouté.
        val existing = AppData(ruleLists = listOf(RuleList("x", "rock 90 ", genre = "Pop")))
        assertEquals(existing.ruleLists, PlaylistBackup.merge(existing, imported) { "n" }.ruleLists)
    }

    @Test
    fun `une ancienne sauvegarde se relit sans listes par règle`() {
        val old = """{"format":"ongaku-backup","version":1,"favorites":[],"playlists":[]}"""
        assertEquals(emptyList<RuleList>(), PlaylistBackup.parse(old, library).ruleLists)
    }
}
