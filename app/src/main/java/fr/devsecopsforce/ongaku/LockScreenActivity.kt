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
import fr.devsecopsforce.ongaku.ui.AppDataViewModel
import fr.devsecopsforce.ongaku.ui.LockScreenPlayer
import fr.devsecopsforce.ongaku.ui.OngakuTheme
import fr.devsecopsforce.ongaku.ui.PlayerViewModel

/**
 * Écran affiché par-dessus le verrouillage. Il est volontairement distinct de MainActivity, et dans
 * sa propre tâche, pour que la bibliothèque et les réglages restent inaccessibles sans déverrouiller.
 */
class LockScreenActivity : ComponentActivity() {
    private val playerViewModel: PlayerViewModel by viewModels()
    private val appDataViewModel: AppDataViewModel by viewModels()

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val keyguard = getSystemService(KeyguardManager::class.java)
            if (intent.action == Intent.ACTION_USER_PRESENT || !keyguard.isKeyguardLocked) finish()
        }
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
                LockScreenPlayer(playerViewModel, appDataViewModel, onClose = ::finish)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(unlockReceiver)
        super.onDestroy()
    }
}
