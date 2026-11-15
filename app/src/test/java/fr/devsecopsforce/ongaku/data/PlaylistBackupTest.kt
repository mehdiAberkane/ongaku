package fr.devsecopsforce.ongaku.data

import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistBackupTest {

    private val library = listOf(
        track(1, title = "Intro", artist = "Alpha", album = "Premier"),
        track(2, title = "Outro", artist = "Alpha", album = "Premier"),
        track(3, title = "Solo", artist = "Beta", album = "Second"),
    )

    @Test
    fun `une sauvegarde relue sur la même bibliothèque redonne les mêmes morceaux`() {
        val data = AppData(
            favorites = setOf(1, 3),
            playlists = listOf(Playlist("p1", "Route", listOf(3, 1))),
        )

        val imported = PlaylistBackup.parse(PlaylistBackup.serialize(data, library), library)

        assertEquals(setOf(1L, 3L), imported.favorites)
        assertEquals(listOf("Route" to listOf(3L, 1L)), imported.playlists)
        assertEquals(0, imported.missingTracks)
    }

    @Test
    fun `un morceau absent de la bibliothèque n'est pas écrit dans la sauvegarde`() {
        val data = AppData(favorites = setOf(1, 99))

        val json = JSONObject(PlaylistBackup.serialize(data, library))

        assertEquals(1, json.getJSONArray("favorites").length())
    }

    @Test
    fun `les morceaux sont retrouvés par leurs tags, sans tenir compte de la casse`() {
        // Les identifiants Android du nouveau téléphone sont différents.
        val newPhone = listOf(track(42, title = "SOLO ", artist = "beta", album = "second"))

        val imported = PlaylistBackup.parse(PlaylistBackup.serialize(AppData(favorites = setOf(3)), library), newPhone)

        assertEquals(setOf(42L), imported.favorites)
    }

    @Test
    fun `un album renommé n'empêche pas de retrouver le titre et l'artiste`() {
        val newPhone = listOf(track(42, title = "Solo", artist = "Beta", album = "Second (Remaster)"))

        val imported = PlaylistBackup.parse(PlaylistBackup.serialize(AppData(favorites = setOf(3)), library), newPhone)

        assertEquals(setOf(42L), imported.favorites)
    }

    @Test
    fun `les morceaux introuvables sont comptés`() {
        val data = AppData(favorites = setOf(1, 2), playlists = listOf(Playlist("p", "L", listOf(3))))

        val imported = PlaylistBackup.parse(PlaylistBackup.serialize(data, library), library.take(1))

        assertEquals(setOf(1L), imported.favorites)
        assertEquals(listOf("L" to emptyList<Long>()), imported.playlists)
        assertEquals(2, imported.missingTracks)
    }

    @Test(expected = JSONException::class)
    fun `un fichier qui n'est pas une sauvegarde Ongaku est refusé`() {
        PlaylistBackup.parse("""{"format":"autre","favorites":[],"playlists":[]}""", library)
    }

    @Test
    fun `la fusion complète une liste du même nom et crée les autres sans rien effacer`() {
        val existing = AppData(
            favorites = setOf(1),
            playlists = listOf(Playlist("p1", "Route", listOf(1, 2)), Playlist("p2", "Sport", listOf(3))),
        )
        val imported = PlaylistBackup.Imported(
            favorites = setOf(3),
            playlists = listOf(" route " to listOf(2, 3), "Soirée " to listOf(1)),
            missingTracks = 0,
        )

        val merged = PlaylistBackup.merge(existing, imported) { "nouvelle" }

        assertEquals(setOf(1L, 3L), merged.favorites)
        assertEquals(
            listOf(
                Playlist("p1", "Route", listOf(1, 2, 3)),
                Playlist("p2", "Sport", listOf(3)),
                Playlist("nouvelle", "Soirée", listOf(1)),
            ),
            merged.playlists,
        )
    }
}
