package fr.devsecopsforce.ongaku

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import fr.devsecopsforce.ongaku.ui.AppDataRepository
import fr.devsecopsforce.ongaku.ui.LockScreenPlayer
import fr.devsecopsforce.ongaku.ui.OngakuTheme
import fr.devsecopsforce.ongaku.ui.PlayerViewModel

/**
 * Écran affiché par-dessus le verrouillage. Il est volontairement distinct de MainActivity, et dans
 * sa propre tâche, pour que la bibliothèque et les réglages restent inaccessibles sans déverrouiller.
 *
 * Certains téléphones (OnePlus notamment) ne déverrouillent pas par empreinte tant qu'un écran est
 * affiché par-dessus le verrouillage : le bouton « Déverrouiller » ouvre donc directement le
 * déverrouillage d'Android, où l'empreinte et le code fonctionnent.
 */
class LockScreenActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()
    private val appData by lazy { AppDataRepository.get(this) }

    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT || !keyguard.isKeyguardLocked) finish()
        }
    }

    /**
     * Ouvre le déverrouillage d'Android ; l'écran se ferme une fois le téléphone déverrouillé. Si
     * l'utilisateur renonce, l'écran de lecture reste affiché.
     */
    private fun unlock() {
        if (!keyguard.isKeyguardLocked) {
            finish()
            return
        }
        keyguard.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()

                // Android ne peut pas afficher le déverrouillage : on rend la main à l'écran de verrouillage.
                override fun onDismissError() = finish()
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()
        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            OngakuTheme {
                LockScreenPlayer(playerViewModel, appData, onUnlock = ::unlock)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }
}
