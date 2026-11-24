package com.example.printing

import android.graphics.Bitmap
import android.graphics.Color
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrCodeHelper {

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val safeSize = sizePx.coerceIn(96, 1024)
        val payload = content.ifBlank { "AA-CRAVE-LOYALTY" }
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, safeSize, safeSize, hints)
        val pixels = IntArray(safeSize * safeSize)
        for (y in 0 until safeSize) {
            val offset = y * safeSize
            for (x in 0 until safeSize) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, safeSize, 0, 0, safeSize, safeSize)
        }
    }

    fun decodeQrFromImageProxy(imageProxy: ImageProxy): String? {
        return try {
            val plane = imageProxy.planes.firstOrNull() ?: return null
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height
            val yBytes = if (rowStride == width) {
                ByteArray(buffer.remaining()).also { buffer.get(it) }
            } else {
                ByteArray(width * height).also { out ->
                    val rowBuffer = ByteArray(rowStride)
                    for (r in 0 until height) {
                        val toRead = minOf(rowStride, buffer.remaining())
                        buffer.get(rowBuffer, 0, toRead)
                        System.arraycopy(rowBuffer, 0, out, r * width, width)
                    }
                }
            }
            decodeQrFromYuvBytes(yBytes, width, height)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeQrFromYuvBytes(yBytes: ByteArray, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || yBytes.size < width * height) return null
        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        return try {
            MultiFormatReader().decode(binaryBitmap, hints).text
        } catch (_: NotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
