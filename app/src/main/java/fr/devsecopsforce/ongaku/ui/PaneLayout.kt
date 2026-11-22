package fr.devsecopsforce.ongaku.ui

/**
 * Mise en page selon la taille de la fenêtre, en dp. Sur un écran large, la bibliothèque et le
 * lecteur s'affichent côte à côte ; sur un téléphone en portrait, le lecteur s'ouvre par-dessus.
 */
object PaneLayout {
    /** Largeur d'une tablette, quelle que soit l'orientation. */
    const val WIDE_DP = 840

    /** Largeur minimale pour deux colonnes en paysage (un téléphone couché dépasse ce seuil). */
    const val LANDSCAPE_MIN_DP = 600

    const val PLAYER_MIN_DP = 320
    const val PLAYER_MAX_DP = 480

    fun twoPane(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= WIDE_DP || (widthDp >= LANDSCAPE_MIN_DP && widthDp > heightDp)

    /** Largeur de la colonne du lecteur : un peu moins de la moitié, dans des bornes lisibles. */
    fun playerWidthDp(widthDp: Int): Int = (widthDp * 42 / 100).coerceIn(PLAYER_MIN_DP, PLAYER_MAX_DP)
}
