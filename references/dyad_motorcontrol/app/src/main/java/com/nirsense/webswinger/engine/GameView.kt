package com.nirsense.webswinger.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * Main game surface.
 *
 * LEFT  2/3 of screen -> move left/right, swipe up to jump (or swipe down
 *                        to drop from ceiling), up/down to climb rope
 * RIGHT 1/3 of screen -> web-shooter joystick (drag to aim+fire, release
 *                        to detach)
 */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    // ---- game objects ----
    val player = Player(context)
    val web = WebLine()
    val platforms = mutableListOf<Platform>()
    val enemies = mutableListOf<Enemy>()
    val ceilingCrawlers = mutableListOf<CeilingCrawler>()
    val shooterEnemies = mutableListOf<ShooterEnemy>()
    val keys = mutableListOf<Key>()
    val gates = mutableListOf<Gate>()
    val crates = mutableListOf<Crate>()
    var door: Door? = null
    var camera = Vec2(0f, 0f)
    var webAttachedCrate: Crate? = null

    // ---- weapon system ----
    val weaponPickups = mutableListOf<WeaponPickup>()
    val inventory = mutableListOf<WeaponItem>()   // max 3 slots
    private var activeWeaponIndex = -1             // -1 = no weapon active
    val spiderBots = mutableListOf<SpiderBot>()
    val webTurrets = mutableListOf<WebTurret>()
    val explosions = mutableListOf<Explosion>()
    val deadEnemyIndices = mutableListOf<Int>()    // temp list for boom-web kills
    val bats = mutableListOf<Bat>()
    val jumpingRobots = mutableListOf<JumpingRobot>()
    val lavas = mutableListOf<Lava>()
    val spiderClones = mutableListOf<SpiderClone>()
    private val deadBatIndices = mutableListOf<Int>()

    // ---- cryo snowball trail ----
    private data class SnowParticle(var x: Float, var y: Float, var life: Float, var size: Float)
    private val snowTrail = mutableListOf<SnowParticle>()

    // ---- special weapon state ----
    private var cryoWebActive = false              // cryo mode toggled on
    private var cryoWebSlotIndex = -1              // inventory slot to consume charge from
    private var boomWebActive = false              // next web hit explodes
    private var jetWebActive = false               // flying mode
    private var jetWebTimer = 0f                   // remaining flight time
    private val jetWebDuration = 5f
    private var springWebActive = false            // next jump is super-high
    private var plasmaWebActive = false            // next web destroys terrain
    private var laserActive = false                // laser vision armed
    private var laserTimer = 0f                    // remaining laser budget (seconds)
    private val laserDuration = 5f
    private var laserFiringTimer = 0f              // visual timer for current shot
    private var laserStartX = 0f; private var laserStartY = 0f
    private var laserEndX = 0f; private var laserEndY = 0f

    // ---- weapon notification ----
    private var weaponNotifyText = ""
    private var weaponNotifyTimer = 0f

    // ---- world dimensions (set per-level) ----
    private var worldWidth = 0f
    private var worldHeight = 0f

    // ---- level state ----
    var currentLevel = 1
    private val totalLevels = 30
    private var levelCompleteTimer = -1f   // >0 means showing "Level Complete"

    // ---- menu / progress ----
    private enum class GameState { MENU, STORE, PLAYING }
    private var gameState = GameState.MENU
    private val prefs = context.getSharedPreferences("webswinger_progress", Context.MODE_PRIVATE)
    private var maxUnlockedLevel = 1
    private val beatenLevels = mutableSetOf<Int>()

    // ---- store / stars ----
    private var stars = 0
    private val purchasedItems = mutableMapOf<WeaponType, Boolean>()
    private val storePrices = mapOf(
        WeaponType.SPRING_WEB  to 2,
        WeaponType.CRYO_WEB    to 2,
        WeaponType.SPIDER_BOT  to 3,
        WeaponType.WEB_TURRET  to 3,
        WeaponType.BOOM_WEB    to 4,
        WeaponType.JET_WEB     to 5,
        WeaponType.LASER_VISION to 5,
        WeaponType.IRON_SPIDER to 6,
        WeaponType.CLONE       to 7,
        WeaponType.PLASMA_WEB  to 4
    )
    private val storeOrder = listOf(
        WeaponType.SPRING_WEB, WeaponType.CRYO_WEB, WeaponType.SPIDER_BOT,
        WeaponType.WEB_TURRET, WeaponType.BOOM_WEB, WeaponType.JET_WEB,
        WeaponType.LASER_VISION, WeaponType.PLASMA_WEB, WeaponType.IRON_SPIDER, WeaponType.CLONE
    )

    // ---- menu scroll ----
    private var menuScrollY = 0f           // current scroll offset (positive = scrolled down)
    private var menuScrollVel = 0f         // fling velocity
    private var menuMaxScroll = 0f         // computed max scroll distance
    private var menuTouchStartY = 0f       // finger down Y for drag detection
    private var menuTouchLastY = 0f        // last finger Y for velocity
    private var menuIsDragging = false     // true once finger has moved enough
    private var menuTouchDownX = 0f        // for tap detection
    private var menuTouchDownY = 0f
    private var menuTouchActive = false    // true between ACTION_DOWN and ACTION_UP in menu

    // ---- thread ----
    private var thread: Thread? = null
    @Volatile private var running = false

    // ---- timing ----
    private val targetFPS = 60
    private val framePeriod = 1000L / targetFPS

    // ---- HUD / joystick ----
    private var joystickCenterX = 0f
    private var joystickCenterY = 0f
    private var joystickRadius = 0f
    private var joystickKnobX = 0f
    private var joystickKnobY = 0f
    private var joystickActive = false

    // ---- movement touch tracking ----
    private var moveTouchId = -1
    private var moveTouchStartX = 0f
    private var moveTouchStartY = 0f
    private var moveTouchCurrentX = 0f
    private var moveTouchCurrentY = 0f
    private var hasSwipedThisTouch = false

    // ---- web joystick touch ----
    private var webTouchId = -1
    private var webJoystickArmed = false  // must return to center before re-firing

    // ---- screen dims ----
    private var screenW = 0f
    private var screenH = 0f

    // ---- paints ----
    private val platPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 80, 95) }
    private val platHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(110, 110, 130); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val joystickBasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val joystickKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 80, 80)
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255); textSize = 32f; typeface = Typeface.MONOSPACE
    }
    private val separatorPaint = Paint().apply {
        color = Color.argb(40, 255, 255, 255); strokeWidth = 2f
    }
    private val skyGradientPaint = Paint()

    // ---- swing damping ----
    private val swingDamping = 0.995f

    init { holder.addCallback(this); isFocusable = true }

    // ================================================================
    //  PROGRESS PERSISTENCE
    // ================================================================
    private fun loadProgress() {
        maxUnlockedLevel = prefs.getInt("maxUnlockedLevel", 1)
        beatenLevels.clear()
        val beaten = prefs.getStringSet("beatenLevels", emptySet()) ?: emptySet()
        beatenLevels.addAll(beaten.mapNotNull { it.toIntOrNull() })
        stars = prefs.getInt("stars", 0)
        purchasedItems.clear()
        val purchased = prefs.getStringSet("purchasedItems", emptySet()) ?: emptySet()
        for (name in purchased) {
            try { purchasedItems[WeaponType.valueOf(name)] = true } catch (_: Exception) {}
        }
    }
    private fun saveProgress() {
        prefs.edit()
            .putInt("maxUnlockedLevel", maxUnlockedLevel)
            .putStringSet("beatenLevels", beatenLevels.map { it.toString() }.toSet())
            .putInt("stars", stars)
            .putStringSet("purchasedItems", purchasedItems.keys.map { it.name }.toSet())
            .apply()
    }

    // ================================================================
    //  LEVEL GENERATION
    // ================================================================
    private fun buildLevel() {
        platforms.clear(); enemies.clear(); ceilingCrawlers.clear(); shooterEnemies.clear()
        keys.clear(); gates.clear(); crates.clear()
        weaponPickups.clear(); spiderBots.clear(); webTurrets.clear(); explosions.clear()
        bats.clear(); jumpingRobots.clear(); lavas.clear(); spiderClones.clear()
        snowTrail.clear()
        door = null; levelCompleteTimer = -1f; webAttachedCrate = null
        web.release(); player.isSwinging = false; player.isShootingWeb = false
        player.onGround = false; player.onCeiling = false
        player.vel.set(0f, 0f)
        // reset weapon modifiers (keep inventory across levels)
        cryoWebActive = false; cryoWebSlotIndex = -1; boomWebActive = false
        jetWebActive = false; jetWebTimer = 0f; springWebActive = false
        plasmaWebActive = false; laserActive = false; laserTimer = 0f; laserFiringTimer = 0f
        player.ironSpiderActive = false; player.ironSpiderHits = 0; player.ironSpiderIFrame = 0f
        player.laserActive = false; player.laserTimer = 0f
        activeWeaponIndex = -1; player.springJumpReady = false
        deadEnemyIndices.clear(); deadBatIndices.clear()

        when ((currentLevel - 1) % totalLevels) {
            0 -> buildLevel1()
            1 -> buildLevel2()
            2 -> buildLevel4()
            3 -> buildLevel5()
            4 -> buildLevel7()
            5 -> buildLevel6_cryo()
            6 -> buildLevel7_spiderbot()
            7 -> buildLevel8_turretBoom()
            8 -> buildLevel9_jetWeb()
            9 -> buildLevel10_ultimate()
            10 -> buildLevel11_batIntro()
            11 -> buildLevel12_jumpRobots()
            12 -> buildLevel13_lavaRun()
            13 -> buildLevel14_batSwarm()
            14 -> buildLevel15_mixedChaos()
            15 -> buildLevel16_laserIntro()
            16 -> buildLevel17_ironSpider()
            17 -> buildLevel18_cloneWars()
            18 -> buildLevel19_plasmaWeb()
            19 -> buildLevel20_gauntlet()
            20 -> buildLevel21_lavaCaves()
            21 -> buildLevel22_skyBats()
            22 -> buildLevel23_robotFactory()
            23 -> buildLevel24_lavaGauntlet()
            24 -> buildLevel25_darkTower()
            25 -> buildLevel26_batCaves()
            26 -> buildLevel27_mechArena()
            27 -> buildLevel28_inferno()
            28 -> buildLevel29_skyFortress()
            29 -> buildLevel30_finale()
        }

        // boundary walls shared by all levels
        val floorY = worldHeight - 60f
        platforms.add(Platform(RectF(-200f, floorY, worldWidth + 200f, worldHeight + 200f)))   // floor
        platforms.add(Platform(RectF(-200f, -200f, worldWidth + 200f, 20f)))                    // ceiling
        platforms.add(Platform(RectF(-200f, -200f, 20f, worldHeight + 200f)))                   // left wall
        platforms.add(Platform(RectF(worldWidth - 20f, -200f, worldWidth + 200f, worldHeight + 200f))) // right wall
    }

    // ---- Level 1: Tutorial – simple traversal ----
    private fun buildLevel1() {
        worldWidth = screenW * 2.5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // a few stepping-stone platforms
        platforms.add(Platform(RectF(500f, floorY - 180f, 680f, floorY - 152f)))
        platforms.add(Platform(RectF(900f, floorY - 260f, 1100f, floorY - 232f)))
        platforms.add(Platform(RectF(1350f, floorY - 200f, 1520f, floorY - 172f)))

        // one slow enemy
        enemies.add(Enemy(800f, floorY, 500f, 1100f))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 2: Blue key + gate, ceiling crawlers introduced ----
    private fun buildLevel2() {
        worldWidth = screenW * 3f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        platforms.add(Platform(RectF(450f, floorY - 240f, 650f, floorY - 212f)))
        platforms.add(Platform(RectF(850f, floorY - 340f, 1060f, floorY - 312f)))
        platforms.add(Platform(RectF(1250f, floorY - 200f, 1450f, floorY - 172f)))
        platforms.add(Platform(RectF(1700f, floorY - 280f, 1900f, floorY - 252f)))

        // blue key on a high platform
        keys.add(Key(950f, floorY - 360f, Key.KeyColor.BLUE))

        // blue gate blocking the exit
        gates.add(Gate(RectF(2100f, 20f, 2130f, floorY), Key.KeyColor.BLUE))

        // enemies
        enemies.add(Enemy(600f, floorY, 300f, 900f))
        enemies.add(Enemy(1600f, floorY, 1400f, 1900f))

        // ceiling crawlers discourage ceiling-running cheese
        ceilingCrawlers.add(CeilingCrawler(500f, 20f, 100f, 1200f))
        ceilingCrawlers.add(CeilingCrawler(1600f, 20f, 1100f, 2200f))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 3: VERTICAL Tower Climb ----
    // ---- Level 4: Crate Puzzle + dual keys, medium ----
    private fun buildLevel4() {
        worldWidth = screenW * 3f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // high ledge with crate
        platforms.add(Platform(RectF(650f, floorY - 350f, 850f, floorY - 322f)))
        crates.add(Crate(750f, floorY - 322f))

        // wall creating a corridor (need to pull crate to crush/block enemy)
        platforms.add(Platform(RectF(1050f, floorY - 280f, 1080f, floorY)))

        // enemy patrolling before the wall
        enemies.add(Enemy(900f, floorY, 700f, 1040f))

        // more traversal
        platforms.add(Platform(RectF(1150f, floorY - 200f, 1350f, floorY - 172f)))
        platforms.add(Platform(RectF(1550f, floorY - 300f, 1750f, floorY - 272f)))
        platforms.add(Platform(RectF(1950f, floorY - 240f, 2150f, floorY - 212f)))

        // pillar for red key
        platforms.add(Platform(RectF(1800f, floorY - 380f, 1830f, floorY - 272f)))

        // keys
        keys.add(Key(750f, floorY - 370f, Key.KeyColor.BLUE))
        keys.add(Key(1815f, floorY - 400f, Key.KeyColor.RED))

        // gates
        gates.add(Gate(RectF(1450f, 20f, 1480f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(2350f, 20f, 2380f, floorY), Key.KeyColor.RED))

        // more enemies
        enemies.add(Enemy(1250f, floorY, 1090f, 1440f))
        enemies.add(Enemy(2050f, floorY, 1850f, 2250f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(500f, 20f, 100f, 1050f))
        ceilingCrawlers.add(CeilingCrawler(1700f, 20f, 1450f, 2400f))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 5: Shooters Introduced, medium-hard ----
    private fun buildLevel5() {
        worldWidth = screenW * 3.5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // varied platforms
        platforms.add(Platform(RectF(400f, floorY - 220f, 600f, floorY - 192f)))
        platforms.add(Platform(RectF(800f, floorY - 320f, 1000f, floorY - 292f)))
        platforms.add(Platform(RectF(1200f, floorY - 180f, 1420f, floorY - 152f)))
        platforms.add(Platform(RectF(1650f, floorY - 340f, 1850f, floorY - 312f)))
        platforms.add(Platform(RectF(2050f, floorY - 260f, 2250f, floorY - 232f)))
        platforms.add(Platform(RectF(2500f, floorY - 300f, 2700f, floorY - 272f)))

        // pillars with shooters on top
        platforms.add(Platform(RectF(900f, floorY - 400f, 930f, floorY - 292f)))
        platforms.add(Platform(RectF(2550f, floorY - 380f, 2580f, floorY - 272f)))

        // shooter enemies on elevated platforms
        shooterEnemies.add(ShooterEnemy(915f, floorY - 400f))
        shooterEnemies.add(ShooterEnemy(2565f, floorY - 380f))

        // ground enemies
        enemies.add(Enemy(500f, floorY, 200f, 800f))
        enemies.add(Enemy(1300f, floorY, 1050f, 1550f))
        enemies.add(Enemy(2300f, floorY, 2100f, 2500f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(700f, 20f, 200f, 1200f))
        ceilingCrawlers.add(CeilingCrawler(2000f, 20f, 1600f, 2800f))

        // red key high up (must swing to reach)
        keys.add(Key(1750f, floorY - 400f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2900f, 20f, 2930f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 6: VERTICAL Ascent – hard ----
    // ---- Level 7: The Gauntlet – very hard ----
    private fun buildLevel7() {
        worldWidth = screenW * 4f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // dense platforms for swinging
        for (i in 0..11) {
            val px = 350f + i * 310f
            val py = floorY - 180f - (i % 3) * 130f
            platforms.add(Platform(RectF(px, py, px + 150f, py + 28f)))
        }

        // pillars
        platforms.add(Platform(RectF(800f, floorY - 370f, 830f, floorY)))
        platforms.add(Platform(RectF(1600f, floorY - 370f, 1630f, floorY)))
        platforms.add(Platform(RectF(2400f, floorY - 370f, 2430f, floorY)))
        platforms.add(Platform(RectF(3200f, floorY - 370f, 3230f, floorY)))

        // ground enemies
        enemies.add(Enemy(500f, floorY, 250f, 790f))
        enemies.add(Enemy(1000f, floorY, 840f, 1590f))
        enemies.add(Enemy(1400f, floorY, 840f, 1590f))
        enemies.add(Enemy(1900f, floorY, 1640f, 2390f))
        enemies.add(Enemy(2700f, floorY, 2440f, 3190f))
        enemies.add(Enemy(3400f, floorY, 3240f, worldWidth - 100f))

        // shooters on pillars (alternate with crates)
        val se1 = ShooterEnemy(1615f, floorY - 370f); se1.shootInterval = 1.5f
        shooterEnemies.add(se1)
        val se2 = ShooterEnemy(3215f, floorY - 370f); se2.shootInterval = 1.5f
        shooterEnemies.add(se2)

        // crates on other pillars
        crates.add(Crate(815f, floorY - 370f))
        crates.add(Crate(2415f, floorY - 370f))

        // ceiling crawlers everywhere
        ceilingCrawlers.add(CeilingCrawler(400f, 20f, 100f, 1200f))
        ceilingCrawlers.add(CeilingCrawler(1400f, 20f, 1000f, 2100f))
        ceilingCrawlers.add(CeilingCrawler(2600f, 20f, 2100f, 3300f))
        ceilingCrawlers.add(CeilingCrawler(3500f, 20f, 3200f, worldWidth - 100f))

        // keys
        keys.add(Key(1615f, floorY - 440f, Key.KeyColor.BLUE))
        keys.add(Key(3215f, floorY - 440f, Key.KeyColor.RED))

        // gates
        gates.add(Gate(RectF(2000f, 20f, 2030f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(3500f, 20f, 3530f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 6: Cryo Web Introduction – lots of enemies, first weapon ----
    private fun buildLevel6_cryo() {
        worldWidth = screenW * 3.5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // platforms
        platforms.add(Platform(RectF(400f, floorY - 200f, 620f, floorY - 172f)))
        platforms.add(Platform(RectF(820f, floorY - 300f, 1020f, floorY - 272f)))
        platforms.add(Platform(RectF(1200f, floorY - 200f, 1400f, floorY - 172f)))
        platforms.add(Platform(RectF(1600f, floorY - 280f, 1820f, floorY - 252f)))
        platforms.add(Platform(RectF(2050f, floorY - 340f, 2250f, floorY - 312f)))
        platforms.add(Platform(RectF(2450f, floorY - 220f, 2650f, floorY - 192f)))
        platforms.add(Platform(RectF(2850f, floorY - 300f, 3050f, floorY - 272f)))

        // many ground enemies – this is where cryo web shines
        enemies.add(Enemy(500f, floorY, 200f, 700f))
        enemies.add(Enemy(900f, floorY, 700f, 1100f))
        enemies.add(Enemy(1300f, floorY, 1100f, 1500f))
        enemies.add(Enemy(1700f, floorY, 1500f, 1900f))
        enemies.add(Enemy(2150f, floorY, 1950f, 2350f))
        enemies.add(Enemy(2550f, floorY, 2350f, 2750f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(600f, 20f, 200f, 1200f))
        ceilingCrawlers.add(CeilingCrawler(1800f, 20f, 1400f, 2200f))
        ceilingCrawlers.add(CeilingCrawler(2800f, 20f, 2400f, 3200f))

        // CRYO WEB pickup early in level!
        weaponPickups.add(WeaponPickup(920f, floorY - 330f, WeaponType.CRYO_WEB))

        // gate + key
        keys.add(Key(2150f, floorY - 380f, Key.KeyColor.BLUE))
        gates.add(Gate(RectF(2700f, 20f, 2730f, floorY), Key.KeyColor.BLUE))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 7: Spider Bot – enemy swarm level ----
    private fun buildLevel7_spiderbot() {
        worldWidth = screenW * 4f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // platforms
        for (i in 0..9) {
            val px = 350f + i * 350f
            val py = floorY - 180f - (i % 3) * 100f
            platforms.add(Platform(RectF(px, py, px + 170f, py + 28f)))
        }

        // walls/pillars
        platforms.add(Platform(RectF(1200f, floorY - 350f, 1230f, floorY)))
        platforms.add(Platform(RectF(2400f, floorY - 350f, 2430f, floorY)))

        // lots of enemies (swarm!) – spider bot is great here
        enemies.add(Enemy(400f, floorY, 200f, 600f))
        enemies.add(Enemy(700f, floorY, 500f, 900f))
        enemies.add(Enemy(1000f, floorY, 800f, 1190f))
        enemies.add(Enemy(1350f, floorY, 1240f, 1600f))
        enemies.add(Enemy(1700f, floorY, 1500f, 1900f))
        enemies.add(Enemy(2100f, floorY, 1900f, 2390f))
        enemies.add(Enemy(2600f, floorY, 2440f, 2900f))
        enemies.add(Enemy(3000f, floorY, 2800f, 3200f))
        enemies.add(Enemy(3400f, floorY, 3200f, worldWidth - 100f))

        // shooters
        shooterEnemies.add(ShooterEnemy(1215f, floorY - 350f))
        shooterEnemies.add(ShooterEnemy(2415f, floorY - 350f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 200f, 1500f))
        ceilingCrawlers.add(CeilingCrawler(2000f, 20f, 1500f, 2800f))
        ceilingCrawlers.add(CeilingCrawler(3200f, 20f, 2800f, worldWidth - 100f))

        // SPIDER BOT pickup
        weaponPickups.add(WeaponPickup(700f, floorY - 260f, WeaponType.SPIDER_BOT))
        // also a cryo web for good measure
        weaponPickups.add(WeaponPickup(2100f, floorY - 280f, WeaponType.CRYO_WEB, 2))

        // keys + gates
        keys.add(Key(1700f, floorY - 320f, Key.KeyColor.RED))
        gates.add(Gate(RectF(3100f, 20f, 3130f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 8: Turret & Boom Web – fortress gauntlet ----
    private fun buildLevel8_turretBoom() {
        worldWidth = screenW * 4.5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // dense platform/pillar layout
        for (i in 0..13) {
            val px = 300f + i * 280f
            val py = floorY - 160f - (i % 4) * 90f
            platforms.add(Platform(RectF(px, py, px + 140f, py + 28f)))
        }

        // tall pillars with shooters
        platforms.add(Platform(RectF(900f, floorY - 400f, 930f, floorY)))
        platforms.add(Platform(RectF(1800f, floorY - 400f, 1830f, floorY)))
        platforms.add(Platform(RectF(2700f, floorY - 400f, 2730f, floorY)))
        platforms.add(Platform(RectF(3600f, floorY - 400f, 3630f, floorY)))

        val se1 = ShooterEnemy(915f, floorY - 400f); se1.shootInterval = 1.5f; shooterEnemies.add(se1)
        val se2 = ShooterEnemy(1815f, floorY - 400f); se2.shootInterval = 1.3f; shooterEnemies.add(se2)
        val se3 = ShooterEnemy(2715f, floorY - 400f); se3.shootInterval = 1.2f; shooterEnemies.add(se3)
        val se4 = ShooterEnemy(3615f, floorY - 400f); se4.shootInterval = 1.0f; shooterEnemies.add(se4)

        // ground enemies
        enemies.add(Enemy(500f, floorY, 200f, 890f))
        enemies.add(Enemy(1100f, floorY, 940f, 1790f))
        enemies.add(Enemy(1500f, floorY, 940f, 1790f))
        enemies.add(Enemy(2200f, floorY, 1840f, 2690f))
        enemies.add(Enemy(3000f, floorY, 2740f, 3590f))
        enemies.add(Enemy(3400f, floorY, 2740f, 3590f))
        enemies.add(Enemy(3800f, floorY, 3640f, worldWidth - 100f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(600f, 20f, 200f, 1400f))
        ceilingCrawlers.add(CeilingCrawler(1600f, 20f, 1400f, 2600f))
        ceilingCrawlers.add(CeilingCrawler(2800f, 20f, 2600f, 3800f))
        ceilingCrawlers.add(CeilingCrawler(3900f, 20f, 3600f, worldWidth - 100f))

        // WEB TURRET pickup
        weaponPickups.add(WeaponPickup(600f, floorY - 240f, WeaponType.WEB_TURRET))
        // BOOM WEB pickup later in level
        weaponPickups.add(WeaponPickup(2200f, floorY - 300f, WeaponType.BOOM_WEB))
        // bonus cryo
        weaponPickups.add(WeaponPickup(3400f, floorY - 280f, WeaponType.CRYO_WEB, 2))

        // keys + gates
        keys.add(Key(1500f, floorY - 440f, Key.KeyColor.BLUE))
        keys.add(Key(3200f, floorY - 440f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2100f, 20f, 2130f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4000f, 20f, 4030f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 9: Jet Web – big gaps, elevated chaos ----
    private fun buildLevel9_jetWeb() {
        worldWidth = screenW * 5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // spread-out platforms with big gaps (need jet web to cross some)
        platforms.add(Platform(RectF(400f, floorY - 220f, 600f, floorY - 192f)))
        platforms.add(Platform(RectF(900f, floorY - 320f, 1050f, floorY - 292f)))
        // big gap here – jet web territory
        platforms.add(Platform(RectF(1600f, floorY - 280f, 1800f, floorY - 252f)))
        platforms.add(Platform(RectF(2200f, floorY - 340f, 2400f, floorY - 312f)))
        // another big gap
        platforms.add(Platform(RectF(3000f, floorY - 200f, 3200f, floorY - 172f)))
        platforms.add(Platform(RectF(3500f, floorY - 300f, 3700f, floorY - 272f)))
        platforms.add(Platform(RectF(4000f, floorY - 240f, 4200f, floorY - 212f)))
        platforms.add(Platform(RectF(4600f, floorY - 280f, 4800f, floorY - 252f)))

        // elevated platforms (hard to reach without jet)
        platforms.add(Platform(RectF(1300f, floorY - 450f, 1500f, floorY - 422f)))
        platforms.add(Platform(RectF(2700f, floorY - 460f, 2900f, floorY - 432f)))

        // many shooters on elevated spots
        val se1 = ShooterEnemy(1400f, floorY - 450f); se1.shootInterval = 1.5f; shooterEnemies.add(se1)
        val se2 = ShooterEnemy(2800f, floorY - 460f); se2.shootInterval = 1.3f; shooterEnemies.add(se2)
        val se3 = ShooterEnemy(4100f, floorY - 240f); se3.shootInterval = 1.8f; shooterEnemies.add(se3)

        // ground enemies
        enemies.add(Enemy(500f, floorY, 200f, 800f))
        enemies.add(Enemy(1700f, floorY, 1400f, 2000f))
        enemies.add(Enemy(2300f, floorY, 2100f, 2600f))
        enemies.add(Enemy(3100f, floorY, 2900f, 3400f))
        enemies.add(Enemy(3600f, floorY, 3400f, 3900f))
        enemies.add(Enemy(4200f, floorY, 4000f, 4500f))
        enemies.add(Enemy(4700f, floorY, 4500f, worldWidth - 100f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 200f, 1600f))
        ceilingCrawlers.add(CeilingCrawler(2000f, 20f, 1600f, 3000f))
        ceilingCrawlers.add(CeilingCrawler(3500f, 20f, 3000f, 4200f))
        ceilingCrawlers.add(CeilingCrawler(4500f, 20f, 4200f, worldWidth - 100f))

        // JET WEB pickup!
        weaponPickups.add(WeaponPickup(950f, floorY - 360f, WeaponType.JET_WEB))
        // spider bot + boom web for variety
        weaponPickups.add(WeaponPickup(2300f, floorY - 380f, WeaponType.SPIDER_BOT))
        weaponPickups.add(WeaponPickup(3600f, floorY - 340f, WeaponType.BOOM_WEB, 2))

        // keys + gates
        keys.add(Key(1400f, floorY - 490f, Key.KeyColor.BLUE))
        keys.add(Key(4600f, floorY - 320f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2500f, 20f, 2530f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4900f, 20f, 4930f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 10: Ultimate Challenge – all weapons available ----
    private fun buildLevel10_ultimate() {
        worldWidth = screenW * 5.5f
        worldHeight = screenH
        val floorY = worldHeight - 60f
        player.pos.set(200f, floorY - 100f)

        // complex platform layout
        for (i in 0..16) {
            val px = 300f + i * 290f
            val py = floorY - 140f - (i % 5) * 80f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        // pillars
        platforms.add(Platform(RectF(800f, floorY - 420f, 830f, floorY)))
        platforms.add(Platform(RectF(1500f, floorY - 420f, 1530f, floorY)))
        platforms.add(Platform(RectF(2200f, floorY - 420f, 2230f, floorY)))
        platforms.add(Platform(RectF(2900f, floorY - 420f, 2930f, floorY)))
        platforms.add(Platform(RectF(3600f, floorY - 420f, 3630f, floorY)))
        platforms.add(Platform(RectF(4300f, floorY - 420f, 4330f, floorY)))

        // many ground enemies
        for (i in 0..11) {
            val ex = 400f + i * 380f
            val lb = (ex - 200f).coerceAtLeast(200f)
            val rb = (ex + 200f).coerceAtMost(worldWidth - 100f)
            enemies.add(Enemy(ex, floorY, lb, rb))
        }

        // shooters on every other pillar
        val se1 = ShooterEnemy(815f, floorY - 420f); se1.shootInterval = 1.3f; shooterEnemies.add(se1)
        val se2 = ShooterEnemy(2215f, floorY - 420f); se2.shootInterval = 1.0f; shooterEnemies.add(se2)
        val se3 = ShooterEnemy(3615f, floorY - 420f); se3.shootInterval = 1.0f; shooterEnemies.add(se3)

        // crates on other pillars
        crates.add(Crate(1515f, floorY - 420f))
        crates.add(Crate(2915f, floorY - 420f))
        crates.add(Crate(4315f, floorY - 420f))

        // ceiling crawlers everywhere
        ceilingCrawlers.add(CeilingCrawler(500f, 20f, 100f, 1200f))
        ceilingCrawlers.add(CeilingCrawler(1200f, 20f, 900f, 2000f))
        ceilingCrawlers.add(CeilingCrawler(2200f, 20f, 1800f, 3000f))
        ceilingCrawlers.add(CeilingCrawler(3200f, 20f, 2800f, 4000f))
        ceilingCrawlers.add(CeilingCrawler(4200f, 20f, 3800f, worldWidth - 100f))

        // ALL weapon pickups – player gets to choose!
        weaponPickups.add(WeaponPickup(500f, floorY - 240f, WeaponType.SPRING_WEB))
        weaponPickups.add(WeaponPickup(1200f, floorY - 280f, WeaponType.CRYO_WEB, 3))
        weaponPickups.add(WeaponPickup(1900f, floorY - 320f, WeaponType.SPIDER_BOT))
        weaponPickups.add(WeaponPickup(2600f, floorY - 300f, WeaponType.WEB_TURRET))
        weaponPickups.add(WeaponPickup(3300f, floorY - 280f, WeaponType.BOOM_WEB, 3))
        weaponPickups.add(WeaponPickup(4000f, floorY - 340f, WeaponType.JET_WEB))

        // keys + gates (double-gated!)
        keys.add(Key(1515f, floorY - 470f, Key.KeyColor.BLUE))
        keys.add(Key(2915f, floorY - 470f, Key.KeyColor.RED))
        gates.add(Gate(RectF(1800f, 20f, 1830f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(3400f, 20f, 3430f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 11: Bat Introduction ----
    private fun buildLevel11_batIntro() {
        worldWidth = screenW * 3f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        platforms.add(Platform(RectF(500f, floorY - 200f, 700f, floorY - 172f)))
        platforms.add(Platform(RectF(1000f, floorY - 280f, 1200f, floorY - 252f)))
        platforms.add(Platform(RectF(1500f, floorY - 180f, 1700f, floorY - 152f)))
        platforms.add(Platform(RectF(2000f, floorY - 300f, 2200f, floorY - 272f)))

        enemies.add(Enemy(600f, floorY, 300f, 900f))
        enemies.add(Enemy(1600f, floorY, 1400f, 1800f))

        bats.add(Bat(700f, floorY - 350f, 300f, 1200f))
        bats.add(Bat(1300f, floorY - 300f, 900f, 1800f))
        bats.add(Bat(1900f, floorY - 380f, 1500f, 2400f))

        keys.add(Key(1100f, floorY - 320f, Key.KeyColor.BLUE))
        gates.add(Gate(RectF(2300f, 20f, 2330f, floorY), Key.KeyColor.BLUE))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 12: Jumping Robots ----
    private fun buildLevel12_jumpRobots() {
        worldWidth = screenW * 3.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..7) {
            val px = 350f + i * 350f
            val py = floorY - 160f - (i % 3) * 100f
            platforms.add(Platform(RectF(px, py, px + 150f, py + 28f)))
        }

        jumpingRobots.add(JumpingRobot(500f, floorY, 200f, 800f))
        jumpingRobots.add(JumpingRobot(1200f, floorY, 900f, 1500f))
        jumpingRobots.add(JumpingRobot(1800f, floorY, 1500f, 2200f))
        jumpingRobots.add(JumpingRobot(2500f, floorY, 2200f, 2800f))

        enemies.add(Enemy(900f, floorY, 600f, 1200f))
        enemies.add(Enemy(2100f, floorY, 1800f, 2400f))

        weaponPickups.add(WeaponPickup(700f, floorY - 240f, WeaponType.CRYO_WEB))
        keys.add(Key(2500f, floorY - 200f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2900f, 20f, 2930f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 13: Lava Run ----
    private fun buildLevel13_lavaRun() {
        worldWidth = screenW * 4f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // lava pits in the floor
        lavas.add(Lava(RectF(600f, floorY - 20f, 900f, floorY)))
        lavas.add(Lava(RectF(1300f, floorY - 20f, 1700f, floorY)))
        lavas.add(Lava(RectF(2200f, floorY - 20f, 2600f, floorY)))
        lavas.add(Lava(RectF(3000f, floorY - 20f, 3300f, floorY)))

        // platforms over lava
        platforms.add(Platform(RectF(650f, floorY - 200f, 850f, floorY - 172f)))
        platforms.add(Platform(RectF(1400f, floorY - 220f, 1600f, floorY - 192f)))
        platforms.add(Platform(RectF(2300f, floorY - 240f, 2500f, floorY - 212f)))
        platforms.add(Platform(RectF(3050f, floorY - 180f, 3250f, floorY - 152f)))

        enemies.add(Enemy(400f, floorY, 200f, 590f))
        enemies.add(Enemy(1100f, floorY, 910f, 1290f))
        enemies.add(Enemy(1900f, floorY, 1710f, 2190f))
        enemies.add(Enemy(2800f, floorY, 2610f, 2990f))

        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 300f, 1300f))
        ceilingCrawlers.add(CeilingCrawler(2400f, 20f, 1900f, 3000f))

        weaponPickups.add(WeaponPickup(750f, floorY - 280f, WeaponType.JET_WEB))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 14: Bat Swarm ----
    private fun buildLevel14_batSwarm() {
        worldWidth = screenW * 3.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..8) {
            val px = 300f + i * 330f
            val py = floorY - 180f - (i % 4) * 80f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        // lots of bats!
        for (i in 0..7) {
            val bx = 400f + i * 350f
            bats.add(Bat(bx, floorY - 300f - (i % 3) * 60f, bx - 200f, bx + 200f))
        }

        enemies.add(Enemy(600f, floorY, 300f, 900f))
        enemies.add(Enemy(1500f, floorY, 1200f, 1800f))
        enemies.add(Enemy(2400f, floorY, 2100f, 2700f))

        weaponPickups.add(WeaponPickup(500f, floorY - 260f, WeaponType.BOOM_WEB, 3))
        keys.add(Key(1800f, floorY - 320f, Key.KeyColor.BLUE))
        gates.add(Gate(RectF(2600f, 20f, 2630f, floorY), Key.KeyColor.BLUE))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 15: Mixed Chaos ----
    private fun buildLevel15_mixedChaos() {
        worldWidth = screenW * 4.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..12) {
            val px = 300f + i * 300f
            val py = floorY - 160f - (i % 4) * 90f
            platforms.add(Platform(RectF(px, py, px + 140f, py + 28f)))
        }

        lavas.add(Lava(RectF(800f, floorY - 20f, 1100f, floorY)))
        lavas.add(Lava(RectF(2200f, floorY - 20f, 2500f, floorY)))
        lavas.add(Lava(RectF(3500f, floorY - 20f, 3800f, floorY)))

        enemies.add(Enemy(500f, floorY, 200f, 790f))
        enemies.add(Enemy(1300f, floorY, 1110f, 1600f))
        enemies.add(Enemy(2800f, floorY, 2510f, 3100f))

        jumpingRobots.add(JumpingRobot(1800f, floorY, 1500f, 2190f))
        jumpingRobots.add(JumpingRobot(3200f, floorY, 2900f, 3490f))

        bats.add(Bat(600f, floorY - 340f, 200f, 1100f))
        bats.add(Bat(1600f, floorY - 320f, 1100f, 2200f))
        bats.add(Bat(2800f, floorY - 360f, 2300f, 3500f))
        bats.add(Bat(3600f, floorY - 300f, 3200f, 4000f))

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 300f))
        ceilingCrawlers.add(CeilingCrawler(1000f, 20f, 400f, 1800f))
        ceilingCrawlers.add(CeilingCrawler(2800f, 20f, 2200f, 3600f))

        weaponPickups.add(WeaponPickup(900f, floorY - 250f, WeaponType.SPIDER_BOT))
        weaponPickups.add(WeaponPickup(2700f, floorY - 280f, WeaponType.CRYO_WEB, 2))

        keys.add(Key(1500f, floorY - 380f, Key.KeyColor.BLUE))
        keys.add(Key(3400f, floorY - 300f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2100f, 20f, 2130f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4000f, 20f, 4030f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 16: Laser Introduction ----
    private fun buildLevel16_laserIntro() {
        worldWidth = screenW * 4f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..10) {
            val px = 350f + i * 320f
            val py = floorY - 180f - (i % 3) * 110f
            platforms.add(Platform(RectF(px, py, px + 140f, py + 28f)))
        }

        // heavy enemy density - laser needed
        for (i in 0..7) {
            val ex = 400f + i * 400f
            enemies.add(Enemy(ex, floorY, (ex - 200f).coerceAtLeast(200f), (ex + 200f).coerceAtMost(worldWidth - 100f)))
        }

        shooterEnemies.add(ShooterEnemy(1200f, floorY - 350f))
        shooterEnemies.add(ShooterEnemy(2500f, floorY - 350f))
        platforms.add(Platform(RectF(1185f, floorY - 350f, 1215f, floorY)))
        platforms.add(Platform(RectF(2485f, floorY - 350f, 2515f, floorY)))

        bats.add(Bat(800f, floorY - 300f, 300f, 1300f))
        bats.add(Bat(2000f, floorY - 320f, 1500f, 2600f))
        bats.add(Bat(3000f, floorY - 280f, 2600f, 3500f))

        weaponPickups.add(WeaponPickup(600f, floorY - 260f, WeaponType.LASER_VISION))
        weaponPickups.add(WeaponPickup(2200f, floorY - 300f, WeaponType.BOOM_WEB, 2))

        keys.add(Key(1800f, floorY - 400f, Key.KeyColor.RED))
        gates.add(Gate(RectF(3200f, 20f, 3230f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 17: Iron Spider ----
    private fun buildLevel17_ironSpider() {
        worldWidth = screenW * 4f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..10) {
            val px = 300f + i * 310f
            val py = floorY - 160f - (i % 4) * 80f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        // gauntlet of everything - iron spider helps survive
        lavas.add(Lava(RectF(700f, floorY - 20f, 900f, floorY)))
        lavas.add(Lava(RectF(1800f, floorY - 20f, 2100f, floorY)))
        lavas.add(Lava(RectF(2800f, floorY - 20f, 3100f, floorY)))

        enemies.add(Enemy(500f, floorY, 200f, 690f))
        enemies.add(Enemy(1200f, floorY, 910f, 1500f))
        enemies.add(Enemy(2400f, floorY, 2110f, 2790f))

        jumpingRobots.add(JumpingRobot(1500f, floorY, 1200f, 1790f))
        jumpingRobots.add(JumpingRobot(2600f, floorY, 2300f, 2790f))

        bats.add(Bat(900f, floorY - 350f, 400f, 1400f))
        bats.add(Bat(2200f, floorY - 330f, 1700f, 2700f))
        bats.add(Bat(3200f, floorY - 350f, 2800f, worldWidth - 200f))

        shooterEnemies.add(ShooterEnemy(1600f, floorY - 350f))
        platforms.add(Platform(RectF(1585f, floorY - 350f, 1615f, floorY)))

        weaponPickups.add(WeaponPickup(400f, floorY - 220f, WeaponType.IRON_SPIDER))
        weaponPickups.add(WeaponPickup(1800f, floorY - 280f, WeaponType.CRYO_WEB, 2))

        keys.add(Key(2400f, floorY - 250f, Key.KeyColor.BLUE))
        gates.add(Gate(RectF(3300f, 20f, 3330f, floorY), Key.KeyColor.BLUE))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 18: Clone Wars ----
    private fun buildLevel18_cloneWars() {
        worldWidth = screenW * 5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..14) {
            val px = 300f + i * 280f
            val py = floorY - 160f - (i % 5) * 70f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        // massive enemy army - clone is essential
        for (i in 0..9) {
            val ex = 400f + i * 420f
            enemies.add(Enemy(ex, floorY, (ex - 200f).coerceAtLeast(200f), (ex + 200f).coerceAtMost(worldWidth - 100f)))
        }

        jumpingRobots.add(JumpingRobot(800f, floorY, 500f, 1100f))
        jumpingRobots.add(JumpingRobot(2000f, floorY, 1700f, 2300f))
        jumpingRobots.add(JumpingRobot(3200f, floorY, 2900f, 3500f))

        bats.add(Bat(1000f, floorY - 300f, 500f, 1500f))
        bats.add(Bat(2500f, floorY - 320f, 2000f, 3000f))
        bats.add(Bat(4000f, floorY - 280f, 3500f, 4500f))

        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 300f, 1600f))
        ceilingCrawlers.add(CeilingCrawler(2500f, 20f, 1800f, 3200f))
        ceilingCrawlers.add(CeilingCrawler(4000f, 20f, 3200f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 240f, WeaponType.CLONE))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.SPIDER_BOT))

        keys.add(Key(1800f, floorY - 350f, Key.KeyColor.BLUE))
        keys.add(Key(3800f, floorY - 300f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2400f, 20f, 2430f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4500f, 20f, 4530f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 19: Plasma Web ----
    private fun buildLevel19_plasmaWeb() {
        worldWidth = screenW * 4f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // walls blocking path - plasma web destroys them
        platforms.add(Platform(RectF(800f, floorY - 400f, 830f, floorY)))
        platforms.add(Platform(RectF(1600f, floorY - 400f, 1630f, floorY)))
        platforms.add(Platform(RectF(2400f, floorY - 400f, 2430f, floorY)))

        for (i in 0..8) {
            val px = 350f + i * 350f
            val py = floorY - 180f - (i % 3) * 100f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        enemies.add(Enemy(500f, floorY, 200f, 790f))
        enemies.add(Enemy(1100f, floorY, 840f, 1590f))
        enemies.add(Enemy(1900f, floorY, 1640f, 2390f))
        enemies.add(Enemy(2700f, floorY, 2440f, 3000f))

        jumpingRobots.add(JumpingRobot(1200f, floorY, 900f, 1590f))

        bats.add(Bat(1000f, floorY - 320f, 500f, 1500f))
        bats.add(Bat(2200f, floorY - 300f, 1700f, 2700f))

        weaponPickups.add(WeaponPickup(400f, floorY - 200f, WeaponType.PLASMA_WEB))
        weaponPickups.add(WeaponPickup(1400f, floorY - 280f, WeaponType.PLASMA_WEB))

        keys.add(Key(2000f, floorY - 350f, Key.KeyColor.RED))
        gates.add(Gate(RectF(3200f, 20f, 3230f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 20: The Gauntlet II ----
    private fun buildLevel20_gauntlet() {
        worldWidth = screenW * 5.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..16) {
            val px = 300f + i * 280f
            val py = floorY - 150f - (i % 5) * 80f
            platforms.add(Platform(RectF(px, py, px + 120f, py + 28f)))
        }

        lavas.add(Lava(RectF(1000f, floorY - 20f, 1300f, floorY)))
        lavas.add(Lava(RectF(2500f, floorY - 20f, 2800f, floorY)))
        lavas.add(Lava(RectF(4000f, floorY - 20f, 4300f, floorY)))

        for (i in 0..8) { enemies.add(Enemy(400f + i * 500f, floorY, (200f + i * 500f).coerceAtLeast(200f), (600f + i * 500f).coerceAtMost(worldWidth - 100f))) }

        jumpingRobots.add(JumpingRobot(800f, floorY, 500f, 990f))
        jumpingRobots.add(JumpingRobot(2200f, floorY, 1900f, 2490f))
        jumpingRobots.add(JumpingRobot(3500f, floorY, 3200f, 3990f))

        bats.add(Bat(600f, floorY - 350f, 200f, 1200f))
        bats.add(Bat(1800f, floorY - 300f, 1300f, 2400f))
        bats.add(Bat(3000f, floorY - 340f, 2500f, 3600f))
        bats.add(Bat(4200f, floorY - 320f, 3800f, worldWidth - 200f))

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 380f))
        shooterEnemies.add(ShooterEnemy(3200f, floorY - 380f))
        platforms.add(Platform(RectF(1485f, floorY - 380f, 1515f, floorY)))
        platforms.add(Platform(RectF(3185f, floorY - 380f, 3215f, floorY)))

        ceilingCrawlers.add(CeilingCrawler(700f, 20f, 200f, 1500f))
        ceilingCrawlers.add(CeilingCrawler(2500f, 20f, 1800f, 3200f))
        ceilingCrawlers.add(CeilingCrawler(4000f, 20f, 3500f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 220f, WeaponType.LASER_VISION))
        weaponPickups.add(WeaponPickup(2000f, floorY - 300f, WeaponType.IRON_SPIDER))
        weaponPickups.add(WeaponPickup(3500f, floorY - 260f, WeaponType.CLONE))

        keys.add(Key(1500f, floorY - 430f, Key.KeyColor.BLUE))
        keys.add(Key(4500f, floorY - 250f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2300f, 20f, 2330f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4800f, 20f, 4830f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 21: Lava Caves ----
    private fun buildLevel21_lavaCaves() {
        worldWidth = screenW * 4.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // extensive lava coverage
        lavas.add(Lava(RectF(500f, floorY - 20f, 900f, floorY)))
        lavas.add(Lava(RectF(1200f, floorY - 20f, 1600f, floorY)))
        lavas.add(Lava(RectF(1900f, floorY - 20f, 2400f, floorY)))
        lavas.add(Lava(RectF(2700f, floorY - 20f, 3200f, floorY)))
        lavas.add(Lava(RectF(3500f, floorY - 20f, 3900f, floorY)))

        // stepping stones
        for (i in 0..12) {
            val px = 400f + i * 300f
            platforms.add(Platform(RectF(px, floorY - 200f - (i % 3) * 80f, px + 100f, floorY - 172f - (i % 3) * 80f)))
        }

        enemies.add(Enemy(300f, floorY, 200f, 490f))
        enemies.add(Enemy(1000f, floorY, 910f, 1190f))
        jumpingRobots.add(JumpingRobot(1700f, floorY, 1610f, 1890f))
        bats.add(Bat(1500f, floorY - 350f, 800f, 2200f))
        bats.add(Bat(3000f, floorY - 300f, 2500f, 3700f))
        ceilingCrawlers.add(CeilingCrawler(1000f, 20f, 400f, 2000f))
        ceilingCrawlers.add(CeilingCrawler(3000f, 20f, 2400f, 4000f))

        weaponPickups.add(WeaponPickup(600f, floorY - 300f, WeaponType.JET_WEB))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 22: Sky Bats ----
    private fun buildLevel22_skyBats() {
        worldWidth = screenW * 4f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // elevated platforms, bats everywhere
        for (i in 0..10) {
            val px = 300f + i * 330f
            val py = floorY - 250f - (i % 4) * 70f
            platforms.add(Platform(RectF(px, py, px + 120f, py + 28f)))
        }

        for (i in 0..9) {
            val bx = 300f + i * 360f
            bats.add(Bat(bx, floorY - 350f - (i % 3) * 40f, (bx - 250f).coerceAtLeast(100f), (bx + 250f).coerceAtMost(worldWidth - 100f)))
        }

        enemies.add(Enemy(600f, floorY, 200f, 1000f))
        enemies.add(Enemy(2000f, floorY, 1500f, 2500f))
        enemies.add(Enemy(3200f, floorY, 2800f, worldWidth - 200f))

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 380f))
        platforms.add(Platform(RectF(1485f, floorY - 380f, 1515f, floorY)))

        weaponPickups.add(WeaponPickup(800f, floorY - 340f, WeaponType.LASER_VISION))
        keys.add(Key(2500f, floorY - 400f, Key.KeyColor.BLUE))
        gates.add(Gate(RectF(3200f, 20f, 3230f, floorY), Key.KeyColor.BLUE))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 23: Robot Factory ----
    private fun buildLevel23_robotFactory() {
        worldWidth = screenW * 5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..14) {
            val px = 300f + i * 280f
            val py = floorY - 150f - (i % 4) * 90f
            platforms.add(Platform(RectF(px, py, px + 130f, py + 28f)))
        }

        // lots of jumping robots
        for (i in 0..5) {
            jumpingRobots.add(JumpingRobot(400f + i * 700f, floorY, (200f + i * 700f).coerceAtLeast(200f), (600f + i * 700f).coerceAtMost(worldWidth - 100f)))
        }

        enemies.add(Enemy(800f, floorY, 400f, 1200f))
        enemies.add(Enemy(2200f, floorY, 1800f, 2600f))
        enemies.add(Enemy(3600f, floorY, 3200f, 4000f))

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 400f))
        shooterEnemies.add(ShooterEnemy(3000f, floorY - 400f))
        platforms.add(Platform(RectF(1485f, floorY - 400f, 1515f, floorY)))
        platforms.add(Platform(RectF(2985f, floorY - 400f, 3015f, floorY)))

        bats.add(Bat(1000f, floorY - 350f, 500f, 1500f))
        bats.add(Bat(2500f, floorY - 320f, 2000f, 3000f))
        bats.add(Bat(4000f, floorY - 340f, 3500f, 4500f))

        ceilingCrawlers.add(CeilingCrawler(600f, 20f, 200f, 1400f))
        ceilingCrawlers.add(CeilingCrawler(2500f, 20f, 1800f, 3200f))
        ceilingCrawlers.add(CeilingCrawler(4200f, 20f, 3500f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 230f, WeaponType.CLONE))
        weaponPickups.add(WeaponPickup(2500f, floorY - 280f, WeaponType.WEB_TURRET))

        keys.add(Key(1800f, floorY - 440f, Key.KeyColor.BLUE))
        keys.add(Key(4000f, floorY - 300f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2200f, 20f, 2230f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4500f, 20f, 4530f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 24: Lava Gauntlet ----
    private fun buildLevel24_lavaGauntlet() {
        worldWidth = screenW * 5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // lava everywhere
        for (i in 0..7) {
            val lx = 400f + i * 550f
            lavas.add(Lava(RectF(lx, floorY - 20f, lx + 300f, floorY)))
        }

        for (i in 0..14) {
            val px = 300f + i * 280f
            platforms.add(Platform(RectF(px, floorY - 200f - (i % 4) * 70f, px + 100f, floorY - 172f - (i % 4) * 70f)))
        }

        for (i in 0..5) { enemies.add(Enemy(300f + i * 750f, floorY, 200f + i * 750f, 500f + i * 750f)) }
        jumpingRobots.add(JumpingRobot(1200f, floorY, 900f, 1500f))
        jumpingRobots.add(JumpingRobot(3000f, floorY, 2700f, 3300f))

        bats.add(Bat(800f, floorY - 340f, 300f, 1500f))
        bats.add(Bat(2200f, floorY - 320f, 1700f, 2800f))
        bats.add(Bat(3800f, floorY - 350f, 3200f, 4400f))

        ceilingCrawlers.add(CeilingCrawler(1000f, 20f, 400f, 2000f))
        ceilingCrawlers.add(CeilingCrawler(3000f, 20f, 2400f, 4000f))

        weaponPickups.add(WeaponPickup(600f, floorY - 280f, WeaponType.JET_WEB))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.IRON_SPIDER))

        keys.add(Key(2000f, floorY - 380f, Key.KeyColor.RED))
        gates.add(Gate(RectF(3800f, 20f, 3830f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 25: Dark Tower ----
    private fun buildLevel25_darkTower() {
        worldWidth = screenW * 4.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // tall pillars creating corridor
        for (i in 0..5) {
            val px = 600f + i * 600f
            platforms.add(Platform(RectF(px, floorY - 420f, px + 30f, floorY)))
        }

        for (i in 0..10) {
            val px = 300f + i * 350f
            platforms.add(Platform(RectF(px, floorY - 200f - (i % 3) * 100f, px + 120f, floorY - 172f - (i % 3) * 100f)))
        }

        lavas.add(Lava(RectF(1100f, floorY - 20f, 1400f, floorY)))
        lavas.add(Lava(RectF(2800f, floorY - 20f, 3200f, floorY)))

        for (i in 0..5) { enemies.add(Enemy(400f + i * 650f, floorY, 200f + i * 650f, 600f + i * 650f)) }
        jumpingRobots.add(JumpingRobot(900f, floorY, 600f, 1090f))
        jumpingRobots.add(JumpingRobot(2500f, floorY, 2200f, 2790f))

        bats.add(Bat(700f, floorY - 380f, 300f, 1100f))
        bats.add(Bat(1800f, floorY - 350f, 1400f, 2200f))
        bats.add(Bat(3500f, floorY - 320f, 3000f, 4000f))

        shooterEnemies.add(ShooterEnemy(1215f, floorY - 420f))
        shooterEnemies.add(ShooterEnemy(2415f, floorY - 420f))
        shooterEnemies.add(ShooterEnemy(3615f, floorY - 420f))

        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 200f, 1600f))
        ceilingCrawlers.add(CeilingCrawler(2800f, 20f, 2200f, 3800f))

        weaponPickups.add(WeaponPickup(500f, floorY - 260f, WeaponType.PLASMA_WEB))
        weaponPickups.add(WeaponPickup(2000f, floorY - 300f, WeaponType.LASER_VISION))

        keys.add(Key(1500f, floorY - 460f, Key.KeyColor.BLUE))
        keys.add(Key(3600f, floorY - 460f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2000f, 20f, 2030f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4100f, 20f, 4130f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 26: Bat Caves ----
    private fun buildLevel26_batCaves() {
        worldWidth = screenW * 4.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..12) {
            val px = 300f + i * 300f
            platforms.add(Platform(RectF(px, floorY - 180f - (i % 4) * 80f, px + 110f, floorY - 152f - (i % 4) * 80f)))
        }

        // bat apocalypse
        for (i in 0..11) {
            val bx = 300f + i * 330f
            bats.add(Bat(bx, floorY - 300f - (i % 4) * 50f, (bx - 200f).coerceAtLeast(100f), (bx + 200f).coerceAtMost(worldWidth - 100f)))
        }

        enemies.add(Enemy(500f, floorY, 200f, 800f))
        enemies.add(Enemy(1800f, floorY, 1500f, 2100f))
        enemies.add(Enemy(3200f, floorY, 2900f, 3500f))

        jumpingRobots.add(JumpingRobot(1200f, floorY, 900f, 1500f))
        jumpingRobots.add(JumpingRobot(2600f, floorY, 2300f, 2900f))

        ceilingCrawlers.add(CeilingCrawler(1000f, 20f, 400f, 1800f))
        ceilingCrawlers.add(CeilingCrawler(3000f, 20f, 2400f, 3800f))

        weaponPickups.add(WeaponPickup(600f, floorY - 260f, WeaponType.LASER_VISION))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.BOOM_WEB, 3))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 27: Mech Arena ----
    private fun buildLevel27_mechArena() {
        worldWidth = screenW * 5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        for (i in 0..14) {
            val px = 300f + i * 280f
            platforms.add(Platform(RectF(px, floorY - 160f - (i % 5) * 70f, px + 120f, floorY - 132f - (i % 5) * 70f)))
        }

        // massive jumping robot army
        for (i in 0..7) {
            jumpingRobots.add(JumpingRobot(400f + i * 550f, floorY, (200f + i * 550f).coerceAtLeast(200f), (600f + i * 550f).coerceAtMost(worldWidth - 100f)))
        }

        enemies.add(Enemy(600f, floorY, 200f, 1000f))
        enemies.add(Enemy(2000f, floorY, 1600f, 2400f))
        enemies.add(Enemy(3500f, floorY, 3100f, 3900f))
        enemies.add(Enemy(4500f, floorY, 4100f, worldWidth - 200f))

        bats.add(Bat(800f, floorY - 350f, 300f, 1300f))
        bats.add(Bat(2200f, floorY - 320f, 1700f, 2700f))
        bats.add(Bat(3800f, floorY - 340f, 3300f, 4300f))

        lavas.add(Lava(RectF(1200f, floorY - 20f, 1500f, floorY)))
        lavas.add(Lava(RectF(2800f, floorY - 20f, 3100f, floorY)))

        shooterEnemies.add(ShooterEnemy(1800f, floorY - 400f))
        shooterEnemies.add(ShooterEnemy(3500f, floorY - 400f))
        platforms.add(Platform(RectF(1785f, floorY - 400f, 1815f, floorY)))
        platforms.add(Platform(RectF(3485f, floorY - 400f, 3515f, floorY)))

        ceilingCrawlers.add(CeilingCrawler(600f, 20f, 200f, 1500f))
        ceilingCrawlers.add(CeilingCrawler(2500f, 20f, 1800f, 3500f))
        ceilingCrawlers.add(CeilingCrawler(4200f, 20f, 3500f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 230f, WeaponType.IRON_SPIDER))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.CLONE))

        keys.add(Key(1800f, floorY - 440f, Key.KeyColor.BLUE))
        keys.add(Key(4200f, floorY - 300f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2500f, 20f, 2530f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(4600f, 20f, 4630f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 28: Inferno ----
    private fun buildLevel28_inferno() {
        worldWidth = screenW * 5.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // lava everywhere!
        for (i in 0..9) {
            val lx = 350f + i * 500f
            lavas.add(Lava(RectF(lx, floorY - 20f, (lx + 280f).coerceAtMost(worldWidth - 300f), floorY)))
        }

        for (i in 0..16) {
            val px = 300f + i * 280f
            platforms.add(Platform(RectF(px, floorY - 200f - (i % 5) * 60f, px + 100f, floorY - 172f - (i % 5) * 60f)))
        }

        for (i in 0..6) { enemies.add(Enemy(300f + i * 700f, floorY, 200f + i * 700f, 500f + i * 700f)) }
        jumpingRobots.add(JumpingRobot(900f, floorY, 600f, 1200f))
        jumpingRobots.add(JumpingRobot(2500f, floorY, 2200f, 2800f))
        jumpingRobots.add(JumpingRobot(4000f, floorY, 3700f, 4300f))

        bats.add(Bat(700f, floorY - 350f, 200f, 1200f))
        bats.add(Bat(2000f, floorY - 320f, 1500f, 2500f))
        bats.add(Bat(3500f, floorY - 340f, 3000f, 4000f))
        bats.add(Bat(4800f, floorY - 300f, 4300f, worldWidth - 200f))

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 380f))
        shooterEnemies.add(ShooterEnemy(3200f, floorY - 380f))
        platforms.add(Platform(RectF(1485f, floorY - 380f, 1515f, floorY)))
        platforms.add(Platform(RectF(3185f, floorY - 380f, 3215f, floorY)))

        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 200f, 1800f))
        ceilingCrawlers.add(CeilingCrawler(2800f, 20f, 2200f, 3800f))
        ceilingCrawlers.add(CeilingCrawler(4500f, 20f, 4000f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 270f, WeaponType.JET_WEB))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.IRON_SPIDER))
        weaponPickups.add(WeaponPickup(4200f, floorY - 260f, WeaponType.LASER_VISION))

        keys.add(Key(1800f, floorY - 420f, Key.KeyColor.BLUE))
        keys.add(Key(4500f, floorY - 280f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2800f, 20f, 2830f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(5000f, 20f, 5030f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 29: Sky Fortress ----
    private fun buildLevel29_skyFortress() {
        worldWidth = screenW * 5.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // high platforms, big gaps
        for (i in 0..8) {
            val px = 400f + i * 550f
            val py = floorY - 300f - (i % 3) * 80f
            platforms.add(Platform(RectF(px, py, px + 140f, py + 28f)))
        }

        // lower platforms too
        for (i in 0..6) {
            val px = 600f + i * 600f
            platforms.add(Platform(RectF(px, floorY - 160f, px + 110f, floorY - 132f)))
        }

        lavas.add(Lava(RectF(800f, floorY - 20f, 1200f, floorY)))
        lavas.add(Lava(RectF(2000f, floorY - 20f, 2500f, floorY)))
        lavas.add(Lava(RectF(3500f, floorY - 20f, 4000f, floorY)))

        for (i in 0..7) { enemies.add(Enemy(350f + i * 600f, floorY, 200f + i * 600f, 550f + i * 600f)) }

        jumpingRobots.add(JumpingRobot(1000f, floorY, 700f, 1190f))
        jumpingRobots.add(JumpingRobot(2700f, floorY, 2400f, 2990f))
        jumpingRobots.add(JumpingRobot(4200f, floorY, 3900f, 4490f))

        for (i in 0..5) {
            val bx = 500f + i * 800f
            bats.add(Bat(bx, floorY - 380f, (bx - 300f).coerceAtLeast(100f), (bx + 300f).coerceAtMost(worldWidth - 100f)))
        }

        shooterEnemies.add(ShooterEnemy(1500f, floorY - 400f))
        shooterEnemies.add(ShooterEnemy(3000f, floorY - 400f))
        shooterEnemies.add(ShooterEnemy(4500f, floorY - 400f))
        platforms.add(Platform(RectF(1485f, floorY - 400f, 1515f, floorY)))
        platforms.add(Platform(RectF(2985f, floorY - 400f, 3015f, floorY)))
        platforms.add(Platform(RectF(4485f, floorY - 400f, 4515f, floorY)))

        ceilingCrawlers.add(CeilingCrawler(800f, 20f, 200f, 1800f))
        ceilingCrawlers.add(CeilingCrawler(2500f, 20f, 1800f, 3500f))
        ceilingCrawlers.add(CeilingCrawler(4200f, 20f, 3500f, worldWidth - 200f))

        weaponPickups.add(WeaponPickup(500f, floorY - 370f, WeaponType.JET_WEB))
        weaponPickups.add(WeaponPickup(2000f, floorY - 380f, WeaponType.CLONE))
        weaponPickups.add(WeaponPickup(3800f, floorY - 350f, WeaponType.PLASMA_WEB))

        keys.add(Key(2000f, floorY - 440f, Key.KeyColor.BLUE))
        keys.add(Key(4800f, floorY - 280f, Key.KeyColor.RED))
        gates.add(Gate(RectF(3000f, 20f, 3030f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(5100f, 20f, 5130f, floorY), Key.KeyColor.RED))
        door = Door(worldWidth - 200f, floorY)
    }

    // ---- Level 30: THE FINALE ----
    private fun buildLevel30_finale() {
        worldWidth = screenW * 6.5f; worldHeight = screenH
        val floorY = worldHeight - 60f; player.pos.set(200f, floorY - 100f)

        // epic platform layout
        for (i in 0..20) {
            val px = 300f + i * 280f
            val py = floorY - 160f - (i % 6) * 60f
            platforms.add(Platform(RectF(px, py, px + 110f, py + 28f)))
        }

        // pillars
        for (i in 0..6) {
            val px = 600f + i * 800f
            platforms.add(Platform(RectF(px, floorY - 420f, px + 30f, floorY)))
        }

        // lava zones
        lavas.add(Lava(RectF(900f, floorY - 20f, 1200f, floorY)))
        lavas.add(Lava(RectF(2100f, floorY - 20f, 2500f, floorY)))
        lavas.add(Lava(RectF(3300f, floorY - 20f, 3700f, floorY)))
        lavas.add(Lava(RectF(4500f, floorY - 20f, 4900f, floorY)))

        // enemy army
        for (i in 0..12) {
            val ex = 400f + i * 440f
            enemies.add(Enemy(ex, floorY, (ex - 200f).coerceAtLeast(200f), (ex + 200f).coerceAtMost(worldWidth - 100f)))
        }

        // jumping robots
        for (i in 0..4) {
            jumpingRobots.add(JumpingRobot(600f + i * 1200f, floorY, (400f + i * 1200f).coerceAtLeast(200f), (800f + i * 1200f).coerceAtMost(worldWidth - 100f)))
        }

        // bat swarm
        for (i in 0..7) {
            val bx = 500f + i * 700f
            bats.add(Bat(bx, floorY - 350f - (i % 3) * 50f, (bx - 300f).coerceAtLeast(100f), (bx + 300f).coerceAtMost(worldWidth - 100f)))
        }

        // shooters on pillars
        shooterEnemies.add(ShooterEnemy(615f, floorY - 420f))
        shooterEnemies.add(ShooterEnemy(2215f, floorY - 420f))
        shooterEnemies.add(ShooterEnemy(3815f, floorY - 420f))
        shooterEnemies.add(ShooterEnemy(5415f, floorY - 420f))

        // ceiling crawlers
        ceilingCrawlers.add(CeilingCrawler(500f, 20f, 200f, 1500f))
        ceilingCrawlers.add(CeilingCrawler(1800f, 20f, 1200f, 2800f))
        ceilingCrawlers.add(CeilingCrawler(3200f, 20f, 2600f, 4200f))
        ceilingCrawlers.add(CeilingCrawler(4800f, 20f, 4200f, worldWidth - 200f))

        // ALL weapon pickups!
        weaponPickups.add(WeaponPickup(500f, floorY - 250f, WeaponType.SPRING_WEB))
        weaponPickups.add(WeaponPickup(1000f, floorY - 280f, WeaponType.CRYO_WEB, 3))
        weaponPickups.add(WeaponPickup(1500f, floorY - 300f, WeaponType.SPIDER_BOT))
        weaponPickups.add(WeaponPickup(2000f, floorY - 280f, WeaponType.WEB_TURRET))
        weaponPickups.add(WeaponPickup(2500f, floorY - 300f, WeaponType.BOOM_WEB, 3))
        weaponPickups.add(WeaponPickup(3000f, floorY - 280f, WeaponType.JET_WEB))
        weaponPickups.add(WeaponPickup(3500f, floorY - 300f, WeaponType.LASER_VISION))
        weaponPickups.add(WeaponPickup(4000f, floorY - 280f, WeaponType.IRON_SPIDER))
        weaponPickups.add(WeaponPickup(4500f, floorY - 300f, WeaponType.CLONE))
        weaponPickups.add(WeaponPickup(5000f, floorY - 280f, WeaponType.PLASMA_WEB))

        // triple-gated
        keys.add(Key(1415f, floorY - 470f, Key.KeyColor.BLUE))
        keys.add(Key(3815f, floorY - 470f, Key.KeyColor.RED))
        gates.add(Gate(RectF(2000f, 20f, 2030f, floorY), Key.KeyColor.BLUE))
        gates.add(Gate(RectF(5000f, 20f, 5030f, floorY), Key.KeyColor.RED))

        door = Door(worldWidth - 200f, floorY)
    }

    // ================================================================
    //  SURFACE CALLBACKS
    // ================================================================
    override fun surfaceCreated(holder: SurfaceHolder) {
        screenW = width.toFloat(); screenH = height.toFloat()
        val jsArea = screenW / 3f
        joystickRadius = jsArea * 0.32f
        joystickCenterX = screenW - jsArea / 2f
        joystickCenterY = screenH / 2f
        joystickKnobX = joystickCenterX; joystickKnobY = joystickCenterY
        skyGradientPaint.shader = LinearGradient(
            0f, 0f, 0f, screenH,
            Color.rgb(12, 12, 36), Color.rgb(40, 30, 70), Shader.TileMode.CLAMP)
        loadProgress()
        gameState = GameState.MENU
        running = true; thread = Thread(this).also { it.start() }
    }
    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, he: Int) {
        screenW = w.toFloat(); screenH = he.toFloat()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) { running = false; thread?.join() }

    // ================================================================
    //  GAME LOOP
    // ================================================================
    override fun run() {
        var lastTime = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = ((now - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0.0001f, 0.05f)
            lastTime = now
            if (gameState == GameState.PLAYING) update(dt)
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) synchronized(holder) { draw(canvas) }
            } finally {
                if (canvas != null) try { holder.unlockCanvasAndPost(canvas) } catch (_: Exception) {}
            }
            val elapsed = (System.nanoTime() - now) / 1_000_000
            val sleep = framePeriod - elapsed
            if (sleep > 0) Thread.sleep(sleep)
        }
    }

    // ================================================================
    //  UPDATE
    // ================================================================
    private fun update(dt: Float) {
        // clear crate reference when web isn't attached
        if (web.state != WebLine.State.ATTACHED) webAttachedCrate = null

        // sync cryo mode flag to player for drawing
        player.cryoMode = cryoWebActive

        // ---- snowball trail update ----
        val trailIter = snowTrail.iterator()
        while (trailIter.hasNext()) { val p = trailIter.next(); p.life -= dt; if (p.life <= 0f) trailIter.remove() }

        // ---- web flight ----
        if (web.state == WebLine.State.FLYING) {
            val prev = Vec2(web.target.x, web.target.y)
            web.updateFlight(dt)

            // spawn snowball trail particles when cryo is active
            if (cryoWebActive) {
                for (j in 0..2) {
                    val ox = (Math.random().toFloat() - 0.5f) * 16f
                    val oy = (Math.random().toFloat() - 0.5f) * 16f
                    snowTrail.add(SnowParticle(web.target.x + ox, web.target.y + oy,
                        0.3f + Math.random().toFloat() * 0.2f, 3f + Math.random().toFloat() * 4f))
                }
            }

            // check web hitting enemies (freeze + consume web, or weapon effects)
            for (i in enemies.indices) {
                val enemy = enemies[i]
                if (enemy.frozen && !boomWebActive) continue
                if (lineIntersectsRect(prev, web.target, enemy.boundingRect())) {
                    if (boomWebActive) {
                        explosions.add(Explosion(enemy.pos.x, enemy.pos.y))
                        deadEnemyIndices.add(i)
                        boomWebActive = false
                    } else if (cryoWebActive) {
                        enemy.freezeLong(60f)
                        consumeCryoCharge()
                    } else {
                        enemy.freeze()
                    }
                    web.release(); break
                }
            }
            // remove exploded enemies (reverse order)
            if (deadEnemyIndices.isNotEmpty()) {
                deadEnemyIndices.sortDescending()
                for (idx in deadEnemyIndices) enemies.removeAt(idx)
                deadEnemyIndices.clear()
            }

            // check ceiling crawlers
            if (web.state == WebLine.State.FLYING) {
                for (i in ceilingCrawlers.indices.reversed()) {
                    val cc = ceilingCrawlers[i]
                    if (cc.frozen && !boomWebActive) continue
                    if (lineIntersectsRect(prev, web.target, cc.boundingRect())) {
                        if (boomWebActive) {
                            explosions.add(Explosion(cc.pos.x, cc.pos.y))
                            ceilingCrawlers.removeAt(i)
                            boomWebActive = false
                        } else if (cryoWebActive) {
                            cc.freezeLong(60f)
                            consumeCryoCharge()
                        } else {
                            cc.freeze()
                        }
                        web.release(); break
                    }
                }
            }

            // check shooter enemies
            if (web.state == WebLine.State.FLYING) {
                for (i in shooterEnemies.indices.reversed()) {
                    val se = shooterEnemies[i]
                    if (se.frozen && !boomWebActive) continue
                    if (lineIntersectsRect(prev, web.target, se.boundingRect())) {
                        if (boomWebActive) {
                            explosions.add(Explosion(se.pos.x, se.pos.y))
                            shooterEnemies.removeAt(i)
                            boomWebActive = false
                        } else if (cryoWebActive) {
                            se.freezeLong(60f)
                            consumeCryoCharge()
                        } else {
                            se.freeze()
                        }
                        web.release(); break
                    }
                }
            }

            // check bats (normal web kills them!)
            if (web.state == WebLine.State.FLYING) {
                for (i in bats.indices.reversed()) {
                    val bat = bats[i]
                    if (!bat.alive) continue
                    if (lineIntersectsRect(prev, web.target, bat.boundingRect())) {
                        if (boomWebActive) {
                            explosions.add(Explosion(bat.pos.x, bat.pos.y))
                            boomWebActive = false
                        }
                        bat.alive = false
                        web.release(); break
                    }
                }
            }

            // check jumping robots
            if (web.state == WebLine.State.FLYING) {
                for (i in jumpingRobots.indices.reversed()) {
                    val jr = jumpingRobots[i]
                    if (jr.frozen && !boomWebActive) continue
                    if (lineIntersectsRect(prev, web.target, jr.boundingRect())) {
                        if (boomWebActive) {
                            explosions.add(Explosion(jr.pos.x, jr.pos.y))
                            jumpingRobots.removeAt(i)
                            boomWebActive = false
                        } else if (cryoWebActive) {
                            jr.freezeLong(60f)
                            consumeCryoCharge()
                        } else {
                            jr.freeze()
                        }
                        web.release(); break
                    }
                }
            }

            // check web hitting crates -> attach web TO the crate
            if (web.state == WebLine.State.FLYING) {
                for (crate in crates) {
                    if (crate.crushed) continue
                    if (lineIntersectsRect(prev, web.target, crate.boundingRect())) {
                        val hit = getClosestIntersectionPoint(prev, web.target, crate.boundingRect())
                        web.attach(hit, player.pos)
                        webAttachedCrate = crate
                        if (!player.onGround && !player.onCeiling) {
                            val dx = player.pos.x - web.attachPoint.x
                            val dy = player.pos.y - web.attachPoint.y
                            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (len > 1f) {
                                val tx = dy / len; val ty = -dx / len
                                val tangentVel = player.vel.x * tx + player.vel.y * ty
                                web.ropeAngularVel = tangentVel / len
                            }
                            player.isSwinging = true
                        }
                        break
                    }
                }
            }

            // check platforms and closed gates
            if (web.state == WebLine.State.FLYING) {
                val surfaces = mutableListOf<RectF>()
                for (plat in platforms) surfaces.add(plat.rect)
                for (gate in gates) { if (!gate.open) surfaces.add(gate.rect) }
                for (surf in surfaces) {
                    if (lineIntersectsRect(prev, web.target, surf)) {
                        // plasma web destroys non-boundary terrain
                        if (plasmaWebActive) {
                            val hitPlat = platforms.find { it.rect === surf }
                            if (hitPlat != null && !isBoundaryPlatform(hitPlat)) {
                                platforms.remove(hitPlat)
                                explosions.add(Explosion(web.target.x, web.target.y, 50f))
                                plasmaWebActive = false
                                weaponNotifyText = "\u26a1 Terrain destroyed!"; weaponNotifyTimer = 1.5f
                                web.release(); break
                            }
                        }
                        val hit = getClosestIntersectionPoint(prev, web.target, surf)
                        web.attach(hit, player.pos)
                        if (!player.onGround && !player.onCeiling) {
                            val dx = player.pos.x - web.attachPoint.x
                            val dy = player.pos.y - web.attachPoint.y
                            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                            if (len > 1f) {
                                val tx = dy / len; val ty = -dx / len
                                val tangentVel = player.vel.x * tx + player.vel.y * ty
                                web.ropeAngularVel = tangentVel / len
                            }
                            player.isSwinging = true
                        }
                        break
                    }
                }
            }
        }

        // ---- web attached to crate -> drag it toward player ----
        webAttachedCrate?.let { crate ->
            if (web.state == WebLine.State.ATTACHED && !crate.crushed) {
                // keep the attach point following the crate
                web.attachPoint.set(crate.pos.x, crate.pos.y)
                web.target.set(crate.pos.x, crate.pos.y)
                // pull the crate toward the player
                val dx = player.pos.x - crate.pos.x
                if (Math.abs(dx) > 60f) {
                    val pullDir = if (dx > 0) 1f else -1f
                    crate.pull(pullDir, 180f, dt)
                }
            } else {
                webAttachedCrate = null
            }
        }

        // ---- pendulum swing ----
        if (web.state == WebLine.State.ATTACHED && player.isSwinging) {
            // rope climb / descend via left-side up/down input
            web.ropeLength -= player.climbInput * web.climbSpeed * dt
            web.ropeLength = web.ropeLength.coerceIn(web.minRopeLength, web.maxRopeLength)

            // pendulum: angular acceleration = -(g / L) * sin(theta)
            val g = player.gravity
            val L = web.ropeLength
            val angAccel = -(g / L) * Math.sin(web.ropeAngle.toDouble()).toFloat()
            web.ropeAngularVel += angAccel * dt

            // left/right input adds torque to build momentum
            val swingPumpStrength = 3.5f
            web.ropeAngularVel += player.moveDir * swingPumpStrength * dt

            web.ropeAngularVel *= swingDamping
            web.ropeAngle += web.ropeAngularVel * dt

            // derive player position from rope
            val ax = web.attachPoint.x
            val ay = web.attachPoint.y
            player.pos.x = ax + L * Math.sin(web.ropeAngle.toDouble()).toFloat()
            player.pos.y = ay + L * Math.cos(web.ropeAngle.toDouble()).toFloat()

            // derive velocity (for when rope is released)
            val vt = web.ropeAngularVel * L    // tangential speed
            val sinA = Math.sin(web.ropeAngle.toDouble()).toFloat()
            val cosA = Math.cos(web.ropeAngle.toDouble()).toFloat()
            player.vel.x = vt * cosA    // tangent x
            player.vel.y = -vt * sinA   // tangent y

            // update web origin for drawing
            web.origin.set(player.pos.x, player.pos.y - player.height * 0.2f)
        }

        // ---- auto-snap rope if too long ----
        if (web.state == WebLine.State.ATTACHED) {
            val dx = player.pos.x - web.attachPoint.x
            val dy = player.pos.y - web.attachPoint.y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (dist > web.maxRopeLength) {
                player.isSwinging = false
                web.release()
                webAttachedCrate = null
            }
        }

        // ---- update ground enemies ----
        for (enemy in enemies) enemy.update(dt, platforms)

        // ---- update ceiling crawlers ----
        for (cc in ceilingCrawlers) cc.update(dt)

        // ---- update jumping robots ----
        for (jr in jumpingRobots) jr.update(dt, platforms)

        // ---- update bats ----
        bats.removeAll { !it.alive }
        for (bat in bats) bat.update(dt, player.pos)

        // ---- update lava ----
        for (lava in lavas) lava.update(dt)

        // ---- update shooter enemies + remove bullets that hit walls ----
        for (se in shooterEnemies) {
            se.update(dt, player.pos)
            se.bullets.removeAll { b ->
                platforms.any { plat -> plat.rect.contains(b.pos.x, b.pos.y) } ||
                gates.any { gate -> !gate.open && gate.rect.contains(b.pos.x, b.pos.y) }
            }
        }

        // ---- player death checks ----
        val pr = player.boundingRect()
        var died = false
        // skip hit detection during iron spider i-frames
        val invincible = player.ironSpiderActive && player.ironSpiderIFrame > 0f
        if (!invincible) {
            for (enemy in enemies) {
                if (enemy.frozen) continue
                if (RectF.intersects(pr, enemy.boundingRect())) { died = true; break }
            }
            if (!died) for (cc in ceilingCrawlers) {
                if (cc.frozen) continue
                if (RectF.intersects(pr, cc.boundingRect())) { died = true; break }
            }
            if (!died) for (se in shooterEnemies) {
                if (se.frozen) continue
                if (RectF.intersects(pr, se.boundingRect())) { died = true; break }
            }
            if (!died) for (jr in jumpingRobots) {
                if (jr.frozen) continue
                if (RectF.intersects(pr, jr.boundingRect())) { died = true; break }
            }
            if (!died) for (bat in bats) {
                if (!bat.alive) continue
                if (RectF.intersects(pr, bat.boundingRect())) { died = true; break }
            }
            // bullet kills player
            if (!died) for (se in shooterEnemies) {
                for (bullet in se.bullets) {
                    if (RectF.intersects(pr, bullet.boundingRect())) { died = true; break }
                }
                if (died) break
            }
            // lava kills player
            if (!died) for (lava in lavas) {
                if (RectF.intersects(pr, lava.rect)) { died = true; break }
            }
        }
        // iron spider absorbs hits
        if (died && player.ironSpiderActive && player.ironSpiderHits > 0) {
            player.ironSpiderHits--
            died = false
            player.ironSpiderIFrame = 1.0f  // 1 second of invincibility
            // knockback
            player.vel.y = -600f
            player.vel.x = if (player.facingRight) -300f else 300f
            if (player.ironSpiderHits <= 0) {
                player.ironSpiderActive = false
                weaponNotifyText = "\ud83d\udee1 Iron Spider broken!"; weaponNotifyTimer = 2f
            } else {
                weaponNotifyText = "\ud83d\udee1 ${player.ironSpiderHits} hits left"; weaponNotifyTimer = 1f
            }
        }
        if (died) {
            // remember which keys were collected
            val savedKeys = keys.filter { it.collected }.map { it.color }.toSet()
            buildLevel()
            // re-collect the keys the player already had
            for (key in keys) {
                if (key.color in savedKeys) key.collected = true
            }
            // re-open any gates whose key is already collected
            for (gate in gates) {
                if (keys.any { it.collected && it.color == gate.requiredKey }) gate.open = true
            }
            return
        }

        // ---- update crates ----
        for (crate in crates) {
            crate.update(dt, platforms)
            // falling crate crushes/freezes enemies
            if (crate.vel.y > 100f) {
                for (enemy in enemies) {
                    if (RectF.intersects(crate.boundingRect(), enemy.boundingRect())) {
                        enemy.freeze()
                    }
                }
            }
        }

        // ---- update keys ----
        for (key in keys) {
            key.update(dt)
            if (!key.collected) {
                if (RectF.intersects(player.boundingRect(), key.boundingRect())) {
                    key.collected = true
                }
            }
        }

        // ---- touch gate with matching key to open it ----
        val pr2 = player.boundingRect()
        for (gate in gates) {
            if (gate.open) continue
            // generous touch zone – 40px margin around the gate
            val touchZone = RectF(gate.rect)
            touchZone.inset(-40f, -40f)
            if (RectF.intersects(pr2, touchZone)) {
                val hasKey = keys.any { it.collected && it.color == gate.requiredKey }
                if (hasKey) gate.open = true
            }
        }

        // ---- update weapon pickups ----
        for (wp in weaponPickups) {
            wp.update(dt)
            if (!wp.collected) {
                if (RectF.intersects(player.boundingRect(), wp.boundingRect())) {
                    wp.collected = true
                    // add to inventory (max 3 slots)
                    val existing = inventory.find { it.type == wp.weaponType }
                    if (existing != null) {
                        existing.charges = (existing.charges + wp.charges).coerceAtMost(15)
                    } else if (inventory.size < 3) {
                        inventory.add(WeaponItem(wp.weaponType, wp.charges))
                    } else {
                        // inventory full – replace oldest (first slot)
                        inventory[0] = WeaponItem(wp.weaponType, wp.charges)
                    }
                    weaponNotifyText = "+ ${wp.weaponType.label} (${wp.charges})"
                    weaponNotifyTimer = 2f
                }
            }
        }

        // ---- update spider bots ----
        spiderBots.removeAll { !it.alive }
        for (bot in spiderBots) {
            bot.update(dt, platforms)
            bot.checkFreezeEnemies(enemies, ceilingCrawlers, shooterEnemies)
        }

        // ---- update web turrets ----
        webTurrets.removeAll { !it.alive }
        for (turret in webTurrets) {
            turret.update(dt, enemies, ceilingCrawlers, shooterEnemies, platforms)
            turret.checkBulletHits(enemies, ceilingCrawlers, shooterEnemies)
        }

        // ---- update spider clones ----
        spiderClones.removeAll { !it.alive }
        for (clone in spiderClones) {
            val cmds = clone.update(dt, player.pos, player.facingRight, player.vel.x, enemies, ceilingCrawlers, shooterEnemies, jumpingRobots, bats, platforms, worldWidth, worldHeight)
            for (cmd in cmds) {
                when (cmd.type) {
                    SpiderClone.EnemyType.GROUND -> {
                        for (e in enemies) {
                            if (!e.frozen && dist2d(e.pos, cmd.target) < 60f) { e.freeze(); break }
                        }
                    }
                    SpiderClone.EnemyType.CEILING -> {
                        for (cc in ceilingCrawlers) {
                            if (!cc.frozen && dist2d(cc.pos, cmd.target) < 60f) { cc.freeze(); break }
                        }
                    }
                    SpiderClone.EnemyType.SHOOTER -> {
                        for (se in shooterEnemies) {
                            if (!se.frozen && dist2d(se.pos, cmd.target) < 60f) { se.freeze(); break }
                        }
                    }
                    SpiderClone.EnemyType.JUMPER -> {
                        for (jr in jumpingRobots) {
                            if (!jr.frozen && dist2d(jr.pos, cmd.target) < 60f) { jr.freeze(); break }
                        }
                    }
                    SpiderClone.EnemyType.BAT -> {
                        for (bat in bats) {
                            if (bat.alive && dist2d(bat.pos, cmd.target) < 60f) { bat.alive = false; break }
                        }
                    }
                }
            }
        }

        // ---- laser vision update ----
        if (laserFiringTimer > 0f) {
            laserFiringTimer -= dt
            player.laserActive = true
            player.laserTimer = laserTimer
            player.laserStartX = laserStartX; player.laserStartY = laserStartY
            player.laserEndX = laserEndX; player.laserEndY = laserEndY
        } else {
            player.laserActive = false; player.laserTimer = 0f
        }
        // deactivate laser when budget runs out
        if (laserActive && laserTimer <= 0f) {
            laserActive = false
            player.laserActive = false; player.laserTimer = 0f
            weaponNotifyText = "\ud83d\udc41 Laser spent!"; weaponNotifyTimer = 1.5f
        }

        // ---- update explosions ----
        explosions.removeAll { !it.alive }
        for (exp in explosions) exp.update(dt)

        // ---- weapon notification timer ----
        if (weaponNotifyTimer > 0f) weaponNotifyTimer -= dt

        // ---- update door ----
        door?.let { d ->
            d.update(dt)
            if (levelCompleteTimer < 0f) {
                if (RectF.intersects(player.boundingRect(), d.boundingRect())) {
                    levelCompleteTimer = 2.5f   // show message for 2.5s
                }
            }
        }

        // ---- level transition countdown ----
        if (levelCompleteTimer > 0f) {
            levelCompleteTimer -= dt
            if (levelCompleteTimer <= 0f) {
                if (currentLevel !in beatenLevels) {
                    stars++   // award a star for first-time completion
                }
                beatenLevels.add(currentLevel)
                if (currentLevel >= maxUnlockedLevel && currentLevel < totalLevels) {
                    maxUnlockedLevel = currentLevel + 1
                }
                saveProgress()
                gameState = GameState.MENU
                return
            }
        }

        // ---- normal player update ----
        player.update(dt, web)
        if (player.ironSpiderIFrame > 0f) player.ironSpiderIFrame -= dt

        // ---- jet web flight (after player update so it overrides gravity) ----
        if (jetWebActive) {
            jetWebTimer -= dt
            if (jetWebTimer <= 0f) {
                jetWebActive = false; jetWebTimer = 0f
            } else {
                // full directional flight: left/right + up/down
                val jetSpeed = 800f
                // vertical: climbInput +1 = up, -1 = down; idle = gentle hover up
                player.vel.y = if (player.climbInput != 0f) player.climbInput * -jetSpeed else -150f
                // horizontal: moveDir already drives vel.x in player.update,
                // but boost it to match jet feel
                player.vel.x = player.moveDir * jetSpeed
            }
        }

        // update web origin (non-swing)
        if (web.state != WebLine.State.NONE && !player.isSwinging) {
            web.origin.set(player.pos.x + player.width * 0.4f, player.pos.y - player.height * 0.2f)
        }

        // ---- collisions ----
        resolveCollisions()

        // keep in world
        player.pos.x = player.pos.x.coerceIn(player.width, worldWidth - player.width)

        // camera – track X, and Y for vertical levels
        val targetCamX = player.pos.x - screenW * 0.35f
        camera.x += (targetCamX - camera.x) * 0.08f
        camera.x = camera.x.coerceIn(0f, (worldWidth - screenW).coerceAtLeast(0f))

        val targetCamY = player.pos.y - screenH * 0.5f
        camera.y += (targetCamY - camera.y) * 0.08f
        camera.y = camera.y.coerceIn(0f, (worldHeight - screenH).coerceAtLeast(0f))
    }

    // ================================================================
    //  COLLISIONS
    // ================================================================
    private fun resolveCollisions() {
        if (!player.isSwinging) {
            player.onGround = false
            player.onCeiling = false
        }

        // Closed gates act as solid walls
        val solidRects = mutableListOf<RectF>()
        for (plat in platforms) solidRects.add(plat.rect)
        for (gate in gates) { if (!gate.open) solidRects.add(gate.rect) }
        // Cryo-frozen enemies become ice-block platforms (normal freeze does NOT)
        for (enemy in enemies) { if (enemy.cryoFrozen) solidRects.add(enemy.iceBlockRect()) }
        for (cc in ceilingCrawlers) { if (cc.cryoFrozen) solidRects.add(cc.iceBlockRect()) }
        for (se in shooterEnemies) { if (se.cryoFrozen) solidRects.add(se.iceBlockRect()) }
        for (jr in jumpingRobots) { if (jr.cryoFrozen) solidRects.add(jr.iceBlockRect()) }

        // Use a slightly expanded rect so that exact-edge contact still registers
        val margin = 2f
        for (r in solidRects) {
            val currentRect = player.boundingRect()
            val testRect = RectF(
                currentRect.left + margin,
                currentRect.top - margin,
                currentRect.right - margin,
                currentRect.bottom + margin
            )
            if (!RectF.intersects(testRect, r)) continue

            if (player.isSwinging) {
                // While swinging, check if player collides with a surface
                val overlapTop    = currentRect.bottom - r.top
                val overlapBottom = r.bottom - currentRect.top
                val overlapLeft   = currentRect.right - r.left
                val overlapRight  = r.right - currentRect.left
                val minOX = minOf(overlapLeft, overlapRight)
                val minOY = minOf(overlapTop, overlapBottom)

                if (minOY < minOX) {
                    if (overlapTop < overlapBottom && overlapTop > 0) {
                        // landed on top
                        player.isSwinging = false
                        web.release(); webAttachedCrate = null
                        player.pos.y = r.top - player.height / 2
                        player.vel.y = 0f
                        player.onGround = true
                    } else if (overlapBottom < overlapTop && overlapBottom > 0) {
                        if (player.ceilingImmuneTimer > 0f) {
                            // just dropped from ceiling — push out but keep swinging
                            player.pos.y = r.bottom + player.height / 2
                            val dx2 = player.pos.x - web.attachPoint.x
                            val dy2 = player.pos.y - web.attachPoint.y
                            web.ropeAngle = Math.atan2(dx2.toDouble(), dy2.toDouble()).toFloat()
                        } else {
                            // hit ceiling -> stick!
                            player.isSwinging = false
                            web.release(); webAttachedCrate = null
                            player.pos.y = r.bottom + player.height / 2
                            player.vel.y = 0f
                            player.onCeiling = true
                        }
                    }
                } else {
                    // hit a wall — push out and reverse angular velocity (bounce)
                    if (overlapLeft < overlapRight && overlapLeft > 0) {
                        player.pos.x = r.left - player.width / 2
                    } else if (overlapRight > 0) {
                        player.pos.x = r.right + player.width / 2
                    }
                    // recalculate rope angle from corrected position
                    val dx = player.pos.x - web.attachPoint.x
                    val dy = player.pos.y - web.attachPoint.y
                    web.ropeAngle = Math.atan2(dx.toDouble(), dy.toDouble()).toFloat()
                    // bounce: reverse and dampen angular velocity
                    web.ropeAngularVel = -web.ropeAngularVel * 0.3f
                }
                continue
            }

            // Normal (non-swing) collision
            val overlapLeft   = currentRect.right - r.left
            val overlapRight  = r.right - currentRect.left
            val overlapTop    = currentRect.bottom - r.top
            val overlapBottom = r.bottom - currentRect.top
            val minOX = minOf(overlapLeft, overlapRight)
            val minOY = minOf(overlapTop, overlapBottom)

            if (minOY < minOX) {
                if (overlapTop < overlapBottom) {
                    // landing on top
                    player.pos.y = r.top - player.height / 2
                    if (player.vel.y > 0) player.vel.y = 0f
                    player.onGround = true
                } else {
                    // hitting ceiling from below -> stick! (unless immune)
                    if (player.ceilingImmuneTimer <= 0f) {
                        player.pos.y = r.bottom + player.height / 2
                        if (player.vel.y < 0) player.vel.y = 0f
                        player.onCeiling = true
                    }
                }
            } else {
                if (overlapLeft < overlapRight) {
                    player.pos.x = r.left - player.width / 2
                } else {
                    player.pos.x = r.right + player.width / 2
                }
                player.vel.x = 0f
            }
        }
    }

    // ================================================================
    //  LINE INTERSECTION HELPERS
    // ================================================================
    private fun lineIntersectsRect(p1: Vec2, p2: Vec2, rect: RectF): Boolean {
        if (rect.contains(p1.x, p1.y) || rect.contains(p2.x, p2.y)) return true
        return lineIntersectsLine(p1, p2, Vec2(rect.left, rect.top), Vec2(rect.right, rect.top)) ||
               lineIntersectsLine(p1, p2, Vec2(rect.right, rect.top), Vec2(rect.right, rect.bottom)) ||
               lineIntersectsLine(p1, p2, Vec2(rect.right, rect.bottom), Vec2(rect.left, rect.bottom)) ||
               lineIntersectsLine(p1, p2, Vec2(rect.left, rect.bottom), Vec2(rect.left, rect.top))
    }
    private fun lineIntersectsLine(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Boolean {
        val d = (a2.x-a1.x)*(b2.y-b1.y) - (a2.y-a1.y)*(b2.x-b1.x)
        if (Math.abs(d) < 0.0001f) return false
        val t = ((b1.x-a1.x)*(b2.y-b1.y) - (b1.y-a1.y)*(b2.x-b1.x)) / d
        val u = ((b1.x-a1.x)*(a2.y-a1.y) - (b1.y-a1.y)*(a2.x-a1.x)) / d
        return t in 0f..1f && u in 0f..1f
    }
    private fun getClosestIntersectionPoint(p1: Vec2, p2: Vec2, rect: RectF): Vec2 {
        if (rect.contains(p2.x, p2.y)) return Vec2(p2.x, p2.y)
        val edges = listOf(
            Pair(Vec2(rect.left, rect.top), Vec2(rect.right, rect.top)),
            Pair(Vec2(rect.right, rect.top), Vec2(rect.right, rect.bottom)),
            Pair(Vec2(rect.right, rect.bottom), Vec2(rect.left, rect.bottom)),
            Pair(Vec2(rect.left, rect.bottom), Vec2(rect.left, rect.top)))
        var best = p2; var bestD = Float.MAX_VALUE
        for ((e1, e2) in edges) {
            val ix = lineSegmentIntersection(p1, p2, e1, e2) ?: continue
            val d = (ix - p1).length()
            if (d < bestD) { bestD = d; best = ix }
        }
        return best
    }
    private fun lineSegmentIntersection(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec2? {
        val d = (a2.x-a1.x)*(b2.y-b1.y) - (a2.y-a1.y)*(b2.x-b1.x)
        if (Math.abs(d) < 0.0001f) return null
        val t = ((b1.x-a1.x)*(b2.y-b1.y) - (b1.y-a1.y)*(b2.x-b1.x)) / d
        val u = ((b1.x-a1.x)*(a2.y-a1.y) - (b1.y-a1.y)*(a2.x-a1.x)) / d
        if (t in 0f..1f && u in 0f..1f) return Vec2(a1.x + t*(a2.x-a1.x), a1.y + t*(a2.y-a1.y))
        return null
    }

    private fun isBoundaryPlatform(plat: Platform): Boolean {
        // boundary platforms are the floor, ceiling, and side walls added in buildLevel()
        val r = plat.rect
        return r.left <= -100f || r.right >= worldWidth + 100f ||
               r.top <= -100f || r.bottom >= worldHeight + 100f
    }

    private fun dist2d(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }

    // ================================================================
    //  MENU DRAWING
    // ================================================================
    private fun drawMenu(canvas: Canvas) {
        // decorative stars (static, no parallax)
        val starPaint = Paint().apply { color = Color.argb(120, 255, 255, 255) }
        val rng = java.util.Random(7)
        for (i in 0 until 80) {
            val sx = rng.nextFloat() * screenW
            val sy = rng.nextFloat() * screenH * 0.7f
            canvas.drawCircle(sx, sy, 1.5f + rng.nextFloat() * 1.5f, starPaint)
        }

        // ---- fixed header ----
        val headerH = screenH * 0.26f
        // title shadow + title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 80, 80); textSize = 80f
            textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        val shadowPaint = Paint(titlePaint).apply { color = Color.argb(60, 0, 0, 0) }
        canvas.drawText("WEB SWINGER", screenW / 2f + 3f, screenH * 0.15f + 3f, shadowPaint)
        canvas.drawText("WEB SWINGER", screenW / 2f, screenH * 0.15f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 255, 255, 255); textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SELECT LEVEL", screenW / 2f, screenH * 0.23f, subPaint)

        // ---- fixed footer ----
        val footerTop = screenH * 0.86f

        // ---- scrollable level grid ----
        val cols = 5
        val btnSize = screenW * 0.14f
        val gap = btnSize * 0.25f
        val totalW = cols * btnSize + (cols - 1) * gap
        val totalRows = (totalLevels + cols - 1) / cols
        val gridContentH = totalRows * btnSize + (totalRows - 1) * gap
        val gridViewH = footerTop - headerH
        val startX = (screenW - totalW) / 2f
        val gridTopY = headerH + 16f   // top of grid content at scroll=0

        // compute max scroll
        menuMaxScroll = (gridContentH - gridViewH + 32f).coerceAtLeast(0f)
        menuScrollY = menuScrollY.coerceIn(0f, menuMaxScroll)

        // apply fling deceleration
        if (!menuIsDragging && kotlin.math.abs(menuScrollVel) > 0.5f) {
            menuScrollY = (menuScrollY + menuScrollVel).coerceIn(0f, menuMaxScroll)
            menuScrollVel *= 0.92f
        } else if (!menuIsDragging) {
            menuScrollVel = 0f
        }

        // clip to grid viewport
        canvas.save()
        canvas.clipRect(0f, headerH, screenW, footerTop)

        for (lvl in 1..totalLevels) {
            val col = (lvl - 1) % cols
            val row = (lvl - 1) / cols
            val bx = startX + col * (btnSize + gap)
            val by = gridTopY + row * (btnSize + gap) - menuScrollY

            // skip off-screen buttons
            if (by + btnSize < headerH || by > footerTop) continue

            val beaten = lvl in beatenLevels
            val unlocked = lvl <= maxUnlockedLevel

            // button background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = when {
                    beaten   -> Color.rgb(30, 100, 30)
                    unlocked -> Color.rgb(50, 50, 75)
                    else     -> Color.rgb(25, 25, 30)
                }
            }
            canvas.drawRoundRect(bx, by, bx + btnSize, by + btnSize, 14f, 14f, bgPaint)

            // border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f
                color = when {
                    beaten   -> Color.rgb(80, 220, 80)
                    unlocked -> Color.rgb(255, 200, 100)
                    else     -> Color.rgb(50, 50, 55)
                }
            }
            canvas.drawRoundRect(bx, by, bx + btnSize, by + btnSize, 14f, 14f, borderPaint)

            // level number
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = btnSize * 0.38f; textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
                color = when {
                    beaten   -> Color.WHITE
                    unlocked -> Color.WHITE
                    else     -> Color.rgb(60, 60, 60)
                }
            }
            canvas.drawText("$lvl", bx + btnSize / 2f, by + btnSize * 0.62f, numPaint)

            // checkmark for beaten
            if (beaten) {
                val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(80, 255, 80); textSize = btnSize * 0.24f
                    textAlign = Paint.Align.RIGHT; typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("✓", bx + btnSize - 6f, by + btnSize * 0.28f, checkPaint)
            }

            // lock icon for locked
            if (!unlocked) {
                val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(70, 70, 70); textSize = btnSize * 0.18f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("LOCKED", bx + btnSize / 2f, by + btnSize * 0.88f, lockPaint)
            }
        }

        canvas.restore()

        // ---- scroll fade overlays ----
        if (menuScrollY > 4f) {
            val fadeP = Paint().apply {
                shader = LinearGradient(0f, headerH, 0f, headerH + 40f,
                    Color.argb(180, 10, 8, 20), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, headerH, screenW, headerH + 40f, fadeP)
        }
        if (menuScrollY < menuMaxScroll - 4f && menuMaxScroll > 0f) {
            val fadeP = Paint().apply {
                shader = LinearGradient(0f, footerTop - 40f, 0f, footerTop,
                    Color.TRANSPARENT, Color.argb(180, 10, 8, 20), Shader.TileMode.CLAMP)
            }
            canvas.drawRect(0f, footerTop - 40f, screenW, footerTop, fadeP)
        }

        // ---- scroll indicator arrows ----
        if (menuMaxScroll > 0f) {
            val arrowP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255); textSize = 28f
                textAlign = Paint.Align.CENTER
            }
            if (menuScrollY > 4f)
                canvas.drawText("▲", screenW / 2f, headerH + 22f, arrowP)
            if (menuScrollY < menuMaxScroll - 4f)
                canvas.drawText("▼  scroll  ▼", screenW / 2f, footerTop - 8f, arrowP)
        }

        // ---- fixed footer area (dark bg to cover scroll) ----
        val footerBg = Paint().apply { color = Color.rgb(10, 8, 20) }
        canvas.drawRect(0f, footerTop, screenW, screenH, footerBg)

        // bottom hint
        val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 255, 255, 255); textSize = 24f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("TAP A LEVEL TO PLAY", screenW / 2f, screenH * 0.89f, hintPaint)

        // ---- star count ----
        val starCountP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 50); textSize = 32f; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("⭐ $stars", 30f, screenH * 0.95f, starCountP)

        // ---- DEBUG buttons (small, bottom-left) ----
        val dbgBtnW = 110f; val dbgBtnH = 40f
        val dbgY = screenH * 0.87f
        val dbgStarsX = 20f
        val dbgResetX = dbgStarsX + dbgBtnW + 12f

        // +999 stars button
        val dbgBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 35, 20); style = Paint.Style.FILL }
        canvas.drawRoundRect(dbgStarsX, dbgY, dbgStarsX + dbgBtnW, dbgY + dbgBtnH, 8f, 8f, dbgBg)
        val dbgBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 100, 40); style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        canvas.drawRoundRect(dbgStarsX, dbgY, dbgStarsX + dbgBtnW, dbgY + dbgBtnH, 8f, 8f, dbgBorder)
        val dbgTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 180, 60); textSize = 18f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("+999 ⭐", dbgStarsX + dbgBtnW / 2f, dbgY + dbgBtnH * 0.68f, dbgTxt)

        // reset button
        val dbgBg2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 20, 20); style = Paint.Style.FILL }
        canvas.drawRoundRect(dbgResetX, dbgY, dbgResetX + dbgBtnW, dbgY + dbgBtnH, 8f, 8f, dbgBg2)
        val dbgBorder2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(140, 50, 50); style = Paint.Style.STROKE; strokeWidth = 1.5f
        }
        canvas.drawRoundRect(dbgResetX, dbgY, dbgResetX + dbgBtnW, dbgY + dbgBtnH, 8f, 8f, dbgBorder2)
        val dbgTxt2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 80, 80); textSize = 18f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("RESET", dbgResetX + dbgBtnW / 2f, dbgY + dbgBtnH * 0.68f, dbgTxt2)

        // ---- STORE button ----
        val storeBtnW = 180f; val storeBtnH = 56f
        val storeBtnX = screenW - storeBtnW - 30f
        val storeBtnY = screenH * 0.90f
        val storeBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(50, 40, 80); style = Paint.Style.FILL }
        canvas.drawRoundRect(storeBtnX, storeBtnY, storeBtnX + storeBtnW, storeBtnY + storeBtnH, 12f, 12f, storeBg)
        val storeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 200, 80); style = Paint.Style.STROKE; strokeWidth = 2.5f
        }
        canvas.drawRoundRect(storeBtnX, storeBtnY, storeBtnX + storeBtnW, storeBtnY + storeBtnH, 12f, 12f, storeBorder)
        val storeTxtP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 80); textSize = 28f; textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("🛒 STORE", storeBtnX + storeBtnW / 2f, storeBtnY + storeBtnH * 0.68f, storeTxtP)
    }

    // ================================================================
    //  STORE UI
    // ================================================================
    private fun drawStore(canvas: Canvas) {
        // background
        val bg = Paint().apply { color = Color.rgb(18, 14, 36) }
        canvas.drawRect(0f, 0f, screenW, screenH, bg)

        // title
        val titleP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 80); textSize = 48f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("🛒 STORE", screenW / 2f, 70f, titleP)

        // star balance
        val balP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 50); textSize = 30f; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("⭐ $stars available", screenW / 2f, 112f, balP)

        // items in 2-column grid
        val cols = 2
        val cardW = screenW * 0.42f
        val cardH = 120f
        val gapX = (screenW - cols * cardW) / (cols + 1)
        val gapY = 18f
        val topY = 140f

        for ((idx, wt) in storeOrder.withIndex()) {
            val col = idx % cols
            val row = idx / cols
            val cx = gapX + col * (cardW + gapX)
            val cy = topY + row * (cardH + gapY)
            val owned = purchasedItems.containsKey(wt)
            val price = storePrices[wt] ?: 0
            val canAfford = stars >= price

            // card bg
            val cardBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (owned) Color.rgb(25, 55, 30) else Color.rgb(35, 30, 60)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(cx, cy, cx + cardW, cy + cardH, 14f, 14f, cardBg)

            // card border
            val bdrColor = if (owned) Color.rgb(80, 200, 80) else if (canAfford) Color.rgb(255, 200, 80) else Color.rgb(80, 70, 100)
            val cardBdr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = bdrColor; style = Paint.Style.STROKE; strokeWidth = 2f
            }
            canvas.drawRoundRect(cx, cy, cx + cardW, cy + cardH, 14f, 14f, cardBdr)

            // icon
            val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f }
            canvas.drawText(wt.icon, cx + 14f, cy + 40f, iconP)

            // name
            val nameP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(wt.color shr 16 and 0xFF, wt.color shr 8 and 0xFF, wt.color and 0xFF)
                textSize = 22f; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(wt.label, cx + 56f, cy + 36f, nameP)

            // description
            val descP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 200, 200, 200); textSize = 14f
            }
            canvas.drawText(wt.description, cx + 14f, cy + 62f, descP)

            // buy button or owned label
            if (owned) {
                val ownP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(80, 200, 80); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("✓ OWNED", cx + 14f, cy + 100f, ownP)
            } else {
                // buy button rect
                val buyX = cx + 14f; val buyY = cy + 78f
                val buyW = cardW - 28f; val buyH = 32f
                val buyBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (canAfford) Color.rgb(50, 120, 50) else Color.rgb(60, 50, 50)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(buyX, buyY, buyX + buyW, buyY + buyH, 8f, 8f, buyBg)
                val buyTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (canAfford) Color.rgb(255, 255, 255) else Color.rgb(120, 100, 100)
                    textSize = 18f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("BUY  ⭐$price", buyX + buyW / 2f, buyY + buyH * 0.72f, buyTxt)
            }
        }

        // ---- BACK button ----
        val backW = 140f; val backH = 50f
        val backX = (screenW - backW) / 2f
        val backY = screenH - 80f
        val backBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(60, 50, 80); style = Paint.Style.FILL }
        canvas.drawRoundRect(backX, backY, backX + backW, backY + backH, 10f, 10f, backBg)
        val backBdr = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(180, 160, 220); style = Paint.Style.STROKE; strokeWidth = 2f
        }
        canvas.drawRoundRect(backX, backY, backX + backW, backY + backH, 10f, 10f, backBdr)
        val backTxt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 24f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("← BACK", backX + backW / 2f, backY + backH * 0.68f, backTxt)
    }

    private fun handleStoreTap(touchX: Float, touchY: Float) {
        // ---- check BUY buttons ----
        val cols = 2
        val cardW = screenW * 0.42f
        val cardH = 120f
        val gapX = (screenW - cols * cardW) / (cols + 1)
        val gapY = 18f
        val topY = 140f

        for ((idx, wt) in storeOrder.withIndex()) {
            val col = idx % cols
            val row = idx / cols
            val cx = gapX + col * (cardW + gapX)
            val cy = topY + row * (cardH + gapY)
            val owned = purchasedItems.containsKey(wt)
            val price = storePrices[wt] ?: 0

            if (!owned && stars >= price) {
                val buyX = cx + 14f; val buyY = cy + 78f
                val buyW = cardW - 28f; val buyH = 32f
                if (touchX >= buyX && touchX <= buyX + buyW &&
                    touchY >= buyY && touchY <= buyY + buyH) {
                    stars -= price
                    purchasedItems[wt] = true
                    saveProgress()
                    return
                }
            }
        }

        // ---- check BACK button ----
        val backW = 140f; val backH = 50f
        val backX = (screenW - backW) / 2f
        val backY = screenH - 80f
        if (touchX >= backX && touchX <= backX + backW &&
            touchY >= backY && touchY <= backY + backH) {
            gameState = GameState.MENU
        }
    }

    // ================================================================
    //  DRAW
    // ================================================================
    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        canvas.drawRect(0f, 0f, screenW, screenH, skyGradientPaint)

        if (gameState == GameState.MENU) { drawMenu(canvas); return }
        if (gameState == GameState.STORE) { drawStore(canvas); return }

        // stars with parallax (works for both horizontal & vertical levels)
        val starPaint = Paint().apply { color = Color.argb(120, 255, 255, 255) }
        val rng = java.util.Random(7)
        for (i in 0 until 80) {
            val sx = rng.nextFloat() * (worldWidth.coerceAtLeast(screenW * 3f)) - camera.x * 0.3f
            val sy = rng.nextFloat() * screenH * 0.7f - camera.y * 0.15f
            canvas.drawCircle(sx, sy, 1.5f + rng.nextFloat() * 1.5f, starPaint)
        }

        canvas.save()
        canvas.translate(-camera.x, -camera.y)

        // platforms
        for (plat in platforms) {
            canvas.drawRect(plat.rect, platPaint)
            canvas.drawRect(plat.rect, platHighlight)
        }

        // gates, keys, crates, door, weapon pickups
        for (gate in gates) gate.draw(canvas)
        for (key in keys) key.draw(canvas)
        for (crate in crates) crate.draw(canvas)
        for (wp in weaponPickups) wp.draw(canvas)
        door?.draw(canvas)

        // enemies (all types)
        for (enemy in enemies) enemy.draw(canvas)
        for (cc in ceilingCrawlers) cc.draw(canvas)
        for (se in shooterEnemies) se.draw(canvas)
        for (jr in jumpingRobots) jr.draw(canvas)
        for (bat in bats) bat.draw(canvas)

        // lava
        for (lava in lavas) lava.draw(canvas)

        // weapon entities
        for (bot in spiderBots) bot.draw(canvas)
        for (turret in webTurrets) turret.draw(canvas)
        for (clone in spiderClones) clone.draw(canvas)
        for (exp in explosions) exp.draw(canvas)

        // cryo snowball trail particles (behind player, in world space)
        for (p in snowTrail) {
            val alpha = (p.life / 0.5f * 180).toInt().coerceIn(0, 180)
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(alpha, 200, 230, 255) }
            canvas.drawCircle(p.x, p.y, p.size * (p.life / 0.5f).coerceIn(0.3f, 1f), sp)
        }

        // player
        player.draw(canvas, web)
        canvas.restore()

        // ---- HUD ----
        val sepX = screenW * 2f / 3f
        canvas.drawLine(sepX, 0f, sepX, screenH, separatorPaint)
        canvas.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, joystickBasePaint)
        canvas.drawCircle(joystickKnobX, joystickKnobY, joystickRadius * 0.35f, joystickKnobPaint)
        canvas.drawText("WEB", joystickCenterX - 24f, joystickCenterY + joystickRadius + 36f, hudPaint)

        // level number
        val lvlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 36f; typeface = Typeface.MONOSPACE
        }
        canvas.drawText("LEVEL $currentLevel", 30f, 50f, lvlPaint)

        // ---- MENU button (top-right, left of joystick area) ----
        val menuBtnW = 110f; val menuBtnH = 44f
        val menuBtnX = screenW * 2f / 3f - menuBtnW - 16f
        val menuBtnY = 12f
        val mbgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 40, 40, 50); style = Paint.Style.FILL }
        canvas.drawRoundRect(menuBtnX, menuBtnY, menuBtnX + menuBtnW, menuBtnY + menuBtnH, 10f, 10f, mbgPaint)
        val mbrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 200, 200, 200); style = Paint.Style.STROKE; strokeWidth = 1.5f }
        canvas.drawRoundRect(menuBtnX, menuBtnY, menuBtnX + menuBtnW, menuBtnY + menuBtnH, 10f, 10f, mbrPaint)
        val mtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(180, 255, 255, 255); textSize = 22f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
        canvas.drawText("☰ MENU", menuBtnX + menuBtnW / 2f, menuBtnY + menuBtnH * 0.7f, mtPaint)

        // collected keys indicator
        var keyHudX = 30f
        val keyHudY = 80f
        for (key in keys) {
            val c = if (key.collected) Color.argb(200,
                if (key.color == Key.KeyColor.BLUE) 60 else 255,
                if (key.color == Key.KeyColor.BLUE) 140 else 60,
                if (key.color == Key.KeyColor.BLUE) 255 else 60)
            else Color.argb(80, 120, 120, 120)
            val kp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; textSize = 28f; typeface = Typeface.DEFAULT_BOLD }
            val label = if (key.color == Key.KeyColor.BLUE) "🔵 BLUE KEY" else "🔴 RED KEY"
            val status = if (key.collected) " ✓" else ""
            canvas.drawText("$label$status", keyHudX, keyHudY, kp)
            keyHudX += 250f
        }

        // ---- weapon inventory HUD ----
        if (inventory.isNotEmpty()) {
            val slotSize = 56f
            val slotGap = 10f
            val startX = 30f
            val startY = keyHudY + 20f

            for (i in inventory.indices) {
                val item = inventory[i]
                val sx = startX + i * (slotSize + slotGap)
                val sy = startY

                // slot background
                val slotRect = RectF(sx, sy, sx + slotSize, sy + slotSize)
                val isActive = (i == activeWeaponIndex)
                val bgColor = if (isActive) Color.argb(160, Color.red(item.type.color),
                    Color.green(item.type.color), Color.blue(item.type.color))
                else Color.argb(80, 40, 40, 60)
                val slotBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
                canvas.drawRoundRect(slotRect, 8f, 8f, slotBg)

                // active border
                if (isActive) {
                    val borderP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f
                    }
                    canvas.drawRoundRect(slotRect, 8f, 8f, borderP)
                }

                // icon
                val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 24f; textAlign = Paint.Align.CENTER; color = Color.WHITE
                }
                canvas.drawText(item.type.icon, sx + slotSize / 2, sy + slotSize / 2 + 4f, iconP)

                // charge count
                val chargeP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 16f; textAlign = Paint.Align.RIGHT; color = Color.WHITE
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText("${item.charges}", sx + slotSize - 4f, sy + slotSize - 4f, chargeP)
            }

            // label
            val invLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(120, 255, 255, 255); textSize = 16f
            }
            canvas.drawText("TAP TO USE", startX, startY + slotSize + 16f, invLabel)
        }

        // ---- weapon notification popup ----
        if (weaponNotifyTimer > 0f) {
            val alpha = (weaponNotifyTimer.coerceAtMost(1f) * 255).toInt()
            val notifyP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 100, 255, 100); textSize = 32f
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(weaponNotifyText, screenW / 2f, 140f, notifyP)
        }

        // ---- jet web timer indicator ----
        if (jetWebActive) {
            val jetP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(255, 200, 50); textSize = 28f
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("🚀 JET: ${jetWebTimer.toInt() + 1}s", screenW / 2f, 100f, jetP)
        }

        // ---- active weapon modifier indicator ----
        if (cryoWebActive || boomWebActive || springWebActive || plasmaWebActive || laserActive || player.ironSpiderActive) {
            val modText = when {
                cryoWebActive -> "❄ CRYO WEB ARMED"
                boomWebActive -> "💥 BOOM WEB ARMED"
                springWebActive -> "⬆ SUPER JUMP READY"
                plasmaWebActive -> "⚡ PLASMA WEB ARMED"
                laserActive -> "👁 LASER ARMED: ${String.format("%.1f", laserTimer)}s"
                player.ironSpiderActive -> "🛡 IRON SPIDER: ${player.ironSpiderHits} hits"
                else -> ""
            }
            val modP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 100); textSize = 24f
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(modText, screenW / 2f, 70f, modP)
        }

        // level complete overlay
        if (levelCompleteTimer > 0f) {
            val overlayPaint = Paint().apply { color = Color.argb(150, 0, 0, 0) }
            canvas.drawRect(0f, 0f, screenW, screenH, overlayPaint)
            val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 255, 100); textSize = 72f
                textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText("LEVEL $currentLevel COMPLETE!", screenW / 2f, screenH / 2f - 20f, bigText)
            val subText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Returning to menu...", screenW / 2f, screenH / 2f + 40f, subText)
        }

        val hintPaint = Paint(hudPaint).apply { textSize = 24f; color = Color.argb(80, 255, 255, 255) }
        canvas.drawText("DRAG TO MOVE | SWIPE UP/DOWN", 40f, screenH - 30f, hintPaint)

        // ---- rope length indicator ----
        if (web.state == WebLine.State.ATTACHED) {
            val dx = player.pos.x - web.attachPoint.x
            val dy = player.pos.y - web.attachPoint.y
            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val frac = (dist / web.maxRopeLength).coerceIn(0f, 1f)

            val barW = 300f; val barH = 14f
            val barX = (screenW - barW) / 2f
            val barY = screenH - 60f

            // background
            val bgPaint = Paint().apply { color = Color.argb(100, 0, 0, 0); style = Paint.Style.FILL }
            canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 7f, 7f, bgPaint)

            // fill — green → yellow → red
            val r = (255 * Math.min(1f, frac * 2f)).toInt()
            val g = (255 * Math.min(1f, 2f * (1f - frac))).toInt()
            val fillPaint = Paint().apply { color = Color.argb(200, r, g, 0); style = Paint.Style.FILL }
            canvas.drawRoundRect(barX, barY, barX + barW * frac, barY + barH, 7f, 7f, fillPaint)

            // border
            val borderPaint = Paint().apply { color = Color.argb(150, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1.5f }
            canvas.drawRoundRect(barX, barY, barX + barW, barY + barH, 7f, 7f, borderPaint)

            // label
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 20f; textAlign = Paint.Align.CENTER }
            canvas.drawText("ROPE: ${dist.toInt()} / ${web.maxRopeLength.toInt()}", screenW / 2f, barY - 6f, labelPaint)
        }
    }

    // ================================================================
    //  TOUCH INPUT
    // ================================================================
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)
        val x = event.getX(actionIndex)
        val y = event.getY(actionIndex)

        // ---- menu state touch (scrollable) ----
        if (gameState == GameState.MENU) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    menuTouchStartY = y
                    menuTouchLastY = y
                    menuTouchDownX = x
                    menuTouchDownY = y
                    menuIsDragging = false
                    menuScrollVel = 0f
                    menuTouchActive = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!menuTouchActive) { /* ignore stale move */ }
                    else {
                        val dy = menuTouchLastY - y
                        if (!menuIsDragging && kotlin.math.abs(y - menuTouchStartY) > 18f) {
                            menuIsDragging = true
                        }
                        if (menuIsDragging) {
                            menuScrollY = (menuScrollY + dy).coerceIn(0f, menuMaxScroll)
                            menuScrollVel = dy
                        }
                        menuTouchLastY = y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (menuTouchActive && !menuIsDragging) {
                        handleMenuTap(menuTouchDownX, menuTouchDownY)
                    }
                    menuIsDragging = false
                    menuTouchActive = false
                }
            }
            return true
        }
        if (gameState == GameState.STORE) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) handleStoreTap(x, y)
            return true
        }

        val sepX = screenW * 2f / 3f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // check menu button tap
                if (checkMenuButtonTap(x, y)) { /* consumed */ }
                // check weapon inventory slot taps first
                else if (checkWeaponSlotTap(x, y)) {
                    // consumed by weapon tap – don't register as move or web
                } else if (x < sepX) {
                    moveTouchId = pointerId
                    moveTouchStartX = x; moveTouchStartY = y
                    moveTouchCurrentX = x; moveTouchCurrentY = y
                    hasSwipedThisTouch = false
                } else {
                    webTouchId = pointerId
                    joystickActive = true
                    webJoystickArmed = false  // must drag out from center first
                    updateJoystick(x, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    val px = event.getX(i); val py = event.getY(i)
                    if (pid == moveTouchId) {
                        moveTouchCurrentX = px; moveTouchCurrentY = py
                        val dx = moveTouchCurrentX - moveTouchStartX
                        val dy = moveTouchCurrentY - moveTouchStartY

                        // horizontal movement
                        player.moveDir = when {
                            dx > 30f  ->  1f
                            dx < -30f -> -1f
                            else      ->  0f
                        }

                        // vertical input: climb rope when swinging, or fly when jet active
                        if (player.isSwinging || jetWebActive) {
                            player.climbInput = when {
                                dy < -60f ->  1f   // finger up = climb up
                                dy >  60f -> -1f   // finger down = descend
                                else      ->  0f
                            }
                        } else {
                            player.climbInput = 0f
                        }

                        // swipe up -> jump (ground) or nothing
                        if (dy < -80f && !hasSwipedThisTouch) {
                            if (player.onCeiling) {
                                // on ceiling, swipe up does nothing (they're upside-down)
                            } else {
                                player.jump(web)
                            }
                            hasSwipedThisTouch = true
                        }
                        // swipe down -> drop from ceiling
                        if (dy > 80f && !hasSwipedThisTouch) {
                            if (player.onCeiling) {
                                player.dropFromCeiling(web)
                            }
                            hasSwipedThisTouch = true
                        }
                    }
                    if (pid == webTouchId) updateJoystick(px, py)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pointerId == moveTouchId) {
                    player.moveDir = 0f; player.climbInput = 0f; moveTouchId = -1
                }
                if (pointerId == webTouchId) {
                    // release web -> fling with current velocity
                    if (player.isSwinging) {
                        player.isSwinging = false
                    }
                    web.release()
                    webAttachedCrate = null
                    joystickActive = false
                    joystickKnobX = joystickCenterX; joystickKnobY = joystickCenterY
                    webTouchId = -1
                }
            }
        }
        return true
    }

    private fun updateJoystick(touchX: Float, touchY: Float) {
        val dx = touchX - joystickCenterX
        val dy = touchY - joystickCenterY
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val clamped = dist.coerceAtMost(joystickRadius)
        if (dist > 1f) {
            joystickKnobX = joystickCenterX + dx / dist * clamped
            joystickKnobY = joystickCenterY + dy / dist * clamped
        }
        val shootThreshold = joystickRadius * 0.4f

        if (dist > shootThreshold && web.state == WebLine.State.NONE) {
            if (laserActive && laserTimer > 0f) {
                // fire laser instead of web
                fireLaser(dx / dist, dy / dist)
            } else {
                val dir = Vec2(dx / dist, dy / dist)
                val from = Vec2(
                    player.pos.x + player.width * 0.4f,
                    player.pos.y - player.height * 0.2f)
                web.shoot(from, dir)
                player.notifyWebShot()
            }
        }
    }

    // ================================================================
    //  LASER FIRE — raycast from hand, stop at wall, explode enemies
    // ================================================================
    private fun fireLaser(dirX: Float, dirY: Float) {
        val maxLen = 900f
        val costPerShot = 0.35f
        laserTimer -= costPerShot
        player.notifyWebShot()

        // origin: player hand
        val ox = player.pos.x + (if (player.facingRight) player.width * 0.4f else -player.width * 0.4f)
        val oy = player.pos.y - player.height * 0.2f

        // raycast: find nearest wall hit along direction
        var hitDist = maxLen
        for (plat in platforms) {
            val r = plat.rect
            val t = rayVsRect(ox, oy, dirX, dirY, r)
            if (t != null && t in 0f..hitDist) hitDist = t
        }
        // also stop at closed gates
        for (gate in gates) {
            if (!gate.open) {
                val t = rayVsRect(ox, oy, dirX, dirY, gate.rect)
                if (t != null && t in 0f..hitDist) hitDist = t
            }
        }

        val endX = ox + dirX * hitDist
        val endY = oy + dirY * hitDist

        // store for drawing
        laserStartX = ox; laserStartY = oy
        laserEndX = endX; laserEndY = endY
        laserFiringTimer = 0.15f  // visual flash duration

        // beam as a thin rect for enemy intersection
        val beamHalf = 8f // half-width of beam hitbox
        val perpX = -dirY; val perpY = dirX
        val bx1 = minOf(ox + perpX * beamHalf, ox - perpX * beamHalf, endX + perpX * beamHalf, endX - perpX * beamHalf)
        val by1 = minOf(oy + perpY * beamHalf, oy - perpY * beamHalf, endY + perpY * beamHalf, endY - perpY * beamHalf)
        val bx2 = maxOf(ox + perpX * beamHalf, ox - perpX * beamHalf, endX + perpX * beamHalf, endX - perpX * beamHalf)
        val by2 = maxOf(oy + perpY * beamHalf, oy - perpY * beamHalf, endY + perpY * beamHalf, endY - perpY * beamHalf)
        val beamRect = RectF(bx1, by1, bx2, by2)

        // helper: is point within beam corridor?
        fun inBeam(px: Float, py: Float): Boolean {
            val dx = px - ox; val dy = py - oy
            val proj = dx * dirX + dy * dirY
            if (proj < 0f || proj > hitDist) return false
            val perp = Math.abs(dx * perpX + dy * perpY)
            return perp < 50f  // generous hit width for enemies
        }

        // kill enemies
        enemies.removeAll { e ->
            if (e.frozen) return@removeAll false
            if (RectF.intersects(beamRect, e.boundingRect()) && inBeam(e.pos.x, e.pos.y)) {
                explosions.add(Explosion(e.pos.x, e.pos.y, 45f)); true
            } else false
        }
        shooterEnemies.removeAll { se ->
            if (se.frozen) return@removeAll false
            if (RectF.intersects(beamRect, se.boundingRect()) && inBeam(se.pos.x, se.pos.y)) {
                explosions.add(Explosion(se.pos.x, se.pos.y, 45f)); true
            } else false
        }
        jumpingRobots.removeAll { jr ->
            if (jr.frozen) return@removeAll false
            if (RectF.intersects(beamRect, jr.boundingRect()) && inBeam(jr.pos.x, jr.pos.y)) {
                explosions.add(Explosion(jr.pos.x, jr.pos.y, 45f)); true
            } else false
        }
        bats.removeAll { bat ->
            if (!bat.alive) return@removeAll false
            if (RectF.intersects(beamRect, bat.boundingRect()) && inBeam(bat.pos.x, bat.pos.y)) {
                explosions.add(Explosion(bat.pos.x, bat.pos.y, 35f)); true
            } else false
        }
        // freeze ceiling crawlers
        for (cc in ceilingCrawlers) {
            if (!cc.frozen && RectF.intersects(beamRect, cc.boundingRect()) && inBeam(cc.pos.x, cc.pos.y)) {
                cc.freeze()
                explosions.add(Explosion(cc.pos.x, cc.pos.y, 35f))
            }
        }

        // deactivate if budget spent
        if (laserTimer <= 0f) {
            laserActive = false
            weaponNotifyText = "\ud83d\udc41 Laser spent!"; weaponNotifyTimer = 1.5f
        }
    }

    /** Slab-based ray vs AABB. Returns distance t, or null if no hit. */
    private fun rayVsRect(ox: Float, oy: Float, dx: Float, dy: Float, r: RectF): Float? {
        var tMin = 0f; var tMax = Float.MAX_VALUE
        if (Math.abs(dx) > 0.0001f) {
            val invD = 1f / dx
            var t0 = (r.left - ox) * invD; var t1 = (r.right - ox) * invD
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tMin = maxOf(tMin, t0); tMax = minOf(tMax, t1)
            if (tMin > tMax) return null
        } else {
            if (ox < r.left || ox > r.right) return null
        }
        if (Math.abs(dy) > 0.0001f) {
            val invD = 1f / dy
            var t0 = (r.top - oy) * invD; var t1 = (r.bottom - oy) * invD
            if (t0 > t1) { val tmp = t0; t0 = t1; t1 = tmp }
            tMin = maxOf(tMin, t0); tMax = minOf(tMax, t1)
            if (tMin > tMax) return null
        } else {
            if (oy < r.top || oy > r.bottom) return null
        }
        return if (tMin >= 0f) tMin else null
    }

    // ================================================================
    //  MENU TAP HANDLING
    // ================================================================
    private fun handleMenuTap(touchX: Float, touchY: Float) {
        // ---- check DEBUG buttons ----
        val dbgBtnW = 110f; val dbgBtnH = 40f
        val dbgY = screenH * 0.87f
        val dbgStarsX = 20f
        val dbgResetX = dbgStarsX + dbgBtnW + 12f

        // +999 stars
        if (touchX >= dbgStarsX && touchX <= dbgStarsX + dbgBtnW &&
            touchY >= dbgY && touchY <= dbgY + dbgBtnH) {
            stars += 999
            saveProgress()
            weaponNotifyText = "+999 ⭐"; weaponNotifyTimer = 2f
            return
        }
        // reset progress
        if (touchX >= dbgResetX && touchX <= dbgResetX + dbgBtnW &&
            touchY >= dbgY && touchY <= dbgY + dbgBtnH) {
            stars = 0; maxUnlockedLevel = 1; beatenLevels.clear(); purchasedItems.clear()
            menuScrollY = 0f
            saveProgress()
            weaponNotifyText = "PROGRESS RESET"; weaponNotifyTimer = 2f
            return
        }

        // ---- check STORE button ----
        val storeBtnW = 180f; val storeBtnH = 56f
        val storeBtnX = screenW - storeBtnW - 30f
        val storeBtnY = screenH * 0.90f
        if (touchX >= storeBtnX && touchX <= storeBtnX + storeBtnW &&
            touchY >= storeBtnY && touchY <= storeBtnY + storeBtnH) {
            gameState = GameState.STORE
            return
        }

        // ---- check level buttons (must match drawMenu grid layout) ----
        val headerH = screenH * 0.26f
        val footerTop = screenH * 0.86f
        // ignore taps outside the grid viewport
        if (touchY < headerH || touchY > footerTop) return

        val cols = 5
        val btnSize = screenW * 0.14f
        val gap = btnSize * 0.25f
        val totalW = cols * btnSize + (cols - 1) * gap
        val startX = (screenW - totalW) / 2f
        val gridTopY = headerH + 16f

        for (lvl in 1..totalLevels) {
            val col = (lvl - 1) % cols
            val row = (lvl - 1) / cols
            val bx = startX + col * (btnSize + gap)
            val by = gridTopY + row * (btnSize + gap) - menuScrollY
            if (touchX >= bx && touchX <= bx + btnSize && touchY >= by && touchY <= by + btnSize) {
                if (lvl <= maxUnlockedLevel) {
                    currentLevel = lvl
                    inventory.clear()
                    for ((wt, _) in purchasedItems) {
                        inventory.add(WeaponItem(wt, 1))
                    }
                    buildLevel()
                    gameState = GameState.PLAYING
                }
                return
            }
        }
    }

    private fun consumeCryoCharge() {
        if (cryoWebSlotIndex in inventory.indices && inventory[cryoWebSlotIndex].type == WeaponType.CRYO_WEB) {
            inventory[cryoWebSlotIndex].charges--
            if (inventory[cryoWebSlotIndex].isEmpty) {
                inventory.removeAt(cryoWebSlotIndex)
                cryoWebActive = false; cryoWebSlotIndex = -1; activeWeaponIndex = -1
            }
        }
    }

    private fun checkMenuButtonTap(touchX: Float, touchY: Float): Boolean {
        val menuBtnW = 110f; val menuBtnH = 44f
        val menuBtnX = screenW * 2f / 3f - menuBtnW - 16f
        val menuBtnY = 12f
        if (touchX >= menuBtnX && touchX <= menuBtnX + menuBtnW &&
            touchY >= menuBtnY && touchY <= menuBtnY + menuBtnH) {
            gameState = GameState.MENU
            return true
        }
        return false
    }

    // ================================================================
    //  WEAPON SLOT TAP DETECTION
    // ================================================================
    private fun checkWeaponSlotTap(touchX: Float, touchY: Float): Boolean {
        if (inventory.isEmpty()) return false
        val slotSize = 56f
        val slotGap = 10f
        val startX = 30f
        // keyHudY depends on keys — approximate
        val keyHudY = 80f
        val startY = keyHudY + 20f

        for (i in inventory.indices) {
            val sx = startX + i * (slotSize + slotGap)
            val sy = startY
            val slotRect = RectF(sx - 10f, sy - 10f, sx + slotSize + 10f, sy + slotSize + 10f)
            if (slotRect.contains(touchX, touchY)) {
                activateWeapon(i)
                return true
            }
        }
        return false
    }

    private fun activateWeapon(index: Int) {
        if (index < 0 || index >= inventory.size) return
        val item = inventory[index]
        if (item.isEmpty) return

        when (item.type) {
            WeaponType.CRYO_WEB -> {
                if (cryoWebActive) {
                    // toggle OFF
                    cryoWebActive = false; cryoWebSlotIndex = -1
                    weaponNotifyText = "❄ Cryo Web disarmed"; weaponNotifyTimer = 1.5f
                } else {
                    // toggle ON – charge consumed when it actually hits
                    cryoWebActive = true; cryoWebSlotIndex = index; boomWebActive = false
                    weaponNotifyText = "❄ Cryo Web armed!"; weaponNotifyTimer = 1.5f
                }
                return   // skip the charge-depletion code below
            }
            WeaponType.SPIDER_BOT -> {
                // deploy immediately at player position
                val goRight = player.facingRight
                val groundY = player.pos.y + player.height / 2
                spiderBots.add(SpiderBot(player.pos.x, groundY, goRight))
                item.charges--
                weaponNotifyText = "🕷 Spider Bot deployed!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.WEB_TURRET -> {
                // place immediately at player position
                val groundY = player.pos.y + player.height / 2
                webTurrets.add(WebTurret(player.pos.x, groundY))
                item.charges--
                weaponNotifyText = "⚙ Turret placed!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.BOOM_WEB -> {
                boomWebActive = true; cryoWebActive = false; cryoWebSlotIndex = -1
                item.charges--
                weaponNotifyText = "💥 Boom Web armed!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.JET_WEB -> {
                jetWebActive = true; jetWebTimer = jetWebDuration
                item.charges--
                weaponNotifyText = "🚀 Jet Web activated!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.SPRING_WEB -> {
                springWebActive = true
                player.springJumpReady = true
                item.charges--
                weaponNotifyText = "⬆ Super Jump ready!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.LASER_VISION -> {
                laserActive = true; laserTimer = laserDuration
                player.laserActive = true; player.laserTimer = laserDuration
                item.charges--
                weaponNotifyText = "👁 Laser Vision activated!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.IRON_SPIDER -> {
                player.ironSpiderActive = true; player.ironSpiderHits = 10
                item.charges--
                weaponNotifyText = "🛡 Iron Spider – 10 hits!"; weaponNotifyTimer = 2f
            }
            WeaponType.CLONE -> {
                spiderClones.add(SpiderClone(context, player.pos.x, player.pos.y - 50f))
                item.charges--
                weaponNotifyText = "👤 Clone deployed!"; weaponNotifyTimer = 1.5f
            }
            WeaponType.PLASMA_WEB -> {
                plasmaWebActive = true; cryoWebActive = false; cryoWebSlotIndex = -1; boomWebActive = false
                item.charges--
                weaponNotifyText = "⚡ Plasma Web armed!"; weaponNotifyTimer = 1.5f
            }
        }
        // remove from inventory if charges depleted
        if (item.isEmpty) {
            inventory.removeAt(index)
            activeWeaponIndex = -1
        } else {
            activeWeaponIndex = index
        }
    }
}
