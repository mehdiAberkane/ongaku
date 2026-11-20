package fr.devsecopsforce.ongaku.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Écran de test : 1080 × 2400 ; le grand côté (2400) borne le petit côté de l'image gardée. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BackgroundImageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()

    private fun photo(width: Int, height: Int, orientation: Int? = null): Uri {
        val file = folder.newFile("photo.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (orientation != null) {
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        return Uri.fromFile(file)
    }

    private fun decode(bytes: ByteArray) = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    @Test
    fun `une grande photo est réduite à la taille de l'écran`() {
        val maxSide = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
        val result = decode(BackgroundImage.prepare(context, photo(6000, 4000))!!)

        assertEquals(maxSide, minOf(result.width, result.height))
        assertEquals(6000.0 / 4000, result.width.toDouble() / result.height, 0.01)
    }

    @Test
    fun `une petite image garde sa taille`() {
        val result = decode(BackgroundImage.prepare(context, photo(800, 600))!!)

        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `une photo prise de côté est redressée`() {
        val result = decode(BackgroundImage.prepare(context, photo(800, 600, ExifInterface.ORIENTATION_ROTATE_90))!!)

        assertEquals(600, result.width)
        assertEquals(800, result.height)
    }

    @Test
    fun `le résultat est une image lisible`() {
        val bytes = BackgroundImage.prepare(context, photo(1200, 900))
        assertNotNull(bytes)
        assertTrue(bytes!!.size < 1200 * 900 * 4)
        assertNotNull(decode(bytes))
        assertTrue(File(folder.root, "photo.jpg").exists())
    }
}
