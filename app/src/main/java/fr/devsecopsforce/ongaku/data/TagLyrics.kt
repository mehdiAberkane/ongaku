package fr.devsecopsforce.ongaku.data

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Paroles incluses dans les tags d'un fichier audio, lues en flux depuis son début : ID3v2 (cadre
 * USLT, MP3) et commentaires Vorbis d'un FLAC (LYRICS ou UNSYNCEDLYRICS). Les pochettes et autres
 * blocs sont sautés sans être chargés. Le fichier n'est jamais modifié.
 */
object TagLyrics {
    /** Au-delà, des paroles n'ont plus de sens : le cadre est ignoré. */
    private const val MAX_TEXT_BYTES = 512 * 1024

    fun read(input: InputStream): String? = try {
        val data = DataInputStream(input)
        val magic = ByteArray(4)
        data.readFully(magic, 0, 3)
        when {
            magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte() -> id3(data)
            magic[0] == 'f'.code.toByte() && magic[1] == 'L'.code.toByte() && magic[2] == 'a'.code.toByte() -> {
                magic[3] = data.readByte()
                if (magic[3] == 'C'.code.toByte()) flac(data) else null
            }
            else -> null
        }
    } catch (e: IOException) {
        null
    }

    private fun id3(data: DataInputStream): String? {
        val major = data.readUnsignedByte()
        data.readUnsignedByte() // révision
        val flags = data.readUnsignedByte()
        val tagSize = synchsafe(data.readInt())
        var read = 0
        if (flags and 0x40 != 0) {
            // En-tête étendu : sa taille est comptée différemment en v2.3 et v2.4.
            val size = data.readInt()
            val skip = if (major >= 4) synchsafe(size) - 4 else size
            data.skipFully(skip)
            read += 4 + skip
        }
        while (read + 10 <= tagSize) {
            val id = ByteArray(4).also { data.readFully(it) }
            if (id[0] == 0.toByte()) return null // remplissage : plus de cadres
            val rawSize = data.readInt()
            val size = if (major >= 4) synchsafe(rawSize) else rawSize
            data.readUnsignedShort() // drapeaux du cadre
            read += 10
            if (size < 0 || read + size > tagSize) return null
            if (String(id, Charsets.ISO_8859_1) == "USLT" && size in 5..MAX_TEXT_BYTES) {
                val frame = ByteArray(size).also { data.readFully(it) }
                return uslt(frame)?.takeIf { it.isNotBlank() }
            }
            data.skipFully(size)
            read += size
        }
        return null
    }

    /** USLT : encodage, langue (3 octets), description terminée par un zéro, puis le texte. */
    internal fun uslt(frame: ByteArray): String? {
        val encoding = frame[0].toInt()
        val wide = encoding == 1 || encoding == 2
        var index = 4
        // Fin de la description : un zéro (ou deux, alignés, en UTF-16).
        while (index < frame.size) {
            if (!wide && frame[index] == 0.toByte()) {
                index += 1
                break
            }
            if (wide && index + 1 < frame.size && frame[index] == 0.toByte() && frame[index + 1] == 0.toByte()) {
                index += 2
                break
            }
            index += if (wide) 2 else 1
        }
        if (index > frame.size) return null
        val text = frame.copyOfRange(index, frame.size)
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(text, charset).trimEnd(' ')
    }

    private fun flac(data: DataInputStream): String? {
        while (true) {
            val header = data.readUnsignedByte()
            val last = header and 0x80 != 0
            val type = header and 0x7F
            val size = (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
            if (type == 4 && size <= MAX_TEXT_BYTES * 4) {
                val block = ByteArray(size).also { data.readFully(it) }
                vorbisLyrics(block)?.let { return it }
            } else {
                data.skipFully(size)
            }
            if (last) return null
        }
    }

    /** Commentaires Vorbis (petit-boutiste) : LYRICS, à défaut UNSYNCEDLYRICS. */
    internal fun vorbisLyrics(block: ByteArray): String? {
        var position = 0
        fun int(): Int {
            if (position + 4 > block.size) throw EOFException()
            val value = (block[position].toInt() and 0xFF) or ((block[position + 1].toInt() and 0xFF) shl 8) or
                ((block[position + 2].toInt() and 0xFF) shl 16) or ((block[position + 3].toInt() and 0xFF) shl 24)
            position += 4
            return value
        }
        return try {
            // Lu d'abord : « position += int() » prendrait la position d'avant la lecture.
            val vendor = int()
            position += vendor
            val count = int()
            val found = HashMap<String, String>()
            repeat(count) {
                val length = int()
                if (length < 0 || position + length > block.size) throw EOFException()
                val entry = String(block, position, length, Charsets.UTF_8)
                position += length
                val key = entry.substringBefore('=').uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS") found.putIfAbsent(key, entry.substringAfter('='))
            }
            (found["LYRICS"] ?: found["UNSYNCEDLYRICS"])?.takeIf { it.isNotBlank() }
        } catch (e: EOFException) {
            null
        }
    }

    private fun synchsafe(value: Int): Int =
        (value and 0x7F) or ((value shr 8 and 0x7F) shl 7) or ((value shr 16 and 0x7F) shl 14) or ((value shr 24 and 0x7F) shl 21)

    private fun DataInputStream.skipFully(count: Int) {
        var left = count.toLong()
        while (left > 0) {
            val skipped = skip(left)
            if (skipped <= 0) {
                if (read() < 0) throw EOFException()
                left--
            } else {
                left -= skipped
            }
        }
    }
}
