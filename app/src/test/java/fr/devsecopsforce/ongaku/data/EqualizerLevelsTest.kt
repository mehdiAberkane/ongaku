package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerLevelsTest {

    @Test
    fun `les niveaux enregistrés sont relus bande par bande`() {
        assertEquals(listOf(300, -150, 0, 450, -1500), EqualizerLevels.parse("300,-150,0,450,-1500", 5, -1500, 1500))
        assertEquals("300,-150,0", EqualizerLevels.format(listOf(300, -150, 0)))
    }

    @Test
    fun `jamais réglés, toutes les bandes sont à 0 dB`() {
        assertEquals(listOf(0, 0, 0, 0, 0), EqualizerLevels.parse("", 5, -1500, 1500))
    }

    @Test
    fun `un réglage d'un autre téléphone est ramené à ses bandes et à ses limites`() {
        // Moins de bandes ici, et des limites plus étroites.
        assertEquals(listOf(1000, -1000, 0), EqualizerLevels.parse("1500,-1500,0,600,600", 3, -1000, 1000))
        // Plus de bandes ici : les nouvelles partent de 0 dB.
        assertEquals(listOf(200, 0, 0, 0), EqualizerLevels.parse("200", 4, -1500, 1500))
    }

    @Test
    fun `une valeur abîmée compte pour 0 dB`() {
        assertEquals(listOf(0, 300, 0), EqualizerLevels.parse("abc,300,", 3, -1500, 1500))
    }

    @Test
    fun `les basses restent entre 0 et 1000`() {
        assertEquals(0, EqualizerLevels.bass(-5))
        assertEquals(1000, EqualizerLevels.bass(4000))
        assertEquals(500, EqualizerLevels.bass(500))
    }

    @Test
    fun `un préréglage absent du téléphone devient Personnalisé`() {
        assertEquals(2, EqualizerLevels.preset(2, presetCount = 10))
        assertEquals(EqualizerSettings.CUSTOM, EqualizerLevels.preset(12, presetCount = 10))
        assertEquals(EqualizerSettings.CUSTOM, EqualizerLevels.preset(EqualizerSettings.CUSTOM, presetCount = 10))
    }

    @Test
    fun `les caractères nuls des noms de préréglages sont retirés`() {
        assertEquals("Rock", EqualizerLevels.presetName("Rock\u0000\u0000\u0000"))
        assertEquals("Heavy Metal", EqualizerLevels.presetName(" Heavy Metal "))
    }

    @Test
    fun `fréquences et niveaux affichés en français`() {
        assertEquals("60 Hz", EqualizerLevels.frequencyLabel(60_000))
        assertEquals("1 kHz", EqualizerLevels.frequencyLabel(1_000_000))
        assertEquals("3,6 kHz", EqualizerLevels.frequencyLabel(3_600_000))
        assertEquals("+3 dB", EqualizerLevels.levelLabel(300))
        assertEquals("−1,5 dB", EqualizerLevels.levelLabel(-150))
        assertEquals("0 dB", EqualizerLevels.levelLabel(0))
    }
}
