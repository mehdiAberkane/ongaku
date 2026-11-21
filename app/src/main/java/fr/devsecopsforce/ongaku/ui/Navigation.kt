// Écran ouvert par-dessus les onglets, et sa sauvegarde : il survit à une rotation ou à un
// changement de thème, qui recréent l'activité.
package fr.devsecopsforce.ongaku.ui

import androidx.compose.runtime.saveable.Saver
import fr.devsecopsforce.ongaku.data.TrackGroup

internal sealed interface Detail {
    /**
     * Album ou artiste, désigné par sa clé ([TrackGroup.key]) et non par une copie de son contenu :
     * la page suit la bibliothèque. [from] : écran où revenir, par exemple la recherche.
     */
    data class Group(val key: String, val from: Detail? = null) : Detail
    data object Search : Detail
    data object Favorites : Detail
    data object RecentlyAdded : Detail
    data object Top50 : Detail
    data class PlaylistDetail(val id: String) : Detail
    data object Settings : Detail
    data object Stats : Detail
    data object Appearance : Detail
}

internal object DetailCodec {
    /** Forme enregistrable : un nom d'écran, son argument, puis l'écran d'où il a été ouvert. */
    fun encode(detail: Detail): List<String> = when (detail) {
        is Detail.Group -> listOf("group", detail.key) + detail.from?.let(::encode).orEmpty()
        is Detail.PlaylistDetail -> listOf("playlist", detail.id)
        Detail.Search -> listOf("search")
        Detail.Favorites -> listOf("favorites")
        Detail.RecentlyAdded -> listOf("recent")
        Detail.Top50 -> listOf("top50")
        Detail.Settings -> listOf("settings")
        Detail.Stats -> listOf("stats")
        Detail.Appearance -> listOf("appearance")
    }

    /** null pour une forme inconnue : on revient alors aux onglets. */
    fun decode(saved: List<String>): Detail? {
        val name = saved.firstOrNull() ?: return null
        return when (name) {
            "group" -> saved.getOrNull(1)?.let { key -> Detail.Group(key, decode(saved.drop(2))) }
            "playlist" -> saved.getOrNull(1)?.let(Detail::PlaylistDetail)
            "search" -> Detail.Search
            "favorites" -> Detail.Favorites
            "recent" -> Detail.RecentlyAdded
            "top50" -> Detail.Top50
            "settings" -> Detail.Settings
            "stats" -> Detail.Stats
            "appearance" -> Detail.Appearance
            else -> null
        }
    }
}

internal val DetailSaver = Saver<Detail?, ArrayList<String>>(
    save = { detail -> detail?.let { ArrayList(DetailCodec.encode(it)) } },
    restore = { DetailCodec.decode(it) },
)

/** Morceaux à ajouter à une liste, gardés pendant une rotation. */
internal val TrackIdsSaver = Saver<List<Long>?, LongArray>(
    save = { it?.toLongArray() },
    restore = { it.toList() },
)

/** L'album ou l'artiste désigné par [key], tel qu'il est dans la bibliothèque actuelle. */
internal fun findGroup(key: String, albums: List<TrackGroup>, artists: List<TrackGroup>): TrackGroup? =
    (if (key.startsWith("artist:")) artists else albums).firstOrNull { it.key == key }
