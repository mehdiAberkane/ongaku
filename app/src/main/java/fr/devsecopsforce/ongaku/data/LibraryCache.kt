package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bibliothèque gardée en mémoire et partagée par l'écran, le service, le widget et la lecture
 * automatique : un appui sur le widget ou une connexion Bluetooth ne relit plus tout MediaStore.
 * Elle est relue quand Android signale un changement de la musique du téléphone.
 */
object LibraryCache {
    private val mutex = Mutex()

    @Volatile
    private var tracks: List<Track>? = null

    /** Incrémenté à chaque changement signalé : une lecture commencée avant reste périmée. */
    @Volatile
    private var version = 0

    @Volatile
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = invalidate()
    }

    /** Les morceaux du téléphone, relus seulement si la musique a changé depuis la dernière lecture. */
    suspend fun tracks(context: Context): List<Track> {
        tracks?.let { return it }
        return mutex.withLock {
            tracks?.let { return@withLock it }
            observe(context.applicationContext)
            val startVersion = version
            val loaded = MusicRepository(context.applicationContext).loadTracks()
            // Un changement signalé pendant la lecture : on rend ce qu'on a, sans le garder.
            if (version == startVersion) tracks = loaded
            loaded
        }
    }

    /** La musique a changé, ou l'utilisateur demande une nouvelle recherche. */
    fun invalidate() {
        version++
        tracks = null
    }

    private fun observe(context: Context) {
        if (observing) return
        observing = true
        // Tout le processus : la bibliothèque sert tant que l'application tourne.
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
    }
}
