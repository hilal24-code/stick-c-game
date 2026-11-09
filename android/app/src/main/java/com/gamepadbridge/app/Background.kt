package com.gamepadbridge.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import kotlin.math.max

/** Menyimpan gambar latar pilihan pengguna di penyimpanan internal aplikasi. */
object BackgroundStore {
    private const val FILE_NAME = "background.jpg"
    private const val MAX_SIDE = 1920

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun save(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.contentResolver

            // 1) Ukuran asli gambar
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            // 2) Decode dengan pengecilan kasar agar hemat memori
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE * 2 || bounds.outHeight / sample > MAX_SIDE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return false

            // 3) Koreksi putaran dari data EXIF (foto kamera sering tersimpan miring)
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            // 4) Perkecil bila perlu lalu simpan
            val scale = minOf(1f, MAX_SIDE.toFloat() / max(bmp.width, bmp.height))
            val out = if (degrees == 0f && scale >= 1f) {
                bmp
            } else {
                val m = Matrix().apply {
                    postRotate(degrees)
                    postScale(scale, scale)
                }
                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            file(context).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            true
        } catch (e: Exception) {
            false
        } catch (e: OutOfMemoryError) {
            false
        }
    }

    fun load(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun delete(context: Context) {
        file(context).delete()
    }
}

/** Latar aplikasi: gradasi gelap + gambar pilihan pengguna dengan opasitas yang bisa diatur. */
@Composable
fun AppBackground(image: ImageBitmap?, opacity: Float, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF181B22), Color(0xFF0A0B0E))))
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = opacity,
                modifier = Modifier.fillMaxSize(),
            )
        }
        content()
    }
}