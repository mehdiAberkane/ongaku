package fr.devsecopsforce.ongaku.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerRulesTest {

    private val minute = 60_000L

    @Test
    fun `minuteur à durée, le temps restant suit l'horloge, pas le morceau`() {
        assertEquals(
            10 * minute,
            SleepTimerRules.remainingMs(false, nowMs = 5 * minute, deadlineMs = 15 * minute, 0, null, 1f),
        )
        assertEquals(0L, SleepTimerRules.remainingMs(false, nowMs = 20 * minute, deadlineMs = 15 * minute, 0, null, 1f))
    }

    @Test
    fun `fin du morceau, le temps restant est ce qui reste à lire, à la vitesse de lecture`() {
        assertEquals(30_000L, SleepTimerRules.remainingMs(true, 0, 0, positionMs = 150_000, durationMs = 180_000, 1f))
        assertEquals(15_000L, SleepTimerRules.remainingMs(true, 0, 0, positionMs = 150_000, durationMs = 180_000, 2f))
        assertNull(SleepTimerRules.remainingMs(true, 0, 0, positionMs = 0, durationMs = null, 1f))
    }

    @Test
    fun `le volume ne baisse que sur la dernière minute, et seulement si l'option est active`() {
        assertEquals(1f, SleepTimerRules.volume(2 * minute, fadeEnabled = true))
        assertEquals(1f, SleepTimerRules.volume(minute, fadeEnabled = true))
        assertEquals(0.5f, SleepTimerRules.volume(30_000, fadeEnabled = true))
        assertEquals(0f, SleepTimerRules.volume(0, fadeEnabled = true))
        assertEquals(1f, SleepTimerRules.volume(30_000, fadeEnabled = false))
        assertEquals(1f, SleepTimerRules.volume(null, fadeEnabled = true))
    }

    @Test
    fun `un seul réveil avant la dernière minute, puis des pas fins`() {
        assertEquals(9 * minute, SleepTimerRules.nextWakeUpMs(10 * minute, true, isPlaying = true, atTrackEnd = false))
        assertEquals(SleepTimerRules.FADE_STEP_MS, SleepTimerRules.nextWakeUpMs(30_000, true, true, false))
        assertEquals(100L, SleepTimerRules.nextWakeUpMs(100, true, true, false))
        // Sans baisse de volume : réveil à l'échéance seulement.
        assertEquals(10 * minute, SleepTimerRules.nextWakeUpMs(10 * minute, false, true, false))
    }

    @Test
    fun `en pause, la fin d'un morceau n'avance pas, une échéance si`() {
        assertNull(SleepTimerRules.nextWakeUpMs(30_000, true, isPlaying = false, atTrackEnd = true))
        assertEquals(9 * minute, SleepTimerRules.nextWakeUpMs(10 * minute, true, isPlaying = false, atTrackEnd = false))
        assertNull(SleepTimerRules.nextWakeUpMs(null, true, isPlaying = true, atTrackEnd = true))
    }

    @Test
    fun `seule l'échéance d'un minuteur à durée arrête la lecture`() {
        assertTrue(SleepTimerRules.expired(atTrackEnd = false, remainingMs = 0))
        assertFalse(SleepTimerRules.expired(atTrackEnd = false, remainingMs = 1))
        // La fin du morceau est confiée au lecteur, qui s'arrête de lui-même.
        assertFalse(SleepTimerRules.expired(atTrackEnd = true, remainingMs = 0))
    }

    @Test
    fun `temps restant affiché arrondi à la seconde supérieure`() {
        assertEquals("30:00", SleepTimerRules.format(30 * minute))
        assertEquals("29:42", SleepTimerRules.format(29 * minute + 41_001))
        assertEquals("0:01", SleepTimerRules.format(1))
        assertEquals("1:05:00", SleepTimerRules.format(65 * minute))
    }

    @Test
    fun `les choix proposés vont de 15 à 60 minutes`() {
        assertEquals(listOf(15, 30, 45, 60), SleepTimerRules.PRESET_MINUTES)
    }
}
