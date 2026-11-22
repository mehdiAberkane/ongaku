// Media3 marque certaines API comme instables (susceptibles de changer d'une version à l'autre).
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package fr.devsecopsforce.ongaku.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import fr.devsecopsforce.ongaku.LockScreenActivity
import fr.devsecopsforce.ongaku.data.ListeningHistory
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var crossfade: CrossfadeController? = null
    private var sleepTimer: SleepTimer? = null
    private var effects: AudioEffects? = null

    /**
     * Session audio partagée par le lecteur actif et le lecteur de relève : l'égaliseur, attaché à
     * cette session, s'applique aux deux et survit à leur échange.
     */
    private var audioSessionId = C.AUDIO_SESSION_ID_UNSET
    private val autoplay by lazy { AutoplayGuard(this, scope) { session?.player } }
    private var stateKeeper: PlaybackStateKeeper? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var settings: PlaybackSettings

    // Android ne garde qu'une référence faible vers cet écouteur : il doit rester un champ.
    private val settingsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PlaybackSettings.KEY_PAUSE_ON_NOISY) {
                (session?.player as? ExoPlayer)
                    ?.setHandleAudioBecomingNoisy(settings.pauseOnNoisy)
            }
            if (key == PlaybackSettings.KEY_CROSSFADE) crossfade?.onSettingsChanged()
        }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (settings.lockScreenPlayer && Settings.canDrawOverlays(context) &&
                session?.player?.isPlaying == true
            ) {
                startActivity(
                    Intent(context, LockScreenActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = PlaybackSettings(this)
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Jamais la session 0 : elle désigne la sortie de tout le téléphone, pas Ongaku seul.
        audioSessionId = getSystemService(AudioManager::class.java).generateAudioSessionId()
            .takeIf { it > 0 } ?: C.AUDIO_SESSION_ID_UNSET
        val player = buildPlayer(isActive = true)
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) effects = AudioEffects(this, audioSessionId)

        val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
        val builder = MediaLibrarySession.Builder(this, player, ResumptionCallback(this, scope, autoplay) { sleepTimer })
        if (launchIntent != null) {
            builder.setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        session = builder.build()
        stateKeeper = PlaybackStateKeeper(this, player, scope)
        crossfade = CrossfadeController(
            player,
            settings,
            PlayCounts(this),
            ListeningHistory.get(this),
            createPlayer = { buildPlayer(isActive = false) },
            onPlayerSwapped = { next ->
                session?.player = next
                stateKeeper?.switchTo(next)
                sleepTimer?.onPlayerSwapped(next)
            },
        )
        sleepTimer = SleepTimer(settings) { crossfade }
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    /**
     * Le lecteur actif gère le focus audio et le débranchement des écouteurs ; un lecteur de relève
     * pour le fondu enchaîné ne les prend qu'en devenant actif.
     */
    private fun buildPlayer(isActive: Boolean): ExoPlayer =
        ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ isActive,
            )
            .setHandleAudioBecomingNoisy(isActive && settings.pauseOnNoisy)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) it.audioSessionId = audioSessionId }

    /** Voir [SessionAccess] : les autres applications reçoivent null et ne peuvent pas se connecter. */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        val pkg = controllerInfo.packageName
        val systemApp = pkg in SessionAccess.CAR_PACKAGES && isSystemApp(pkg)
        return session.takeIf { SessionAccess.allowed(pkg, packageName, controllerInfo.isTrusted, systemApp) }
    }

    /** Installée avec le système (éventuellement mise à jour depuis) : voir [SessionAccess.CAR_PACKAGES]. */
    private fun isSystemApp(pkg: String): Boolean = try {
        val flags = packageManager.getApplicationInfo(pkg, 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        settings.prefs.unregisterOnSharedPreferenceChangeListener(settingsListener)
        stateKeeper?.release()
        stateKeeper = null
        autoplay.stop()
        sleepTimer?.release()
        sleepTimer = null
        effects?.release()
        effects = null
        scope.cancel()
        crossfade?.release()
        crossfade = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
