package com.nirsense.webswinger.engine

import android.content.Context
import android.graphics.*

/**
 * Player character with full sprite-based animation.
 *
 * States driven by flags: onGround, onCeiling, isSwinging, isShootingWeb
 */
class Player(context: Context) {

    // ---- physics state ----
    var pos = Vec2(300f, 500f)
    var vel = Vec2(0f, 0f)
    var onGround = false
    var onCeiling = false

    // ---- dimensions ----
    val width = 90f
    val height = 135f

    // ---- tunables ----
    val moveSpeed = 600f
    val jumpVel = -1050f
    val superJumpVel = -1800f   // spring web super jump
    val gravity = 2200f
    var springJumpReady = false  // set by GameView when spring web is used
    var cryoMode = false         // set by GameView when cryo web is armed
    var ironSpiderActive = false // iron spider mode – gray palette, 10 hits
    var ironSpiderHits = 0       // hits remaining
    var ironSpiderIFrame = 0f    // invincibility timer after hit
    var laserActive = false      // laser currently firing (visual)
    var laserTimer = 0f          // remaining laser budget
    var laserStartX = 0f; var laserStartY = 0f  // beam start
    var laserEndX = 0f; var laserEndY = 0f      // beam end (wall-clipped)

    // ---- sprites ----
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val ironSpritePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    }

    private val runSheet: Bitmap = loadAsset(context, "user_run.png")
    private val runFrameCount = 6
    private val runFrameW = runSheet.width / runFrameCount
    private val runFrameH = runSheet.height

    private val standSprite: Bitmap   = loadAsset(context, "user_stand.png")
    private val jumpSprite: Bitmap    = loadAsset(context, "user_jump.png")
    private val webSprite: Bitmap     = loadAsset(context, "user_web.png")
    private val webJumpSprite: Bitmap = loadAsset(context, "user_web_jump.png")
    private val swingSprite: Bitmap   = loadAsset(context, "user_swing.png")
    private val stickSprite: Bitmap   = loadAsset(context, "user_stick.png")

    // ---- animation state ----
    private var animTime = 0f
    var runFrame = 0; private set

    private var webShootTimer = 0f
    private val webShootDuration = 0.25f

    // ---- web line paint ----
    private val webLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; strokeWidth = 3f; style = Paint.Style.STROKE
    }

    // ---- input ----
    var moveDir = 0f
    var climbInput = 0f       // +1 climb up, -1 descend
    var facingRight = true

    // ---- state flags (set by GameView) ----
    var isSwinging = false
    var isShootingWeb = false
    var ceilingImmuneTimer = 0f   // prevents re-sticking to ceiling right after dropping

    // ---- helpers ----
    private fun loadAsset(ctx: Context, name: String): Bitmap {
        val raw = BitmapFactory.decodeStream(ctx.assets.open(name))
        return removeGrayBackground(raw)
    }

    private fun removeGrayBackground(
        src: Bitmap, grayTol: Int = 45, lo: Int = 60, hi: Int = 240
    ): Bitmap {
        val w = src.width; val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]; val a = (c ushr 24) and 0xFF
            if (a == 0) continue
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val avg = (r + g + b) / 3
            val md = maxOf(Math.abs(r - g), Math.abs(r - b), Math.abs(g - b))
            if (md <= grayTol && avg in lo..hi) px[i] = 0
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    fun boundingRect(): RectF =
        RectF(pos.x - width / 2, pos.y - height / 2, pos.x + width / 2, pos.y + height / 2)

    // ---------- update ----------
    fun update(dt: Float, web: WebLine) {
        if (moveDir > 0) facingRight = true
        else if (moveDir < 0) facingRight = false

        // web-shoot flash timer
        if (isShootingWeb) {
            webShootTimer += dt
            if (webShootTimer >= webShootDuration) {
                isShootingWeb = false; webShootTimer = 0f
            }
        }

        // tick ceiling immunity
        if (ceilingImmuneTimer > 0f) ceilingImmuneTimer -= dt

        // gravity (not while on surface or swinging)
        if (!onGround && !onCeiling && !isSwinging) {
            vel.y += gravity * dt
        }

        // horizontal movement
        if (!isSwinging) {
            if (onGround || onCeiling) {
                vel.x = moveDir * moveSpeed
            } else {
                // air: only move laterally while actively pushing a direction
                vel.x = moveDir * moveSpeed
            }
        }

        // integrate (swing integration done in GameView)
        if (!isSwinging) {
            pos.x += vel.x * dt
            pos.y += vel.y * dt
        }

        // run animation
        animTime += dt
        val running = (onGround || onCeiling) && Math.abs(moveDir) > 0.1f
        if (running) {
            if (animTime > 1f / 12f) {
                runFrame = (runFrame + 1) % runFrameCount; animTime = 0f
            }
        } else {
            runFrame = 0
        }
    }

    fun jump(web: WebLine) {
        if (onGround) {
            if (web.state == WebLine.State.ATTACHED) {
                // Web is anchored – yank yourself up by shortening the rope
                onGround = false
                isSwinging = true
                // Calculate current rope geometry
                val dx = pos.x - web.attachPoint.x
                val dy = pos.y - web.attachPoint.y
                val fullLen = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                web.ropeAngle = Math.atan2(dx.toDouble(), dy.toDouble()).toFloat()
                // Shorten the rope a bit to lift off the ground
                val jumpPull = 50f
                web.ropeLength = (fullLen - jumpPull)
                    .coerceIn(web.minRopeLength, web.maxRopeLength)
                // Seed a small angular velocity from horizontal input for feel
                web.ropeAngularVel = moveDir * 1.5f
                // Velocity will be derived by pendulum next frame
                vel.y = 0f
            } else {
                val jv = if (springJumpReady) { springJumpReady = false; superJumpVel } else jumpVel
                vel.y = jv; onGround = false
            }
        }
    }

    fun dropFromCeiling(web: WebLine) {
        if (onCeiling) {
            onCeiling = false
            ceilingImmuneTimer = 0.5f  // ignore ceiling for 500ms
            if (web.state == WebLine.State.ATTACHED) {
                // Web is anchored – transition into swing
                isSwinging = true
                val dx = pos.x - web.attachPoint.x
                val dy = pos.y - web.attachPoint.y
                web.ropeLength = Math.sqrt((dx*dx + dy*dy).toDouble()).toFloat()
                    .coerceIn(web.minRopeLength, web.maxRopeLength)
                web.ropeAngle = Math.atan2(dx.toDouble(), dy.toDouble()).toFloat()
                web.ropeAngularVel = moveDir * 1.5f
                vel.y = 0f
            } else {
                vel.y = 400f
            }
        }
    }

    fun notifyWebShot() { isShootingWeb = true; webShootTimer = 0f }

    // ---------- draw ----------
    fun draw(canvas: Canvas, web: WebLine) {
        val cx = pos.x; val cy = pos.y
        val running = (onGround || onCeiling) && Math.abs(moveDir) > 0.1f
        val flipV = onCeiling

        canvas.save()
        if (!facingRight) canvas.scale(-1f, 1f, cx, cy)
        if (flipV) canvas.scale(1f, -1f, cx, cy)

        when {
            onCeiling && !running && !isShootingWeb ->
                drawSingle(canvas, stickSprite, cx, cy, 0.85f)
            running && !isShootingWeb ->
                drawRunFrame(canvas, cx, cy)
            isShootingWeb && (onGround || onCeiling) ->
                drawSingle(canvas, webSprite, cx, cy)
            isShootingWeb && !onGround && !onCeiling ->
                drawSingle(canvas, webJumpSprite, cx, cy)
            isSwinging ->
                drawSingle(canvas, swingSprite, cx, cy, 0.85f)
            !onGround && !onCeiling ->
                drawSingle(canvas, jumpSprite, cx, cy)
            else ->
                drawSingle(canvas, standSprite, cx, cy)
        }
        canvas.restore()

        // web line
        if (web.state == WebLine.State.FLYING || web.state == WebLine.State.ATTACHED) {
            val handX = if (facingRight) cx + width * 0.4f else cx - width * 0.4f
            val handY = cy - height * 0.2f

            if (cryoMode && web.state == WebLine.State.FLYING) {
                // cryo snowball: icy trailing line + snowball at tip
                val cryoLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(140, 140, 210, 255); strokeWidth = 4f; style = Paint.Style.STROKE
                }
                canvas.drawLine(handX, handY, web.target.x, web.target.y, cryoLinePaint)
                // snowball core
                val snowCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(220, 240, 255) }
                canvas.drawCircle(web.target.x, web.target.y, 10f, snowCore)
                // icy glow ring
                val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(80, 160, 220, 255); style = Paint.Style.STROKE; strokeWidth = 4f
                }
                canvas.drawCircle(web.target.x, web.target.y, 14f, glowPaint)
                // inner sparkle
                val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255) }
                canvas.drawCircle(web.target.x - 3f, web.target.y - 3f, 3f, sparkPaint)
            } else {
                canvas.drawLine(handX, handY, web.target.x, web.target.y, webLinePaint)
            }

            if (web.state == WebLine.State.ATTACHED) {
                val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
                canvas.drawCircle(web.attachPoint.x, web.attachPoint.y, 6f, sp)
            }
        }

        // iron spider i-frame blink is handled by the paint alpha in drawSingle/drawRunFrame

        // laser beam (directional, wall-clipped)
        if (laserActive) {
            // main beam
            val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 40, 40); strokeWidth = 6f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(laserStartX, laserStartY, laserEndX, laserEndY, beamPaint)
            // glow
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(60, 255, 100, 50); strokeWidth = 18f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(laserStartX, laserStartY, laserEndX, laserEndY, glowPaint)
            // core
            val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 255, 200, 150); strokeWidth = 2f; strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(laserStartX, laserStartY, laserEndX, laserEndY, corePaint)
            // impact flash at end
            val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(150, 255, 200, 80)
            }
            canvas.drawCircle(laserEndX, laserEndY, 12f, flashPaint)
        }
    }

    private fun drawSingle(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, extraScale: Float = 1f) {
        val scale = height / bmp.height.toFloat() * extraScale
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val bottom = cy + height / 2
        val paint = if (ironSpiderActive) ironSpritePaint else spritePaint
        // blink during i-frames: visible 70% of each cycle
        if (ironSpiderIFrame > 0f && ((ironSpiderIFrame * 10f).toInt() % 2 == 0)) {
            paint.alpha = 60
        } else {
            paint.alpha = 255
        }
        canvas.drawBitmap(bmp, null, RectF(cx - dw/2, bottom - dh, cx + dw/2, bottom), paint)
    }

    private fun drawRunFrame(canvas: Canvas, cx: Float, cy: Float) {
        val src = Rect(runFrame * runFrameW, 0, (runFrame+1) * runFrameW, runFrameH)
        val scale = height / runFrameH.toFloat() * 0.75f
        val dw = runFrameW * scale; val dh = runFrameH * scale
        val bottom = cy + height / 2
        val paint = if (ironSpiderActive) ironSpritePaint else spritePaint
        if (ironSpiderIFrame > 0f && ((ironSpiderIFrame * 10f).toInt() % 2 == 0)) {
            paint.alpha = 60
        } else {
            paint.alpha = 255
        }
        canvas.drawBitmap(runSheet, src, RectF(cx - dw/2, bottom - dh, cx + dw/2, bottom), paint)
    }
}
