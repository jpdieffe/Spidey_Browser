// Weapons active logic — applied from Game.js when player fires web

// Returns modified weapon behavior flags
function applyWeapon(player, type, enemies, platforms, game) {
  switch (type) {
    case 'SPRING_WEB':
      player.springReady = true;
      break;
    case 'CRYO_WEB':
      player.cryoReady = true;
      break;
    case 'BOOM_WEB':
      player.boomReady = true;
      break;
    case 'PLASMA_WEB':
      player.plasmaReady = true;
      break;
    case 'JET_WEB':
      player.jetActive = true;
      player.jetTimer = C.JET_DURATION;
      player.web.release();
      break;
    case 'LASER_VISION':
      player.laserActive = true;
      player.laserTimer = C.LASER_DURATION;
      break;
    case 'IRON_SPIDER':
      player.ironSpiderActive = true;
      player.ironSpiderHits = C.IRON_SPIDER_HITS;
      break;
    case 'SPIDER_BOT':
      game.spawnSpiderBot(player.cx, player.y);
      break;
    case 'WEB_TURRET':
      game.spawnWebTurret(player.cx, player.bottom - 60);
      break;
    case 'CLONE':
      game.spawnClone(player.cx, player.y);
      break;
  }
}

// Spider Clone AI
class SpiderClone {
  constructor(x, y) {
    this.x = x; this.y = y;
    this.w = C.PLAYER_W; this.h = C.PLAYER_H;
    this.vx = 0; this.vy = 0;
    this.timer = C.CLONE_LIFETIME;
    this.alive = true;
    this.onGround = false;
    this.facingRight = true;
    this.state = 'FOLLOW'; // FOLLOW | ATTACK
    this.webCooldown = 0;
    this.web = null; // { x, y } attached point or null
    this.runFrame = 0;
    this.runFrameTimer = 0;
  }
  get cx() { return this.x + this.w/2; }
  get cy() { return this.y + this.h/2; }
  get right()  { return this.x + this.w; }
  get bottom() { return this.y + this.h; }

  update(dt, playerX, playerY, enemies, platforms) {
    this.timer -= dt;
    if (this.timer <= 0) { this.alive = false; return; }
    if (this.webCooldown > 0) this.webCooldown -= dt;

    // Find nearest enemy
    let nearestEnemy = null, nearDist = 450;
    for (const e of enemies) {
      if (!e.alive || e.frozen) continue;
      const dx = e.cx - this.cx, dy = e.cy - this.cy;
      const d = Math.sqrt(dx*dx + dy*dy);
      if (d < nearDist) { nearDist = d; nearestEnemy = e; }
    }

    if (nearestEnemy && this.webCooldown <= 0) {
      // Fire web at enemy
      nearestEnemy.freeze(false);
      this.webCooldown = 1.2;
    }

    // Movement: follow player
    const dx = playerX - this.cx;
    const targetVX = Math.abs(dx) > 80 ? Math.sign(dx) * C.MOVE_SPEED : 0;
    this.vx = targetVX;
    this.facingRight = this.vx >= 0;

    this.vy += C.GRAVITY * dt;
    this.x += this.vx * dt;
    this.y += this.vy * dt;

    this.onGround = false;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const oy = p.centerY < this.cy ? (p.y + p.h - this.y) : (p.y - this.bottom);
      if (oy < 0) { this.y += oy; this.vy = 0; this.onGround = true; }
      else { const ox = p.centerX < this.cx ? -(p.right - this.x) : (this.right - p.x); if (Math.abs(ox) < Math.abs(oy)) { this.x += ox; this.vx = 0; } }
    }

    // Jump over obstacles
    if (this.onGround && Math.abs(dx) > 80) {
      // Simple pathfinding: jump if player is higher or if blocked
    }

    // Animation
    if (this.onGround && Math.abs(this.vx) > 10) {
      this.runFrameTimer += dt;
      if (this.runFrameTimer >= 1/C.RUN_FPS) { this.runFrameTimer = 0; this.runFrame = (this.runFrame+1) % C.RUN_FRAMES; }
    }
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    ctx.globalAlpha = 0.85;
    if (!this.facingRight) { ctx.translate(sx + this.w, sy); ctx.scale(-1, 1); }
    else { ctx.translate(sx, sy); }

    const frame = !this.onGround ? 'user_jump' : (Math.abs(this.vx) > 10 ? 'user_run' : 'user_stand');
    if (frame === 'user_run') {
      const img = Assets.get('user_run');
      if (img && img.complete) {
        const fw = img.width / C.RUN_FRAMES;
        ctx.drawImage(img, this.runFrame * fw, 0, fw, img.height, 0, 0, this.w, this.h);
      }
    } else {
      const img = Assets.get(frame);
      if (img && img.complete) {
        // Blue tint for clone
        ctx.drawImage(img, 0, 0, this.w, this.h);
      }
    }
    // Blue overlay
    ctx.globalAlpha = 0.3;
    ctx.fillStyle = '#4488ff';
    ctx.fillRect(0, 0, this.w, this.h);
    ctx.restore();

    // Lifetime bar above
    const barW = 50, barH = 4;
    const barX = this.cx - camX - barW/2, barY = sy - 12;
    ctx.fillStyle = '#222';
    ctx.fillRect(barX, barY, barW, barH);
    ctx.fillStyle = '#4488ff';
    ctx.fillRect(barX, barY, barW * (this.timer / C.CLONE_LIFETIME), barH);
  }
}
