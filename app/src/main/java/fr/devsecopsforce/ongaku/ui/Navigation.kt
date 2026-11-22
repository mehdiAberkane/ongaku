// Écran ouvert par-dessus les onglets, et sa sauvegarde : il survit à une rotation ou à un
// changement de thème, qui recréent l'activité.
package fr.devsecopsforce.ongaku.ui

import androidx.compose.runtime.saveable.Saver
import fr.devsecopsforce.ongaku.data.FolderTree
import fr.devsecopsforce.ongaku.data.SmartList
import fr.devsecopsforce.ongaku.data.TrackGroup

internal sealed interface Detail {
    /**
     * Album ou artiste, désigné par sa clé ([TrackGroup.key]) et non par une copie de son contenu :
     * la page suit la bibliothèque. [from] : écran où revenir, par exemple la recherche.
     */
    data class Group(val key: String, val from: Detail? = null) : Detail
    data object Search : Detail
    data object Favorites : Detail
    /** Liste automatique : ajouts récents, plus écoutés, redécouvertes… */
    data class Smart(val kind: SmartList) : Detail
    data class PlaylistDetail(val id: String) : Detail
    data object Settings : Detail
    data object Stats : Detail
    data object Appearance : Detail
    data object Equalizer : Detail

    /** Dossier du téléphone, par son chemin ([FolderTree]) ; la racine est l'onglet Dossiers. */
    data class Folder(val path: String) : Detail
}

internal object DetailCodec {
    /** Forme enregistrable : un nom d'écran, son argument, puis l'écran d'où il a été ouvert. */
    fun encode(detail: Detail): List<String> = when (detail) {
        is Detail.Group -> listOf("group", detail.key) + detail.from?.let(::encode).orEmpty()
        is Detail.PlaylistDetail -> listOf("playlist", detail.id)
        Detail.Search -> listOf("search")
        Detail.Favorites -> listOf("favorites")
        is Detail.Smart -> listOf("smart", detail.kind.name)
        Detail.Settings -> listOf("settings")
        Detail.Stats -> listOf("stats")
        Detail.Appearance -> listOf("appearance")
        Detail.Equalizer -> listOf("equalizer")
        is Detail.Folder -> listOf("folder", detail.path)
    }

    /** null pour une forme inconnue : on revient alors aux onglets. */
    fun decode(saved: List<String>): Detail? {
        val name = saved.firstOrNull() ?: return null
        return when (name) {
            "group" -> saved.getOrNull(1)?.let { key -> Detail.Group(key, decode(saved.drop(2))) }
            "playlist" -> saved.getOrNull(1)?.let(Detail::PlaylistDetail)
            "search" -> Detail.Search
            "favorites" -> Detail.Favorites
            "smart" -> SmartList.entries.firstOrNull { it.name == saved.getOrNull(1) }?.let(Detail::Smart)
            // Noms d'avant les listes automatiques supplémentaires.
            "recent" -> Detail.Smart(SmartList.RECENTLY_ADDED)
            "top50" -> Detail.Smart(SmartList.MOST_PLAYED)
            "settings" -> Detail.Settings
            "stats" -> Detail.Stats
            "appearance" -> Detail.Appearance
            "equalizer" -> Detail.Equalizer
            "folder" -> saved.getOrNull(1)?.let(Detail::Folder)
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

/** Le groupe désigné par [key] (préfixe « artist: », « genre: », « decade: » ou album), tel qu'il est dans la bibliothèque actuelle. */
internal fun findGroup(
    key: String,
    albums: List<TrackGroup>,
    artists: List<TrackGroup>,
    genres: List<TrackGroup> = emptyList(),
    decades: List<TrackGroup> = emptyList(),
): TrackGroup? = when {
    key.startsWith("artist:") -> artists
    key.startsWith("genre:") -> genres
    key.startsWith("decade:") -> decades
    else -> albums
}.firstOrNull { it.key == key }