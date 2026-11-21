package fr.devsecopsforce.ongaku.playback

/**
 * Décisions du minuteur de sommeil, sans lecteur ni horloge : [SleepTimer] ne fait qu'appliquer.
 * Les durées sont en millisecondes de temps réel.
 */
object SleepTimerRules {
    /** Choix proposés sur l'écran de lecture. */
    val PRESET_MINUTES = listOf(15, 30, 45, 60)

    /** Baisse progressive du volume avant l'arrêt. */
    const val FADE_MS = 60_000L

    /** Pas de la baisse de volume : assez fin pour ne pas s'entendre par paliers. */
    const val FADE_STEP_MS = 250L

    /**
     * Temps avant l'arrêt. Minuteur à durée : jusqu'à l'échéance. Fin du morceau : ce qui reste à
     * lire, à la vitesse de lecture ; null si la durée du morceau n'est pas encore connue.
     */
    fun remainingMs(
        atTrackEnd: Boolean,
        nowMs: Long,
        deadlineMs: Long,
        positionMs: Long,
        durationMs: Long?,
        speed: Float,
    ): Long? = if (atTrackEnd) {
        durationMs?.let { ((it - positionMs).coerceAtLeast(0) / speed.coerceAtLeast(0.1f)).toLong() }
    } else {
        (deadlineMs - nowMs).coerceAtLeast(0)
    }

    /** Volume à appliquer (de 0 à 1) : plein jusqu'à la dernière minute, puis en baisse régulière. */
    fun volume(remainingMs: Long?, fadeEnabled: Boolean): Float = when {
        !fadeEnabled || remainingMs == null || remainingMs >= FADE_MS -> 1f
        else -> (remainingMs.toFloat() / FADE_MS).coerceIn(0f, 1f)
    }

    /**
     * Prochain réveil : au début de la baisse de volume, puis à chaque pas ; à l'échéance sans
     * baisse. null si rien n'est à surveiller (fin du morceau de durée inconnue, ou à l'arrêt :
     * un événement du lecteur relancera le calcul).
     */
    fun nextWakeUpMs(remainingMs: Long?, fadeEnabled: Boolean, isPlaying: Boolean, atTrackEnd: Boolean): Long? {
        if (remainingMs == null) return null
        // La fin d'un morceau n'avance pas pendant la pause ; une échéance, si.
        if (atTrackEnd && !isPlaying) return null
        return when {
            fadeEnabled && remainingMs > FADE_MS -> remainingMs - FADE_MS
            fadeEnabled -> FADE_STEP_MS.coerceAtMost(remainingMs.coerceAtLeast(1))
            else -> remainingMs.coerceAtLeast(1)
        }
    }

    /** Minuteur à durée arrivé à échéance : la lecture s'arrête. La fin du morceau, c'est le lecteur. */
    fun expired(atTrackEnd: Boolean, remainingMs: Long?): Boolean = !atTrackEnd && remainingMs != null && remainingMs <= 0

    /** Texte du temps restant : « 29:41 », ou « 1:05:00 » au-delà d'une heure. */
    fun format(remainingMs: Long): String {
        val total = (remainingMs + 999) / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
