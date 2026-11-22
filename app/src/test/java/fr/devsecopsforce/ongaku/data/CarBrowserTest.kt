package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarBrowserTest {

    private val labels = CarBrowser.Labels("Favoris", "Listes", "Albums", "Artistes", "Ajouts récents")
    private val tracks = listOf(
        track(1, title = "One", artist = "Daft Punk", album = "Discovery", albumId = 1, trackNumber = 1, dateAddedSeconds = 10),
        track(2, title = "Two", artist = "Daft Punk", album = "Discovery", albumId = 1, trackNumber = 2, dateAddedSeconds = 30),
        track(3, title = "Punk Song", artist = "Other", album = "R&B/Soul #1", albumId = 2, dateAddedSeconds = 20),
    )
    private val source = CarBrowser.Source(
        tracks,
        AppData(favorites = setOf(3, 1), playlists = listOf(Playlist("p1", "Route", listOf(3, 1, 99)))),
        mergeFeaturing = true,
    )

    @Test
    fun `la racine propose cinq dossiers`() {
        val root = CarBrowser.children(CarBrowser.ROOT, source, labels)!!
        assertEquals(listOf("Favoris", "Listes", "Albums", "Artistes", "Ajouts récents"), root.map { it.title })
        assertTrue(root.all { it.browsable })
    }

    @Test
    fun `listes, albums et artistes s'ouvrent sur leurs morceaux`() {
        val playlists = CarBrowser.children(CarBrowser.PLAYLISTS, source, labels)!!
        assertEquals(listOf("playlist/p1"), playlists.map { it.id })
        // Morceau disparu (99) ignoré, ordre de la liste gardé.
        assertEquals(listOf(3L, 1L), CarBrowser.children("playlist/p1", source, labels)!!.map { it.track?.id })

        val album = CarBrowser.children(CarBrowser.ALBUMS, source, labels)!!.first { it.title == "Discovery" }
        assertEquals(listOf(1L, 2L), CarBrowser.children(album.id, source, labels)!!.map { it.track?.id })

        val artist = CarBrowser.children(CarBrowser.ARTISTS, source, labels)!!.first { it.title == "Daft Punk" }
        assertEquals(2, CarBrowser.children(artist.id, source, labels)!!.size)
    }

    @Test
    fun `favoris et ajouts récents`() {
        assertEquals(listOf(1L, 3L), CarBrowser.tracksOf(CarBrowser.FAVORITES, source)!!.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), CarBrowser.tracksOf(CarBrowser.RECENT, source)!!.map { it.id })
    }

    @Test
    fun `un morceau touché lance tout son dossier à partir de lui, même si la clé contient des signes`() {
        val album = CarBrowser.children(CarBrowser.ALBUMS, source, labels)!!.first { it.title == "R&B/Soul #1" }
        val song = CarBrowser.children(album.id, source, labels)!!.single()
        assertEquals(album.id to 3L, CarBrowser.parseTrackId(song.id))
        assertEquals(listOf(3L) to 0, CarBrowser.playFrom(song.id, source)?.let { (list, i) -> list.map { it.id } to i })

        val second = CarBrowser.playFrom("${CarBrowser.FAVORITES}#3", source)!!
        assertEquals(1, second.second)
    }

    @Test
    fun `identifiants inconnus ou périmés`() {
        assertNull(CarBrowser.children("inconnu", source, labels))
        assertNull(CarBrowser.parseTrackId(CarBrowser.ALBUMS))
        assertNull(CarBrowser.playFrom("playlist/disparue#1", source))
        assertNull(CarBrowser.playFrom("${CarBrowser.FAVORITES}#2", source))
    }

    @Test
    fun `recherche vocale, l'artiste nommé exactement passe devant`() {
        assertEquals(listOf(1L, 2L), CarBrowser.search("daft punk", source).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), CarBrowser.search("PUNK", source).map { it.id }.sorted())
        assertEquals(emptyList<Track>(), CarBrowser.search("  ", source))
    }
}
