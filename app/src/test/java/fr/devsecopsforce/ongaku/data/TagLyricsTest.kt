package fr.devsecopsforce.ongaku.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagLyricsTest {

    private fun id3Frame(id: String, body: ByteArray, major: Int): ByteArray {
        val size = if (major >= 4) synchsafe(body.size) else body.size
        return id.toByteArray(Charsets.ISO_8859_1) + ByteBuffer.allocate(4).putInt(size).array() + byteArrayOf(0, 0) + body
    }

    private fun synchsafe(size: Int) = (size and 0x7F) or ((size shr 7 and 0x7F) shl 8) or
        ((size shr 14 and 0x7F) shl 16) or ((size shr 21 and 0x7F) shl 24)

    private fun id3(major: Int, vararg frames: ByteArray): ByteArray {
        val body = frames.fold(ByteArray(0)) { all, frame -> all + frame } + ByteArray(32) // remplissage
        return "ID3".toByteArray() + byteArrayOf(major.toByte(), 0, 0) +
            ByteBuffer.allocate(4).putInt(synchsafe(body.size)).array() + body + byteArrayOf(1, 2, 3)
    }

    private fun uslt(encoding: Int, description: ByteArray, text: ByteArray) =
        byteArrayOf(encoding.toByte()) + "fra".toByteArray() + description + text

    @Test
    fun `paroles ID3 en UTF-8, après une pochette`() {
        val picture = id3Frame("APIC", ByteArray(5_000) { 7 }, 3)
        val lyrics = id3Frame("USLT", uslt(3, "desc".toByteArray() + 0, "Première ligne\nDeuxième".toByteArray()), 3)
        val file = id3(3, id3Frame("TIT2", byteArrayOf(3) + "Titre".toByteArray(), 3), picture, lyrics)

        assertEquals("Première ligne\nDeuxième", TagLyrics.read(ByteArrayInputStream(file)))
    }

    @Test
    fun `paroles ID3 en UTF-16 et tailles « synchsafe » de la v2_4`() {
        val text = "[00:01.00]Été".toByteArray(Charsets.UTF_16)
        val lyrics = id3Frame("USLT", uslt(1, "".toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0), text), 4)
        val file = id3(4, id3Frame("APIC", ByteArray(300) { 1 }, 4), lyrics)

        assertEquals("[00:01.00]Été", TagLyrics.read(ByteArrayInputStream(file)))
    }

    @Test
    fun `sans cadre de paroles, rien`() {
        val file = id3(3, id3Frame("TIT2", byteArrayOf(3) + "Titre".toByteArray(), 3))
        assertNull(TagLyrics.read(ByteArrayInputStream(file)))
    }

    private fun vorbis(vararg entries: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun int(value: Int) = out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        val vendor = "test".toByteArray()
        int(vendor.size); out.write(vendor)
        int(entries.size)
        entries.forEach { val bytes = it.toByteArray(Charsets.UTF_8); int(bytes.size); out.write(bytes) }
        return out.toByteArray()
    }

    private fun flacBlock(type: Int, last: Boolean, body: ByteArray) =
        byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (body.size shr 16).toByte(), (body.size shr 8).toByte(), body.size.toByte()) + body

    @Test
    fun `paroles d'un FLAC, après la pochette`() {
        val file = "fLaC".toByteArray() +
            flacBlock(0, false, ByteArray(34)) +
            flacBlock(6, false, ByteArray(8_000) { 9 }) +
            flacBlock(4, true, vorbis("TITLE=Nuit", "lyrics=[00:02.00]Bonsoir")) +
            ByteArray(100)

        assertEquals("[00:02.00]Bonsoir", TagLyrics.read(ByteArrayInputStream(file)))
    }

    @Test
    fun `LYRICS passe devant UNSYNCEDLYRICS`() {
        assertEquals("A", TagLyrics.vorbisLyrics(vorbis("UNSYNCEDLYRICS=B", "LYRICS=A")))
        assertEquals("B", TagLyrics.vorbisLyrics(vorbis("UNSYNCEDLYRICS=B")))
        assertNull(TagLyrics.vorbisLyrics(vorbis("TITLE=X")))
    }

    @Test
    fun `fichier tronqué ou inconnu, rien et sans erreur`() {
        assertNull(TagLyrics.read(ByteArrayInputStream("ID3".toByteArray() + byteArrayOf(3, 0, 0, 0, 0, 10, 0))))
        assertNull(TagLyrics.read(ByteArrayInputStream("OggS-pas-gere".toByteArray())))
        assertNull(TagLyrics.read(ByteArrayInputStream(ByteArray(0))))
    }
}
