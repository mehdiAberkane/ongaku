package fr.devsecopsforce.ongaku.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import fr.devsecopsforce.ongaku.R
import java.io.IOException

/** Format ligne ; garde ce nom pour ne pas casser les widgets déjà posés. */
open class OngakuWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetDisplay.render(context)
    }
}

class OngakuRectangleWidget : OngakuWidget()

class OngakuSquareWidget : OngakuWidget()

/** Dernier état connu, conservé pour afficher le morceau même quand le lecteur est arrêté. */
object WidgetDisplay {
    private const val PREFS = "ongaku_widget"
    private const val ARTWORK_SIZE_PX = 300

    private data class Format(
        val provider: Class<out OngakuWidget>,
        val layout: Int,
        val artworkPlaceholder: Int,
    )

    private val formats = listOf(
        Format(OngakuWidget::class.java, R.layout.widget_ongaku, R.drawable.ic_widget_note),
        Format(OngakuRectangleWidget::class.java, R.layout.widget_ongaku_rectangle, R.drawable.ic_widget_note),
        // La pochette du carré occupe tout le fond : une note géante gênerait les commandes.
        Format(OngakuSquareWidget::class.java, R.layout.widget_ongaku_square, R.drawable.widget_background),
    )

    fun save(context: Context, title: String?, artist: String?, artwork: Uri?, playing: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString("title", title)
            putString("artist", artist)
            putString("artwork", artwork?.toString())
            putBoolean("playing", playing)
        }
    }

    fun render(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val placed = formats.map { format ->
            format to manager.getAppWidgetIds(ComponentName(context, format.provider))
        }.filter { (_, ids) -> ids.isNotEmpty() }
        if (placed.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val artwork = prefs.getString("artwork", null)?.let { loadArtwork(context, it.toUri()) }
        placed.forEach { (format, ids) ->
            manager.updateAppWidget(ids, buildViews(context, format, artwork))
        }
    }

    private fun buildViews(context: Context, format: Format, artwork: Bitmap?): RemoteViews {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val views = RemoteViews(context.packageName, format.layout)
        views.setTextViewText(R.id.widget_title, prefs.getString("title", null) ?: "Ongaku")
        views.setTextViewText(R.id.widget_artist, prefs.getString("artist", null) ?: "Appuie sur lecture")
        views.setImageViewResource(
            R.id.widget_play,
            if (prefs.getBoolean("playing", false)) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_art, artwork)
        } else {
            views.setImageViewResource(R.id.widget_art, format.artworkPlaceholder)
        }

        views.setOnClickPendingIntent(R.id.widget_prev, action(context, WidgetActionReceiver.ACTION_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_play, action(context, WidgetActionReceiver.ACTION_PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.widget_next, action(context, WidgetActionReceiver.ACTION_NEXT))
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
            views.setOnClickPendingIntent(
                R.id.widget_open,
                PendingIntent.getActivity(
                    context,
                    0,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
        return views
    }

    private fun action(context: Context, name: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            name.hashCode(),
            Intent(context, WidgetActionReceiver::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun loadArtwork(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= ARTWORK_SIZE_PX) sample *= 2
            BitmapFactory.decodeFileDescriptor(
                descriptor.fileDescriptor,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )?.let { bitmap ->
                // Une image trop lourde ferait échouer la mise à jour du widget.
                if (bitmap.width > ARTWORK_SIZE_PX) {
                    bitmap.scale(ARTWORK_SIZE_PX, bitmap.height * ARTWORK_SIZE_PX / bitmap.width)
                } else {
                    bitmap
                }
            }
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }
}
