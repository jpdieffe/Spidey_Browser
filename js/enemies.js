// All enemy types

class BaseEnemy {
  constructor(x, y, w, h) {
    this.x = x; this.y = y; this.w = w; this.h = h;
    this.vx = 0; this.vy = 0;
    this.alive = true;
    this.frozen = false;
    this.frozenTimer = 0;
    this.cryoFrozen = false;
    this.leftBound = x - 200;
    this.rightBound = x + 200;
    this.facingRight = true;
  }
  get cx() { return this.x + this.w / 2; }
  get cy() { return this.y + this.h / 2; }
  get right()  { return this.x + this.w; }
  get bottom() { return this.y + this.h; }

  freeze(cryo = false) {
    this.frozen = true;
    this.cryoFrozen = cryo;
    this.frozenTimer = cryo ? C.FREEZE_CRYO : C.FREEZE_NORMAL;
  }

  updateFreeze(dt) {
    if (this.frozen) {
      this.frozenTimer -= dt;
      if (this.frozenTimer <= 0) { this.frozen = false; this.cryoFrozen = false; }
      return true;
    }
    return false;
  }

  // Returns {x,y} for top of frozen block (used as platform)
  getFrozenPlatform() {
    if (!this.cryoFrozen) return null;
    return { x: this.x, y: this.y, w: this.w, h: this.h };
  }

  drawFrozen(ctx, camX, camY) {
    const sx = this.x - camX, sy = this.y - camY;
    if (this.cryoFrozen) {
      ctx.save();
      ctx.fillStyle = 'rgba(100,200,255,0.6)';
      ctx.strokeStyle = '#88ddff';
      ctx.lineWidth = 2;
      ctx.fillRect(sx, sy, this.w, this.h);
      ctx.strokeRect(sx, sy, this.w, this.h);
      // Ice crystal lines
      ctx.strokeStyle = 'rgba(255,255,255,0.5)';
      ctx.lineWidth = 1;
      for (let i = 0; i < 3; i++) {
        const bx = sx + this.w * (0.2 + i * 0.3), by = sy;
        ctx.beginPath();
        ctx.moveTo(bx, by + this.h * 0.1);
        ctx.lineTo(bx, by + this.h * 0.9);
        ctx.stroke();
      }
      ctx.restore();
    } else {
      // Normal freeze — gray overlay
      ctx.save();
      ctx.globalAlpha = 0.55;
      ctx.fillStyle = '#aaaacc';
      ctx.fillRect(sx, sy, this.w, this.h);
      ctx.restore();
    }
  }
}

// Ground Patrol Robot (red bipedal)
class Enemy extends BaseEnemy {
  constructor(x, y, leftBound, rightBound) {
    super(x, y, 90, 120);
    this.speed = 120 + Math.random() * 80;
    this.leftBound = leftBound; this.rightBound = rightBound;
    this.onGround = true;
    this.vx = this.speed;
  }

  update(dt, platforms) {
    if (this.updateFreeze(dt)) { this.vx = 0; return; }
    this.vx = this.facingRight ? this.speed : -this.speed;
    this.x += this.vx * dt;
    if (this.x <= this.leftBound) { this.x = this.leftBound; this.facingRight = true; }
    if (this.right >= this.rightBound) { this.x = this.rightBound - this.w; this.facingRight = false; }
    this._applyGravity(dt, platforms);
  }

  _applyGravity(dt, platforms) {
    this.vy += C.GRAVITY * dt;
    this.y += this.vy * dt;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const oy = p.centerY < this.cy ? (p.y + p.h - this.y) : (p.y - this.bottom);
      if (oy < 0) { this.y += oy; this.vy = 0; this.onGround = true; }
    }
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    const sx = this.x - camX, sy = this.y - camY;
    if (this.frozen) { this.drawFrozen(ctx, camX, camY); }
    // Draw body regardless (showing through ice for cryo)
    ctx.save();
    if (this.frozen && !this.cryoFrozen) ctx.globalAlpha = 0.3;
    // Body
    ctx.fillStyle = '#cc2222';
    ctx.fillRect(sx + 15, sy + 30, 60, 60); // torso
    // Head
    ctx.fillStyle = '#dd3333';
    ctx.fillRect(sx + 22, sy + 8, 46, 28);
    // Eyes
    ctx.fillStyle = '#ffff00';
    ctx.fillRect(sx + 28, sy + 14, 10, 10);
    ctx.fillRect(sx + 52, sy + 14, 10, 10);
    // Legs
    ctx.fillStyle = '#991111';
    ctx.fillRect(sx + 18, sy + 88, 20, 32);
    ctx.fillRect(sx + 52, sy + 88, 20, 32);
    // Arms raised
    ctx.fillRect(sx + 2, sy + 35, 14, 35);
    ctx.fillRect(sx + 74, sy + 35, 14, 35);
    ctx.restore();
  }
}

// Ceiling Crawler (purple crab on ceiling)
class CeilingCrawler extends BaseEnemy {
  constructor(x, y, leftBound, rightBound) {
    super(x, y, 70, 50);
    this.speed = 100 + Math.random() * 60;
    this.leftBound = leftBound; this.rightBound = rightBound;
    this.facingRight = Math.random() > 0.5;
    this.legPhase = Math.random() * Math.PI * 2;
  }

  update(dt) {
    if (this.updateFreeze(dt)) { this.vx = 0; return; }
    this.legPhase += dt * 8;
    this.vx = this.facingRight ? this.speed : -this.speed;
    this.x += this.vx * dt;
    if (this.x <= this.leftBound) { this.x = this.leftBound; this.facingRight = true; }
    if (this.right >= this.rightBound) { this.x = this.rightBound - this.w; this.facingRight = false; }
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    if (this.frozen) { this.drawFrozen(ctx, camX, camY); }
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    if (this.frozen && !this.cryoFrozen) ctx.globalAlpha = 0.3;
    ctx.translate(sx + this.w/2, sy + this.h/2);
    ctx.scale(1, -1); // upside down
    // Body
    ctx.fillStyle = '#882299';
    ctx.beginPath();
    ctx.ellipse(0, 0, 28, 18, 0, 0, Math.PI*2);
    ctx.fill();
    // Eyes
    ctx.fillStyle = '#ff4444';
    ctx.fillRect(-12, -6, 8, 8);
    ctx.fillRect(4, -6, 8, 8);
    // Legs
    ctx.strokeStyle = '#aa44bb';
    ctx.lineWidth = 3;
    for (let i = 0; i < 4; i++) {
      const side = i < 2 ? -1 : 1;
      const idx = i % 2;
      const legAngle = this.legPhase + idx * Math.PI;
      ctx.beginPath();
      ctx.moveTo(side * 20, 0);
      ctx.lineTo(side * (30 + Math.cos(legAngle) * 8), 12 + Math.sin(legAngle) * 4);
      ctx.stroke();
    }
    ctx.restore();
  }
}

// Shooter Enemy (stationary turret)
class ShooterEnemy extends BaseEnemy {
  constructor(x, y, shootInterval) {
    super(x, y, 80, 100);
    this.shootInterval = shootInterval || (1 + Math.random());
    this.shootTimer = this.shootInterval * Math.random();
    this.bullets = [];
  }

  update(dt, playerX, playerY) {
    if (this.updateFreeze(dt)) { return; }
    const dx = playerX - this.cx, dy = playerY - this.cy;
    const dist = Math.sqrt(dx*dx + dy*dy);
    this.facingRight = dx > 0;
    if (dist <= 900) {
      this.shootTimer -= dt;
      if (this.shootTimer <= 0) {
        this.shootTimer = this.shootInterval;
        const spd = 500;
        const nx = dx / dist, ny = dy / dist;
        this.bullets.push({ x: this.cx, y: this.cy, vx: nx*spd, vy: ny*spd, life: 3 });
      }
    }
    for (const b of this.bullets) {
      b.x += b.vx * dt; b.y += b.vy * dt; b.life -= dt;
    }
    this.bullets = this.bullets.filter(b => b.life > 0);
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    if (this.frozen) { this.drawFrozen(ctx, camX, camY); }
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    if (this.frozen && !this.cryoFrozen) ctx.globalAlpha = 0.3;
    // Base
    ctx.fillStyle = '#556677';
    ctx.fillRect(sx + 10, sy + 40, 60, 60);
    // Turret dome
    ctx.fillStyle = '#778899';
    ctx.beginPath();
    ctx.arc(sx + 40, sy + 40, 28, Math.PI, 0);
    ctx.fill();
    // Barrel
    ctx.strokeStyle = '#aabbcc';
    ctx.lineWidth = 6;
    ctx.beginPath();
    const barrelAngle = this.facingRight ? -0.3 : Math.PI + 0.3;
    ctx.moveTo(sx + 40, sy + 40);
    ctx.lineTo(sx + 40 + Math.cos(barrelAngle) * 40, sy + 40 + Math.sin(barrelAngle) * 24);
    ctx.stroke();
    // Eye
    ctx.fillStyle = '#ff2222';
    ctx.beginPath();
    ctx.arc(sx + 40, sy + 34, 8, 0, Math.PI*2);
    ctx.fill();
    ctx.restore();

    // Bullets
    ctx.save();
    ctx.fillStyle = '#ff6600';
    for (const b of this.bullets) {
      ctx.beginPath();
      ctx.arc(b.x - camX, b.y - camY, 6, 0, Math.PI*2);
      ctx.fill();
    }
    ctx.restore();
  }
}

// Flying Bat
class Bat extends BaseEnemy {
  constructor(x, y, patrolLeft, patrolRight, patrolY) {
    super(x, y, 50, 30);
    this.speed = 180 + Math.random() * 60;
    this.patrolLeft = patrolLeft; this.patrolRight = patrolRight;
    this.patrolY = patrolY || y;
    this.chasing = false;
    this.bobPhase = Math.random() * Math.PI * 2;
    this.wingPhase = 0;
    this.baseX = x; this.baseY = y;
  }

  update(dt, playerX, playerY) {
    if (this.updateFreeze(dt)) { return; }
    this.bobPhase += dt * 3;
    this.wingPhase += dt * 12;
    const dx = playerX - this.cx, dy = playerY - this.cy;
    const dist = Math.sqrt(dx*dx + dy*dy);
    this.chasing = dist <= 500;
    if (this.chasing) {
      this.vx += (dx / dist * this.speed - this.vx) * Math.min(1, dt * 4);
      this.vy += (dy / dist * this.speed - this.vy) * Math.min(1, dt * 4);
      this.facingRight = dx > 0;
    } else {
      this.vx = this.facingRight ? this.speed * 0.5 : -this.speed * 0.5;
      this.vy = Math.sin(this.bobPhase) * 50;
      if (this.x < this.patrolLeft) { this.facingRight = true; }
      if (this.right > this.patrolRight) { this.facingRight = false; }
    }
    this.x += this.vx * dt;
    this.y += this.vy * dt;
    this.y = this.patrolY + Math.sin(this.bobPhase) * 30 + (this.chasing ? (playerY - this.patrolY) * 0.3 : 0);
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    if (this.frozen) { this.drawFrozen(ctx, camX, camY); }
    const sx = this.x - camX + this.w/2, sy = this.y - camY + this.h/2;
    ctx.save();
    if (this.frozen && !this.cryoFrozen) ctx.globalAlpha = 0.3;
    ctx.translate(sx, sy);
    if (!this.facingRight) ctx.scale(-1, 1);
    const wingFlap = Math.sin(this.wingPhase) * 0.6;
    // Wings
    ctx.fillStyle = '#551166';
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.quadraticCurveTo(-30, -20 + wingFlap * 20, -50, -10 + wingFlap * 15);
    ctx.quadraticCurveTo(-30, 10, 0, 5);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.quadraticCurveTo(30, -20 + wingFlap * 20, 50, -10 + wingFlap * 15);
    ctx.quadraticCurveTo(30, 10, 0, 5);
    ctx.fill();
    // Body
    ctx.fillStyle = '#882299';
    ctx.beginPath();
    ctx.ellipse(0, 2, 10, 12, 0, 0, Math.PI*2);
    ctx.fill();
    // Eyes
    ctx.fillStyle = '#ff2222';
    ctx.fillRect(-5, -4, 4, 4);
    ctx.fillRect(1, -4, 4, 4);
    ctx.restore();
  }
}

// Jumping Robot (green spring robot)
class JumpingRobot extends BaseEnemy {
  constructor(x, y, leftBound, rightBound) {
    super(x, y, 70, 90);
    this.speed = 100 + Math.random() * 60;
    this.leftBound = leftBound; this.rightBound = rightBound;
    this.onGround = true;
    this.jumpTimer = 2 + Math.random() * 1.5;
    this.springCompress = 0;
  }

  update(dt, platforms) {
    if (this.updateFreeze(dt)) { this.vx = 0; return; }
    this.vx = this.facingRight ? this.speed : -this.speed;
    if (this.onGround) {
      this.jumpTimer -= dt;
      if (this.jumpTimer <= 0) {
        this.jumpTimer = 2 + Math.random() * 1.5;
        this.vy = -(1200 + Math.random() * 300);
        this.onGround = false;
        this.springCompress = 0.3;
      }
    }
    if (this.springCompress > 0) this.springCompress -= dt * 2;
    this.vy += C.GRAVITY * dt;
    this.x += this.vx * dt;
    this.y += this.vy * dt;
    if (this.x <= this.leftBound) { this.x = this.leftBound; this.facingRight = true; }
    if (this.right >= this.rightBound) { this.x = this.rightBound - this.w; this.facingRight = false; }
    this.onGround = false;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const oy = p.centerY < this.cy ? (p.y + p.h - this.y) : (p.y - this.bottom);
      if (oy < 0) { this.y += oy; this.vy = 0; this.onGround = true; }
    }
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    if (this.frozen) { this.drawFrozen(ctx, camX, camY); }
    const sx = this.x - camX, sy = this.y - camY;
    const compress = Math.max(0, this.springCompress);
    ctx.save();
    if (this.frozen && !this.cryoFrozen) ctx.globalAlpha = 0.3;
    // Springs / legs
    ctx.strokeStyle = '#338833';
    ctx.lineWidth = 4;
    for (let i = 0; i < 2; i++) {
      const lx = sx + 12 + i * 36;
      ctx.beginPath();
      for (let j = 0; j < 5; j++) {
        const zig = j % 2 === 0 ? -6 : 6;
        ctx.lineTo(lx + zig, sy + 60 + j * (10 - compress * 4));
      }
      ctx.stroke();
    }
    // Body
    ctx.fillStyle = '#228822';
    ctx.fillRect(sx + 5, sy + 10, 60, 55);
    // Head
    ctx.fillStyle = '#33aa33';
    ctx.fillRect(sx + 12, sy, 46, 18);
    // Eyes
    ctx.fillStyle = '#ffff00';
    ctx.fillRect(sx + 17, sy + 4, 8, 8);
    ctx.fillRect(sx + 45, sy + 4, 8, 8);
    ctx.restore();
  }
}
