package fr.devsecopsforce.ongaku.widget

import fr.devsecopsforce.ongaku.data.AppData

/** Une image possible pour le widget : un fichier d'image de fond d'Ongaku, ou une pochette. */
sealed interface WidgetImage {
    /** Image choisie dans Ongaku, rangée dans `files/backgrounds/`. */
    data class Background(val name: String) : WidgetImage

    /** Pochette de l'album (adresse MediaStore). */
    data class Artwork(val uri: String) : WidgetImage

    companion object {
        /**
         * Images à essayer, dans l'ordre : celle choisie pour le widget, celle du morceau, la pochette,
         * puis l'image par défaut de l'écran de lecture et celle de l'application. La première qui se
         * charge est affichée ; si aucune ne se charge, le widget garde son image de base.
         */
        fun candidates(data: AppData, trackId: Long?, artworkUri: String?): List<WidgetImage> =
            listOfNotNull(
                data.widgetBackground?.let(::Background),
                trackId?.let(data.trackBackgrounds::get)?.let(::Background),
                artworkUri?.let(::Artwork),
                data.playerBackground?.let(::Background),
                data.appBackground?.let(::Background),
            ).distinct()
    }
}

/**
 * Garde la dernière image décodée : le widget se redessine à chaque pause, lecture ou changement de
 * morceau, souvent avec la même image. Les noms des images de fond changent avec leur contenu.
 */
class WidgetImageCache<B : Any> {
    private var last: Pair<WidgetImage, B>? = null

    /** La première image de [candidates] qui se charge, sans redécoder la dernière affichée. */
    @Synchronized
    fun pick(candidates: List<WidgetImage>, load: (WidgetImage) -> B?): B? {
        for (candidate in candidates) {
            last?.takeIf { it.first == candidate }?.let { return it.second }
            val image = load(candidate) ?: continue
            last = candidate to image
            return image
        }
        return null
    }
}