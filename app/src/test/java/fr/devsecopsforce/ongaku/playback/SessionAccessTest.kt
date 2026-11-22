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

    @Test
    fun `Android Auto installé avec le système se connecte`() {
        assertTrue(SessionAccess.allowed("com.google.android.projection.gearhead", own, trusted = false, systemApp = true))
        assertTrue(SessionAccess.allowed("com.google.android.googlequicksearchbox", own, trusted = false, systemApp = true))
    }

    @Test
    fun `une application installée à part sous le nom d'Android Auto est refusée`() {
        assertFalse(SessionAccess.allowed("com.google.android.projection.gearhead", own, trusted = false, systemApp = false))
        // Une application système quelconque n'en profite pas.
        assertFalse(SessionAccess.allowed("com.example.systeme", own, trusted = false, systemApp = true))
    }

    @Test
    fun `la liaison par l'ancienne interface passe, le vrai demandeur est jugé à la connexion`() {
        assertTrue(SessionAccess.bindingAllowed(SessionAccess.LEGACY_BINDING, own, trusted = false))
        // À la connexion, ce nom générique n'a aucun droit.
        assertFalse(SessionAccess.allowed(SessionAccess.LEGACY_BINDING, own, trusted = false))
        assertFalse(SessionAccess.bindingAllowed("com.example.espion", own, trusted = false))
        assertTrue(SessionAccess.bindingAllowed(own, own, trusted = false))
    }}
