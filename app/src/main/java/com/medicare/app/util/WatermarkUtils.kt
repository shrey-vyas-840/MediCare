package com.medicare.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.util.Locale

object WatermarkUtils {
    fun applyWatermark(
        originalBitmap: Bitmap,
        medicineName: String,
        scheduledTime: String,
        captureTimeMillis: Long,
        submissionTimeMillis: Long?,
        dateStr: String,
        patientName: String = "Patient"
    ): Bitmap {
        // Create mutable copy of original bitmap (keeping original unchanged)
        val watermarked = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarked)
        val width = watermarked.width
        val height = watermarked.height

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = (width / 35f).coerceAtLeast(24f)
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#99000000") // Semi-transparent black background
            style = Paint.Style.FILL
        }

        val lines = PhotoProofFormatter.lines(patientName, medicineName, dateStr, scheduledTime, captureTimeMillis, submissionTimeMillis)
            .flatMap { line -> wrapLine(line, paint, (width - 2 * (width * 0.025f)).coerceAtLeast(80f)) }

        val lineHeight = paint.textSize * 1.4f
        val padding = (width * 0.025f).coerceAtLeast(12f)
        val boxHeight = (lines.size * lineHeight) + (padding * 2)
        val boxWidth = width.toFloat()

        val left = 0f
        val top = height - boxHeight

        // Draw semi-transparent banner at the bottom
        canvas.drawRect(left, top, left + boxWidth, top + boxHeight, backgroundPaint)

        // Draw text lines
        var currentY = top + padding + paint.textSize
        for (line in lines) {
            canvas.drawText(line, padding, currentY, paint)
            currentY += lineHeight
        }

        return watermarked
    }

    private fun wrapLine(value: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(value) <= maxWidth) return listOf(value)
        val words = value.split(' ')
        val result = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(next) > maxWidth) {
                result += current
                current = word
            } else current = next
        }
        if (current.isNotEmpty()) result += current
        return result
    }
}
