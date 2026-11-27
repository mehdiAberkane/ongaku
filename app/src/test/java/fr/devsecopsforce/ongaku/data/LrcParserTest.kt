package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `lignes horodatées, triées, balises ignorées`() {
        val lyrics = LrcParser.parse(
            """
            [ti:Titre]
            [ar:Artiste]
            [00:12.50]Deuxième
            [00:05.00]Première
            [01:02]Troisième
            """.trimIndent(),
        )!!
        assertTrue(lyrics.synced)
        assertEquals(listOf(5_000L, 12_500L, 62_000L), lyrics.lines.map { it.timeMs })
        assertEquals(listOf("Première", "Deuxième", "Troisième"), lyrics.lines.map { it.text })
    }

    @Test
    fun `plusieurs horodatages sur une ligne, refrain répété`() {
        val lyrics = LrcParser.parse("[00:10.00][00:30.00]Refrain\n[00:20.00]Couplet")!!
        assertEquals(listOf("Refrain", "Couplet", "Refrain"), lyrics.lines.map { it.text })
        assertEquals(listOf(10_000L, 20_000L, 30_000L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `fractions de seconde et décalage`() {
        val lyrics = LrcParser.parse("[offset:+500]\n[00:01.5]A\n[00:02.45]B\n[00:03.456]C")!!
        assertEquals(listOf(1_000L, 1_950L, 2_956L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `un texte sans horodatage donne des paroles simples`() {
        val lyrics = LrcParser.parse("Première ligne\r\n\r\nDeuxième ligne")!!
        assertFalse(lyrics.synced)
        assertEquals(listOf("Première ligne", "Deuxième ligne"), lyrics.lines.map { it.text })
        assertEquals(-1, lyrics.currentLine(10_000))
    }

    @Test
    fun `rien à afficher`() {
        assertNull(LrcParser.parse(""))
        assertNull(LrcParser.parse("[ar:Artiste]\n[ti:Titre]"))
    }

    @Test
    fun `ligne en cours selon la position`() {
        val lyrics = LrcParser.parse("[00:05.00]A\n[00:10.00]B\n[00:20.00]C")!!
        assertEquals(-1, lyrics.currentLine(4_999))
        assertEquals(0, lyrics.currentLine(5_000))
        assertEquals(1, lyrics.currentLine(19_999))
        assertEquals(2, lyrics.currentLine(600_000))
    }
}
