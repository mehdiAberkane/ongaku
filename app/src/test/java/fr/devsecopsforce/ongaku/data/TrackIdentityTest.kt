package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TrackIdentityTest {

    private val a = track(1, title = "Alpha", artist = "A", album = "X")
    private val b = track(2, title = "Bravo", artist = "B", album = "Y")
    private val c = track(3, title = "Charlie", artist = "C", album = "Z")
    private val library = listOf(a, b, c)
    private val index = library.associate { it.id to it.key() }

    @Test
    fun `premier passage, rien ne change, l'empreinte est créée`() {
        val result = TrackIdentity.reconcile(null, library, referenced = setOf(1, 2))

        assertTrue(result.remap.isEmpty())
        assertEquals(index, result.index)
        assertEquals(0, result.missing)
    }

    @Test
    fun `premier passage, un identifiant déjà orphelin est écarté pour ne jamais viser un autre fichier`() {
        val result = TrackIdentity.reconcile(null, library, referenced = setOf(1, 99))

        assertEquals(mapOf(99L to -99L), result.remap)
        assertEquals(1, result.missing)
    }

    @Test
    fun `bibliothèque inchangée, aucune correction`() {
        val result = TrackIdentity.reconcile(index, library, referenced = setOf(1, 2, 3))

        assertTrue(result.remap.isEmpty())
        assertEquals(index, result.index)
    }

    @Test
    fun `identifiants redistribués, chaque morceau est retrouvé par son fichier`() {
        // Android a tout renuméroté, et l'ancien 1 désigne maintenant un autre fichier.
        val renumbered = listOf(a.renumbered(30), b.renumbered(1), c.renumbered(10))

        val result = TrackIdentity.reconcile(index, renumbered, referenced = setOf(1, 2, 3))

        assertEquals(mapOf(1L to 30L, 2L to 1L, 3L to 10L), result.remap)
        assertEquals(3, result.recovered)
        assertEquals(0, result.missing)
        assertEquals(renumbered.associate { it.id to it.key() }, result.index)
    }

    @Test
    fun `fichier déplacé, retrouvé par ses tags`() {
        val moved = a.copy(id = 40, relativePath = "Download/alpha.mp3", sizeBytes = 5)

        val result = TrackIdentity.reconcile(index, listOf(moved, b, c), referenced = setOf(1))

        assertEquals(mapOf(1L to 40L), result.remap)
    }

    @Test
    fun `album renommé, retrouvé par le titre et l'artiste`() {
        val retagged = a.copy(id = 41, relativePath = "Ailleurs/a.mp3", sizeBytes = 7, album = "X (Remaster)")

        val result = TrackIdentity.reconcile(index, listOf(retagged, b, c), referenced = setOf(1))

        assertEquals(mapOf(1L to 41L), result.remap)
    }

    @Test
    fun `même identifiant mais autre fichier, l'ancien morceau n'est pas confondu avec le nouveau`() {
        val stranger = track(1, title = "Inconnu", artist = "Q", album = "Q", relativePath = "Music/inconnu.mp3")

        val result = TrackIdentity.reconcile(index, listOf(stranger, b, c), referenced = setOf(1))

        assertEquals(mapOf(1L to -1L), result.remap)
        assertEquals(1, result.missing)
        assertEquals(a.key(), result.index[-1L])
        assertEquals(stranger.key(), result.index[1L])
    }

    @Test
    fun `un morceau introuvable revient quand son fichier réapparaît`() {
        val gone = TrackIdentity.reconcile(index, listOf(b, c), referenced = setOf(1, 2))
        assertEquals(mapOf(1L to -1L), gone.remap)

        val back = TrackIdentity.reconcile(gone.index, listOf(a.renumbered(50), b, c), referenced = setOf(-1, 2))

        assertEquals(mapOf(-1L to 50L), back.remap)
        assertEquals(0, back.missing)
        assertTrue(-1L !in back.index)
    }

    @Test
    fun `un morceau disparu et plus référencé est oublié`() {
        val result = TrackIdentity.reconcile(index, listOf(b, c), referenced = setOf(2))

        assertTrue(result.remap.isEmpty())
        assertEquals(setOf(2L, 3L), result.index.keys)
    }

    @Test
    fun `deux anciens morceaux ne prennent jamais le même fichier`() {
        // Deux copies du même morceau, une seule reste.
        val twin = a.copy(id = 4, relativePath = "Copie/Alpha.mp3", sizeBytes = 99)
        val previous = (library + twin).associate { it.id to it.key() }

        val result = TrackIdentity.reconcile(previous, listOf(a.renumbered(60), b, c), referenced = setOf(1, 4))

        assertEquals(60L, result.remap[1L])
        assertEquals(-4L, result.remap[4L])
    }
}
