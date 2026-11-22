package fr.devsecopsforce.ongaku.playback

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import fr.devsecopsforce.ongaku.data.EqualizerLevels
import fr.devsecopsforce.ongaku.data.EqualizerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce que l'égaliseur du téléphone propose, et son état actuel ; lu par l'écran Égaliseur. */
data class EqualizerInfo(
    /** Session audio commune aux deux lecteurs du fondu : l'égaliseur du système s'y attache. */
    val audioSessionId: Int,
    /** null si le téléphone refuse l'égaliseur (effet absent ou occupé). */
    val bands: List<Band>?,
    val minLevel: Int,
    val maxLevel: Int,
    val presets: List<String>,
    /** Niveaux appliqués, en millibels : ceux du préréglage choisi ou ceux réglés à la main. */
    val levels: List<Int>,
    val bassSupported: Boolean,
) {
    /** [centerMilliHertz] : fréquence centrale de la bande. */
    data class Band(val centerMilliHertz: Int)
}

/**
 * Égaliseur et amplification des basses, attachés à la session audio que partagent le lecteur actif
 * et le lecteur de relève du fondu enchaîné : l'effet s'applique aux deux et survit à l'échange.
 * Les réglages viennent de [EqualizerSettings], relus à chaque changement.
 */
class AudioEffects(private val context: Context, private val audioSessionId: Int) {
    private val settings = EqualizerSettings(context)
    private val equalizer: Equalizer? = create { Equalizer(0, audioSessionId) }
    private val bassBoost: BassBoost? = create { BassBoost(0, audioSessionId) }?.let { bass ->
        if (bass.strengthSupported) {
            bass
        } else {
            bass.release()
            null
        }
    }

    // Android ne garde qu'une référence faible vers cet écouteur : il doit rester un champ.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> apply() }

    init {
        settings.prefs.registerOnSharedPreferenceChangeListener(listener)
        apply()
        // L'égaliseur du système (s'il y en a un) peut s'attacher à cette session.
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION))
    }

    private fun apply() {
        val enabled = settings.enabled
        val levels = equalizer?.let { eq -> applyEqualizer(eq, enabled) }.orEmpty()
        bassBoost?.let { bass ->
            safely {
                val strength = EqualizerLevels.bass(settings.bassStrength)
                bass.setStrength(strength.toShort())
                bass.enabled = enabled && strength > 0
            }
        }
        _info.value = describe(levels)
    }

    /** Applique le préréglage ou les niveaux à la main ; rend les niveaux en place. */
    private fun applyEqualizer(eq: Equalizer, enabled: Boolean): List<Int> = safely {
        val range = eq.bandLevelRange
        val preset = EqualizerLevels.preset(settings.preset, eq.numberOfPresets.toInt())
        if (preset != EqualizerSettings.CUSTOM) {
            eq.usePreset(preset.toShort())
        } else {
            EqualizerLevels.parse(settings.bandLevels, eq.numberOfBands.toInt(), range[0].toInt(), range[1].toInt())
                .forEachIndexed { band, level -> eq.setBandLevel(band.toShort(), level.toShort()) }
        }
        eq.enabled = enabled
        List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
    }.orEmpty()

    private fun describe(levels: List<Int>): EqualizerInfo {
        val eq = equalizer
        val bands = eq?.let { safely { List(it.numberOfBands.toInt()) { band -> EqualizerInfo.Band(it.getCenterFreq(band.toShort())) } } }
        val range = eq?.let { safely { it.bandLevelRange } }
        return EqualizerInfo(
            audioSessionId = audioSessionId,
            bands = bands,
            minLevel = range?.get(0)?.toInt() ?: 0,
            maxLevel = range?.get(1)?.toInt() ?: 0,
            presets = eq?.let { safely { List(it.numberOfPresets.toInt()) { p -> EqualizerLevels.presetName(it.getPresetName(p.toShort())) } } }.orEmpty(),
            levels = levels,
            bassSupported = bassBoost != null,
        )
    }

    fun release() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(listener)
        context.sendBroadcast(sessionIntent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION))
        equalizer?.release()
        bassBoost?.release()
        _info.value = null
    }

    private fun sessionIntent(action: String) = Intent(action)
        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

    companion object {
        private val _info = MutableStateFlow<EqualizerInfo?>(null)

        /** null tant que le service de lecture n'a pas démarré. */
        val info: StateFlow<EqualizerInfo?> = _info.asStateFlow()

        /**
         * Effet refusé par le téléphone (absent, ou pris par une autre application avec une
         * priorité plus haute) : Ongaku joue sans, au lieu de planter.
         */
        private fun <T> create(block: () -> T): T? = try {
            block()
        } catch (e: RuntimeException) {
            null
        }

        private fun <T> safely(block: () -> T): T? = try {
            block()
        } catch (e: RuntimeException) {
            null
        }
    }
}
