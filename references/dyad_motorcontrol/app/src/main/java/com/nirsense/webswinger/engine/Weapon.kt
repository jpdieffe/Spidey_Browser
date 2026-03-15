package com.nirsense.webswinger.engine

import android.graphics.*

/**
 * Weapon types and related classes for the inventory system.
 *
 * Weapons are found as pickups in levels and stored in the player's inventory.
 * Each has limited charges. Tap the HUD slot to activate.
 */
enum class WeaponType(
    val label: String,
    val icon: String,
    val color: Int,
    val maxCharges: Int,
    val description: String
) {
    CRYO_WEB("Cryo Web", "❄", Color.rgb(100, 200, 255), 3,
        "Freezes enemies for 30 seconds"),
    SPIDER_BOT("Spider Bot", "🕷", Color.rgb(160, 80, 200), 2,
        "Deploys a robot spider that freezes enemies"),
    WEB_TURRET("Web Turret", "⚙", Color.rgb(80, 200, 80), 2,
        "Places a turret that shoots at enemies"),
    BOOM_WEB("Boom Web", "💥", Color.rgb(255, 100, 30), 3,
        "Explodes enemies on contact"),
    JET_WEB("Jet Web", "🚀", Color.rgb(255, 200, 50), 2,
        "Fly freely for 5 seconds"),
    SPRING_WEB("Spring Web", "⬆", Color.rgb(50, 255, 120), 3,
        "Next jump launches you extra high"),
    LASER_VISION("Laser Vision", "👁", Color.rgb(255, 50, 50), 3,
        "Shoot lasers instead of webs – explodes enemies!"),
    IRON_SPIDER("Iron Spider", "🛡", Color.rgb(180, 180, 190), 1,
        "Gray armor – takes 10 hits to die"),
    CLONE("Clone", "👤", Color.rgb(100, 150, 255), 1,
        "Summons an AI clone that webs enemies"),
    PLASMA_WEB("Plasma Web", "⚡", Color.rgb(200, 50, 255), 2,
        "Destroys terrain on contact")
}

/**
 * A weapon item in the player's inventory with remaining charges.
 */
data class WeaponItem(
    val type: WeaponType,
    var charges: Int = type.maxCharges
) {
    val isEmpty get() = charges <= 0
}

/**
 * Collectible weapon pickup placed in levels.
 * Drawn as a glowing orb with the weapon's icon/colour.
 */
class WeaponPickup(
    x: Float, y: Float,
    val weaponType: WeaponType,
    val charges: Int = weaponType.maxCharges
) {
    var pos = Vec2(x, y)
    var collected = false
    val size = 44f

    private var bobTime = (Math.random() * Math.PI * 2).toFloat()
    private var pulseTime = 0f

    fun update(dt: Float) {
        if (!collected) {
            bobTime += dt * 2.5f
            pulseTime += dt * 4f
        }
    }

    fun boundingRect(): RectF =
        RectF(pos.x - size / 2, pos.y - size / 2, pos.x + size / 2, pos.y + size / 2)

    fun draw(canvas: Canvas) {
        if (collected) return
        val bob = Math.sin(bobTime.toDouble()).toFloat() * 8f
        val cx = pos.x
        val cy = pos.y + bob
        val pulse = 1f + Math.sin(pulseTime.toDouble()).toFloat() * 0.15f

        // outer glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, Color.red(weaponType.color),
                Color.green(weaponType.color), Color.blue(weaponType.color))
        }
        canvas.drawCircle(cx, cy, size * 0.9f * pulse, glowPaint)

        // inner orb
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = weaponType.color; alpha = 200
        }
        canvas.drawCircle(cx, cy, size * 0.45f * pulse, orbPaint)

        // bright core
        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, size * 0.2f, corePaint)

        // icon text
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 22f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(weaponType.icon, cx, cy + 8f, iconPaint)

        // label below
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = weaponType.color; textSize = 16f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD; alpha = 200
        }
        canvas.drawText(weaponType.label, cx, cy + size * 0.7f + 14f, labelPaint)
    }
}
