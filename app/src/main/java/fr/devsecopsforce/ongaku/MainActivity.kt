package fr.devsecopsforce.ongaku

import android.os.Bundle
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
        setContent {
            OngakuTheme {
                OngakuApp(libraryViewModel, playerViewModel, appData)
            }
        }
    }
}
