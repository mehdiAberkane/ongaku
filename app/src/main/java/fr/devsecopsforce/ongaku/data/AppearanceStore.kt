package fr.devsecopsforce.ongaku.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CoverShape { ROUNDED, CIRCLE, SQUARE }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Couleur ARGB choisie ; null utilise les couleurs du système. */
    val accent: Int? = null,
    val pureBlack: Boolean = false,
    val backgroundDim: Float = 0.72f,
    val backgroundBlur: Int = 0,
    val coverShape: CoverShape = CoverShape.ROUNDED,
    val accentFromArtwork: Boolean = false,
    val textScale: Float = 1f,
    val compactList: Boolean = false,
    val playerDim: Float = 0.08f,
    val startTab: Int = 0,
)

/** Instance unique : l'application et l'écran de verrouillage suivent les mêmes réglages. */
class AppearanceStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("ongaku_appearance", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Appearance> = _state.asStateFlow()

    /** Couleur tirée de la pochette en cours ; jamais enregistrée, elle change avec le morceau. */
    val artworkAccent = MutableStateFlow<Int?>(null)

    fun update(transform: (Appearance) -> Appearance) {
        val updated = transform(_state.value)
        _state.value = updated
        prefs.edit {
            putString("theme_mode", updated.themeMode.name)
            if (updated.accent == null) remove("accent") else putInt("accent", updated.accent)
            putBoolean("pure_black", updated.pureBlack)
            putFloat("background_dim", updated.backgroundDim)
            putInt("background_blur", updated.backgroundBlur)
            putString("cover_shape", updated.coverShape.name)
            putBoolean("accent_from_artwork", updated.accentFromArtwork)
            putFloat("text_scale", updated.textScale)
            putBoolean("compact_list", updated.compactList)
            putFloat("player_dim", updated.playerDim)
            putInt("start_tab", updated.startTab)
        }
    }

    private fun load(): Appearance {
        val defaults = Appearance()
        return Appearance(
            themeMode = enumValue(prefs.getString("theme_mode", null), defaults.themeMode),
            accent = if (prefs.contains("accent")) prefs.getInt("accent", 0) else null,
            pureBlack = prefs.getBoolean("pure_black", defaults.pureBlack),
            backgroundDim = prefs.getFloat("background_dim", defaults.backgroundDim),
            backgroundBlur = prefs.getInt("background_blur", defaults.backgroundBlur),
            coverShape = enumValue(prefs.getString("cover_shape", null), defaults.coverShape),
            accentFromArtwork = prefs.getBoolean("accent_from_artwork", defaults.accentFromArtwork),
            textScale = prefs.getFloat("text_scale", defaults.textScale),
            compactList = prefs.getBoolean("compact_list", defaults.compactList),
            playerDim = prefs.getFloat("player_dim", defaults.playerDim),
            startTab = prefs.getInt("start_tab", defaults.startTab),
        )
    }

    private inline fun <reified T : Enum<T>> enumValue(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        @Volatile
        private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore = instance ?: synchronized(this) {
            instance ?: AppearanceStore(context.applicationContext).also { instance = it }
        }
    }
}
