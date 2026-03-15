// Game objects: Key, Gate, Door, Crate, Lava, Explosion, WeaponPickup

class Key {
  constructor(x, y, color) {
    this.x = x; this.y = y; this.color = color; // 'blue' | 'red'
    this.w = 30; this.h = 36;
    this.collected = false;
    this.bobPhase = Math.random() * Math.PI * 2;
  }
  update(dt) { this.bobPhase += dt * 2; }
  draw(ctx, camX, camY) {
    if (this.collected) return;
    const sx = this.x - camX, sy = this.y - camY + Math.sin(this.bobPhase) * 5;
    ctx.save();
    ctx.fillStyle = this.color === 'blue' ? '#4488ff' : '#ff4444';
    ctx.shadowColor = ctx.fillStyle;
    ctx.shadowBlur = 12;
    // Key shape
    ctx.beginPath();
    ctx.arc(sx + 15, sy + 10, 10, 0, Math.PI*2);
    ctx.fill();
    ctx.fillStyle = this.color === 'blue' ? '#2266dd' : '#cc2222';
    ctx.fillRect(sx + 13, sy + 18, 4, 16);
    ctx.fillRect(sx + 13, sy + 24, 8, 4);
    ctx.fillRect(sx + 13, sy + 30, 8, 4);
    ctx.restore();
  }
}

class Gate {
  constructor(x, y, w, h, color) {
    this.x = x; this.y = y; this.w = w; this.h = h;
    this.color = color;
    this.open = false;
  }
  draw(ctx, camX, camY) {
    if (this.open) return;
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    ctx.fillStyle = this.color === 'blue' ? 'rgba(50,100,255,0.7)' : 'rgba(255,50,50,0.7)';
    ctx.strokeStyle = this.color === 'blue' ? '#88aaff' : '#ff8888';
    ctx.lineWidth = 3;
    ctx.fillRect(sx, sy, this.w, this.h);
    ctx.strokeRect(sx, sy, this.w, this.h);
    // Bars
    ctx.strokeStyle = this.color === 'blue' ? '#aaccff' : '#ffaaaa';
    ctx.lineWidth = 4;
    const bars = Math.max(2, Math.floor(this.w / 30));
    for (let i = 0; i <= bars; i++) {
      ctx.beginPath();
      ctx.moveTo(sx + (this.w / bars) * i, sy);
      ctx.lineTo(sx + (this.w / bars) * i, sy + this.h);
      ctx.stroke();
    }
    ctx.restore();
  }
}

class Door {
  constructor(x, y) {
    this.x = x; this.y = y; this.w = 60; this.h = 90;
    this.glowPhase = 0;
  }
  get cx() { return this.x + this.w/2; }
  get cy() { return this.y + this.h/2; }
  update(dt) { this.glowPhase += dt * 3; }
  draw(ctx, camX, camY) {
    const sx = this.x - camX, sy = this.y - camY;
    const glow = 8 + Math.sin(this.glowPhase) * 6;
    ctx.save();
    ctx.shadowColor = '#00ff44';
    ctx.shadowBlur = glow;
    ctx.fillStyle = '#005522';
    ctx.fillRect(sx, sy, this.w, this.h);
    ctx.strokeStyle = '#00ff44';
    ctx.lineWidth = 3;
    ctx.strokeRect(sx, sy, this.w, this.h);
    // Door arch
    ctx.fillStyle = '#00cc33';
    ctx.beginPath();
    ctx.moveTo(sx + 8, sy + this.h);
    ctx.lineTo(sx + 8, sy + 40);
    ctx.arc(sx + this.w/2, sy + 40, this.w/2 - 8, Math.PI, 0);
    ctx.lineTo(sx + this.w - 8, sy + this.h);
    ctx.fill();
    // Star
    ctx.fillStyle = '#ffff00';
    ctx.font = '18px Arial';
    ctx.fillText('★', sx + this.w/2 - 9, sy + 28);
    ctx.restore();
  }
}

class Crate {
  constructor(x, y) {
    this.x = x; this.y = y; this.w = 70; this.h = 70;
    this.vx = 0; this.vy = 0;
    this.onGround = false;
    this.webAttached = false;
  }
  get cx() { return this.x + this.w/2; }
  get cy() { return this.y + this.h/2; }
  get right()  { return this.x + this.w; }
  get bottom() { return this.y + this.h; }

  update(dt, platforms) {
    if (!this.webAttached) this.vy += C.GRAVITY * dt;
    this.x += this.vx * dt;
    this.y += this.vy * dt;
    this.onGround = false;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const oy = p.centerY < this.cy ? (p.y + p.h - this.y) : (p.y - this.bottom);
      if (oy < 0) { this.y += oy; this.vy = 0; this.onGround = true; }
      else { const ox = p.centerX < this.cx ? -(p.right - this.x) : (this.right - p.x); if (Math.abs(ox) < Math.abs(oy)) { this.x += ox; this.vx = 0; } }
    }
  }

  draw(ctx, camX, camY) {
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    ctx.fillStyle = '#8B5C2A';
    ctx.fillRect(sx, sy, this.w, this.h);
    ctx.strokeStyle = '#5C3A1A';
    ctx.lineWidth = 2;
    ctx.strokeRect(sx, sy, this.w, this.h);
    // Wood grain
    ctx.strokeStyle = '#6B4520';
    ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(sx, sy + this.h/3); ctx.lineTo(sx + this.w, sy + this.h/3); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(sx, sy + 2*this.h/3); ctx.lineTo(sx + this.w, sy + 2*this.h/3); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(sx + this.w/2, sy); ctx.lineTo(sx + this.w/2, sy + this.h); ctx.stroke();
    ctx.restore();
  }
}

class Lava {
  constructor(x, y, w, h) {
    this.x = x; this.y = y; this.w = w; this.h = h;
    this.wavePhase = Math.random() * Math.PI * 2;
    this.bubbles = [];
    for (let i = 0; i < 4; i++) {
      this.bubbles.push({ rx: Math.random(), phase: Math.random() * Math.PI * 2, size: 4 + Math.random() * 6 });
    }
  }
  get right()  { return this.x + this.w; }
  get bottom() { return this.y + this.h; }

  update(dt) {
    this.wavePhase += dt * 2;
    for (const b of this.bubbles) b.phase += dt * (1.5 + b.size * 0.1);
  }

  draw(ctx, camX, camY) {
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    // Glow
    ctx.shadowColor = '#ff4400';
    ctx.shadowBlur = 16;
    // Body gradient
    const grad = ctx.createLinearGradient(sx, sy, sx, sy + this.h);
    grad.addColorStop(0, '#ff6600');
    grad.addColorStop(0.4, '#dd2200');
    grad.addColorStop(1, '#880000');
    ctx.fillStyle = grad;
    ctx.beginPath();
    ctx.moveTo(sx, sy + 12);
    const waves = Math.ceil(this.w / 20);
    for (let i = 0; i <= waves; i++) {
      const wx = sx + (i / waves) * this.w;
      const wy = sy + Math.sin(this.wavePhase + i * 0.8) * 6 + 4;
      ctx.lineTo(wx, wy);
    }
    ctx.lineTo(sx + this.w, sy + this.h);
    ctx.lineTo(sx, sy + this.h);
    ctx.closePath();
    ctx.fill();
    // Bubbles
    ctx.fillStyle = '#ff8800';
    for (const b of this.bubbles) {
      const bx = sx + b.rx * this.w;
      const by = sy + 6 + Math.sin(b.phase) * 8;
      const r = b.size * Math.max(0.3, Math.sin(b.phase * 0.5));
      ctx.beginPath();
      ctx.arc(bx, by, Math.abs(r), 0, Math.PI*2);
      ctx.fill();
    }
    ctx.restore();
  }
}

class Explosion {
  constructor(x, y) {
    this.x = x; this.y = y;
    this.timer = 0.6;
    this.particles = [];
    for (let i = 0; i < 16; i++) {
      const angle = (i / 16) * Math.PI * 2;
      const speed = 150 + Math.random() * 200;
      this.particles.push({
        x, y,
        vx: Math.cos(angle) * speed, vy: Math.sin(angle) * speed,
        life: 0.4 + Math.random() * 0.2,
        maxLife: 0.6,
        color: ['#ff8800','#ff4400','#ffcc00','#ffffff'][Math.floor(Math.random()*4)]
      });
    }
  }

  update(dt) {
    this.timer -= dt;
    for (const p of this.particles) {
      p.x += p.vx * dt; p.y += p.vy * dt;
      p.vy += 400 * dt;
      p.life -= dt;
    }
    this.particles = this.particles.filter(p => p.life > 0);
  }

  get done() { return this.timer <= 0; }

  draw(ctx, camX, camY) {
    const progress = 1 - this.timer / 0.6;
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    // Shockwave
    ctx.globalAlpha = Math.max(0, 0.6 - progress);
    ctx.strokeStyle = '#ffaa00';
    ctx.lineWidth = 3;
    ctx.shadowColor = '#ff8800';
    ctx.shadowBlur = 10;
    ctx.beginPath();
    ctx.arc(sx, sy, progress * 80, 0, Math.PI*2);
    ctx.stroke();
    // Particles
    ctx.globalAlpha = 1;
    for (const p of this.particles) {
      ctx.globalAlpha = p.life / p.maxLife;
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x - camX, p.y - camY, 4 * (p.life / p.maxLife), 0, Math.PI*2);
      ctx.fill();
    }
    ctx.restore();
  }
}

class WeaponPickup {
  constructor(x, y, type) {
    this.x = x; this.y = y; this.w = 36; this.h = 36;
    this.type = type;
    this.collected = false;
    this.bobPhase = Math.random() * Math.PI * 2;
  }
  update(dt) { this.bobPhase += dt * 2; }
  draw(ctx, camX, camY) {
    if (this.collected) return;
    const sx = this.x - camX, sy = this.y - camY + Math.sin(this.bobPhase) * 6;
    const def = WEAPONS[this.type];
    ctx.save();
    ctx.shadowColor = def.color;
    ctx.shadowBlur = 14;
    ctx.fillStyle = def.color + '44';
    ctx.strokeStyle = def.color;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(sx + this.w/2, sy + this.h/2, 20, 0, Math.PI*2);
    ctx.fill();
    ctx.stroke();
    ctx.shadowBlur = 0;
    ctx.font = '18px Arial';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillStyle = '#fff';
    ctx.fillText(def.icon, sx + this.w/2, sy + this.h/2);
    ctx.restore();
  }
}

// Spider Bot (ally)
class SpiderBot {
  constructor(x, y) {
    this.x = x; this.y = y; this.w = 40; this.h = 30;
    this.vx = 280; this.vy = 0;
    this.timer = C.SPIDERBOT_LIFETIME;
    this.facingRight = true;
    this.alive = true;
    this.legPhase = 0;
  }
  get cx() { return this.x + this.w/2; }
  get cy() { return this.y + this.h/2; }
  get right()  { return this.x + this.w; }
  get bottom() { return this.y + this.h; }

  update(dt, platforms) {
    this.timer -= dt;
    if (this.timer <= 0) { this.alive = false; return; }
    this.legPhase += dt * 10;
    this.vy += C.GRAVITY * dt;
    this.x += this.vx * dt;
    this.y += this.vy * dt;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const oy = p.centerY < this.cy ? (p.y + p.h - this.y) : (p.y - this.bottom);
      if (oy < 0) { this.y += oy; this.vy = 0; }
      else { this.vx *= -1; this.facingRight = !this.facingRight; }
    }
    this.facingRight = this.vx > 0;
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    const sx = this.x - camX + this.w/2, sy = this.y - camY + this.h/2;
    ctx.save();
    ctx.translate(sx, sy);
    if (!this.facingRight) ctx.scale(-1,1);
    ctx.fillStyle = '#882299';
    ctx.beginPath(); ctx.ellipse(0, 0, 16, 12, 0, 0, Math.PI*2); ctx.fill();
    ctx.strokeStyle = '#aa44bb'; ctx.lineWidth = 2;
    for (let i = 0; i < 4; i++) {
      const side = i < 2 ? -1 : 1;
      ctx.beginPath();
      ctx.moveTo(side*12, 0);
      ctx.lineTo(side*(16 + Math.cos(this.legPhase + i)*5), this.h/2 * Math.sin(this.legPhase + i));
      ctx.stroke();
    }
    ctx.fillStyle = '#ff2222'; ctx.beginPath(); ctx.arc(-6, -4, 3, 0, Math.PI*2); ctx.fill();
    ctx.beginPath(); ctx.arc(2, -4, 3, 0, Math.PI*2); ctx.fill();
    ctx.restore();
  }
}

// Web Turret (ally)
class WebTurret {
  constructor(x, y) {
    this.x = x; this.y = y; this.w = 50; this.h = 60;
    this.timer = C.TURRET_LIFETIME;
    this.shotsLeft = C.TURRET_SHOTS;
    this.shootTimer = 0.6;
    this.alive = true;
    this.bullets = [];
    this.aimAngle = 0;
  }
  get cx() { return this.x + this.w/2; }
  get cy() { return this.y + this.h/2; }

  update(dt, enemies) {
    this.timer -= dt;
    if (this.timer <= 0 || this.shotsLeft <= 0) { this.alive = false; return; }
    // Find nearest enemy in range
    let nearest = null, nearDist = 700;
    for (const e of enemies) {
      if (!e.alive || e.frozen) continue;
      const dx = e.cx - this.cx, dy = e.cy - this.cy;
      const d = Math.sqrt(dx*dx + dy*dy);
      if (d < nearDist) { nearDist = d; nearest = e; }
    }
    if (nearest) {
      const dx = nearest.cx - this.cx, dy = nearest.cy - this.cy;
      this.aimAngle = Math.atan2(dy, dx);
      this.shootTimer -= dt;
      if (this.shootTimer <= 0) {
        this.shootTimer = 0.6;
        this.shotsLeft--;
        this.bullets.push({ x: this.cx, y: this.cy - 10,
          vx: Math.cos(this.aimAngle)*600, vy: Math.sin(this.aimAngle)*600, life: 2 });
      }
    }
    for (const b of this.bullets) { b.x += b.vx*dt; b.y += b.vy*dt; b.life -= dt; }
    this.bullets = this.bullets.filter(b => b.life > 0);
  }

  draw(ctx, camX, camY) {
    if (!this.alive) return;
    const sx = this.x - camX, sy = this.y - camY;
    ctx.save();
    ctx.fillStyle = '#336633';
    ctx.fillRect(sx + 8, sy + 30, 34, 30);
    ctx.fillStyle = '#448844';
    ctx.beginPath(); ctx.arc(sx + 25, sy + 30, 18, Math.PI, 0); ctx.fill();
    ctx.strokeStyle = '#88ff88'; ctx.lineWidth = 4;
    ctx.beginPath();
    ctx.moveTo(sx+25, sy+30);
    ctx.lineTo(sx+25+Math.cos(this.aimAngle)*32, sy+30+Math.sin(this.aimAngle)*24);
    ctx.stroke();
    ctx.fillStyle = '#44ff44';
    for (const b of this.bullets) {
      ctx.beginPath();
      ctx.arc(b.x - camX, b.y - camY, 5, 0, Math.PI*2);
      ctx.fill();
    }
    ctx.restore();
  }
}
