package fr.devsecopsforce.ongaku.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoplayRulesTest {

    private val own = "fr.devsecopsforce.ongaku"

    @Test
    fun `une lecture arrêtée par l'autoradio qui démarre est relancée`() {
        assertTrue(AutoplayRules.shouldResume(isPlaying = false, stoppedByUser = false, resumesDone = 0, routedToDevice = true))
    }

    @Test
    fun `rien n'est relancé si la musique joue déjà`() {
        assertFalse(AutoplayRules.shouldResume(isPlaying = true, stoppedByUser = false, resumesDone = 0, routedToDevice = true))
    }

    @Test
    fun `une pause demandée dans Ongaku n'est jamais contredite`() {
        assertFalse(AutoplayRules.shouldResume(isPlaying = false, stoppedByUser = true, resumesDone = 0, routedToDevice = true))
    }

    @Test
    fun `sans la voiture comme sortie audio, rien n'est relancé`() {
        // Sinon la musique repartirait sur le haut-parleur du téléphone.
        assertFalse(AutoplayRules.shouldResume(isPlaying = false, stoppedByUser = false, resumesDone = 0, routedToDevice = false))
    }

    @Test
    fun `on n'insiste pas au-delà de trois relances`() {
        assertTrue(AutoplayRules.shouldResume(false, false, resumesDone = AutoplayRules.MAX_RESUMES - 1, routedToDevice = true))
        assertFalse(AutoplayRules.shouldResume(false, false, resumesDone = AutoplayRules.MAX_RESUMES, routedToDevice = true))
    }

    @Test
    fun `Ongaku et la notification du système rendent la main à l'utilisateur`() {
        assertTrue(AutoplayRules.userTookOver(own, own))
        assertTrue(AutoplayRules.userTookOver("com.android.systemui", own))
    }

    @Test
    fun `une commande de la voiture ne coupe pas la surveillance`() {
        assertFalse(AutoplayRules.userTookOver("com.android.bluetooth", own))
        assertFalse(AutoplayRules.userTookOver("android.media.session.MediaController", own))
    }

    @Test
    fun `les relevés couvrent le démarrage d'un autoradio lent`() {
        assertEquals(AutoplayRules.CHECKS_MS.sorted(), AutoplayRules.CHECKS_MS)
        assertTrue(AutoplayRules.CHECKS_MS.last() >= 30_000L)
    }

    @Test
    fun `le journal garde les dernières étapes, les plus anciennes partent`() {
        var stored = ""
        repeat(AutoplayJournal.MAX_ENTRIES + 5) { stored = AutoplayJournal.append(stored, "étape $it") }
        val lines = stored.split("\n")
        assertEquals(AutoplayJournal.MAX_ENTRIES, lines.size)
        assertEquals("étape 5", lines.first())
        assertEquals("étape ${AutoplayJournal.MAX_ENTRIES + 4}", lines.last())
    }
}
