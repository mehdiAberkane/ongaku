package fr.devsecopsforce.ongaku.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Image de fond choisie par l'utilisateur, ramenée à la taille de l'écran avant d'être gardée. */
object BackgroundImage {
    private const val JPEG_QUALITY = 90

    /**
     * Décode l'image [source], la redresse selon son orientation (photos d'appareil), la réduit pour
     * couvrir l'écran ([ImportLimits.fitSize]) et la réencode. null si ce n'est pas une image.
     */
    fun prepare(context: Context, source: Uri): ByteArray? {
        val resolver = context.contentResolver
        fun open() = resolver.openInputStream(source) ?: throw IOException("image illisible")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val metrics = context.resources.displayMetrics
        val maxSide = maxOf(metrics.widthPixels, metrics.heightPixels)
        val options = BitmapFactory.Options().apply {
            inSampleSize = ImportLimits.sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val (width, height) = ImportLimits.fitSize(decoded.width, decoded.height, maxSide)

        val matrix = orientation(open())
        if (width != decoded.width || height != decoded.height) {
            matrix.preScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        }
        val image = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }

        val output = ByteArrayOutputStream()
        // Le PNG garde la transparence ; une photo prend beaucoup moins de place en JPEG.
        val format = if (image.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        if (!image.compress(format, JPEG_QUALITY, output)) return null
        return output.toByteArray()
    }

    /** Rotation ou miroir à appliquer selon l'orientation enregistrée par l'appareil photo. */
    private fun orientation(input: java.io.InputStream): Matrix {
        val value = try {
            input.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } catch (e: IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return Matrix().apply {
            when (value) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
    }
}
