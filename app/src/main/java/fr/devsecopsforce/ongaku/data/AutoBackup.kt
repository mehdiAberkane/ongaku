package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Sauvegarde automatique : une archive complète ([BackupArchive]) datée, écrite dans un dossier
 * choisi une fois par l'utilisateur (éventuellement synchronisé par une autre application), à
 * l'ouverture d'Ongaku ou au démarrage de la lecture quand elle est due. Les données ne changent
 * que quand Ongaku est utilisé : il n'y a rien à sauvegarder entre deux utilisations.
 */
object AutoBackupRules {
    val INTERVALS_DAYS = listOf(1, 7, 30)
    val KEEP_COUNTS = listOf(3, 5, 10)
    const val DEFAULT_INTERVAL_DAYS = 7
    const val DEFAULT_KEEP = 5

    /** Seuls les fichiers à ce nom sont ceux d'Ongaku : les autres ne sont jamais supprimés. */
    const val PREFIX = "ongaku-auto-"
    const val SUFFIX = ".zip"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Marge : une sauvegarde quotidienne faite à 18 h reste due le lendemain à 17 h 30. */
    private const val SLACK_MS = 60L * 60 * 1000

    private val NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")

    fun isDue(enabled: Boolean, hasFolder: Boolean, lastRunMs: Long, nowMs: Long, intervalDays: Int): Boolean =
        enabled && hasFolder && (lastRunMs <= 0 || nowMs - lastRunMs >= intervalDays * DAY_MS - SLACK_MS)

    /** « ongaku-auto-2026-11-22-183000.zip » : l'ordre alphabétique est l'ordre chronologique. */
    fun fileName(at: LocalDateTime): String = PREFIX + at.format(NAME_FORMAT) + SUFFIX

    fun isOurs(name: String): Boolean = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /** Sauvegardes d'Ongaku à supprimer pour n'en garder que les [keep] plus récentes. */
    fun toDelete(names: List<String>, keep: Int): List<String> =
        names.filter(::isOurs).sortedDescending().drop(keep.coerceAtLeast(1))

    fun interval(stored: Int): Int = if (stored in INTERVALS_DAYS) stored else DEFAULT_INTERVAL_DAYS

    fun keep(stored: Int): Int = if (stored in KEEP_COUNTS) stored else DEFAULT_KEEP

    /** Dossier lisible à partir de l'identifiant Android : « primary:Download/Ongaku » → « Download/Ongaku ». */
    fun folderLabel(treeDocumentId: String): String = treeDocumentId.substringAfter(':').ifEmpty { treeDocumentId }
}

/** Réglages de la sauvegarde automatique ; propres à ce téléphone, ils ne sont pas eux-mêmes sauvegardés. */
class AutoBackupSettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("ongaku_auto_backup", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    var intervalDays: Int
        get() = AutoBackupRules.interval(prefs.getInt(KEY_INTERVAL, AutoBackupRules.DEFAULT_INTERVAL_DAYS))
        set(value) = prefs.edit { putInt(KEY_INTERVAL, AutoBackupRules.interval(value)) }

    var keep: Int
        get() = AutoBackupRules.keep(prefs.getInt(KEY_KEEP, AutoBackupRules.DEFAULT_KEEP))
        set(value) = prefs.edit { putInt(KEY_KEEP, AutoBackupRules.keep(value)) }

    /** Dossier choisi (adresse donnée par le sélecteur d'Android, avec un accès permanent). */
    var folderUri: String?
        get() = prefs.getString(KEY_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_FOLDER, value) }

    var lastRunMs: Long
        get() = prefs.getLong(KEY_LAST_RUN, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_RUN, value) }

    /** La dernière tentative a échoué (dossier supprimé, accès retiré…). */
    var lastFailed: Boolean
        get() = prefs.getBoolean(KEY_FAILED, false)
        set(value) = prefs.edit { putBoolean(KEY_FAILED, value) }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_INTERVAL = "interval_days"
        const val KEY_KEEP = "keep"
        const val KEY_FOLDER = "folder_uri"
        const val KEY_LAST_RUN = "last_run_ms"
        const val KEY_FAILED = "last_failed"
    }
}
