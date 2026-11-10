package com.medicare.app.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WatermarkUtils {
    fun applyWatermark(
        originalBitmap: Bitmap,
        medicineName: String,
        scheduledTime: String,
        captureTimeMillis: Long,
        submissionTimeMillis: Long,
        dateStr: String
    ): Bitmap {
        // Create mutable copy of original bitmap (keeping original unchanged)
        val watermarked = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(watermarked)
        val width = watermarked.width
        val height = watermarked.height

        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val captureStr = timeFormat.format(Date(captureTimeMillis))
        val submissionStr = timeFormat.format(Date(submissionTimeMillis))

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

        val lines = listOf(
            "MediCare Verified Dose",
            "Medicine: $medicineName",
            "Scheduled: $scheduledTime on $dateStr",
            "Captured: $captureStr",
            "Submitted: $submissionStr"
        )

        val lineHeight = paint.textSize * 1.4f
        val padding = lineHeight * 0.6f
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
}
