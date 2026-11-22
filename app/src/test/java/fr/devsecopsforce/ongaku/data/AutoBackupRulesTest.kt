package fr.devsecopsforce.ongaku.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupRulesTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    @Test
    fun `due seulement si activée, avec un dossier, et l'intervalle écoulé`() {
        assertTrue(AutoBackupRules.isDue(true, true, lastRunMs = 0, nowMs = 10 * day, intervalDays = 7))
        assertTrue(AutoBackupRules.isDue(true, true, lastRunMs = day, nowMs = 8 * day, intervalDays = 7))
        assertFalse(AutoBackupRules.isDue(true, true, lastRunMs = day, nowMs = 5 * day, intervalDays = 7))
        assertFalse(AutoBackupRules.isDue(false, true, lastRunMs = 0, nowMs = 10 * day, intervalDays = 7))
        assertFalse(AutoBackupRules.isDue(true, false, lastRunMs = 0, nowMs = 10 * day, intervalDays = 7))
    }

    @Test
    fun `une sauvegarde quotidienne reste due le lendemain un peu plus tôt`() {
        val yesterday18h = 10 * day + 18 * hour
        assertTrue(AutoBackupRules.isDue(true, true, yesterday18h, nowMs = 11 * day + 17 * hour + 30 * 60 * 1000, intervalDays = 1))
        assertFalse(AutoBackupRules.isDue(true, true, yesterday18h, nowMs = 11 * day + 12 * hour, intervalDays = 1))
    }

    @Test
    fun `nom daté, rangé dans l'ordre chronologique`() {
        assertEquals("ongaku-auto-2026-11-22-183005.zip", AutoBackupRules.fileName(LocalDateTime.of(2026, 11, 22, 18, 30, 5)))
        assertTrue(AutoBackupRules.fileName(LocalDateTime.of(2026, 1, 2, 9, 5)) < AutoBackupRules.fileName(LocalDateTime.of(2026, 11, 1, 0, 0)))
    }

    @Test
    fun `seules les plus anciennes sauvegardes d'Ongaku sont supprimées`() {
        val names = listOf(
            "ongaku-auto-2026-11-20-1800.zip",
            "ongaku-auto-2026-11-22-1800.zip",
            "ongaku-auto-2026-11-21-1800.zip",
            "ongaku-sauvegarde-2026-11-01.zip",
            "photo.jpg",
            "ongaku-auto-notes.txt",
        )
        assertEquals(listOf("ongaku-auto-2026-11-20-1800.zip"), AutoBackupRules.toDelete(names, keep = 2))
        assertEquals(emptyList<String>(), AutoBackupRules.toDelete(names, keep = 5))
        // Jamais moins d'une : la dernière sauvegarde n'est pas supprimée.
        assertEquals(2, AutoBackupRules.toDelete(names, keep = 0).size)
    }

    @Test
    fun `valeurs inconnues ramenées aux valeurs par défaut`() {
        assertEquals(7, AutoBackupRules.interval(3))
        assertEquals(30, AutoBackupRules.interval(30))
        assertEquals(5, AutoBackupRules.keep(4))
        assertEquals("Download/Ongaku", AutoBackupRules.folderLabel("primary:Download/Ongaku"))
    }
}
