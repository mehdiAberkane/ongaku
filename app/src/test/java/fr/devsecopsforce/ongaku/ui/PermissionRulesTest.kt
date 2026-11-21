package fr.devsecopsforce.ongaku.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRulesTest {

    @Test
    fun `avant toute demande, la permission n'est pas bloquée`() {
        // Android ne propose pas d'explication avant la première demande : ce n'est pas un blocage.
        assertFalse(PermissionRules.blocked(asked = false, granted = false, showRationale = false))
    }

    @Test
    fun `après un premier refus, on peut encore demander`() {
        assertFalse(PermissionRules.blocked(asked = true, granted = false, showRationale = true))
    }

    @Test
    fun `refusée et sans explication possible, seuls les paramètres permettent de l'accorder`() {
        assertTrue(PermissionRules.blocked(asked = true, granted = false, showRationale = false))
    }

    @Test
    fun `une permission accordée n'est jamais bloquée`() {
        assertFalse(PermissionRules.blocked(asked = true, granted = true, showRationale = false))
    }
}
