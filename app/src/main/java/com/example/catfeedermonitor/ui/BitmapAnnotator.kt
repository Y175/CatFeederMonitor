package com.example.catfeedermonitor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.catfeedermonitor.logic.DetectionResult

object BitmapAnnotator {
    fun drawDetections(bitmap: Bitmap, detections: List<DetectionResult>): Bitmap {
        // Create a mutable copy of the bitmap to draw on
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 8f // Thicker for high-res photos
            isAntiAlias = true
        }
        
        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 60f
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        
        val textBgPaint = Paint().apply {
            color = Color.BLACK
            alpha = 160
            style = Paint.Style.FILL
        }

        for (result in detections) {
            val box = result.boundingBox
            
            // Draw Bounding Box
            strokePaint.color = Color.RED

            // Draw Bounding Box
            canvas.drawRect(box, strokePaint)

            // Prepare Text
            val text = "${result.label} ${(result.score * 100).toInt()}%"
            val textWidth = textPaint.measureText(text)
            val textHeight = textPaint.textSize
            val padding = 10f
            
            // Calculate Text Background Rect
            // Position it above the box, or inside if near top edge
            var bgLeft = box.left
            var bgTop = box.top - textHeight - padding * 2
            var bgRight = box.left + textWidth + padding * 2
            var bgBottom = box.top
            
            if (bgTop < 0) {
                // If off-screen at top, move inside the box
                bgTop = box.top
                bgBottom = box.top + textHeight + padding * 2
            }

            // Ensure text doesn't go off-screen horizontally
            if (bgLeft < 0) {
                val diff = -bgLeft
                bgLeft += diff
                bgRight += diff
            }
            if (bgRight > canvas.width) {
                val diff = bgRight - canvas.width
                bgLeft -= diff
                bgRight -= diff
            }

            val textBgRect = RectF(bgLeft, bgTop, bgRight, bgBottom)
            
            // Draw Text Background
            canvas.drawRect(textBgRect, textBgPaint)

            // Draw Text
            canvas.drawText(text, bgLeft + padding, bgBottom - padding, textPaint)
        }
        
        return mutableBitmap
    }
}
