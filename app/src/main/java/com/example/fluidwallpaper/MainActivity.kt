package com.example.fluidwallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.fluidwallpaper.FluidWallpaperService.Companion.PREF_EFFECT_MODE
import com.example.fluidwallpaper.FluidWallpaperService.Companion.PREF_NAME

class MainActivity : AppCompatActivity() {
    private lateinit var renderer: FluidEffectRenderer
    private lateinit var surfaceView: SurfaceView
    private lateinit var handler: Handler
    private var running = false
    private val drawRunner = Runnable { drawFrame() }
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        renderer = FluidEffectRenderer(this)
        surfaceView = findViewById(R.id.preview_surface)
        val effectSpinner: Spinner = findViewById(R.id.effect_spinner)
        val setWallpaperButton: Button = findViewById(R.id.set_wallpaper_button)

        // Set up effect spinner
        val effects = FluidWallpaperService.EffectType.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, effects)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        effectSpinner.adapter = adapter
        effectSpinner.setSelection(FluidWallpaperService.EffectType.COMET.ordinal)
        effectSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val selectedEffect = FluidWallpaperService.EffectType.valueOf(effects[position])
                renderer.setEffectMode(selectedEffect)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        })

        // Set up set wallpaper button
        setWallpaperButton.setOnClickListener {
            val selectedEffect = FluidWallpaperService.EffectType.valueOf(effectSpinner.selectedItem.toString())
            val prefs = getSharedPreferences(FluidWallpaperService.PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(FluidWallpaperService.PREF_EFFECT_MODE, selectedEffect.name).apply()
            try {
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this, FluidWallpaperService::class.java))
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Initialize surface view
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                renderer.setDimensions(surfaceView.width.toFloat(), surfaceView.height.toFloat())
                running = true
                handler = Handler(Looper.getMainLooper())
                handler.post(drawRunner)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                renderer.setDimensions(width.toFloat(), height.toFloat())
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                running = false
                handler.removeCallbacks(drawRunner)
            }
        })

        // Handle touch events
        surfaceView.setOnTouchListener { _, event ->
            renderer.handleTouchEvent(event)
            true
        }
    }

    private fun drawFrame() {
        var canvas = try {
            surfaceView.holder.lockCanvas()
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
                    surfaceView.holder.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        if (running) {
            handler.postDelayed(drawRunner, 16)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        handler.removeCallbacks(drawRunner)
    }
}