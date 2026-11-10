package fr.devsecopsforce.ongaku.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LibraryFilter(
    val minSeconds: Int = 0,
    /** 0 signifie sans limite. */
    val maxSeconds: Int = 0,
    /** Liste d'exclusion : une extension encore jamais vue reste visible par défaut. */
    val excludedExtensions: Set<String> = emptySet(),
    val watchAllFolders: Boolean = true,
    val watchedFolders: Set<String> = emptySet(),
) {
    fun matches(track: Track): Boolean {
        val seconds = track.durationMs / 1000
        return seconds >= minSeconds &&
            (maxSeconds == 0 || seconds <= maxSeconds) &&
            track.extension !in excludedExtensions &&
            (watchAllFolders || track.folder in watchedFolders)
    }
}

class LibraryFilterStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ongaku_library_filter", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LibraryFilter> = _state.asStateFlow()

    fun update(transform: (LibraryFilter) -> LibraryFilter) {
        val updated = transform(_state.value)
        _state.value = updated
        prefs.edit {
            putInt("min_seconds", updated.minSeconds)
            putInt("max_seconds", updated.maxSeconds)
            putStringSet("excluded_extensions", updated.excludedExtensions)
            putBoolean("watch_all_folders", updated.watchAllFolders)
            putStringSet("watched_folders", updated.watchedFolders)
        }
    }

    private fun load() = LibraryFilter(
        minSeconds = prefs.getInt("min_seconds", 0),
        maxSeconds = prefs.getInt("max_seconds", 0),
        excludedExtensions = prefs.getStringSet("excluded_extensions", null).orEmpty().toSet(),
        watchAllFolders = prefs.getBoolean("watch_all_folders", true),
        watchedFolders = prefs.getStringSet("watched_folders", null).orEmpty().toSet(),
    )

    companion object {
        @Volatile
        private var instance: LibraryFilterStore? = null

        fun get(context: Context): LibraryFilterStore = instance ?: synchronized(this) {
            instance ?: LibraryFilterStore(context.applicationContext).also { instance = it }
        }
    }
}
