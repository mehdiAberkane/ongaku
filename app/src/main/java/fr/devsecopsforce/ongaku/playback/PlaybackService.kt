package fr.devsecopsforce.ongaku.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import fr.devsecopsforce.ongaku.LockScreenActivity
import fr.devsecopsforce.ongaku.data.PlayCounts
import fr.devsecopsforce.ongaku.data.PlaybackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private var crossfade: CrossfadeController? = null
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
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(settings.pauseOnNoisy)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val launchIntent: Intent? = packageManager.getLaunchIntentForPackage(packageName)
        val builder = MediaSession.Builder(this, player)
            .setCallback(ResumptionCallback(this, scope))
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
        crossfade = CrossfadeController(this, player, settings, PlayCounts(this))
        stateKeeper = PlaybackStateKeeper(this, player, scope)
        settings.prefs.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

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
