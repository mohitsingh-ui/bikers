package com.ridesync.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a QR code for a join deep link using zxing's bit matrix, drawn on a
 * Compose Canvas (no bitmap allocation). White quiet-zone padding is included
 * so scanners lock on reliably.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 220.dp,
) {
    val matrix = remember(content) { encode(content) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        if (matrix != null) {
            val dim = matrix.first
            val bits = matrix.second
            Canvas(Modifier.size(size)) {
                val cell = this.size.width / dim
                for (y in 0 until dim) {
                    for (x in 0 until dim) {
                        if (bits[y * dim + x]) {
                            drawRect(
                                color = Color(0xFF0D0F13),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.6f, cell + 0.6f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun encode(content: String): Pair<Int, BooleanArray>? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints)
    val w = matrix.width
    val bits = BooleanArray(w * w)
    for (y in 0 until w) for (x in 0 until w) bits[y * w + x] = matrix.get(x, y)
    w to bits
} catch (_: Exception) {
    null
}
