package com.example.fluidwallpaper

import android.R.attr.height
import android.R.attr.width
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.SurfaceView
import kotlin.random.Random

class FluidSurfaceView(context: Context) : SurfaceView(context), Runnable {
    private var thread: Thread? = null
    private var isRunning = false
    private val paint = Paint().apply {
        isAntiAlias = true
        color = Color.BLUE
    }
    private val fluidParticles = mutableListOf<FluidParticle>()

    init {
        // Khởi tạo các hạt chất lỏng
        for (i in 0 until 300) {
            fluidParticles.add(FluidParticle())
        }
    }

    override fun run() {
        while (isRunning) {
            drawFluid()
        }
    }

    private fun drawFluid() {
        if (holder.surface.isValid) {
            val canvas = holder.lockCanvas()
            canvas.drawColor(Color.WHITE) // Nền đen

            // Vẽ các hạt chất lỏng
            for (particle in fluidParticles) {
                particle.update()
                canvas.drawCircle(particle.x, particle.y, particle.size, paint)
            }

            holder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                // Nói tốc hạt chất lỏng
                fluidParticles.add(FluidParticle().apply {
                    x = event.x
                    y = event.y
                })
                drawFluid()
            }
        }
        return true
    }

    fun start() {
        isRunning = true
        thread = Thread(this)
        thread?.start()
    }

    fun stop() {
        isRunning = false
        thread?.join()
    }

    inner class FluidParticle {
        var x = Random.nextFloat() * width
        var y = Random.nextFloat() * height
        var size = Random.nextFloat() * 5 + 5 // Kích thước hạt chất lỏng
        private var speedX = Random.nextFloat() * 2 - 1 // Tốc độ di chuyển ngang
        private var speedY = Random.nextFloat() * 2 - 1 // Tốc độ di chuyển dọc

        fun update() {
            x += speedX
            y += speedY

            // Đảo chiều nếu ra ngoài biên
            if (x < 0 || x > width.toFloat()) speedX *= -1
            if (y < 0 || y > height.toFloat()) speedY *= -1
        }
    }
}