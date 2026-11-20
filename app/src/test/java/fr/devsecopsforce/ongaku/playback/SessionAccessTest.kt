package fr.devsecopsforce.ongaku.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccessTest {

    private val own = "fr.devsecopsforce.ongaku"

    @Test
    fun `Ongaku se connecte toujours`() {
        assertTrue(SessionAccess.allowed(own, own, trusted = false))
    }

    @Test
    fun `un contrôleur de confiance se connecte`() {
        assertTrue(SessionAccess.allowed("com.android.systemui", own, trusted = true))
    }

    @Test
    fun `une autre application est refusée`() {
        assertFalse(SessionAccess.allowed("com.example.espion", own, trusted = false))
        // Un nom proche ne suffit pas.
        assertFalse(SessionAccess.allowed("$own.faux", own, trusted = false))
    }
}
