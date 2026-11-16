// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.os.Bundle
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.SessionCommand
import fr.devsecopsforce.ongaku.data.Track
import fr.devsecopsforce.ongaku.data.mediaItem

/**
 * Commandes envoyées par l'interface au service de lecture, pour la lecture aléatoire : seul le
 * service peut fixer l'ordre mélangé. Sans lecture aléatoire, l'interface agit directement sur la file.
 *
 * L'ajout se fait dans le service, en même temps que le rangement : Media3 exécute une commande
 * personnalisée avant les ajouts qu'on lui a demandés juste avant, le rangement visait alors une file
 * qui n'avait pas encore les nouveaux morceaux.
 */
object QueueCommands {
    private const val PREFIX = "fr.devsecopsforce.ongaku.queue."

    /** Ajoute des morceaux juste après le morceau en cours, ou à la fin de l'ordre de lecture. */
    val ADD = SessionCommand("${PREFIX}ADD", Bundle.EMPTY)

    /** Déplace un morceau dans l'ordre de lecture. */
    val MOVE = SessionCommand("${PREFIX}MOVE", Bundle.EMPTY)

    private const val ARG_IDS = "ids"
    private const val ARG_TITLES = "titles"
    private const val ARG_ARTISTS = "artists"
    private const val ARG_ALBUMS = "albums"
    private const val ARG_ARTWORKS = "artworks"
    private const val ARG_NEXT = "next"
    private const val ARG_FROM = "from"
    private const val ARG_TO = "to"

    /** Au-delà, un envoi risque de dépasser la taille permise entre l'interface et le service. */
    const val MAX_ITEMS_PER_COMMAND = 200

    /** Les morceaux voyagent par leur identifiant et leurs tags ; le service reconstruit leur élément. */
    fun addArgs(tracks: List<Track>, next: Boolean) = Bundle().apply {
        putLongArray(ARG_IDS, tracks.map { it.id }.toLongArray())
        putStringArray(ARG_TITLES, tracks.map { it.title }.toTypedArray())
        putStringArray(ARG_ARTISTS, tracks.map { it.artist }.toTypedArray())
        putStringArray(ARG_ALBUMS, tracks.map { it.album }.toTypedArray())
        putStringArray(ARG_ARTWORKS, tracks.map { it.artworkUri.toString() }.toTypedArray())
        putBoolean(ARG_NEXT, next)
    }

    internal fun items(args: Bundle): List<MediaItem>? {
        val ids = args.getLongArray(ARG_IDS) ?: return null
        val titles = args.getStringArray(ARG_TITLES) ?: return null
        val artists = args.getStringArray(ARG_ARTISTS) ?: return null
        val albums = args.getStringArray(ARG_ALBUMS) ?: return null
        val artworks = args.getStringArray(ARG_ARTWORKS) ?: return null
        if (setOf(titles.size, artists.size, albums.size, artworks.size) != setOf(ids.size)) return null
        return ids.indices.map { i -> mediaItem(ids[i], titles[i], artists[i], albums[i], artworks[i].toUri()) }
    }

    fun moveArgs(from: Int, to: Int) = Bundle().apply {
        putInt(ARG_FROM, from)
        putInt(ARG_TO, to)
    }

    /** Applique la commande ; false si elle n'est pas reconnue ou ses arguments sont faux. */
    fun apply(player: ExoPlayer, command: SessionCommand, args: Bundle): Boolean {
        when (command.customAction) {
            ADD.customAction -> {
                val items = items(args)?.takeIf { it.isNotEmpty() } ?: return false
                val next = args.getBoolean(ARG_NEXT)
                val current = player.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET }
                val start = if (next && current != null) current + 1 else player.mediaItemCount
                player.addMediaItems(start, items)
                if (player.shuffleModeEnabled) {
                    setOrder(
                        player,
                        QueueOrder.place(player.playOrder(), (start until start + items.size).toList(), current.takeIf { next }),
                    )
                }
            }
            MOVE.customAction -> {
                val order = player.playOrder()
                setOrder(player, QueueOrder.move(order, args.getInt(ARG_FROM, -1), args.getInt(ARG_TO, -1)))
            }
            else -> return false
        }
        return true
    }

    private fun setOrder(player: ExoPlayer, order: List<Int>) {
        if (order == player.playOrder()) return
        player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), SystemClock.elapsedRealtime()))
    }
}
