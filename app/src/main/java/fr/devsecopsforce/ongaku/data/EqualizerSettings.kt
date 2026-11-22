package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Réglages de l'égaliseur intégré. Les bandes et les préréglages viennent du téléphone : on ne garde
 * que des numéros et des niveaux, relus contre ce que le téléphone propose ([EqualizerLevels]).
 */
class EqualizerSettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Égaliseur et basses appliqués ; coupé par défaut, le son reste celui d'origine. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** Préréglage du téléphone ; [CUSTOM] pour les niveaux réglés à la main. */
    var preset: Int
        get() = prefs.getInt(KEY_PRESET, CUSTOM)
        set(value) = prefs.edit { putInt(KEY_PRESET, value) }

    /** Niveaux des bandes, en millibels, dans l'ordre des bandes ; vide si jamais réglés. */
    var bandLevels: String
        get() = prefs.getString(KEY_BAND_LEVELS, null).orEmpty()
        set(value) = prefs.edit { putString(KEY_BAND_LEVELS, value) }

    /** Amplification des basses, de 0 (aucune) à [EqualizerLevels.MAX_BASS]. */
    var bassStrength: Int
        get() = prefs.getInt(KEY_BASS, 0)
        set(value) = prefs.edit { putInt(KEY_BASS, EqualizerLevels.bass(value)) }

    /** Niveaux réglés à la main : le préréglage passe à « Personnalisé ». */
    fun setCustomLevels(levels: List<Int>) = prefs.edit {
        putInt(KEY_PRESET, CUSTOM)
        putString(KEY_BAND_LEVELS, EqualizerLevels.format(levels))
    }

    companion object {
        const val PREFS = "ongaku_equalizer"
        const val CUSTOM = -1
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_BAND_LEVELS = "band_levels"
        private const val KEY_BASS = "bass_strength"
    }
}

object EqualizerLevels {
    /** Échelle d'Android pour l'amplification des basses. */
    const val MAX_BASS = 1000

    fun format(levels: List<Int>): String = levels.joinToString(",")

    /**
     * Niveaux enregistrés, ramenés à ce que le téléphone accepte : autant de valeurs que de bandes
     * (0 dB pour une bande sans valeur), chacune entre [min] et [max]. Un réglage écrit sur un autre
     * téléphone, ou abîmé, ne peut donc pas produire de valeur refusée.
     */
    fun parse(stored: String, bandCount: Int, min: Int, max: Int): List<Int> {
        val values = stored.split(',').map { it.trim().toIntOrNull() }
        return List(bandCount) { band -> (values.getOrNull(band) ?: 0).coerceIn(min, max) }
    }

    fun bass(strength: Int): Int = strength.coerceIn(0, MAX_BASS)

    /**
     * Nom d'un préréglage tel que le donne le téléphone : certains le complètent de caractères nuls,
     * qui n'ont rien à faire à l'écran.
     */
    fun presetName(raw: String): String = raw.substringBefore('\u0000').trim()

    /** Préréglage enregistré encore proposé par le téléphone, sinon « Personnalisé ». */
    fun preset(stored: Int, presetCount: Int): Int =
        if (stored in 0 until presetCount) stored else EqualizerSettings.CUSTOM

    /** « 60 Hz », « 1 kHz », « 14 kHz » : fréquence centrale d'une bande, donnée en millihertz. */
    fun frequencyLabel(milliHertz: Int): String {
        val hertz = milliHertz / 1000
        return if (hertz >= 1000) {
            val kilo = hertz / 1000.0
            if (kilo == kilo.toInt().toDouble()) "${kilo.toInt()} kHz" else "%.1f kHz".format(kilo).replace('.', ',')
        } else {
            "$hertz Hz"
        }
    }

    /** « +3 dB », « −1,5 dB », « 0 dB » : niveau donné en millibels. */
    fun levelLabel(milliBels: Int): String {
        val db = milliBels / 100.0
        val text = if (db == db.toInt().toDouble()) "${kotlin.math.abs(db.toInt())}" else "%.1f".format(kotlin.math.abs(db)).replace('.', ',')
        return when {
            milliBels > 0 -> "+$text dB"
            milliBels < 0 -> "−$text dB"
            else -> "0 dB"
        }
    }
}
