package fr.devsecopsforce.ongaku.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FullBackupTest {

    private val context = RuntimeEnvironment.getApplication()
    private val song = track(1, title = "Song", artist = "A", album = "X")
    private val other = track(2, title = "Other", artist = "B", album = "Y")
    private val library = listOf(song, other)

    private fun tempFile(bytes: ByteArray) = File.createTempFile("img", ".img").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun `l'archive garde la description et les images`() {
        val out = ByteArrayOutputStream()
        BackupArchive.write(out, """{"format":"ongaku-backup"}""", mapOf("images/a.img" to tempFile(byteArrayOf(1, 2, 3))))

        val contents = BackupArchive.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals("""{"format":"ongaku-backup"}""", contents.json)
        assertArrayEquals(byteArrayOf(1, 2, 3), contents.images["images/a.img"])
    }

    @Test
    fun `une ancienne sauvegarde JSON se lit encore`() {
        val contents = BackupArchive.read(ByteArrayInputStream("""{"format":"ongaku-backup"}""".toByteArray()))
        assertEquals("""{"format":"ongaku-backup"}""", contents.json)
        assertTrue(contents.images.isEmpty())
    }

    @Test
    fun `un chemin piégé dans l'archive est ignoré`() {
        assertTrue(BackupArchive.isSafeImagePath("images/0f3a-b1.img"))
        assertFalse(BackupArchive.isSafeImagePath("images/../ongaku.json"))
        assertFalse(BackupArchive.isSafeImagePath("images/sub/x.img"))
        assertFalse(BackupArchive.isSafeImagePath("/data/x.img"))
        assertFalse(BackupArchive.isSafeImagePath("images/"))

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("ongaku.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("images/../../evil")); zip.write(byteArrayOf(9)); zip.closeEntry()
        }
        assertTrue(BackupArchive.read(ByteArrayInputStream(out.toByteArray())).images.isEmpty())
    }

    @Test
    fun `une image trop lourde fait refuser l'archive`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("ongaku.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("images/big.img")); zip.write(ByteArray((BackupArchive.MAX_IMAGE_BYTES + 1).toInt())); zip.closeEntry()
        }
        try {
            BackupArchive.read(ByteArrayInputStream(out.toByteArray()))
            fail("archive acceptée")
        } catch (e: BackupArchive.TooLargeException) {
            // attendu
        }
    }

    @Test
    fun `les préférences gardent leur type`() {
        val values = mapOf("b" to true, "i" to 7, "l" to 1_795_000_000_000L, "f" to 0.72f, "s" to "DARK", "set" to setOf("mp3", "ogg"))
        assertEquals(values, PrefsCodec.decode(PrefsCodec.encode(values)))
    }

    @Test
    fun `réglages, écoutes et images passent par la sauvegarde, retrouvés par leurs tags`() {
        val extras = PlaylistBackup.Extras(
            playCounts = mapOf(1L to 5),
            lastPlayed = mapOf(1L to 9_000L),
            settings = mapOf("ongaku_settings" to mapOf("crossfade_seconds" to 4), "inconnu" to mapOf("x" to 1)),
            appImage = "images/app.img",
            trackImages = mapOf(2L to "images/t2.img"),
        )
        val text = PlaylistBackup.serialize(AppData(), library, extras = extras)
        // Sur un autre téléphone, les morceaux ont d'autres identifiants.
        val elsewhere = listOf(song.copy(id = 10), other.copy(id = 20))
        val imported = PlaylistBackup.parse(text, elsewhere)

        assertEquals(mapOf(10L to (5 to 9_000L)), imported.playCounts)
        assertEquals(mapOf("ongaku_settings" to mapOf("crossfade_seconds" to 4)), imported.settings)
        assertEquals("images/app.img", imported.appImage)
        assertNull(imported.playerImage)
        assertEquals(mapOf(20L to "images/t2.img"), imported.trackImages)
    }

    @Test
    fun `une image importée ne remplace jamais une image déjà choisie`() {
        val imported = PlaylistBackup.Imported(
            favorites = emptySet(), playlists = emptyList(), missingTracks = 0,
            appImage = "images/app.img", playerImage = "images/player.img",
            trackImages = mapOf(1L to "images/t1.img", 2L to "images/t2.img"),
        )
        val local = AppData(appBackground = "mine.img", trackBackgrounds = mapOf(1L to "mine1.img"))

        assertEquals(setOf("images/player.img", "images/t2.img"), PlaylistBackup.neededImages(local, imported))
        val saved = mapOf("images/player.img" to "p.img", "images/t2.img" to "t2.img")
        val merged = PlaylistBackup.mergeImages(local, imported, saved)
        assertEquals("mine.img", merged.appBackground)
        assertEquals("p.img", merged.playerBackground)
        assertEquals(mapOf(1L to "mine1.img", 2L to "t2.img"), merged.trackBackgrounds)
    }

    @Test
    fun `réimporter ses écoutes ne les double pas`() {
        val counts = PlayCounts(context)
        counts.increment(1, nowMs = 1_000)
        counts.increment(1, nowMs = 2_000)

        counts.mergeMax(mapOf(1L to (2 to 2_000L), 3L to (4 to 500L)))
        assertEquals(mapOf(1L to 2, 3L to 4), counts.all())
        counts.mergeMax(mapOf(1L to (6 to 1_000L)))
        assertEquals(6, counts.all()[1L])
        assertEquals(2_000L, counts.lastPlayed()[1L])
    }

    @Test
    fun `un import sans écoutes nouvelles n'écrit aucun fichier`() {
        val counts = PlayCounts(context)
        counts.mergeMax(emptyMap())
        assertTrue(counts.prefs.all.isEmpty())
        assertTrue(counts.lastPlayedPrefs.all.isEmpty())

        ListeningHistory.resetForTests()
        val history = ListeningHistory.get(context)
        history.merge(emptyList(), emptyMap())
        assertFalse(File(context.filesDir, "history.json").exists())
    }}
