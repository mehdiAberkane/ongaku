package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderTreeTest {

    private val library = listOf(
        track(1, title = "b", folder = "Music"),
        track(2, title = "a", folder = "Music"),
        track(3, title = "x", folder = "Music/Anime"),
        track(4, title = "y", folder = "Music/Anime/OST"),
        track(5, title = "z", folder = "Music/rock"),
        track(6, title = "d", folder = "Download"),
    )

    @Test
    fun `la racine montre les dossiers de premier niveau avec tous leurs morceaux`() {
        val root = FolderTree.view(library, "")
        assertEquals(listOf("Download" to 1, "Music" to 5), root.subfolders.map { it.name to it.trackCount })
        assertEquals(emptyList<Track>(), root.tracks)
    }

    @Test
    fun `un dossier montre ses sous-dossiers puis ses propres fichiers, par nom`() {
        val music = FolderTree.view(library, "Music")
        assertEquals(listOf("Music/Anime", "Music/rock"), music.subfolders.map { it.path })
        assertEquals(listOf(2, 1), music.subfolders.map { it.trackCount })
        // Par nom de fichier : « a.mp3 » avant « b.mp3 ».
        assertEquals(listOf(2L, 1L), music.tracks.map { it.id })
    }

    @Test
    fun `lecture avec les sous-dossiers, dans l'ordre affiché`() {
        assertEquals(listOf(2L, 1L, 3L, 4L, 5L), FolderTree.tracksUnder(library, "Music").map { it.id })
        assertEquals(listOf(3L, 4L), FolderTree.tracksUnder(library, "Music/Anime").map { it.id })
        assertEquals(6, FolderTree.tracksUnder(library, "").size)
    }

    @Test
    fun `un dossier au nom proche n'est pas un sous-dossier`() {
        assertFalse(FolderTree.isUnder("Music2", "Music"))
        assertTrue(FolderTree.isUnder("Music/Anime", "Music"))
        assertEquals(emptyList<Track>(), FolderTree.tracksUnder(listOf(track(1, folder = "Music2")), "Music"))
    }

    @Test
    fun `les barres en trop d'un chemin de carte SD ne créent pas de dossier vide`() {
        assertEquals("storage/1234-ABCD/Music", FolderTree.normalize("/storage/1234-ABCD/Music/"))
        val root = FolderTree.view(listOf(track(1, folder = "/storage/1234-ABCD/Music")), "")
        assertEquals(listOf("storage"), root.subfolders.map { it.name })
    }

    @Test
    fun `un morceau à la racine reste à la racine`() {
        val root = FolderTree.view(listOf(track(1, folder = "")), "")
        assertEquals(listOf(1L), root.tracks.map { it.id })
    }

    @Test
    fun `parent et nom d'un chemin`() {
        assertEquals("Music", FolderTree.parent("Music/Anime"))
        assertEquals("", FolderTree.parent("Music"))
        assertNull(FolderTree.parent(""))
        assertEquals("Anime", FolderTree.name("Music/Anime"))
    }
}
