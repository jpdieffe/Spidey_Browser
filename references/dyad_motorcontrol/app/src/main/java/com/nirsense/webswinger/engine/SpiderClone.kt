package com.nirsense.webswinger.engine

import android.content.Context
import android.graphics.*

/**
 * AI-controlled clone that uses the same player sprites tinted blue.
 * - Follows the player, swings from ceilings/platforms
 * - Fires webs at nearby unfrozen enemies to freeze them
 * - Limited 25 s lifetime
 */
class SpiderClone(context: Context, startX: Float, startY: Float) {

    // ---- dimensions (same as Player) ----
    val width = 90f
    val height = 135f

    var pos = Vec2(startX, startY)
    var vel = Vec2(0f, 0f)
    var alive = true
    var lifetime = 0f
    private val maxLifetime = 25f
    private val gravity = 2200f
    private val moveSpeed = 550f
    private val jumpVel = -1100f

    // AI
    private enum class AIState { FOLLOW, ATTACK, SWING }
    private var aiState = AIState.FOLLOW
    private var webTimer = 0f
    private val webCooldown = 1.2f
    private var attackCooldown = 0f

    // web visual
    private var webOriginX = 0f; private var webOriginY = 0f
    private var webTargetX = 0f; private var webTargetY = 0f
    private var webVisTimer = 0f

    // swing state
    private var isSwinging = false
    private var swingAnchorX = 0f; private var swingAnchorY = 0f
    private var ropeLength = 0f
    private var ropeAngle = 0f
    private var ropeAngularVel = 0f
    private var swingTimer = 0f
    private val maxSwingTime = 2.5f
    private var swingCooldown = 0f

    // ground / ceiling
    private var onGround = false
    private var onCeiling = false
    private var ceilingImmuneTimer = 0f

    // animation
    private var animTime = 0f
    var facingRight = true; private set
    private var runFrame = 0

    // ---- sprites (same assets as Player, tinted blue) ----
    private val blueTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.4f, 0f,   0f,   0f, 20f,
            0f,   0.5f, 0f,   0f, 40f,
            0f,   0f,   1.2f, 0f, 60f,
            0f,   0f,   0f,   0.85f, 0f
        )))
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

    private val webLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 120, 180, 255); strokeWidth = 2.5f; style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 80, 140, 255)
    }

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

    // ================================================================
    //  UPDATE
    // ================================================================
    fun update(
        dt: Float,
        playerPos: Vec2,
        playerFacingRight: Boolean,
        playerVelX: Float,
        enemies: List<Enemy>,
        ceilingCrawlers: List<CeilingCrawler>,
        shooterEnemies: List<ShooterEnemy>,
        jumpingRobots: List<JumpingRobot>,
        bats: List<Bat>,
        platforms: List<Platform>,
        worldWidth: Float,
        worldHeight: Float
    ): List<FreezeCommand> {
        if (!alive) return emptyList()
        lifetime += dt
        if (lifetime > maxLifetime) { alive = false; return emptyList() }

        animTime += dt
        webTimer -= dt
        attackCooldown -= dt
        webVisTimer -= dt
        swingCooldown -= dt
        if (ceilingImmuneTimer > 0f) ceilingImmuneTimer -= dt

        val freezeCommands = mutableListOf<FreezeCommand>()

        // ---- find nearest unfrozen enemy (with line-of-sight check) ----
        var nearestDist = Float.MAX_VALUE
        var nearestPos: Vec2? = null
        var nearestType = EnemyType.GROUND

        for (e in enemies) {
            if (e.frozen) continue
            val d = dist(pos, e.pos)
            if (d < nearestDist && hasLineOfSight(pos, e.pos, platforms)) { nearestDist = d; nearestPos = e.pos; nearestType = EnemyType.GROUND }
        }
        for (cc in ceilingCrawlers) {
            if (cc.frozen) continue
            val d = dist(pos, cc.pos)
            if (d < nearestDist && hasLineOfSight(pos, cc.pos, platforms)) { nearestDist = d; nearestPos = cc.pos; nearestType = EnemyType.CEILING }
        }
        for (se in shooterEnemies) {
            if (se.frozen) continue
            val d = dist(pos, se.pos)
            if (d < nearestDist && hasLineOfSight(pos, se.pos, platforms)) { nearestDist = d; nearestPos = se.pos; nearestType = EnemyType.SHOOTER }
        }
        for (jr in jumpingRobots) {
            if (jr.frozen) continue
            val d = dist(pos, jr.pos)
            if (d < nearestDist && hasLineOfSight(pos, jr.pos, platforms)) { nearestDist = d; nearestPos = jr.pos; nearestType = EnemyType.JUMPER }
        }
        for (bat in bats) {
            if (!bat.alive) continue
            val d = dist(pos, bat.pos)
            if (d < nearestDist && hasLineOfSight(pos, bat.pos, platforms)) { nearestDist = d; nearestPos = bat.pos; nearestType = EnemyType.BAT }
        }

        // ---- decide AI state ----
        val playerDist = dist(pos, playerPos)

        if (nearestPos != null && nearestDist < 450f && attackCooldown <= 0f) {
            aiState = AIState.ATTACK
        } else if (isSwinging) {
            aiState = AIState.SWING
        } else {
            aiState = AIState.FOLLOW
        }

        // ---- execute AI ----
        var idling = false
        when (aiState) {
            AIState.ATTACK -> {
                nearestPos?.let { target ->
                    if (webTimer <= 0f && nearestDist < 450f) {
                        webTimer = webCooldown
                        attackCooldown = 1.8f
                        webOriginX = pos.x; webOriginY = pos.y
                        webTargetX = target.x; webTargetY = target.y
                        webVisTimer = 0.35f
                        freezeCommands.add(FreezeCommand(target, nearestType))
                    }
                    moveToward(target.x, 300f, dt)
                }
            }
            AIState.FOLLOW -> {
                // Position behind the player (opposite of where they face)
                val behindOffset = if (playerFacingRight) -100f else 100f
                val targetX = playerPos.x + behindOffset
                val playerIdle = Math.abs(playerVelX) < 30f
                val dxToTarget = targetX - pos.x

                if (playerIdle && Math.abs(dxToTarget) < 70f && (onGround || onCeiling)) {
                    // Player is still and we're close — stop and chill
                    vel.x = 0f
                    facingRight = playerFacingRight
                    idling = true
                } else {
                    moveToward(targetX, moveSpeed, dt)
                }

                // jump if player is above
                if (onGround && playerPos.y < pos.y - 80f) {
                    vel.y = jumpVel; onGround = false
                }
                // drop from ceiling if player is below
                if (onCeiling && playerPos.y > pos.y + 80f) {
                    onCeiling = false; ceilingImmuneTimer = 0.4f; vel.y = 300f
                }

                // try to start a swing if falling and need to keep up
                if (!isSwinging && !onGround && !onCeiling && vel.y > 100f && swingCooldown <= 0f) {
                    tryStartSwing(platforms, playerPos)
                }
                // also swing if too far behind horizontally
                if (!isSwinging && !onCeiling && playerDist > 400f && swingCooldown <= 0f) {
                    tryStartSwing(platforms, playerPos)
                }
            }
            AIState.SWING -> { /* physics below */ }
        }

        // ---- swing physics ----
        if (isSwinging) {
            swingTimer += dt
            if (swingTimer > maxSwingTime) {
                releaseSwing()
            } else {
                val angAccel = -(gravity / ropeLength) * Math.sin(ropeAngle.toDouble()).toFloat()
                ropeAngularVel += angAccel * dt
                ropeAngularVel *= 0.995f
                ropeAngle += ropeAngularVel * dt

                pos.x = swingAnchorX + ropeLength * Math.sin(ropeAngle.toDouble()).toFloat()
                pos.y = swingAnchorY + ropeLength * Math.cos(ropeAngle.toDouble()).toFloat()

                val vt = ropeAngularVel * ropeLength
                val cosA = Math.cos(ropeAngle.toDouble()).toFloat()
                val sinA = Math.sin(ropeAngle.toDouble()).toFloat()
                vel.x = vt * cosA
                vel.y = -vt * sinA

                // release when heading toward player with momentum
                val dx = playerPos.x - pos.x
                if (swingTimer > 0.4f && ((dx > 0 && vel.x > 150f) || (dx < 0 && vel.x < -150f))) {
                    releaseSwing()
                }

                // release if swinging into a platform
                if (isSwinging) {
                    val swRect = boundingRect()
                    for (plat in platforms) {
                        if (RectF.intersects(swRect, plat.rect)) {
                            // push out of the platform
                            val oTop = swRect.bottom - plat.rect.top
                            val oBot = plat.rect.bottom - swRect.top
                            if (oTop in 0f..oBot) {
                                pos.y = plat.rect.top - height / 2
                                vel.y = minOf(vel.y, 0f)
                            } else {
                                pos.y = plat.rect.bottom + height / 2
                                vel.y = maxOf(vel.y, 0f)
                            }
                            releaseSwing()
                            break
                        }
                    }
                }
            }
        }

        // ---- gravity ----
        if (!isSwinging && !onGround && !onCeiling) {
            vel.y += gravity * dt
            vel.y = vel.y.coerceAtMost(1200f)  // terminal velocity to prevent tunneling
        }
        if (onCeiling) vel.y = 0f

        // ---- integrate position ----
        if (!isSwinging) {
            pos.x += vel.x * dt
            pos.y += vel.y * dt
        }

        // ---- clamp to world ----
        pos.x = pos.x.coerceIn(width / 2, worldWidth - width / 2)

        // ---- platform collisions ----
        val wasOnGround = onGround
        onGround = false
        // Ground stick: nudge down 1 px so collision overlap is always detected
        if (wasOnGround && vel.y >= 0f && !isSwinging) pos.y += 1f
        val prevCeiling = onCeiling
        if (!onCeiling) { /* only check ceiling stick if not already on one */ }
        val myRect = boundingRect()
        for (plat in platforms) {
            val r = plat.rect
            if (!RectF.intersects(myRect, r)) continue
            val overlapTop = myRect.bottom - r.top
            val overlapBot = r.bottom - myRect.top
            val overlapL = myRect.right - r.left
            val overlapR = r.right - myRect.left
            val minOX = minOf(overlapL, overlapR)
            val minOY = minOf(overlapTop, overlapBot)
            if (minOY < minOX) {
                if (overlapTop < overlapBot && overlapTop > 0 && vel.y >= 0) {
                    pos.y = r.top - height / 2
                    vel.y = 0f
                    onGround = true
                    if (isSwinging) releaseSwing()
                } else if (overlapBot < overlapTop && overlapBot > 0
                    && vel.y <= 0 && ceilingImmuneTimer <= 0f) {
                    pos.y = r.bottom + height / 2
                    vel.y = 0f
                    onCeiling = true
                    if (isSwinging) releaseSwing()
                }
            } else {
                if (overlapL < overlapR) pos.x = r.left - width / 2
                else pos.x = r.right + width / 2
                vel.x = 0f
            }
        }

        // ceiling = top of world
        if (pos.y - height / 2 < 20f && vel.y <= 0 && ceilingImmuneTimer <= 0f) {
            pos.y = 20f + height / 2
            vel.y = 0f
            onCeiling = true
            if (isSwinging) releaseSwing()
        }

        // Verify ceiling contact — detach if walked past platform edge
        if (onCeiling && pos.y - height / 2 > 25f) {
            // Not at world ceiling, must be under a platform — check overlap
            var stillUnderPlatform = false
            for (plat in platforms) {
                val r = plat.rect
                if (pos.x + width / 2 > r.left && pos.x - width / 2 < r.right
                    && Math.abs((pos.y - height / 2) - r.bottom) < 8f) {
                    stillUnderPlatform = true; break
                }
            }
            if (!stillUnderPlatform) {
                onCeiling = false
                ceilingImmuneTimer = 0.3f
            }
        }

        // world floor fallback — don't fall out of the level
        if (pos.y + height / 2 > worldHeight) {
            pos.y = worldHeight - height / 2
            vel.y = 0f
            onGround = true
        }

        // ---- facing direction ----
        if (!idling) {
            if (vel.x > 10f) facingRight = true
            else if (vel.x < -10f) facingRight = false
        }

        // run anim
        val running = (onGround || onCeiling) && Math.abs(vel.x) > 10f
        if (running) {
            if (animTime > 1f / 12f) {
                runFrame = (runFrame + 1) % runFrameCount; animTime = 0f
            }
        } else {
            runFrame = 0
        }

        return freezeCommands
    }

    private fun releaseSwing() {
        isSwinging = false
        swingCooldown = 0.3f
    }

    private fun tryStartSwing(platforms: List<Platform>, playerPos: Vec2) {
        var bestDist = Float.MAX_VALUE
        var bestX = 0f; var bestY = 0f

        // prefer anchor points in the direction of the player
        val dirBias = if (playerPos.x > pos.x) 1f else -1f

        for (plat in platforms) {
            val px = plat.rect.centerX()
            // bottom of platform as anchor
            for (py in listOf(plat.rect.bottom, plat.rect.top)) {
                if (py >= pos.y) continue
                val d = dist(pos, Vec2(px, py))
                if (d !in 60f..450f) continue
                // bias toward anchors in the direction of the player
                val xScore = (px - pos.x) * dirBias
                val score = d - xScore * 0.3f  // lower = better
                if (score < bestDist) {
                    bestDist = score; bestX = px; bestY = py
                }
            }
        }

        // ceiling as swing point — offset toward player
        val ceilY = 20f
        if (ceilY < pos.y) {
            val cx = pos.x + (playerPos.x - pos.x).coerceIn(-300f, 300f)
            val d = dist(pos, Vec2(cx, ceilY))
            if (d in 60f..500f) {
                val xScore = (cx - pos.x) * dirBias
                val score = d - xScore * 0.3f
                if (score < bestDist) {
                    bestDist = score; bestX = cx; bestY = ceilY
                }
            }
        }

        if (bestDist < Float.MAX_VALUE) {
            swingAnchorX = bestX; swingAnchorY = bestY
            val actualDist = dist(pos, Vec2(bestX, bestY))
            ropeLength = actualDist
            val dx = pos.x - bestX; val dy = pos.y - bestY
            ropeAngle = Math.atan2(dx.toDouble(), dy.toDouble()).toFloat()
            // seed angular velocity toward the player
            ropeAngularVel = vel.x / actualDist * 0.5f + dirBias * 0.8f
            isSwinging = true
            swingTimer = 0f
        }
    }

    private fun moveToward(tx: Float, speed: Float, dt: Float) {
        val dx = tx - pos.x
        if (onGround || onCeiling) {
            vel.x = when {
                dx > 60f -> speed
                dx < -60f -> -speed
                dx > 20f -> speed * 0.4f
                dx < -20f -> -speed * 0.4f
                else -> vel.x * 0.6f
            }
        } else {
            vel.x += (if (dx > 0) 1f else -1f) * speed * 2f * dt
            val maxAir = speed * 1.3f
            vel.x = vel.x.coerceIn(-maxAir, maxAir)
        }
    }

    /** True if no platform blocks the straight line from a to b. */
    private fun hasLineOfSight(a: Vec2, b: Vec2, platforms: List<Platform>): Boolean {
        for (plat in platforms) {
            if (lineIntersectsRect(a.x, a.y, b.x, b.y, plat.rect)) return false
        }
        return true
    }

    private fun lineIntersectsRect(x1: Float, y1: Float, x2: Float, y2: Float, r: RectF): Boolean {
        // Check if line segment (x1,y1)-(x2,y2) intersects the rectangle
        var tMin = 0f; var tMax = 1f
        val dx = x2 - x1; val dy = y2 - y1
        // X slab
        if (Math.abs(dx) < 0.001f) {
            if (x1 < r.left || x1 > r.right) return false
        } else {
            val invD = 1f / dx
            var t0 = (r.left - x1) * invD; var t1 = (r.right - x1) * invD
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tMin = maxOf(tMin, t0); tMax = minOf(tMax, t1)
            if (tMin > tMax) return false
        }
        // Y slab
        if (Math.abs(dy) < 0.001f) {
            if (y1 < r.top || y1 > r.bottom) return false
        } else {
            val invD = 1f / dy
            var t0 = (r.top - y1) * invD; var t1 = (r.bottom - y1) * invD
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tMin = maxOf(tMin, t0); tMax = minOf(tMax, t1)
            if (tMin > tMax) return false
        }
        return true
    }

    private fun dist(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // ================================================================
    //  DRAW — uses Player sprites with blue tint
    // ================================================================
    fun draw(canvas: Canvas) {
        if (!alive) return
        val cx = pos.x; val cy = pos.y

        val lifeLeft = maxLifetime - lifetime
        val alphaFrac = if (lifeLeft < 3f) (lifeLeft / 3f).coerceIn(0.1f, 1f) else 1f

        val currentPaint = if (alphaFrac < 1f) {
            Paint(blueTintPaint).apply { alpha = (255 * alphaFrac * 0.85f).toInt() }
        } else blueTintPaint

        // glow aura
        val gp = Paint(glowPaint).apply { alpha = (25 * alphaFrac).toInt().coerceAtLeast(5) }
        canvas.drawCircle(cx, cy, width.toFloat(), gp)

        // rope when swinging
        if (isSwinging) {
            val ropePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((180 * alphaFrac).toInt(), 120, 180, 255); strokeWidth = 2.5f
            }
            canvas.drawLine(cx, cy - height * 0.2f, swingAnchorX, swingAnchorY, ropePaint)
        }

        // web attack line
        if (webVisTimer > 0f) {
            val wp = Paint(webLinePaint).apply { alpha = (160 * alphaFrac).toInt() }
            canvas.drawLine(webOriginX, webOriginY, webTargetX, webTargetY, wp)
        }

        canvas.save()
        if (!facingRight) canvas.scale(-1f, 1f, cx, cy)
        if (onCeiling) canvas.scale(1f, -1f, cx, cy)

        val running = (onGround || onCeiling) && Math.abs(vel.x) > 10f

        when {
            onCeiling && !running -> drawSingle(canvas, stickSprite, cx, cy, currentPaint, 0.85f)
            running               -> drawRunFrame(canvas, cx, cy, currentPaint)
            webVisTimer > 0f && (onGround || onCeiling) -> drawSingle(canvas, webSprite, cx, cy, currentPaint)
            webVisTimer > 0f      -> drawSingle(canvas, webJumpSprite, cx, cy, currentPaint)
            isSwinging            -> drawSingle(canvas, swingSprite, cx, cy, currentPaint, 0.85f)
            !onGround && !onCeiling -> drawSingle(canvas, jumpSprite, cx, cy, currentPaint)
            else                  -> drawSingle(canvas, standSprite, cx, cy, currentPaint)
        }

        canvas.restore()

        // timer warning near end of life
        if (lifeLeft < 5f) {
            if ((lifeLeft * 4f).toInt() % 2 == 0) {
                val warnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(150, 100, 150, 255); textSize = 16f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("⏱", cx, cy - height / 2 - 8f, warnPaint)
            }
        }
    }

    private fun drawSingle(canvas: Canvas, bmp: Bitmap, cx: Float, cy: Float, paint: Paint, extraScale: Float = 1f) {
        val scale = height / bmp.height.toFloat() * extraScale
        val dw = bmp.width * scale; val dh = bmp.height * scale
        val bottom = cy + height / 2
        canvas.drawBitmap(bmp, null, RectF(cx - dw / 2, bottom - dh, cx + dw / 2, bottom), paint)
    }

    private fun drawRunFrame(canvas: Canvas, cx: Float, cy: Float, paint: Paint) {
        val src = Rect(runFrame * runFrameW, 0, (runFrame + 1) * runFrameW, runFrameH)
        val scale = height / runFrameH.toFloat() * 0.75f
        val dw = runFrameW * scale; val dh = runFrameH * scale
        val bottom = cy + height / 2
        canvas.drawBitmap(runSheet, src, RectF(cx - dw / 2, bottom - dh, cx + dw / 2, bottom), paint)
    }

    enum class EnemyType { GROUND, CEILING, SHOOTER, JUMPER, BAT }
    data class FreezeCommand(val target: Vec2, val type: EnemyType)
}
