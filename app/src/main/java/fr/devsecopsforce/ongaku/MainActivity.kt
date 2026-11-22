package fr.devsecopsforce.ongaku

import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import fr.devsecopsforce.ongaku.ui.AppDataRepository
import fr.devsecopsforce.ongaku.ui.LibraryViewModel
import fr.devsecopsforce.ongaku.ui.OngakuApp
import fr.devsecopsforce.ongaku.ui.OngakuTheme
import fr.devsecopsforce.ongaku.ui.PlayerViewModel

class MainActivity : ComponentActivity() {
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val appData by lazy { AppDataRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recréée après une rotation : la demande a déjà été lancée.
        if (savedInstanceState == null) handleVoiceSearch(intent)
        setContent {
            OngakuTheme {
                OngakuApp(libraryViewModel, playerViewModel, appData)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceSearch(intent)
    }

    /** « Joue … sur Ongaku », demandé à l'Assistant le téléphone en main. */
    private fun handleVoiceSearch(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        playerViewModel.playFromSearch(intent.getStringExtra(SearchManager.QUERY).orEmpty())
    }
}