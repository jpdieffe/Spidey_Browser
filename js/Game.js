// Main Game controller — state machine, update loop, rendering
const GSTATE = { MENU: 'MENU', PLAYING: 'PLAYING', DEAD: 'DEAD', COMPLETE: 'COMPLETE' };

class Game {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.state = GSTATE.MENU;
    this.currentLevel = 1;

    // Progress
    this.maxUnlocked = parseInt(localStorage.getItem('ws_maxUnlocked') || '1');
    this.beatenLevels = new Set(JSON.parse(localStorage.getItem('ws_beaten') || '[]'));
    this.stars = parseInt(localStorage.getItem('ws_stars') || '0');

    // Camera
    this.camX = 0; this.camY = 0;

    // Zoom — scale canvas rendering so the world appears zoomed out
    this.ZOOM = 0.7;

    // Input
    this.keys = {};
    this.mouseX = 0; this.mouseY = 0;
    this.leftMouseDown = false;
    this.rightMouseDown = false;
    this._setupInput();

    // Game objects
    this.player = null;
    this.levelData = null;
    this.spiderBots = [];
    this.webTurrets = [];
    this.clones = [];
    this.explosions = [];
    this.heldKeys = {};

    // Timers
    this.levelCompleteTimer = 0;
    this.levelCompleteEarned = false;
    this.deathTimer = 0;

    this._resize();
    window.addEventListener('resize', () => this._resize());
  }

  _resize() {
    this.canvas.width = window.innerWidth;
    this.canvas.height = window.innerHeight;
    this.screenW = this.canvas.width;
    this.screenH = this.canvas.height;
    // Virtual game dimensions (world is built in these coords, then scaled down by ZOOM)
    this.vW = this.screenW / this.ZOOM;
    this.vH = this.screenH / this.ZOOM;
  }

  _setupInput() {
    window.addEventListener('keydown', e => {
      this.keys[e.code] = true;
      this.heldKeys[e.code] = true;
      e.preventDefault && e.preventDefault();
      if (this.state === GSTATE.PLAYING && this.player) {
        // Cycle weapon with Q/E
        if (e.code === 'KeyQ') this.player.cycleWeapon(-1);
        if (e.code === 'KeyE') this.player.cycleWeapon(1);
        // Activate weapon with F
        if (e.code === 'KeyF') this._activateWeapon();
      }
    });
    window.addEventListener('keyup', e => {
      this.keys[e.code] = false;
      this.heldKeys[e.code] = false;
    });
    this.canvas.addEventListener('mousemove', e => {
      const rect = this.canvas.getBoundingClientRect();
      this.mouseX = e.clientX - rect.left;
      this.mouseY = e.clientY - rect.top;
    });
    this.canvas.addEventListener('mousedown', e => {
      if (e.button === 0) { this.leftMouseDown = true; this._onLeftClick(); }
      if (e.button === 2) { this.rightMouseDown = true; this._onRightClick(); }
      e.preventDefault();
    });
    this.canvas.addEventListener('mouseup', e => {
      if (e.button === 0) this.leftMouseDown = false;
      if (e.button === 2) this.rightMouseDown = false;
    });
    this.canvas.addEventListener('contextmenu', e => e.preventDefault());
  }

  _onLeftClick() {
    if (this.state !== GSTATE.PLAYING || !this.player || !this.player.alive) return;
    const p = this.player;
    const worldMX = this.mouseX / this.ZOOM + this.camX;
    const worldMY = this.mouseY / this.ZOOM + this.camY;

    // Check active weapon for special fire modes
    const activeW = p.getActiveWeapon();
    if (activeW) {
      switch (activeW.type) {
        case 'SPRING_WEB':
        case 'BOOM_WEB':
        case 'CRYO_WEB':
        case 'PLASMA_WEB':
          // These modify next web shot — activate then fire
          if (p.useWeapon(activeW.type)) {
            applyWeapon(p, activeW.type, this._allEnemies(), this.levelData.platforms, this);
          }
          break;
        case 'JET_WEB':
        case 'LASER_VISION':
        case 'IRON_SPIDER':
        case 'SPIDER_BOT':
        case 'WEB_TURRET':
        case 'CLONE':
          if (p.useWeapon(activeW.type)) {
            applyWeapon(p, activeW.type, this._allEnemies(), this.levelData.platforms, this);
          }
          return; // Don't fire regular web
      }
    }

    // Fire / extend web
    if (p.web.isNone || p.web.isFlying) {
      p.web.fire(p.cx, p.cy, worldMX, worldMY);
      p.isSwinging = false;
    }
  }

  _onRightClick() {
    if (this.state !== GSTATE.PLAYING || !this.player) return;
    this.player.web.release();
    this.player.isSwinging = false;
  }

  _activateWeapon() {
    const p = this.player;
    const activeW = p.getActiveWeapon();
    if (!activeW) return;
    if (p.useWeapon(activeW.type)) {
      applyWeapon(p, activeW.type, this._allEnemies(), this.levelData.platforms, this);
    }
  }

  _allEnemies() {
    return this.levelData ? this.levelData.enemies : [];
  }

  spawnSpiderBot(x, y) {
    this.spiderBots.push(new SpiderBot(x, y));
  }

  spawnWebTurret(x, y) {
    this.webTurrets.push(new WebTurret(x, y));
  }

  spawnClone(x, y) {
    this.clones = this.clones.filter(c => c.alive); // max 1
    if (this.clones.length < 1) this.clones.push(new SpiderClone(x, y));
  }

  startLevel(n) {
    this.currentLevel = n;
    this.levelData = buildLevel(n, this.vW, this.vH);
    const ld = this.levelData;
    const floorY = this.vH - 24;
    this.player = new Player(100, floorY - C.PLAYER_H - 10);
    this.player.spawnX = 100;
    this.player.spawnY = floorY - C.PLAYER_H - 10;
    this.spiderBots = []; this.webTurrets = []; this.clones = []; this.explosions = [];
    this.camX = 0; this.camY = 0;
    this.levelCompleteTimer = 0; this.levelCompleteEarned = false;
    this.deathTimer = 0;
    this.state = GSTATE.PLAYING;
    document.getElementById('hud').classList.remove('hidden');
    document.getElementById('level-complete').classList.add('hidden');
    document.getElementById('game-over').classList.add('hidden');
    document.getElementById('menu-screen').style.display = 'none';
    document.getElementById('level-display').textContent = `Level ${n}`;
  }

  returnToMenu() {
    this.state = GSTATE.MENU;
    document.getElementById('hud').classList.add('hidden');
    document.getElementById('level-complete').classList.add('hidden');
    document.getElementById('game-over').classList.add('hidden');
    document.getElementById('menu-screen').style.display = '';
    this._buildMenu();
  }

  saveProgress() {
    localStorage.setItem('ws_maxUnlocked', this.maxUnlocked);
    localStorage.setItem('ws_beaten', JSON.stringify([...this.beatenLevels]));
    localStorage.setItem('ws_stars', this.stars);
  }

  _buildMenu() {
    const container = document.getElementById('level-buttons');
    container.innerHTML = '';
    for (let i = 1; i <= 30; i++) {
      const btn = document.createElement('button');
      btn.className = 'level-btn';
      btn.textContent = i;
      const beaten = this.beatenLevels.has(String(i));
      const unlocked = i <= this.maxUnlocked;
      if (beaten) btn.classList.add('beaten');
      else if (unlocked) btn.classList.add('unlocked');
      else btn.classList.add('locked');
      if (unlocked) {
        btn.addEventListener('click', () => this.startLevel(i));
      }
      container.appendChild(btn);
    }
  }

  update(dt) {
    if (this.state !== GSTATE.PLAYING) return;
    const p = this.player;
    const ld = this.levelData;

    // Gather input
    const left  = this.keys['ArrowLeft']  || this.keys['KeyA'];
    const right = this.keys['ArrowRight'] || this.keys['KeyD'];
    const up    = this.keys['ArrowUp']    || this.keys['KeyW'];
    const down  = this.keys['ArrowDown']  || this.keys['KeyS'];

    p.moveDir   = (right ? 1 : 0) - (left ? 1 : 0);
    p.climbInput = (down ? 1 : 0) - (up ? 1 : 0);
    p.jumpPressed = up || this.keys['Space'];

    // World mouse (divide by ZOOM to convert screen px → virtual world px)
    const worldMX = this.mouseX / this.ZOOM + this.camX;
    const worldMY = this.mouseY / this.ZOOM + this.camY;

    // Build dynamic platform list (includes cryo-frozen enemies as platforms)
    const allPlatforms = [...ld.platforms];
    for (const e of ld.enemies) {
      if (e.alive && e.cryoFrozen) {
        allPlatforms.push(new Platform(e.x, e.y, e.w, e.h));
      }
    }
    // Closed gates as platforms
    for (const g of ld.gates) {
      if (!g.open) allPlatforms.push(new Platform(g.x, g.y, g.w, g.h));
    }

    // Player update
    p.update(dt, allPlatforms, worldMX, worldMY);

    // Jump release (one-shot)
    if (!up && !this.keys['Space']) p.jumpConsumed = false;

    // Death check: lava
    for (const lv of ld.lavas) {
      if (rectsOverlap(p.x, p.y, p.w, p.h, lv.x, lv.y, lv.w, lv.h)) {
        if (p.takeDamage()) p.die();
      }
    }

    // Death check: enemy contact
    for (const e of ld.enemies) {
      if (!e.alive || e.frozen) continue;
      if (rectsOverlap(p.x, p.y, p.w, p.h, e.x, e.y, e.w, e.h)) {
        if (e instanceof ShooterEnemy) continue; // only bullets kill
        if (p.takeDamage()) p.die();
      }
    }

    // Shooter bullets
    for (const e of ld.enemies) {
      if (!(e instanceof ShooterEnemy) || !e.alive) continue;
      for (const b of e.bullets) {
        if (rectsOverlap(p.x, p.y, p.w, p.h, b.x-6, b.y-6, 12, 12)) {
          if (p.takeDamage()) p.die();
        }
      }
    }

    // Web hitting enemies
    if (p.web.isFlying && !p.web.hitEnemy) {
      for (const e of ld.enemies) {
        if (!e.alive) continue;
        if (rectsOverlap(p.web.endX-4, p.web.endY-4, 8, 8, e.x, e.y, e.w, e.h)) {
          p.web.hitEnemy = e;
          if (p.boomReady) {
            e.alive = false;
            this.explosions.push(new Explosion(e.cx, e.cy));
            p.boomReady = false;
          } else if (p.cryoReady) {
            e.freeze(true);
            p.cryoReady = false;
            p.web.release();
          } else if (p.plasmaReady) {
            // Plasma web: destroy nearest non-boundary platform
            let nearDist = Infinity, nearIdx = -1;
            for (let i = 0; i < ld.platforms.length; i++) {
              const pl = ld.platforms[i];
              if (pl.type === 'boundary') continue;
              const dx = pl.centerX - p.web.endX, dy = pl.centerY - p.web.endY;
              const d = Math.sqrt(dx*dx+dy*dy);
              if (d < nearDist) { nearDist = d; nearIdx = i; }
            }
            if (nearIdx >= 0) {
              const pl = ld.platforms[nearIdx];
              this.explosions.push(new Explosion(pl.centerX, pl.centerY));
              ld.platforms.splice(nearIdx, 1);
            }
            p.plasmaReady = false;
            p.web.release();
          } else {
            e.freeze(false);
            p.web.release();
          }
          break;
        }
      }
    }

    // Laser hitting enemies
    if (p.laserActive) {
      const lx = p.cx, ly = p.cy;
      const ldx = Math.cos(p.laserAngle) * 900;
      const ldy = Math.sin(p.laserAngle) * 900;
      for (const e of ld.enemies) {
        if (!e.alive) continue;
        const t = rayAABB(lx, ly, ldx, ldy, e.x, e.y, e.w, e.h);
        if (t !== null) {
          e.alive = false;
          this.explosions.push(new Explosion(e.cx, e.cy));
        }
      }
    }

    // Update enemies
    const floorY = this.vH - 24;
    for (const e of ld.enemies) {
      if (!e.alive) continue;
      if (e instanceof CeilingCrawler) e.update(dt);
      else if (e instanceof ShooterEnemy) e.update(dt, p.cx, p.cy);
      else if (e instanceof Bat) e.update(dt, p.cx, p.cy);
      else e.update(dt, allPlatforms);
      // Kill if fallen out of world
      if (e.y > floorY + 200) e.alive = false;
    }

    // Turret bullets hitting enemies
    for (const t of this.webTurrets) {
      for (const b of t.bullets) {
        for (const e of ld.enemies) {
          if (!e.alive || e.frozen) continue;
          if (rectsOverlap(b.x-5, b.y-5, 10, 10, e.x, e.y, e.w, e.h)) {
            e.freeze(false);
            b.life = 0;
          }
        }
      }
    }

    // Spider bot hitting enemies
    for (const sb of this.spiderBots) {
      if (!sb.alive) continue;
      for (const e of ld.enemies) {
        if (!e.alive || e.frozen) continue;
        if (rectsOverlap(sb.x, sb.y, sb.w, sb.h, e.x, e.y, e.w, e.h)) {
          e.freeze(false);
        }
      }
      sb.update(dt, allPlatforms);
    }
    this.spiderBots = this.spiderBots.filter(s => s.alive);

    // Web turrets
    for (const t of this.webTurrets) t.update(dt, ld.enemies);
    this.webTurrets = this.webTurrets.filter(t => t.alive);

    // Clones
    for (const c of this.clones) c.update(dt, p.cx, p.cy, ld.enemies, allPlatforms);
    this.clones = this.clones.filter(c => c.alive);

    // Explosions
    for (const ex of this.explosions) ex.update(dt);
    this.explosions = this.explosions.filter(ex => !ex.done);

    // Update objects
    ld.door.update(dt);
    for (const k of ld.keys) k.update(dt);
    for (const lv of ld.lavas) lv.update(dt);
    for (const cr of ld.crates) cr.update(dt, allPlatforms);

    // Collect keys
    for (const k of ld.keys) {
      if (k.collected) continue;
      if (rectsOverlap(p.x, p.y, p.w, p.h, k.x, k.y, k.w, k.h)) k.collected = true;
    }

    // Open gates — player walks into gate while carrying matching key
    for (const g of ld.gates) {
      if (g.open) continue;
      const hasKey = ld.keys.some(k => k.collected && k.color === g.color);
      if (hasKey && rectsOverlap(p.x - 30, p.y, p.w + 60, p.h, g.x, g.y, g.w, g.h)) {
        g.open = true;
      }
    }

    // Collect weapon pickups
    for (const wp of ld.weaponPickups) {
      if (wp.collected) continue;
      if (rectsOverlap(p.x, p.y, p.w, p.h, wp.x, wp.y, wp.w, wp.h)) {
        wp.collected = true;
        p.addToInventory(wp.type);
      }
    }

    // Crate web attach
    if (p.web.isFlying) {
      for (const cr of ld.crates) {
        if (rectsOverlap(p.web.endX-4, p.web.endY-4, 8, 8, cr.x, cr.y, cr.w, cr.h)) {
          cr.webAttached = true;
          p.web.attach(cr.cx, cr.cy, p.cx, p.cy);
          break;
        }
      }
    }
    // Move attached crate with web
    if (p.web.isAttached) {
      for (const cr of ld.crates) {
        if (cr.webAttached) {
          cr.x = p.web.endX - cr.w/2;
          cr.y = p.web.endY - cr.h/2;
        }
      }
    } else {
      for (const cr of ld.crates) cr.webAttached = false;
    }

    // Crate crush enemies
    for (const cr of ld.crates) {
      if (cr.vy > 200) {
        for (const e of ld.enemies) {
          if (!e.alive) continue;
          if (rectsOverlap(cr.x, cr.y, cr.w, cr.h, e.x, e.y, e.w, e.h)) {
            if (e.frozen) e.alive = false;
            else e.freeze(false);
          }
        }
      }
    }

    // Door / level complete
    const door = ld.door;
    if (rectsOverlap(p.x, p.y, p.w, p.h, door.x, door.y, door.w, door.h)) {
      if (this.levelCompleteTimer === 0) {
        this.levelCompleteTimer = 2.5;
        if (!this.beatenLevels.has(String(this.currentLevel))) {
          this.beatenLevels.add(String(this.currentLevel));
          this.stars++;
          this.levelCompleteEarned = true;
        }
        const next = this.currentLevel + 1;
        if (next > this.maxUnlocked && next <= 30) this.maxUnlocked = next;
        this.saveProgress();
        document.getElementById('level-complete').classList.remove('hidden');
        document.getElementById('stars-earned').textContent =
          this.levelCompleteEarned ? '★ +1 Star!' : 'Level Cleared!';
      }
    }
    if (this.levelCompleteTimer > 0) {
      this.levelCompleteTimer -= dt;
      if (this.levelCompleteTimer <= 0) {
        this.returnToMenu();
      }
    }

    // Death
    if (!p.alive || p.y > this.vH + 200) {
      p.alive = false;
      this.deathTimer += dt;
      if (this.deathTimer > 1.2) {
        this.state = GSTATE.DEAD;
        document.getElementById('game-over').classList.remove('hidden');
      }
    }

    // Camera — smooth horizontal follow; world height = vH so no vertical scroll
    const targetX = p.cx - this.vW * 0.35;
    this.camX += (targetX - this.camX) * Math.min(1, dt * 5);
    this.camX = Math.max(0, Math.min(ld.worldWidth - this.vW, this.camX));
    this.camY = 0;

    // Update inventory HUD
    this._updateHUD();
  }

  _updateHUD() {
    const p = this.player;
    const inv = document.getElementById('inventory');
    inv.innerHTML = '';
    for (let i = 0; i < 3; i++) {
      const slot = document.createElement('div');
      slot.className = 'inv-slot' + (i === p.activeSlot ? ' active' : '');
      if (p.inventory[i]) {
        const def = WEAPONS[p.inventory[i].type];
        slot.textContent = def.icon;
        slot.style.borderColor = i === p.activeSlot ? '#FFD700' : def.color;
        const ch = document.createElement('span');
        ch.className = 'charges';
        ch.textContent = p.inventory[i].charges;
        slot.appendChild(ch);
      }
      inv.appendChild(slot);
    }
  }

  draw() {
    const ctx = this.ctx;
    const W = this.screenW, H = this.screenH;
    ctx.clearRect(0, 0, W, H);

    if (this.state !== GSTATE.PLAYING && this.state !== GSTATE.DEAD && this.state !== GSTATE.COMPLETE) return;

    const ld = this.levelData;
    if (!ld) return;

    // Sky background (full canvas)
    const grad = ctx.createLinearGradient(0, 0, 0, H);
    grad.addColorStop(0, '#0a0a18');
    grad.addColorStop(1, '#18182A');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, W, H);

    // Stars (screen space, parallax)
    ctx.fillStyle = 'rgba(255,255,255,0.5)';
    for (let i = 0; i < 80; i++) {
      const sx = ((i * 137.5 + this.camX * 0.05) % W + W) % W;
      const sy = ((i * 91.3) % H + H) % H;
      ctx.fillRect(sx, sy, 1.5, 1.5);
    }

    // Apply zoom — all game content drawn in virtual coordinate space
    ctx.save();
    ctx.scale(this.ZOOM, this.ZOOM);

    const cx = this.camX, cy = this.camY;
    const vW = this.vW;

    // Platforms
    for (const p of ld.platforms) {
      if (p.type === 'boundary') continue;
      const sx = p.x - cx, sy = p.y - cy;
      if (sx + p.w < -50 || sx > vW + 50) continue;
      ctx.save();
      ctx.fillStyle = C.COL_PLATFORM;
      ctx.strokeStyle = '#3a5070';
      ctx.lineWidth = 1;
      ctx.fillRect(sx, sy, p.w, p.h);
      ctx.strokeRect(sx, sy, p.w, p.h);
      // Grip texture lines
      ctx.strokeStyle = '#4a6090';
      ctx.lineWidth = 1;
      for (let i = 10; i < p.w; i += 20) {
        ctx.beginPath(); ctx.moveTo(sx+i, sy); ctx.lineTo(sx+i, sy+p.h); ctx.stroke();
      }
      ctx.restore();
    }

    // Floor visual
    ctx.save();
    ctx.fillStyle = C.COL_GROUND;
    ctx.fillRect(-cx, this.vH - 24 - cy, ld.worldWidth, 24);
    ctx.restore();

    // Objects
    for (const lv of ld.lavas) lv.draw(ctx, cx, cy);
    for (const g of ld.gates) g.draw(ctx, cx, cy);
    ld.door.draw(ctx, cx, cy);
    for (const k of ld.keys) k.draw(ctx, cx, cy);
    for (const w of ld.weaponPickups) w.draw(ctx, cx, cy);
    for (const cr of ld.crates) cr.draw(ctx, cx, cy);

    // Enemies
    for (const e of ld.enemies) {
      if (!e.alive) continue;
      e.draw(ctx, cx, cy);
    }

    // Allies
    for (const sb of this.spiderBots) sb.draw(ctx, cx, cy);
    for (const t of this.webTurrets) t.draw(ctx, cx, cy);
    for (const c of this.clones) c.draw(ctx, cx, cy);

    // Explosions
    for (const ex of this.explosions) ex.draw(ctx, cx, cy);

    // Player
    if (this.player) this.player.draw(ctx, cx, cy);

    // Web aim indicator (in virtual space — mouse converted to virtual coords)
    if (this.state === GSTATE.PLAYING && this.player && this.player.web.isNone) {
      const vmx = this.mouseX / this.ZOOM, vmy = this.mouseY / this.ZOOM;
      ctx.save();
      ctx.globalAlpha = 0.25;
      ctx.strokeStyle = '#ffffff';
      ctx.setLineDash([6, 8]);
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(this.player.cx - cx, this.player.cy - cy);
      ctx.lineTo(vmx, vmy);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.restore();
    }

    // End zoom transform
    ctx.restore();

    // Cross-hair on mouse (screen space — drawn after restore)
    if (this.state === GSTATE.PLAYING) {
      ctx.save();
      ctx.strokeStyle = 'rgba(255,255,255,0.7)';
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(this.mouseX, this.mouseY, 8, 0, Math.PI*2);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(this.mouseX - 12, this.mouseY);
      ctx.lineTo(this.mouseX + 12, this.mouseY);
      ctx.moveTo(this.mouseX, this.mouseY - 12);
      ctx.lineTo(this.mouseX, this.mouseY + 12);
      ctx.stroke();
      ctx.restore();
    }
  }
}
