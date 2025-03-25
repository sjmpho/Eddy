package com.example.eddy.servicesAndModels

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class DrawingView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL // Or Paint.Style.STROKE for outline
        strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw a rectangle (left, top, right, bottom)
        canvas.drawRect(100f, 200f, 500f, 600f, paint)
    }
}