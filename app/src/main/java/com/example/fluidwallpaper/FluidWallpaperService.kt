package com.example.fluidwallpaper

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder

class FluidWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine {
        return FluidWallpaperEngine()
    }

    enum class EffectType {
        RIPPLE, SPARKLE, LIGHTNING, COMET, FIREWORK, SPINNING_CIRCLE, SCATTER_CONVERGE, VORTEX, WAVE, GRID_WAVE, FIRE
    }

    inner class FluidWallpaperEngine : Engine() {
        private var running = false
        private val handler = Handler(Looper.getMainLooper())
        private val renderer = FluidEffectRenderer(this@FluidWallpaperService)
        private lateinit var sharedPreferences: SharedPreferences
        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREF_EFFECT_MODE) {
                updateEffectMode()
            }
        }
        private val drawRunner = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            renderer.setDimensions(
                surfaceHolder?.surfaceFrame?.width()?.toFloat() ?: 0f,
                surfaceHolder?.surfaceFrame?.height()?.toFloat() ?: 0f
            )
            updateEffectMode()
            sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
            running = true
            handler.post(drawRunner)
        }

        private fun updateEffectMode() {
            val modeString = sharedPreferences.getString(PREF_EFFECT_MODE, EffectType.LIGHTNING.name)
            val mode = try {
                EffectType.valueOf(modeString ?: EffectType.LIGHTNING.name)
            } catch (e: IllegalArgumentException) {
                EffectType.LIGHTNING
            }
            renderer.setEffectMode(mode)
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            event?.let { renderer.handleTouchEvent(it) }
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

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.setDimensions(width.toFloat(), height.toFloat())
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
            running = false
            handler.removeCallbacks(drawRunner)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas = try {
                holder.lockCanvas()
            } catch (e: Exception) {
                null
            }
            if (canvas != null) {
                try {
                    renderer.update()
                    renderer.draw(canvas)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
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
    }

    companion object {
        const val PREF_NAME = "FluidWallpaperPrefs"
        const val PREF_EFFECT_MODE = "effect_mode"
    }
}