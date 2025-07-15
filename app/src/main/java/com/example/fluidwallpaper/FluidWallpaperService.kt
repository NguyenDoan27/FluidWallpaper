package com.example.fluidwallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.provider.CalendarContract
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import java.util.logging.Handler
import kotlin.random.Random

class FluidWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return FluidWallpaperEngine()
    }

    data class Ripple(var x: Float, var y: Float, var radius: Float, var alpha: Float)
    inner class FluidWallpaperEngine : Engine() {
        private var running = false
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        private val ripples = mutableListOf<Ripple>()
        private val handler = android.os.Handler(Looper.getMainLooper())
        private val drawRunner = Runnable { drawFrame() }


        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            ripples.add(Ripple(500f, 500f, 0f, 255f)) // Add initial ripple
            running = true
            handler.post(drawRunner)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event != null) {
                println("Touch event: action=${event.action}, x=${event.x}, y=${event.y}")
                if (event.action == MotionEvent.ACTION_DOWN) {
                    ripples.add(Ripple(event.x, event.y, 0f, 255f))
                    paint.color = Color.argb(255, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                    println("Added ripple at (${event.x}, ${event.y})")
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            running = visible
            if (visible) {
                handler.post(drawRunner)
            } else {
                handler.removeCallbacks(drawRunner)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            running = false
            handler.removeCallbacks(drawRunner)
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    canvas.drawColor(Color.DKGRAY) // Màu xám để kiểm tra
                    updateRipples()
                    drawRipples(canvas)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            if (running) {
                handler.postDelayed(drawRunner, 16)
            }
        }

        private fun updateRipples() {
            val iterator = ripples.iterator()
            while (iterator.hasNext()) {
                val ripple = iterator.next()
                ripple.radius += 10f
                ripple.alpha -= 3f
                if (ripple.alpha <= 0) iterator.remove()
            }
        }

        private fun drawRipples(canvas: Canvas) {
            println("Drawing ${ripples.size} ripples")
            for (ripple in ripples) {
                paint.alpha = ripple.alpha.toInt()
                canvas.drawCircle(ripple.x, ripple.y, ripple.radius, paint)
            }
        }
    }
}