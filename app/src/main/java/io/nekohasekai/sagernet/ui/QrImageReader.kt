package io.nekohasekai.sagernet.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import io.nekohasekai.sagernet.ktx.Logs
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * NekoBox Fast: reads a link out of a QR code stored in a **picture**.
 *
 * The camera based scanner was removed (it required the CAMERA permission), but
 * zxing's *decoder* is pure Java and needs no hardware at all - so a screenshot,
 * a photo of a QR poster, or any saved image can be imported instead.
 *
 * Returns the decoded text, or null when no QR code could be read.
 */
object QrImageReader {

    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        // QR codes are usually dense; give the detector the stronger mode
        DecodeHintType.TRY_HARDER to true
    )

    fun readText(context: Context, uri: Uri): String? {
        val bitmap = loadBitmap(context, uri) ?: return null
        try {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null

            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val reader = MultiFormatReader().apply { setHints(hints) }
            return try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            } catch (e: Exception) {
                // NotFoundException / ChecksumException / FormatException -> no usable code
                Logs.w(e)
                null
            } finally {
                reader.reset()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val raw = try {
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Logs.w(e)
            null
        } ?: return null

        val degrees = readRotation(context, uri)
        if (degrees == 0f) return raw

        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                .also { if (it != raw) raw.recycle() }
        } catch (e: OutOfMemoryError) {
            raw
        } catch (e: Exception) {
            Logs.w(e)
            raw
        }
    }

    private fun readRotation(context: Context, uri: Uri): Float {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }
}