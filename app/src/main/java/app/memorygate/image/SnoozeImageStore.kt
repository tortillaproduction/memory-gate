package app.memorygate.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * スヌーズ用ゲートに表示する画像の保存先（アプリ内部ストレージ `files/snooze_images/`）。
 * Photo Picker で選んだ画像を長辺 [MAX_LONG_SIDE]px 程度に縮小してコピーする。
 */
class SnoozeImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "snooze_images")

    /** 選んだ画像を縮小してコピーし、保存先のパスを返す。読み込めなければ null */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(uri) ?: return@withContext null
            dir.mkdirs()
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "画像を読み込めませんでした", e)
            null
        }
    }

    /** 画像ファイルを削除する。このストアの管理下のファイル以外は削除しない */
    suspend fun delete(path: String?) = withContext(Dispatchers.IO) {
        if (path == null) return@withContext
        val file = File(path)
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
    }

    private fun decodeScaled(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder は EXIF の回転も反映する。info.size も回転後の幅・高さなので、縦横比を保って縮小できる
            val source = ImageDecoder.createSource(appContext.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (w, h) = ImageScaling.scaledSize(info.size.width, info.size.height, MAX_LONG_SIDE)
                decoder.setTargetSize(w, h)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageScaling.sampleSize(bounds.outWidth, bounds.outHeight, MAX_LONG_SIDE)
            }
            val sampled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            val (w, h) = ImageScaling.scaledSize(sampled.width, sampled.height, MAX_LONG_SIDE)
            val scaled = if (w == sampled.width && h == sampled.height) {
                sampled
            } else {
                sampled.scale(w, h).also { if (it !== sampled) sampled.recycle() }
            }
            // BitmapFactory は EXIF の回転を反映しないので、ここで回転・反転する
            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            applyExifOrientation(scaled, orientation)
        }

    /** EXIF の向きに合わせて回転・反転する（縦横比は保つ。90°・270° の回転では幅と高さが入れ替わる） */
    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply { postRotate(270f); postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        const val MAX_LONG_SIDE = 1024
        private const val JPEG_QUALITY = 90
        private const val TAG = "MemoryGate"
    }
}

/** 画像の縮小サイズの計算（ユニットテスト用に分離） */
object ImageScaling {

    /** 長辺が [maxLongSide] 以下になるよう縦横比を保って縮小したサイズ。小さい画像は拡大しない */
    fun scaledSize(width: Int, height: Int, maxLongSide: Int): Pair<Int, Int> {
        val longSide = maxOf(width, height)
        if (longSide <= maxLongSide) return width to height
        val scale = maxLongSide.toDouble() / longSide
        return maxOf(1, Math.round(width * scale).toInt()) to maxOf(1, Math.round(height * scale).toInt())
    }

    /** BitmapFactory の inSampleSize（2 の累乗で、縮小後も長辺が [maxLongSide] 以上残る最大値） */
    fun sampleSize(width: Int, height: Int, maxLongSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxLongSide) sample *= 2
        return sample
    }
}

/**
 * 編集中に選んだ画像ファイルの後始末。どのファイルを削除するかを決める（ユニットテスト用に分離）。
 *
 * @param original 編集前に保存されていた画像
 * @param imported この編集中に取り込んだ画像（選び直した分も含む）
 */
class SnoozeImageSession(private val original: String?) {
    private val imported = mutableListOf<String>()

    fun onImported(path: String) {
        imported += path
    }

    /** 保存したときに削除するファイル: 使わなくなった元の画像と、選び直して使わなくなった画像 */
    fun filesToDeleteOnSave(saved: String?): List<String> =
        (listOfNotNull(original) + imported).filter { it != saved }.distinct()

    /** 保存せずに画面を閉じたときに削除するファイル: この編集中に取り込んだ画像 */
    fun filesToDeleteOnDiscard(): List<String> = imported.filter { it != original }.distinct()

    /** 誘導先を削除したときに削除するファイル */
    fun filesToDeleteOnTargetDeleted(): List<String> = (listOfNotNull(original) + imported).distinct()
}
