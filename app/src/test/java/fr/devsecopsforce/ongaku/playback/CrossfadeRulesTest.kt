package fr.devsecopsforce.ongaku.playback

import fr.devsecopsforce.ongaku.playback.CrossfadeRules.Fade
import fr.devsecopsforce.ongaku.playback.CrossfadeRules.Incoming
import fr.devsecopsforce.ongaku.playback.CrossfadeRules.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les deux lecteurs du fondu enchaîné sont décrits par leur état : position et durée du lecteur
 * actif, état du lecteur de relève.
 */
class CrossfadeRulesTest {

    private val fade = 6_000L
    private val duration = 200_000L

    private fun watch(
        remainingMs: Long,
        incoming: Incoming = Incoming.NONE,
        fadeMs: Long = fade,
        durationMs: Long? = duration,
        isPlaying: Boolean = true,
        hasNext: Boolean = true,
        repeatOne: Boolean = false,
    ) = CrossfadeRules.watch(
        fadeMs = fadeMs,
        durationMs = durationMs,
        positionMs = (durationMs ?: duration) - remainingMs,
        isPlaying = isPlaying,
        hasNext = hasNext,
        repeatOne = repeatOne,
        incoming = incoming,
    )

    @Test
    fun `pas de fondu quand il est désactivé ou impossible`() {
        assertEquals(Watch.CANCEL, watch(5_000, fadeMs = 0))
        assertEquals(Watch.CANCEL, watch(5_000, durationMs = null))
        assertEquals(Watch.CANCEL, watch(5_000, isPlaying = false))
        assertEquals(Watch.CANCEL, watch(5_000, hasNext = false))
        assertEquals(Watch.CANCEL, watch(5_000, repeatOne = true))
    }

    @Test
    fun `pas de fondu sur un morceau plus court que deux fondus`() {
        assertEquals(Watch.CANCEL, watch(5_000, durationMs = 11_999))
        assertEquals(Watch.PREPARE, watch(5_000, durationMs = 12_000))
    }

    @Test
    fun `la relève se prépare trois secondes avant le début du fondu`() {
        assertEquals(Watch.NOTHING, watch(fade + 3_001))
        assertEquals(Watch.PREPARE, watch(fade + 3_000))
        assertEquals(Watch.PREPARE, watch(fade))
    }

    @Test
    fun `trop tard pour préparer quand il reste moins d'un demi-fondu`() {
        assertEquals(Watch.PREPARE, watch(fade / 2 + 1))
        assertEquals(Watch.NOTHING, watch(fade / 2))
    }

    @Test
    fun `le fondu attend que la relève soit prête`() {
        assertEquals(Watch.NOTHING, watch(fade, Incoming.PREPARING))
        assertEquals(Watch.START, watch(fade, Incoming.READY))
        assertEquals(Watch.NOTHING, watch(fade + 1, Incoming.READY))
    }

    @Test
    fun `un retour en arrière abandonne la relève`() {
        assertEquals(Watch.CANCEL, watch(fade + 4_001, Incoming.READY))
        assertEquals(Watch.NOTHING, watch(fade + 4_000, Incoming.READY))
    }

    @Test
    fun `les volumes se croisent à mi-chemin et totalisent toujours 1`() {
        val start = CrossfadeRules.fade(remainingMs = fade, fadeDurationMs = fade, elapsedMs = 0) as Fade.Volumes
        assertEquals(0f, start.incoming, 0.001f)
        assertEquals(1f, start.outgoing, 0.001f)

        val middle = CrossfadeRules.fade(remainingMs = fade / 2, fadeDurationMs = fade, elapsedMs = 3_000) as Fade.Volumes
        assertEquals(0.5f, middle.incoming, 0.001f)

        var previous = -1f
        for (remaining in fade downTo 1 step 250) {
            val step = CrossfadeRules.fade(remaining, fade, fade - remaining) as Fade.Volumes
            assertEquals(1f, step.incoming + step.outgoing, 0.0001f)
            assertTrue("le volume de la relève ne redescend jamais", step.incoming >= previous)
            previous = step.incoming
        }
    }

    @Test
    fun `le fondu se termine à la fin du morceau ou si le lecteur actif n'avance plus`() {
        assertEquals(Fade.Finish, CrossfadeRules.fade(remainingMs = 0, fadeDurationMs = fade, elapsedMs = fade))
        assertEquals(Fade.Finish, CrossfadeRules.fade(remainingMs = 2_000, fadeDurationMs = fade, elapsedMs = fade + 3_001))
        assertTrue(CrossfadeRules.fade(remainingMs = 2_000, fadeDurationMs = fade, elapsedMs = fade + 3_000) is Fade.Volumes)
    }

    @Test
    fun `un retour en arrière pendant le fondu l'annule`() {
        assertEquals(Fade.Cancel, CrossfadeRules.fade(remainingMs = fade + 1_001, fadeDurationMs = fade, elapsedMs = 500))
    }

    @Test
    fun `une écoute compte après 30 secondes, ou la moitié d'un morceau court`() {
        assertFalse(CrossfadeRules.shouldCountPlay(listenedMs = 29_999, durationMs = 200_000))
        assertTrue(CrossfadeRules.shouldCountPlay(listenedMs = 30_000, durationMs = 200_000))
        assertFalse(CrossfadeRules.shouldCountPlay(listenedMs = 19_999, durationMs = 40_000))
        assertTrue(CrossfadeRules.shouldCountPlay(listenedMs = 20_000, durationMs = 40_000))
        assertTrue(CrossfadeRules.shouldCountPlay(listenedMs = 30_000, durationMs = null))
    }
}
