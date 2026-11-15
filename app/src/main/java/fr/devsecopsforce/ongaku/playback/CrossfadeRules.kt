package fr.devsecopsforce.ongaku.playback

import kotlin.math.sin

/**
 * Décisions du fondu enchaîné et du compteur d'écoutes, sans lecteur : [CrossfadeController] relève
 * l'état des deux lecteurs et applique ce qui est décidé ici. Séparé pour être testé.
 */
internal object CrossfadeRules {
    const val PREPARE_LEAD_MS = 3000L
    const val FADE_TIMEOUT_MARGIN_MS = 3000L
    const val COUNT_PLAY_AFTER_MS = 30_000L

    /** Où en est le lecteur de relève. */
    enum class Incoming { NONE, PREPARING, READY }

    /** Ce qu'il faut faire avant le fondu, à chaque relevé. */
    enum class Watch { NOTHING, CANCEL, PREPARE, START }

    /** Ce qu'il faut faire pendant le fondu, à chaque relevé. */
    sealed interface Fade {
        data object Cancel : Fade
        data object Finish : Fade
        data class Volumes(val incoming: Float, val outgoing: Float) : Fade
    }

    /**
     * Avant le fondu. [durationMs] vaut null tant que la durée du morceau est inconnue.
     * La relève se prépare [PREPARE_LEAD_MS] avant le début du fondu, puis le fondu démarre
     * quand il reste [fadeMs] et que la relève est prête.
     */
    fun watch(
        fadeMs: Long,
        durationMs: Long?,
        positionMs: Long,
        isPlaying: Boolean,
        hasNext: Boolean,
        repeatOne: Boolean,
        incoming: Incoming,
    ): Watch {
        val canFade = fadeMs > 0 && durationMs != null && durationMs >= fadeMs * 2 &&
            isPlaying && hasNext && !repeatOne
        if (!canFade) return Watch.CANCEL

        val remaining = durationMs - positionMs
        return when {
            // Retour en arrière alors que la relève était déjà prête.
            remaining > fadeMs + PREPARE_LEAD_MS + 1000 -> Watch.CANCEL
            incoming == Incoming.NONE ->
                if (remaining <= fadeMs + PREPARE_LEAD_MS && remaining > fadeMs / 2) {
                    Watch.PREPARE
                } else {
                    Watch.NOTHING
                }
            remaining <= fadeMs && incoming == Incoming.READY -> Watch.START
            else -> Watch.NOTHING
        }
    }

    /**
     * Pendant le fondu. La progression suit la position du morceau qui se termine, pas l'horloge ;
     * [elapsedMs] ne sert que de garde-fou si le lecteur actif n'avance plus.
     */
    fun fade(remainingMs: Long, fadeDurationMs: Long, elapsedMs: Long): Fade = when {
        // Retour en arrière pendant le fondu.
        remainingMs > fadeDurationMs + 1000 -> Fade.Cancel
        remainingMs <= 0 || elapsedMs > fadeDurationMs + FADE_TIMEOUT_MARGIN_MS -> Fade.Finish
        else -> {
            val progress = (1f - remainingMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
            // Courbes en S : chaque morceau part et arrive en pente douce. Les deux volumes se
            // croisent à mi-chemin et totalisent toujours 1.
            val fadeIn = sin(progress * (Math.PI.toFloat() / 2)).let { it * it }
            Fade.Volumes(incoming = fadeIn, outgoing = 1f - fadeIn)
        }
    }

    /** Une écoute compte une fois par lecture : 30 s, ou la moitié d'un morceau plus court. */
    fun shouldCountPlay(listenedMs: Long, durationMs: Long?): Boolean {
        val threshold = if (durationMs == null) {
            COUNT_PLAY_AFTER_MS
        } else {
            minOf(COUNT_PLAY_AFTER_MS, durationMs / 2)
        }
        return listenedMs >= threshold
    }
}
