package fr.devsecopsforce.ongaku.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryFilterTest {

    @Test
    fun `le filtre par défaut garde tout`() {
        val filter = LibraryFilter()
        assertTrue(filter.matches(track(1, durationMs = 1_000, extension = "ogg", folder = "Ailleurs")))
    }

    @Test
    fun `durée minimale incluse, en secondes entières`() {
        val filter = LibraryFilter(minSeconds = 30)
        assertTrue(filter.matches(track(1, durationMs = 30_000)))
        // 29,999 s compte pour 29 s.
        assertFalse(filter.matches(track(2, durationMs = 29_999)))
    }

    @Test
    fun `durée maximale incluse, zéro voulant dire sans limite`() {
        assertTrue(LibraryFilter(maxSeconds = 0).matches(track(1, durationMs = 10_000_000)))
        val filter = LibraryFilter(maxSeconds = 300)
        assertTrue(filter.matches(track(2, durationMs = 300_999)))
        assertFalse(filter.matches(track(3, durationMs = 301_000)))
    }

    @Test
    fun `une extension exclue est masquée, une extension inconnue reste visible`() {
        val filter = LibraryFilter(excludedExtensions = setOf("wav"))
        assertFalse(filter.matches(track(1, extension = "wav")))
        assertTrue(filter.matches(track(2, extension = "opus")))
    }

    @Test
    fun `seuls les dossiers surveillés comptent quand tous ne le sont pas`() {
        val filter = LibraryFilter(watchAllFolders = false, watchedFolders = setOf("Music"))
        assertTrue(filter.matches(track(1, folder = "Music")))
        assertFalse(filter.matches(track(2, folder = "Download")))
        // Les dossiers choisis sont ignorés tant que tous les dossiers sont surveillés.
        assertTrue(filter.copy(watchAllFolders = true).matches(track(3, folder = "Download")))
    }

    @Test
    fun `un minimum supérieur au maximum est une plage invalide`() {
        assertFalse(LibraryFilter.isValidDuration(300, 60))
        assertTrue(LibraryFilter.isValidDuration(60, 300))
        assertTrue(LibraryFilter.isValidDuration(60, 60))
        // Maximum vide : pas de limite haute, tout minimum convient.
        assertTrue(LibraryFilter.isValidDuration(3_600, 0))
    }

    @Test
    fun `une plage invalide déjà enregistrée ne masque pas la bibliothèque`() {
        val filter = LibraryFilter(minSeconds = 300, maxSeconds = 60)
        assertFalse(filter.durationValid)
        assertTrue(filter.matches(track(1, durationMs = 10_000)))
        assertTrue(filter.matches(track(2, durationMs = 600_000)))
    }

    @Test
    fun `une plage invalide n'annule pas les autres critères`() {
        val filter = LibraryFilter(minSeconds = 300, maxSeconds = 60, excludedExtensions = setOf("ogg"))
        assertFalse(filter.matches(track(1, extension = "ogg")))
    }}
