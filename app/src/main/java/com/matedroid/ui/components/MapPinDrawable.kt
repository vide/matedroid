package com.matedroid.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.content.res.Resources

/**
 * Creates a slick pin-needle map marker: a filled circle head with a thin needle pointing down.
 */
fun createPinMarkerDrawable(resources: Resources, headColorArgb: Int): Drawable {
    val density = resources.displayMetrics.density
    val width = (24 * density).toInt()
    val height = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val headRadius = 8 * density
    val headCy = headRadius + 1 * density

    // Needle
    val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.FILL
    }
    val needlePath = Path().apply {
        moveTo(cx - 2.5f * density, headCy + headRadius * 0.5f)
        lineTo(cx, height.toFloat() - 1 * density)
        lineTo(cx + 2.5f * density, headCy + headRadius * 0.5f)
        close()
    }
    canvas.drawPath(needlePath, needlePaint)

    // Head circle
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = headColorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, headCy, headRadius, headPaint)

    // Subtle border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    canvas.drawCircle(cx, headCy, headRadius, borderPaint)

    return BitmapDrawable(resources, bitmap)
}

/**
 * Creates a map marker with a bolt/zap icon inside the circle head,
 * for charge stop locations. The bolt is drawn in white on the colored head.
 */
fun createZapMarkerDrawable(resources: Resources, headColorArgb: Int): Drawable {
    val density = resources.displayMetrics.density
    val width = (24 * density).toInt()
    val height = (36 * density).toInt()
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val cx = width / 2f
    val headRadius = 8 * density
    val headCy = headRadius + 1 * density

    // Needle
    val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF444444.toInt()
        style = Paint.Style.FILL
    }
    val needlePath = Path().apply {
        moveTo(cx - 2.5f * density, headCy + headRadius * 0.5f)
        lineTo(cx, height.toFloat() - 1 * density)
        lineTo(cx + 2.5f * density, headCy + headRadius * 0.5f)
        close()
    }
    canvas.drawPath(needlePath, needlePaint)

    // Head circle
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = headColorArgb
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, headCy, headRadius, headPaint)

    // Subtle border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x44000000
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    canvas.drawCircle(cx, headCy, headRadius, borderPaint)

    // Bolt icon inside the head circle
    val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val s = headRadius * 0.55f
    val boltPath = Path().apply {
        moveTo(cx + 0.1f * s, headCy - 1.0f * s)
        lineTo(cx - 0.6f * s, headCy + 0.1f * s)
        lineTo(cx - 0.05f * s, headCy + 0.1f * s)
        lineTo(cx - 0.1f * s, headCy + 1.0f * s)
        lineTo(cx + 0.6f * s, headCy - 0.1f * s)
        lineTo(cx + 0.05f * s, headCy - 0.1f * s)
        close()
    }
    canvas.drawPath(boltPath, boltPaint)

    return BitmapDrawable(resources, bitmap)
}
