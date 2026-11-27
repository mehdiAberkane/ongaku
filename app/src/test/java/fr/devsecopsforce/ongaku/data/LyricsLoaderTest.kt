package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsLoaderTest {

    @Test
    fun `le lrc est à côté du morceau, même nom`() {
        assertEquals("primary:Music/Rock/01 Aube.lrc", LyricsLoader.lrcDocumentId("primary:Music", "Music/Rock/01 Aube.flac"))
        assertEquals("primary:Music/Rock/Live/Scene.lrc", LyricsLoader.lrcDocumentId("primary:Music/Rock", "Music/Rock/Live/Scene.mp3"))
    }

    @Test
    fun `dossier racine du stockage choisi`() {
        assertEquals("primary:Download/a.b.lrc", LyricsLoader.lrcDocumentId("primary:", "Download/a.b.mp3"))
    }

    @Test
    fun `un morceau hors du dossier choisi n'a pas de lrc accessible`() {
        assertNull(LyricsLoader.lrcDocumentId("primary:Music/Jazz", "Music/Rock/01 Aube.flac"))
        // « Music2 » n'est pas dans « Music ».
        assertNull(LyricsLoader.lrcDocumentId("primary:Music", "Music2/x.mp3"))
    }

    @Test
    fun `carte SD, à condition que ce soit la même`() {
        assertEquals("1234-ABCD:Music/x.lrc", LyricsLoader.lrcDocumentId("1234-ABCD:Music", "/storage/1234-ABCD/Music/x.mp3"))
        assertNull(LyricsLoader.lrcDocumentId("1234-ABCD:Music", "/storage/9999-0000/Music/x.mp3"))
        assertNull(LyricsLoader.lrcDocumentId("1234-ABCD:Music", "Music/x.mp3"))
    }

    @Test
    fun `un fichier sans extension garde son nom`() {
        assertEquals("primary:Music/piste.lrc", LyricsLoader.lrcDocumentId("primary:Music", "Music/piste"))
    }
}
