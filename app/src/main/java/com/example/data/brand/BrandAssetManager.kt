package com.example.data.brand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val Context.brandAssetDataStore by preferencesDataStore(name = "aa_crave_brand_assets")

enum class LogoFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg"),
    WEBP("webp", "image/webp"),
    SVG("svg", "image/svg+xml");

    companion object {
        fun fromMimeOrName(mimeType: String?, fileName: String?): LogoFormat? {
            return when {
                mimeType?.contains("png", ignoreCase = true) == true ||
                    fileName?.endsWith(".png", ignoreCase = true) == true -> PNG
                mimeType?.contains("jpeg", ignoreCase = true) == true ||
                    mimeType?.contains("jpg", ignoreCase = true) == true ||
                    fileName?.endsWith(".jpg", ignoreCase = true) == true ||
                    fileName?.endsWith(".jpeg", ignoreCase = true) == true -> JPG
                mimeType?.contains("webp", ignoreCase = true) == true ||
                    fileName?.endsWith(".webp", ignoreCase = true) == true -> WEBP
                mimeType?.contains("svg", ignoreCase = true) == true ||
                    fileName?.endsWith(".svg", ignoreCase = true) == true -> SVG
                else -> null
            }
        }
    }
}

data class BrandLogoState(
    val hasOwnerLogo: Boolean,
    val filePath: String? = null,
    val format: LogoFormat? = null,
    val originalFileName: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null
)

class BrandAssetManager(private val context: Context) {

    private val logoPathKey = stringPreferencesKey("owner_logo_path")
    private val logoFormatKey = stringPreferencesKey("owner_logo_format")
    private val logoNameKey = stringPreferencesKey("owner_logo_name")
    private val logoDimsKey = stringPreferencesKey("owner_logo_dims")

    val logoState: Flow<BrandLogoState> = context.brandAssetDataStore.data.map { prefs ->
        val path = prefs[logoPathKey]
        val fileExists = !path.isNullOrBlank() && File(path).exists()
        if (!fileExists) {
            BrandLogoState(hasOwnerLogo = false)
        } else {
            val dims = prefs[logoDimsKey]?.split("x")
            val format = prefs[logoFormatKey]?.let { name ->
                LogoFormat.entries.firstOrNull { it.name == name }
            }
            BrandLogoState(
                hasOwnerLogo = true,
                filePath = path,
                format = format,
                originalFileName = prefs[logoNameKey],
                widthPx = dims?.getOrNull(0)?.toIntOrNull(),
                heightPx = dims?.getOrNull(1)?.toIntOrNull()
            )
        }
    }

    suspend fun importOwnerLogo(uri: Uri): Result<BrandLogoState> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val displayName = queryFileName(uri) ?: "aa_crave_logo"
            val format = LogoFormat.fromMimeOrName(mimeType, displayName)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unsupported logo format. Please select PNG, JPG, WEBP, or SVG.")
                )

            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not read selected logo file."))

            if (bytes.isEmpty() || bytes.size > MAX_LOGO_BYTES) {
                return@withContext Result.failure(
                    IllegalArgumentException("Logo file must be between 1 byte and 5 MB.")
                )
            }

            var widthPx: Int? = null
            var heightPx: Int? = null

            if (format == LogoFormat.SVG) {
                val text = bytes.toString(Charsets.UTF_8)
                if (!text.contains("<svg", ignoreCase = true)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid SVG file: missing <svg> root element.")
                    )
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Selected image could not be decoded.")
                    )
                }
                if (bounds.outWidth < MIN_DIM_PX || bounds.outHeight < MIN_DIM_PX ||
                    bounds.outWidth > MAX_DIM_PX || bounds.outHeight > MAX_DIM_PX
                ) {
                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Logo dimensions (${bounds.outWidth}x${bounds.outHeight}) must be between ${MIN_DIM_PX}px and ${MAX_DIM_PX}px."
                        )
                    )
                }
                widthPx = bounds.outWidth
                heightPx = bounds.outHeight
            }

            val assetsDir = File(context.filesDir, "brand_assets").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            // Preserve previous uploaded originals by saving to a unique versioned filename
            val targetFile = File(assetsDir, "aa_crave_owner_${timestamp}.${format.extension}")
            FileOutputStream(targetFile).use { out ->
                out.write(bytes)
                out.flush()
            }

            context.brandAssetDataStore.edit { prefs ->
                prefs[logoPathKey] = targetFile.absolutePath
                prefs[logoFormatKey] = format.name
                prefs[logoNameKey] = displayName
                if (widthPx != null && heightPx != null) {
                    prefs[logoDimsKey] = "${widthPx}x${heightPx}"
                } else {
                    prefs.remove(logoDimsKey)
                }
            }

            Result.success(
                BrandLogoState(
                    hasOwnerLogo = true,
                    filePath = targetFile.absolutePath,
                    format = format,
                    originalFileName = displayName,
                    widthPx = widthPx,
                    heightPx = heightPx
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearToTemporaryPlaceholder() {
        context.brandAssetDataStore.edit { prefs ->
            prefs.remove(logoPathKey)
            prefs.remove(logoFormatKey)
            prefs.remove(logoNameKey)
            prefs.remove(logoDimsKey)
        }
    }

    fun decodeRasterBitmap(logoState: BrandLogoState, maxTargetPx: Int = 512): Bitmap? {
        if (!logoState.hasOwnerLogo || logoState.filePath.isNullOrBlank()) return null
        if (logoState.format == LogoFormat.SVG) return null
        val file = File(logoState.filePath)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > maxTargetPx * 2 ||
            bounds.outHeight / sampleSize > maxTargetPx * 2
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    fun readSvgContent(logoState: BrandLogoState): String? {
        if (!logoState.hasOwnerLogo || logoState.format != LogoFormat.SVG || logoState.filePath.isNullOrBlank()) {
            return null
        }
        val file = File(logoState.filePath)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    private fun queryFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    companion object {
        private const val MAX_LOGO_BYTES = 5 * 1024 * 1024
        private const val MIN_DIM_PX = 32
        private const val MAX_DIM_PX = 4096
    }
}
