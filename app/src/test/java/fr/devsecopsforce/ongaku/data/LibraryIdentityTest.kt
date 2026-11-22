package fr.devsecopsforce.ongaku.data

import androidx.core.content.edit
import fr.devsecopsforce.ongaku.playback.PlaybackStateKeeper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LibraryIdentityTest {

    private val context = RuntimeEnvironment.getApplication()
    private val store = AppDataStore(context)
    private val counts = PlayCounts(context)
    private val resume = PlaybackStateKeeper.prefs(context)

    private val a = track(1, title = "Alpha")
    private val b = track(2, title = "Bravo")
    private val c = track(3, title = "Charlie")

    @Before
    fun setUp() {
        LibraryIdentity.resetForTests()
        store.save(
            AppData(
                favorites = setOf(1, 2),
                playlists = listOf(Playlist("p", "Route", listOf(3, 1))),
                trackBackgrounds = mapOf(2L to "fond.img"),
            ),
        )
        counts.increment(1, nowMs = 1_000)
        counts.increment(1, nowMs = 2_000)
        counts.increment(3, nowMs = 3_000)
        resume.edit {
            putString(PlaybackStateKeeper.KEY_QUEUE, "1,2,3")
            putString(PlaybackStateKeeper.KEY_CURRENT, "2")
            putLong(PlaybackStateKeeper.KEY_POSITION, 12_000)
        }
        // Premier chargement : l'empreinte est prise, rien ne bouge.
        LibraryIdentity.check(context, listOf(a, b, c))
    }

    @Test
    fun `le premier chargement ne modifie aucune donnée`() {
        assertEquals(setOf(1L, 2L), store.load().favorites)
        assertEquals(0, LibraryIdentity.generation.value)
        assertNull(LibraryIdentity.pendingReport.value)
    }

    @Test
    fun `après une renumérotation, toutes les données suivent leurs fichiers`() {
        LibraryIdentity.resetForTests() // nouveau processus : l'empreinte est relue sur disque
        LibraryIdentity.check(context, listOf(a.renumbered(20), b.renumbered(30), c.renumbered(1)))

        val data = store.load()
        assertEquals(setOf(20L, 30L), data.favorites)
        assertEquals(listOf(1L, 20L), data.playlists.single().trackIds)
        assertEquals(mapOf(30L to "fond.img"), data.trackBackgrounds)
        assertEquals(mapOf(20L to 2, 1L to 1), counts.all())
        assertEquals(mapOf(20L to 2_000L, 1L to 3_000L), counts.lastPlayed())
        assertEquals("20,30,1", resume.getString(PlaybackStateKeeper.KEY_QUEUE, null))
        assertEquals("30", resume.getString(PlaybackStateKeeper.KEY_CURRENT, null))
        assertEquals(12_000L, resume.getLong(PlaybackStateKeeper.KEY_POSITION, 0))
        assertEquals(1, LibraryIdentity.generation.value)
        assertEquals(LibraryIdentity.Report(recovered = 3, missing = 0), LibraryIdentity.pendingReport.value)
    }

    @Test
    fun `un fichier disparu reste dans les listes, signalé, puis revient`() {
        LibraryIdentity.check(context, listOf(b, c))

        assertEquals(setOf(-1L, 2L), store.load().favorites)
        assertEquals(LibraryIdentity.Report(recovered = 0, missing = 1), LibraryIdentity.pendingReport.value)
        LibraryIdentity.reportShown()

        LibraryIdentity.check(context, listOf(a.renumbered(70), b, c))

        assertEquals(setOf(70L, 2L), store.load().favorites)
        assertEquals(listOf(3L, 70L), store.load().playlists.single().trackIds)
        assertEquals(LibraryIdentity.Report(recovered = 1, missing = 0), LibraryIdentity.pendingReport.value)
    }

    @Test
    fun `une bibliothèque vide au démarrage ne touche à rien`() {
        LibraryIdentity.check(context, emptyList())

        assertEquals(setOf(1L, 2L), store.load().favorites)
        assertEquals(0, LibraryIdentity.generation.value)
    }

    @Test
    fun `deux anciens compteurs vers un même morceau s'additionnent`() {
        counts.remap(mapOf(1L to 3L))

        assertEquals(mapOf(3L to 3), counts.all())
        // La dernière écoute est la plus récente des deux.
        assertEquals(mapOf(3L to 3_000L), counts.lastPlayed())
    }

    @Test
    fun `une dernière écoute seule suffit à faire suivre le morceau`() {
        counts.prefs.edit(commit = true) { clear() }
        counts.remap(mapOf(1L to 5L))

        assertEquals(mapOf(5L to 2_000L, 3L to 3_000L), counts.lastPlayed())
    }
}
