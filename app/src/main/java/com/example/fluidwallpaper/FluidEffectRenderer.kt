package com.example.fluidwallpaper

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.math.ceil

class FluidEffectRenderer(val context: Context) {
    val listColor: IntArray = intArrayOf(
        R.color.color_39FF14,  // Neon Green
        R.color.color_FF2400,  // Scarlet Red
        R.color.color_9D00FF,  // Purple
        R.color.color_FF4500,  // Orange Red
        R.color.color_FF10F0,  // Magenta
        R.color.color_00FFFF,  // Cyan
        R.color.color_FFFF00,  // Yellow
        R.color.color_FFA500   // Orange
    )

    val fireColors: IntArray = intArrayOf(
        R.color.color_FF2400,  // Scarlet Red
        R.color.color_FF4500,  // Orange Red
        R.color.color_FFFF00,  // Yellow
        R.color.color_FFA500   // Orange
    )

    data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var alpha: Float,
        var velocityX: Float,
        var velocityY: Float,
        var life: Float,
        var type: FluidWallpaperService.EffectType,
        var color: Int,
        var phase: Float = 0f,
        var batchId: Int = 0,
        var angle: Float = 0f,
        var isEjected: Boolean = false,
        var initialRadius: Float = 0f,
        var targetRadius: Float = 0f,
        var rotationDirection: Float = 1f,
        var isConverging: Boolean = false,
        var targetX: Float = 0f,
        var targetY: Float = 0f,
        var originalX: Float = 0f,  // For GRID_WAVE, LIGHTNING_RAYS, FIRE
        var originalY: Float = 0f,  // For GRID_WAVE, LIGHTNING_RAYS, FIRE
        var originalColor: Int = 0, // For GRID_WAVE, LIGHTNING_RAYS, FIRE
        var colorChangeTime: Float = 0f // For GRID_WAVE, LIGHTNING_RAYS, FIRE
    )

    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    private var currentBatchId = 0
    private val particles = mutableListOf<Particle>()
    private var effectMode = FluidWallpaperService.EffectType.COMET
    private var lastParticleAddTime = 0L
    private val particleAddInterval = 50L
    private val maxParticles = 200
    private var lastRandomParticleTime = 0L
    private val randomParticleInterval = 100L
    private var width = 0f
    private var height = 0f
    private var lastX = 0f
    private var lastY = 0f
    private val spreadRadius = 200f // GRID_WAVE, FIRE: radius for color and motion spread
    private val colorChangeDuration = 1.5f // GRID_WAVE, FIRE: how long color change lasts
    private val springConstant = 0.05f // GRID_WAVE, LIGHTNING_RAYS, FIRE: spring force
    private val damping = 0.9f // GRID_WAVE, LIGHTNING_RAYS, FIRE: damping for oscillation

    fun setDimensions(width: Float, height: Float) {
        this.width = width
        this.height = height
        initializeParticles()
    }

    fun setEffectMode(mode: FluidWallpaperService.EffectType) {
        effectMode = mode
        particles.clear()
        if (mode == FluidWallpaperService.EffectType.SCATTER_CONVERGE ||
            mode == FluidWallpaperService.EffectType.VORTEX ||
            mode == FluidWallpaperService.EffectType.WAVE) {
            addRandomParticles(50)
        } else if (mode == FluidWallpaperService.EffectType.GRID_WAVE) {
            initializeGridWaveParticles()
        } else if (mode == FluidWallpaperService.EffectType.FIRE) {
            initializeFireParticles()
        } else {
            particles.add(
                Particle(
                    x = width / 2,
                    y = height / 2,
                    radius = when (mode) {
                        FluidWallpaperService.EffectType.RIPPLE -> 10f
                        FluidWallpaperService.EffectType.SPARKLE -> 3f
                        FluidWallpaperService.EffectType.LIGHTNING -> 5f
                        FluidWallpaperService.EffectType.COMET -> 3f
                        FluidWallpaperService.EffectType.FIREWORK -> 3f
                        FluidWallpaperService.EffectType.SPINNING_CIRCLE -> 3f
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> 3f
                        FluidWallpaperService.EffectType.VORTEX -> 3f
                        FluidWallpaperService.EffectType.WAVE -> 3f
                        FluidWallpaperService.EffectType.GRID_WAVE -> 3f
                        FluidWallpaperService.EffectType.FIRE -> 4f
                    },
                    alpha = when (mode) {
                        FluidWallpaperService.EffectType.RIPPLE -> 255f
                        FluidWallpaperService.EffectType.SPARKLE -> 200f
                        FluidWallpaperService.EffectType.LIGHTNING -> 255f
                        FluidWallpaperService.EffectType.COMET -> 200f
                        FluidWallpaperService.EffectType.FIREWORK -> 200f
                        FluidWallpaperService.EffectType.SPINNING_CIRCLE -> 200f
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> 200f
                        FluidWallpaperService.EffectType.VORTEX -> 200f
                        FluidWallpaperService.EffectType.WAVE -> 200f
                        FluidWallpaperService.EffectType.GRID_WAVE -> 200f
                        FluidWallpaperService.EffectType.FIRE -> 255f
                    },
                    velocityX = when (mode) {
                        FluidWallpaperService.EffectType.RIPPLE -> 0f
                        FluidWallpaperService.EffectType.SPARKLE -> 3f
                        FluidWallpaperService.EffectType.LIGHTNING -> 0f
                        FluidWallpaperService.EffectType.COMET -> 3f
                        FluidWallpaperService.EffectType.FIREWORK -> 3f
                        FluidWallpaperService.EffectType.SPINNING_CIRCLE -> 0f
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.VORTEX -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.WAVE -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.GRID_WAVE -> 0f
                        FluidWallpaperService.EffectType.FIRE -> Random.nextFloat() * 2f - 1f
                    },
                    velocityY = when (mode) {
                        FluidWallpaperService.EffectType.RIPPLE -> 0f
                        FluidWallpaperService.EffectType.SPARKLE -> -3f
                        FluidWallpaperService.EffectType.LIGHTNING -> 0f
                        FluidWallpaperService.EffectType.COMET -> -3f
                        FluidWallpaperService.EffectType.FIREWORK -> -3f
                        FluidWallpaperService.EffectType.SPINNING_CIRCLE -> 0f
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.VORTEX -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.WAVE -> Random.nextFloat() * 4f - 2f
                        FluidWallpaperService.EffectType.GRID_WAVE -> 0f
                        FluidWallpaperService.EffectType.FIRE -> -Random.nextFloat() * 5f - 5f
                    },
                    life = 1f,
                    type = mode,
                    color = ContextCompat.getColor(context, listColor.random()),
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    originalX = width / 2,
                    originalY = height / 2,
                    originalColor = ContextCompat.getColor(context, listColor.random())
                )
            )
        }
        println("Effect mode set to $mode")
    }

    private fun initializeParticles() {
        if (effectMode == FluidWallpaperService.EffectType.SCATTER_CONVERGE ||
            effectMode == FluidWallpaperService.EffectType.VORTEX ||
            effectMode == FluidWallpaperService.EffectType.WAVE) {
            particles.clear()
            addRandomParticles(50)
        } else if (effectMode == FluidWallpaperService.EffectType.GRID_WAVE) {
            initializeGridWaveParticles()
        } else if (effectMode == FluidWallpaperService.EffectType.FIRE) {
            initializeFireParticles()
        }
    }

    private fun initializeGridWaveParticles() {
        particles.clear()
        val aspectRatio = width / height
        val cols = ceil(sqrt(maxParticles.toFloat() * aspectRatio)).toInt()
        val rows = ceil(maxParticles.toFloat() / cols).toInt()
        val gridSpacingX = width / (cols - 1).coerceAtLeast(1)
        val gridSpacingY = height / (rows - 1).coerceAtLeast(1)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (particles.size >= maxParticles) return
                val x = col * gridSpacingX
                val y = row * gridSpacingY
                if (x <= width && y <= height) {
                    val color = ContextCompat.getColor(context, listColor.random())
                    particles.add(
                        Particle(
                            x = x,
                            y = y,
                            radius = 3f,
                            alpha = 200f,
                            velocityX = 0f,
                            velocityY = 0f,
                            life = Float.MAX_VALUE,
                            type = FluidWallpaperService.EffectType.GRID_WAVE,
                            color = color,
                            originalX = x,
                            originalY = y,
                            originalColor = color,
                            colorChangeTime = 0f
                        )
                    )
                }
            }
        }
        println("Initialized $rows x $cols grid particles for GRID_WAVE with spacing ($gridSpacingX, $gridSpacingY)")
    }


    private fun initializeFireParticles() {
        particles.clear()
        val particleCount = maxParticles // Sử dụng toàn bộ giới hạn hạt
        val baseY = height
        val spacing = width / (particleCount / 4).toFloat() // Giảm khoảng cách để tăng mật độ
        repeat(particleCount) { i ->
            val x = (i % (particleCount / 4)) * spacing + Random.nextFloat() * spacing * 0.5f
            val y = baseY - Random.nextFloat() * 30f // Tăng độ lan tỏa dọc
            val color = ContextCompat.getColor(context, fireColors.random())
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    radius = Random.nextFloat() * 4f + 3f, // Tăng kích thước hạt
                    alpha = 255f,
                    velocityX = Random.nextFloat() * 3f - 1.5f, // Tăng dao động ngang
                    velocityY = -Random.nextFloat() * 8f - 6f, // Tăng tốc độ hướng lên
                    life = Random.nextFloat() * 3f + 3f, // Tăng tuổi thọ
                    type = FluidWallpaperService.EffectType.FIRE,
                    color = color,
                    originalX = x,
                    originalY = y,
                    originalColor = color,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat()
                )
            )
        }
        println("Initialized $particleCount fire particles at bottom of screen")
    }

    private fun addRandomParticles(count: Int) {
        if (particles.size >= maxParticles - count) {
            repeat(count) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
        repeat(count) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = 200f,
                    velocityX = Random.nextFloat() * 4f - 2f,
                    velocityY = Random.nextFloat() * 4f - 2f,
                    life = Random.nextFloat() * 2f + 1f,
                    type = effectMode,
                    color = ContextCompat.getColor(context, listColor.random()),
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    originalX = 0f,
                    originalY = 0f,
                    originalColor = ContextCompat.getColor(context, listColor.random())
                )
            )
        }
        println("Added $count random particles for $effectMode")
    }

    fun handleTouchEvent(event: MotionEvent) {
        println("Touch event: action=${event.action}, x=${event.x}, y=${event.y}")
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                if (System.currentTimeMillis() - lastParticleAddTime > particleAddInterval) {
                    when (effectMode) {
                        FluidWallpaperService.EffectType.RIPPLE -> addRipple(event.x, event.y)
                        FluidWallpaperService.EffectType.SPARKLE -> addSparkleParticlesOnTouch(event.x, event.y)
                        FluidWallpaperService.EffectType.LIGHTNING -> addLightning(event.x, event.y, event.x, event.y)
                        FluidWallpaperService.EffectType.COMET -> addCometParticles(event.x, event.y, event.x, event.y, 0f, 0f)
                        FluidWallpaperService.EffectType.FIREWORK -> addFireworkParticles(event.x, event.y)
                        FluidWallpaperService.EffectType.SPINNING_CIRCLE -> addSpinningCircleParticles(event.x, event.y)
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> convergeParticles(event.x, event.y)
                        FluidWallpaperService.EffectType.VORTEX -> addVortexParticles(event.x, event.y)
                        FluidWallpaperService.EffectType.WAVE -> addWaveParticles(event.x, event.y, 0f, 0f)
                        FluidWallpaperService.EffectType.GRID_WAVE -> triggerGridWave(event.x, event.y, 0f, 0f)
                        FluidWallpaperService.EffectType.FIRE -> attractFireParticles(event.x, event.y)
                    }
                    lastParticleAddTime = System.currentTimeMillis()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (System.currentTimeMillis() - lastParticleAddTime > particleAddInterval) {
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY
                    when (effectMode) {
                        FluidWallpaperService.EffectType.SPARKLE -> addSparkleParticlesOnSwipe(lastX, lastY, event.x, event.y, deltaX * 0.1f, deltaY * 0.1f)
                        FluidWallpaperService.EffectType.LIGHTNING -> addLightning(lastX, lastY, event.x, event.y)
                        FluidWallpaperService.EffectType.COMET -> addCometParticles(lastX, lastY, event.x, event.y, deltaX * 0.1f, deltaY * 0.1f)
                        FluidWallpaperService.EffectType.FIREWORK -> addFireworkParticles(event.x, event.y)
                        FluidWallpaperService.EffectType.SCATTER_CONVERGE -> dragParticles(event.x, event.y, deltaX, deltaY)
                        FluidWallpaperService.EffectType.VORTEX -> updateVortexParticles(event.x, event.y, deltaX, deltaY)
                        FluidWallpaperService.EffectType.WAVE -> addWaveParticles(event.x, event.y, deltaX, deltaY)
                        FluidWallpaperService.EffectType.GRID_WAVE -> triggerGridWave(event.x, event.y, deltaX, deltaY)
                        FluidWallpaperService.EffectType.FIRE -> dragFireParticles(event.x, event.y)
                        else -> {}
                    }
                    lastParticleAddTime = System.currentTimeMillis()
                    lastX = event.x
                    lastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> {
                when (effectMode) {
                    FluidWallpaperService.EffectType.VORTEX -> disperseParticles()
                    FluidWallpaperService.EffectType.FIRE -> releaseFireParticles()
                    else -> {}
                }
            }
        }
    }

    private fun triggerGridWave(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        val dragSpeed = sqrt(deltaX.pow(2) + deltaY.pow(2)) / 0.016f
        particles.filter { it.type == FluidWallpaperService.EffectType.GRID_WAVE }
            .forEach { particle ->
                val newColor = ContextCompat.getColor(context, listColor.random())
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < spreadRadius) {
                    val influence = (1f - distance / spreadRadius).coerceIn(0f, 1f)
                    particle.color = newColor
                    particle.colorChangeTime = colorChangeDuration
                    val angle = atan2(particle.y - y, particle.x - x)
                    val pushDistance = 20f * influence
                    particle.velocityX += cos(angle) * pushDistance
                    particle.velocityY += sin(angle) * pushDistance
                }
            }
    }



    private fun attractFireParticles(x: Float, y: Float) {
        val attractionRadius = 300f
        particles.filter { it.type == FluidWallpaperService.EffectType.FIRE }
            .forEach { particle ->
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < attractionRadius) {
                    particle.isConverging = true
                    particle.targetX = x
                    particle.targetY = y
                    val angle = atan2(y - particle.y, x - particle.x)
                    val speed = distance / 0.3f
                    particle.velocityX = cos(angle) * speed
                    particle.velocityY = sin(angle) * speed
                    particle.color = ContextCompat.getColor(context, fireColors.random())
                    particle.colorChangeTime = colorChangeDuration
                }
            }
        println("Attracting FIRE particles to ($x, $y)")
    }

    private fun dragFireParticles(x: Float, y: Float) {
        val dragRadius = 300f
        particles.filter { it.type == FluidWallpaperService.EffectType.FIRE && it.isConverging }
            .forEach { particle ->
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < dragRadius) {
                    particle.targetX = x
                    particle.targetY = y
                    val angle = atan2(y - particle.y, x - particle.x)
                    val speed = distance / 0.3f
                    particle.velocityX = cos(angle) * speed
                    particle.velocityY = sin(angle) * speed
                }
            }
        println("Dragging FIRE particles to ($x, $y)")
    }

    private fun releaseFireParticles() {
        particles.filter { it.type == FluidWallpaperService.EffectType.FIRE && it.isConverging }
            .forEach { particle ->
                particle.isConverging = false
                particle.velocityX = Random.nextFloat() * 2f - 1f
                particle.velocityY = -Random.nextFloat() * 5f - 5f
                particle.life = Random.nextFloat() * 2f + 2f
            }
        println("Released FIRE particles")
    }

    private fun convergeParticles(x: Float, y: Float) {
        val convergenceRadius = 200f
        particles.filter { it.type == FluidWallpaperService.EffectType.SCATTER_CONVERGE }
            .forEach { particle ->
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < convergenceRadius) {
                    particle.isConverging = true
                    particle.targetX = x
                    particle.targetY = y
                    val angle = atan2(y - particle.y, x - particle.x)
                    val speed = distance / 0.5f
                    particle.velocityX = cos(angle) * speed
                    particle.velocityY = sin(angle) * speed
                }
            }
        println("Converging particles to ($x, $y)")
    }

    private fun dragParticles(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        val dragRadius = 150f
        val dragSpeed = sqrt(deltaX.pow(2) + deltaY.pow(2)) / 0.016f
        val dragAngle = atan2(deltaY, deltaX)
        particles.filter { it.type == FluidWallpaperService.EffectType.SCATTER_CONVERGE }
            .forEach { particle ->
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < dragRadius) {
                    particle.isConverging = true
                    particle.targetX = x
                    particle.targetY = y
                    val influence = (1f - distance / dragRadius).coerceIn(0f, 1f)
                    val targetVelocityX = cos(dragAngle) * dragSpeed * 0.5f
                    val targetVelocityY = sin(dragAngle) * dragSpeed * 0.5f
                    particle.velocityX = particle.velocityX * 0.7f + targetVelocityX * 0.3f * influence
                    particle.velocityY = particle.velocityY * 0.7f + targetVelocityY * 0.3f * influence
                }
            }
        println("Dragging particles to ($x, $y) with velocity ($deltaX, $deltaY)")
    }

    private fun disperseParticles() {
        particles.filter {
            it.type == FluidWallpaperService.EffectType.SCATTER_CONVERGE ||
                    it.type == FluidWallpaperService.EffectType.VORTEX ||
                    it.type == FluidWallpaperService.EffectType.WAVE && it.isConverging
        }.forEach { particle ->
            particle.isConverging = false
            val angle = Random.nextFloat() * 2 * Math.PI.toFloat()
            val randomSpeed = Random.nextFloat() * 2f + 1f
            val randomVelocityX = cos(angle) * randomSpeed
            val randomVelocityY = sin(angle) * randomSpeed
            particle.velocityX = particle.velocityX * 0.6f + randomVelocityX * 0.4f
            particle.velocityY = particle.velocityY * 0.6f + randomVelocityY * 0.4f
        }
        println("Dispersing particles with sliding effect")
    }

    private fun addRipple(x: Float, y: Float) {
        if (particles.size >= maxParticles) particles.removeAt(0)
        val rippleColor = ContextCompat.getColor(context, listColor.random())
        particles.add(
            Particle(
                x = x,
                y = y,
                radius = 0f,
                alpha = 255f,
                velocityX = 0f,
                velocityY = 0f,
                life = 1f,
                type = FluidWallpaperService.EffectType.RIPPLE,
                color = rippleColor,
                originalX = x,
                originalY = y,
                originalColor = rippleColor
            )
        )
        println("Added ripple at ($x, $y) with color $rippleColor")
    }

    private fun addSparkleParticlesOnTouch(x: Float, y: Float) {
        if (particles.size >= maxParticles - 50) particles.removeAt(0)
        val particleColor = ContextCompat.getColor(context, listColor.random())
        repeat(50) {
            particles.add(
                Particle(
                    x = x + Random.nextFloat() * 15f - 7.5f,
                    y = y + Random.nextFloat() * 15f - 7.5f,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = Random.nextFloat() * 80f + 160f,
                    velocityX = Random.nextFloat() * 3f - 1.5f,
                    velocityY = Random.nextFloat() * 3f - 1.5f,
                    life = 1.5f,
                    type = FluidWallpaperService.EffectType.SPARKLE,
                    color = particleColor,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    originalX = x,
                    originalY = y,
                    originalColor = particleColor
                )
            )
        }
        println("Added 50 sparkle particles on touch at ($x, $y) with color $particleColor")
    }

    private fun addSparkleParticlesOnSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        velocityX: Float,
        velocityY: Float
    ) {
        if (particles.size >= maxParticles - 15) particles.removeAt(0)
        val particleColor = ContextCompat.getColor(context, listColor.random())
        val distance = sqrt((endX - startX).pow(2) + (endY - startY).pow(2))
        val segmentCount = (distance / 20f).toInt().coerceAtMost(3)
        if (segmentCount > 0) {
            for (i in 1..segmentCount) {
                val t = i.toFloat() / segmentCount
                val interpX = startX + t * (endX - startX)
                val interpY = startY + t * (endY - startY)
                repeat(5) { j ->
                    val offsetT = (j - 2) * 0.2f
                    val lineX = interpX + offsetT * (endX - startX) * 0.1f
                    val lineY = interpY + offsetT * (endY - startY) * 0.1f
                    val wobble = sin(t * 2 * Math.PI.toFloat() + j * 0.3f) * 5f
                    particles.add(
                        Particle(
                            x = lineX + wobble * cos(
                                atan2((endY - startY).toDouble(), (endX - startX).toDouble()).toFloat()
                            ),
                            y = lineY + wobble * sin(
                                atan2((endY - startY).toDouble(), (endX - startX).toDouble()).toFloat()
                            ),
                            radius = Random.nextFloat() * 2f + 2f,
                            alpha = Random.nextFloat() * 80f + 140f,
                            velocityX = velocityX * 0.8f + Random.nextFloat() * 1f - 0.5f,
                            velocityY = velocityY * 0.8f + Random.nextFloat() * 1f - 0.5f,
                            life = 1.5f,
                            type = FluidWallpaperService.EffectType.SPARKLE,
                            color = particleColor,
                            phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                            originalX = lineX,
                            originalY = lineY,
                            originalColor = particleColor
                        )
                    )
                }
            }
            println("Added $segmentCount sparkle segments on swipe from ($startX, $startY) to ($endX, $endY) with color $particleColor")
        }
    }

    private fun addCometParticles(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        velocityX: Float,
        velocityY: Float
    ) {
        if (particles.size >= maxParticles - 30) particles.removeAt(0)
        val mainAngle = atan2(endY - startY, endX - startX)
        val coneAngle = Math.PI.toFloat() / 2.2f
        val particleCount = 30
        val spreadFactor = 3.5f
        val direction = listOf(1f, -1f)
        repeat(particleCount) { i ->
            val particleColor = ContextCompat.getColor(context, listColor.random())
            val angleOffset = (i.toFloat() / (particleCount - 1) - 0.5f) * coneAngle
            val particleAngle = mainAngle + angleOffset
            val backOffset = i * 3f
            val startOffsetX = cos(mainAngle) * backOffset
            val startOffsetY = sin(mainAngle) * backOffset
            val speed = Random.nextFloat() * 2f + 3f
            val velX = direction.random() * cos(particleAngle) * speed * spreadFactor + velocityX * 0.3f
            val velY = direction.random() * sin(particleAngle) * speed * spreadFactor + velocityY * 0.3f
            particles.add(
                Particle(
                    x = startX - startOffsetX + Random.nextFloat() * 10f - 5f,
                    y = startY - startOffsetY + Random.nextFloat() * 10f - 5f,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = Random.nextFloat() * 80f + 170f,
                    velocityX = velX,
                    velocityY = velY,
                    life = 1.8f + Random.nextFloat() * 0.7f,
                    type = FluidWallpaperService.EffectType.COMET,
                    color = particleColor,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    batchId = currentBatchId,
                    originalX = startX,
                    originalY = startY,
                    originalColor = particleColor
                )
            )
        }
        currentBatchId++
        println("Added comet with $particleCount particles from ($startX, $startY) to ($endX, $endY)")
    }

    private fun addFireworkParticles(x: Float, y: Float) {
        if (particles.size >= maxParticles - 50) particles.removeAt(0)
        val particleCount = 50
        val maxSpeed = 8f
        repeat(particleCount) { i ->
            val particleColor = ContextCompat.getColor(context, listColor.random())
            val angle = (i.toFloat() / particleCount) * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * maxSpeed + 2f
            particles.add(
                Particle(
                    x = x + Random.nextFloat() * 5f - 2.5f,
                    y = y + Random.nextFloat() * 5f - 2.5f,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = Random.nextFloat() * 80f + 170f,
                    velocityX = cos(angle) * speed,
                    velocityY = sin(angle) * speed,
                    life = 2.0f + Random.nextFloat() * 0.5f,
                    type = FluidWallpaperService.EffectType.FIREWORK,
                    color = particleColor,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    batchId = currentBatchId,
                    originalX = x,
                    originalY = y,
                    originalColor = particleColor
                )
            )
        }
        currentBatchId++
    }

    private fun addSpinningCircleParticles(x: Float, y: Float) {
        if (particles.size >= maxParticles - 36) {
            repeat(36) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
        val particleCountPerCircle = 20
        val radii = listOf(50f, 65f, 80f, 95f, 110f)
        val initialRadii = listOf(60f, 75f, 90f, 105f, 120f)
        val rotationDirections = listOf(1f, -1f, 1f, -1f, 1f)
        val ejectCircleIndex = Random.nextInt(radii.size)
        val ejectParticleIndex = Random.nextInt(particleCountPerCircle)
        repeat(Random.nextInt(2, 5)) { circleIndex ->
            val targetRadius = radii[circleIndex]
            val initialRadius = initialRadii[circleIndex]
            val rotationDirection = rotationDirections[circleIndex]
            repeat(particleCountPerCircle) { i ->
                val angle = (i.toFloat() / particleCountPerCircle) * 2 * Math.PI.toFloat()
                val colorIndex = Random.nextInt(listColor.size)
                val particleColor = ContextCompat.getColor(context, listColor[colorIndex])
                val initialX = x + cos(angle) * initialRadius
                val initialY = y + sin(angle) * initialRadius
                val velocityX = -cos(angle) * (initialRadius - targetRadius) / 0.2f
                val velocityY = -sin(angle) * (initialRadius - targetRadius) / 0.2f
                particles.add(
                    Particle(
                        x = initialX,
                        y = initialY,
                        radius = 3f,
                        alpha = 200f,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        life = 1.2f,
                        type = FluidWallpaperService.EffectType.SPINNING_CIRCLE,
                        color = particleColor,
                        phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                        batchId = currentBatchId,
                        angle = angle,
                        isEjected = circleIndex == ejectCircleIndex && i == ejectParticleIndex && Random.nextFloat() < 0.5f,
                        initialRadius = initialRadius,
                        targetRadius = targetRadius,
                        rotationDirection = rotationDirection,
                        originalX = initialX,
                        originalY = initialY,
                        originalColor = particleColor
                    )
                )
            }
        }
        currentBatchId++
        println("Added three spinning circles with $particleCountPerCircle particles each at ($x, $y) with multiple colors")
    }

    private fun addVortexParticles(x: Float, y: Float) {
        if (particles.size >= maxParticles - 40) {
            repeat(40) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
        val particleCount = 50
        repeat(particleCount) { i ->
            val orbitRadius = Random.nextFloat() * 50f + 100f
            val particleColor = ContextCompat.getColor(context, listColor.random())
            val angle = (i.toFloat() / particleCount) * 2 * Math.PI.toFloat()
            val speed = Random.nextFloat() * 3f + 3f
            val orbitX = x + cos(angle) * orbitRadius
            val orbitY = y + sin(angle) * orbitRadius
            val tangentialAngle = angle + Math.PI.toFloat() / 2
            particles.add(
                Particle(
                    x = orbitX,
                    y = orbitY,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = Random.nextFloat() * 80f + 170f,
                    velocityX = cos(tangentialAngle) * speed,
                    velocityY = sin(tangentialAngle) * speed,
                    life = Random.nextFloat() * 1f + 1.5f,
                    type = FluidWallpaperService.EffectType.VORTEX,
                    color = particleColor,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    batchId = currentBatchId,
                    targetX = x,
                    targetY = y,
                    isConverging = true,
                    targetRadius = orbitRadius,
                    originalX = orbitX,
                    originalY = orbitY,
                    originalColor = particleColor
                )
            )
        }
        currentBatchId++
    }

    private fun updateVortexParticles(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        val dragRadius = 150f
        val dragSpeed = sqrt(deltaX.pow(2) + deltaY.pow(2)) / 0.016f
        particles.filter { it.type == FluidWallpaperService.EffectType.VORTEX && it.isConverging }
            .forEach { particle ->
                val distance = sqrt((particle.x - x).pow(2) + (particle.y - y).pow(2))
                if (distance < dragRadius) {
                    particle.targetX = x
                    particle.targetY = y
                    val angleToCenter = atan2(particle.y - y, particle.x - x)
                    val tangentialAngle = angleToCenter + Math.PI.toFloat() / 2
                    val orbitSpeed = (dragSpeed * 0.01f).coerceIn(2f, 6f)
                    val influence = (1f - distance / dragRadius).coerceIn(0f, 1f)
                    val radialSpeed = (distance - particle.targetRadius) * 0.1f * influence
                    particle.velocityX = cos(tangentialAngle) * orbitSpeed + cos(angleToCenter) * radialSpeed
                    particle.velocityY = sin(tangentialAngle) * orbitSpeed + sin(angleToCenter) * radialSpeed
                    particle.targetRadius = (particle.targetRadius + dragSpeed * 0.005f * influence).coerceIn(50f, 150f)
                }
            }
        println("Updating vortex particles to ($x, $y) with velocity ($deltaX, $deltaY)")
    }

    private fun addWaveParticles(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        if (particles.size >= maxParticles) {
            repeat(30) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
        val particleCount = 30
        val waveWidth = Random.nextFloat() * 50f + 50f
        val baseSpeed = Random.nextFloat() * 3f + 3f
        val dragSpeed = sqrt(deltaX.pow(2) + deltaY.pow(2)) / 0.016f
        val directionAngle = if (deltaX == 0f && deltaY == 0f) Random.nextFloat() * 2 * Math.PI.toFloat() else atan2(deltaY, deltaX)
        val perpendicularAngle = directionAngle + Math.PI.toFloat() / 2
        repeat(particleCount) { i ->
            val particleColor = ContextCompat.getColor(context, listColor.random())
            val t = (i.toFloat() / (particleCount - 1)) - 0.5f
            val offsetX = cos(perpendicularAngle) * t * waveWidth
            val offsetY = sin(perpendicularAngle) * t * waveWidth
            val speed = baseSpeed + dragSpeed * 0.05f
            val waveFrequency = (0.05f + dragSpeed * 0.001f).coerceIn(0.05f, 0.2f)
            particles.add(
                Particle(
                    x = x + offsetX,
                    y = y + offsetY,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = Random.nextFloat() * 80f + 170f,
                    velocityX = cos(directionAngle) * speed,
                    velocityY = sin(directionAngle) * speed,
                    life = Random.nextFloat() * 1f + 1.5f,
                    type = FluidWallpaperService.EffectType.WAVE,
                    color = particleColor,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat(),
                    batchId = currentBatchId,
                    targetX = x,
                    targetY = y,
                    isConverging = true,
                    targetRadius = waveFrequency,
                    originalX = x + offsetX,
                    originalY = y + offsetY,
                    originalColor = particleColor
                )
            )
        }
        currentBatchId++
    }

    fun update() {
        if (effectMode == FluidWallpaperService.EffectType.SCATTER_CONVERGE ||
            effectMode == FluidWallpaperService.EffectType.VORTEX ||
            effectMode == FluidWallpaperService.EffectType.WAVE ||
            effectMode == FluidWallpaperService.EffectType.FIRE) {
            if (System.currentTimeMillis() - lastRandomParticleTime > randomParticleInterval) {
                if (effectMode == FluidWallpaperService.EffectType.FIRE) {
                    addFireParticles(5)
                } else {
                    addRandomParticles(5)
                }
                lastRandomParticleTime = System.currentTimeMillis()
            }
        }
        updateParticles()
    }

    private fun addFireParticles(count: Int) {
        if (particles.size >= maxParticles - count) {
            repeat(count) { if (particles.isNotEmpty()) particles.removeAt(0) }
        }
        repeat(count) {
            val x = Random.nextFloat() * width
            val y = height - Random.nextFloat() * 30f // Tăng độ lan tỏa
            val color = ContextCompat.getColor(context, fireColors.random())
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    radius = Random.nextFloat() * 5f + 4f,
                    alpha = 255f,
                    velocityX = Random.nextFloat() * 3f - 1.5f,
                    velocityY = -Random.nextFloat() * 8f - 6f,
                    life = Random.nextFloat() * 3f + 3f,
                    type = FluidWallpaperService.EffectType.FIRE,
                    color = color,
                    originalX = x,
                    originalY = y,
                    originalColor = color,
                    phase = Random.nextFloat() * 2 * Math.PI.toFloat()
                )
            )
        }
        println("Added $count fire particles at bottom of screen")
    }

    private fun updateParticles() {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            when (particle.type) {
                FluidWallpaperService.EffectType.RIPPLE -> {
                    particle.radius += 20f
                    particle.alpha -= 10f
                    if (particle.alpha <= 0) iterator.remove()
                }
                FluidWallpaperService.EffectType.SPARKLE -> {
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    particle.phase += 0.15f
                    particle.x += sin(particle.phase.toDouble()).toFloat() * 1.5f
                    particle.y += cos(particle.phase.toDouble()).toFloat() * 1.5f
                    particle.life -= 0.06f
                    particle.alpha = 200f * particle.life
                    particle.velocityX *= 0.94f
                    particle.velocityY *= 0.94f
                    if (particle.life <= 0) iterator.remove()
                }
                FluidWallpaperService.EffectType.COMET -> {
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    particle.phase += 0.08f
                    particle.x += sin(particle.phase * 3) * 0.8f
                    particle.y += cos(particle.phase * 2) * 0.8f
                    particle.radius *= 0.97f
                    particle.life -= 0.015f
                    particle.alpha = (220f * particle.life)
                    particle.velocityX *= 0.985f
                    particle.velocityY *= 0.985f
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.LIGHTNING -> {
                    particle.alpha -= 10f
                    particle.radius *= 0.95f
                    if (particle.alpha <= 0) iterator.remove()
                }
                FluidWallpaperService.EffectType.FIREWORK -> {
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    particle.velocityY += 0.2f
                    particle.phase += 0.1f
                    particle.radius *= 0.98f
                    particle.life -= 0.02f
                    particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                    particle.velocityX *= 0.95f
                    particle.velocityY *= 0.95f
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.SPINNING_CIRCLE -> {
                    if (particle.isEjected && particle.life < 0.3f) {
                        particle.x += particle.velocityX
                        particle.y += particle.velocityY
                        particle.velocityY += 0.1f
                        particle.radius *= 0.98f
                        particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                        particle.velocityX *= 0.95f
                        particle.velocityY *= 0.95f
                    } else {
                        val centerX = particle.x - cos(particle.angle) * particle.targetRadius
                        val centerY = particle.y - sin(particle.angle) * particle.targetRadius
                        if (particle.life > 0.8f) {
                            particle.x += particle.velocityX * 0.016f
                            particle.y += particle.velocityY * 0.016f
                            particle.velocityX *= 0.9f
                            particle.velocityY *= 0.9f
                        } else {
                            particle.angle += 0.3f * particle.rotationDirection
                            particle.x = centerX + cos(particle.angle) * particle.targetRadius
                            particle.y = centerY + sin(particle.angle) * particle.targetRadius
                            particle.velocityX = 0f
                            particle.velocityY = 0f
                            if (particle.isEjected && particle.life <= 0.5f) {
                                val ejectAngle = particle.angle + Random.nextFloat() * 0.5f - 0.25f
                                particle.velocityX = cos(ejectAngle) * 8f
                                particle.velocityY = sin(ejectAngle) * 8f
                            }
                        }
                    }
                    particle.life -= 0.02f
                    particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.SCATTER_CONVERGE -> {
                    if (particle.isConverging) {
                        particle.x += particle.velocityX * 0.016f
                        particle.y += particle.velocityY * 0.016f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                        val distanceToTarget = sqrt(
                            (particle.x - particle.targetX).pow(2) + (particle.y - particle.targetY).pow(2)
                        )
                        if (distanceToTarget < 10f) {
                            particle.velocityX = 0f
                            particle.velocityY = 0f
                        }
                    } else {
                        particle.x += particle.velocityX
                        particle.y += particle.velocityY
                        particle.phase += 0.05f
                        particle.x += sin(particle.phase.toDouble()).toFloat() * 1f
                        particle.y += cos(particle.phase.toDouble()).toFloat() * 1f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                    }
                    particle.life -= 0.02f
                    particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.VORTEX -> {
                    if (particle.isConverging) {
                        particle.x += particle.velocityX * 0.016f
                        particle.y += particle.velocityY * 0.016f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                        val distanceToTarget = sqrt(
                            (particle.x - particle.targetX).pow(2) + (particle.y - particle.targetY).pow(2)
                        )
                        if (distanceToTarget < 10f) {
                            particle.velocityX = 0f
                            particle.velocityY = 0f
                        }
                    } else {
                        particle.x += particle.velocityX
                        particle.y += particle.velocityY
                        particle.phase += 0.05f
                        particle.x += sin(particle.phase.toDouble()).toFloat() * 1f
                        particle.y += cos(particle.phase.toDouble()).toFloat() * 1f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                    }
                    particle.life -= 0.02f
                    particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.WAVE -> {
                    if (particle.isConverging) {
                        particle.x += particle.velocityX * 0.016f
                        particle.y += particle.velocityY * 0.016f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                        particle.phase += particle.targetRadius
                        val waveAngle = atan2(particle.velocityY, particle.velocityX)
                        val perpendicularAngle = waveAngle + Math.PI.toFloat() / 2
                        particle.x += sin(perpendicularAngle) * sin(particle.phase.toDouble()).toFloat() * 10f
                        particle.y += cos(perpendicularAngle) * sin(particle.phase.toDouble()).toFloat() * 10f
                    } else {
                        particle.x += particle.velocityX
                        particle.y += particle.velocityY
                        particle.phase += 0.05f
                        particle.x += sin(particle.phase.toDouble()).toFloat() * 1f
                        particle.y += cos(particle.phase.toDouble()).toFloat() * 1f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                    }
                    particle.life -= 0.02f
                    particle.alpha = (200f * particle.life).coerceAtLeast(0f)
                    if (particle.life <= 0 || particle.radius < 0.5f) iterator.remove()
                }
                FluidWallpaperService.EffectType.GRID_WAVE -> {
                    val dx = particle.originalX - particle.x
                    val dy = particle.originalY - particle.y
                    particle.velocityX += dx * springConstant
                    particle.velocityY += dy * springConstant
                    particle.velocityX *= damping
                    particle.velocityY *= damping
                    particle.x += particle.velocityX
                    particle.y += particle.velocityY
                    if (particle.colorChangeTime > 0f) {
                        particle.colorChangeTime -= 0.016f
                        if (particle.colorChangeTime <= 0f) {
                            particle.color = particle.originalColor
                        }
                    }
                    if (particle.colorChangeTime > 0f && particle.color != particle.originalColor) {
                        particles.filter {
                            it.type == FluidWallpaperService.EffectType.GRID_WAVE &&
                                    it.colorChangeTime <= 0f &&
                                    sqrt((it.x - particle.x).pow(2) + (it.y - particle.y).pow(2)) < spreadRadius * 0.5f
                        }.forEach { neighbor ->
                            neighbor.color = particle.color
                            neighbor.colorChangeTime = colorChangeDuration
                            val angle = atan2(neighbor.y - particle.y, neighbor.x - particle.x)
                            val influence = (1f - sqrt((neighbor.x - particle.x).pow(2) + (neighbor.y - particle.y).pow(2)) / (spreadRadius * 0.5f)).coerceIn(0f, 1f)
                            neighbor.velocityX += cos(angle) * 10f * influence
                            neighbor.velocityY += sin(angle) * 10f * influence
                        }
                    }
                    particle.alpha = 200f
                }

                FluidWallpaperService.EffectType.FIRE -> {
                    if (particle.isConverging) {
                        particle.x += particle.velocityX * 0.016f
                        particle.y += particle.velocityY * 0.016f
                        particle.velocityX *= 0.99f
                        particle.velocityY *= 0.99f
                        val distanceToTarget = sqrt(
                            (particle.x - particle.targetX).pow(2) + (particle.y - particle.targetY).pow(2)
                        )
                        if (distanceToTarget < 10f) {
                            particle.velocityX = 0f
                            particle.velocityY = 0f
                        }
                    } else {
                        particle.x += particle.velocityX
                        particle.y += particle.velocityY
                        particle.phase += 0.1f
                        particle.x += sin(particle.phase.toDouble()).toFloat() * 2f
                        particle.life -= 0.02f
                        particle.alpha = (255f * particle.life / 4f).coerceAtLeast(0f)
                        particle.radius *= 0.98f
                        if (particle.y < height * 0.2f || particle.life <= 0 || particle.radius < 0.5f) {
                            iterator.remove()
                        }
                    }
                    if (particle.colorChangeTime > 0f) {
                        particle.colorChangeTime -= 0.016f
                        if (particle.colorChangeTime <= 0f) {
                            particle.color = particle.originalColor
                        }
                    }
                }
            }
            if (particle.x < 0f || particle.x > width) {
                particle.velocityX = -particle.velocityX
                particle.x = particle.x.coerceIn(0f, width)
            }
            if (particle.y < 0f || particle.y > height) {
                particle.velocityY = -particle.velocityY
                particle.y = particle.y.coerceIn(0f, height)
            }
        }
    }

    fun draw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val groupedParticles = particles.groupBy { it.type to it.color }
        groupedParticles.forEach { (key, particles) ->
            val (type, color) = key
            paint.color = color
            glowPaint.color = color
            when (type) {
                FluidWallpaperService.EffectType.LIGHTNING -> {
                    if (particles.size > 1) {
                        val path = Path()
                        path.moveTo(particles[0].x, particles[0].y)
                        for (i in 1 until particles.size) {
                            val p1 = particles[i - 1]
                            val p2 = particles[i]
                            val midX = (p1.x + p2.x) / 2
                            val midY = (p1.y + p2.y) / 2
                            val ctrlX = midX + (Random.nextFloat() - 0.5f) * 20f
                            val ctrlY = midY + (Random.nextFloat() - 0.5f) * 20f
                            path.quadTo(ctrlX, ctrlY, p2.x, p2.y)
                        }
                        glowPaint.alpha = 100
                        glowPaint.strokeWidth = 10f
                        paint.strokeWidth = 10f
                        paint.style = Paint.Style.STROKE
                        canvas.drawPath(path, glowPaint)
                        canvas.drawPath(path, paint)
                    }
                }
                FluidWallpaperService.EffectType.COMET,
                FluidWallpaperService.EffectType.FIREWORK,
                FluidWallpaperService.EffectType.SPINNING_CIRCLE,
                FluidWallpaperService.EffectType.SCATTER_CONVERGE,
                FluidWallpaperService.EffectType.VORTEX,
                FluidWallpaperService.EffectType.WAVE,
                FluidWallpaperService.EffectType.GRID_WAVE,
                FluidWallpaperService.EffectType.FIRE -> {
                    glowPaint.strokeWidth = 0f
                    glowPaint.style = Paint.Style.FILL
                    paint.strokeWidth = 0f
                    paint.style = Paint.Style.FILL
                    particles.sortedByDescending { it.life }.forEach { particle ->
                        glowPaint.alpha = (particle.alpha * 0.5f).toInt()
                        canvas.drawCircle(particle.x, particle.y, particle.radius * 3f, glowPaint)
                        paint.alpha = particle.alpha.toInt()
                        canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
                    }
                }
                else -> {
                    particles.forEach { particle ->
                        glowPaint.alpha = (particle.alpha * 0.7f).toInt()
                        canvas.drawCircle(particle.x, particle.y, particle.radius + 8f, glowPaint)
                        paint.alpha = particle.alpha.toInt()
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(particle.x, particle.y, particle.radius, paint)
                    }
                }
            }
        }
        println("Drawing ${particles.size} particles")
    }

    private fun addLightning(startX: Float, startY: Float, endX: Float, endY: Float) {
        if (particles.size >= maxParticles - 20) particles.removeAt(0)
        val lightningColor = ContextCompat.getColor(context, listColor.random())
        val path = generateLightningPath(startX, startY, endX, endY, 8)
        val segmentCount = 20
        for (i in 0 until segmentCount) {
            val t = i.toFloat() / segmentCount
            val point = getPointOnPath(path, t)
            particles.add(
                Particle(
                    x = point.x,
                    y = point.y,
                    radius = Random.nextFloat() * 3f + 2f,
                    alpha = 255f,
                    velocityX = 0f,
                    velocityY = 0f,
                    life = 0.5f + Random.nextFloat() * 0.5f,
                    type = FluidWallpaperService.EffectType.LIGHTNING,
                    color = lightningColor,
                    originalX = point.x,
                    originalY = point.y,
                    originalColor = lightningColor
                )
            )
        }
        println("Added lightning from ($startX, $startY) to ($endX, $endY)")
    }

    private fun generateLightningPath(startX: Float, startY: Float, endX: Float, endY: Float, depth: Int): Path {
        val path = Path()
        path.moveTo(startX, startY)
        if (depth <= 0) {
            path.lineTo(endX, endY)
            return path
        }
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        val displace = 50f * depth / 16f
        val newMidX = midX + (Random.nextFloat() - 0.5f) * displace
        val newMidY = midY + (Random.nextFloat() - 0.5f) * displace
        generateLightningPath(startX, startY, newMidX, newMidY, depth - 1, path)
        generateLightningPath(newMidX, newMidY, endX, endY, depth - 1, path)
        return path
    }

    private fun generateLightningPath(x1: Float, y1: Float, x2: Float, y2: Float, depth: Int, path: Path) {
        if (depth <= 0) {
            path.lineTo(x2, y2)
            return
        }
        val midX = (x1 + x2) / 2
        val midY = (y1 + y2) / 2
        val displace = 50f * depth / 16f
        val newMidX = midX + (Random.nextFloat() - 0.5f) * displace
        val newMidY = midY + (Random.nextFloat() - 0.5f) * displace
        generateLightningPath(x1, y1, newMidX, newMidY, depth - 1, path)
        generateLightningPath(newMidX, newMidY, x2, y2, depth - 1, path)
    }

    private fun getPointOnPath(path: Path, t: Float): PointF {
        val measure = PathMeasure(path, false)
        val length = measure.length
        val coords = FloatArray(2)
        measure.getPosTan(length * t, coords, null)
        return PointF(coords[0], coords[1])
    }
}