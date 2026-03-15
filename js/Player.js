// Player — physics, input, animation, weapons
class Player {
  constructor(x, y) {
    this.x = x; this.y = y;
    this.vx = 0; this.vy = 0;
    this.w = C.PLAYER_W; this.h = C.PLAYER_H;

    // State
    this.onGround = false;
    this.onCeiling = false;
    this.isSwinging = false;
    this.facingRight = true;
    this.alive = true;
    this.dead = false;

    // Input
    this.moveDir = 0;     // -1, 0, +1
    this.climbInput = 0; // -1=up, +1=down
    this.jumpPressed = false;
    this.jumpConsumed = false;

    // Web
    this.web = new WebLine();

    // Ceiling
    this.ceilingImmuneTimer = 0;

    // Weapons
    this.inventory = [];  // [{type, charges}]
    this.activeSlot = 0;

    // Power-up active states
    this.jetActive = false; this.jetTimer = 0;
    this.laserActive = false; this.laserTimer = 0;
    this.ironSpiderHits = 0; this.ironSpiderActive = false; this.iframesTimer = 0;
    this.springReady = false;
    this.boomReady = false;
    this.cryoReady = false;
    this.plasmaReady = false;

    // Animation
    this.runFrame = 0;
    this.runFrameTimer = 0;

    // Laser aim
    this.laserAngle = 0;

    // For spawn
    this.spawnX = x; this.spawnY = y;
  }

  get cx() { return this.x + this.w / 2; }
  get cy() { return this.y + this.h / 2; }
  get bottom() { return this.y + this.h; }
  get right()  { return this.x + this.w; }

  respawn() {
    this.x = this.spawnX; this.y = this.spawnY;
    this.vx = 0; this.vy = 0;
    this.onGround = false; this.onCeiling = false;
    this.isSwinging = false;
    this.web.release();
    this.alive = true; this.dead = false;
    this.jetActive = false; this.laserActive = false;
    this.iframesTimer = 0;
    this.ironSpiderActive = false; this.ironSpiderHits = 0;
  }

  takeDamage() {
    if (this.iframesTimer > 0) return false;
    if (this.ironSpiderActive) {
      this.ironSpiderHits--;
      this.iframesTimer = C.IFRAMES;
      if (this.ironSpiderHits <= 0) {
        this.ironSpiderActive = false;
        // Remove armor from inventory
        this.inventory = this.inventory.filter(it => it.type !== 'IRON_SPIDER');
      }
      return false; // absorbed
    }
    return true; // kill
  }

  die() { this.alive = false; this.dead = true; this.web.release(); }

  // Use active weapon (called on web fire or key press)
  useWeapon(type) {
    const slot = this.inventory.find(s => s.type === type);
    if (!slot || slot.charges <= 0) return false;
    slot.charges--;
    if (slot.charges <= 0) this.inventory = this.inventory.filter(s => s.type !== type);
    return true;
  }

  getActiveWeapon() {
    return this.inventory[this.activeSlot] || null;
  }

  addToInventory(type) {
    const existing = this.inventory.find(s => s.type === type);
    if (existing) { existing.charges += WEAPONS[type].defaultCharges; return; }
    if (this.inventory.length < 3) {
      this.inventory.push({ type, charges: WEAPONS[type].defaultCharges });
    }
  }

  cycleWeapon(dir) {
    if (this.inventory.length === 0) return;
    this.activeSlot = (this.activeSlot + dir + this.inventory.length) % this.inventory.length;
  }

  update(dt, platforms, cursorWorldX, cursorWorldY) {
    if (!this.alive) return;

    // Ceiling immunity
    if (this.ceilingImmuneTimer > 0) this.ceilingImmuneTimer -= dt;

    // Iframes
    if (this.iframesTimer > 0) this.iframesTimer -= dt;

    const w = this.web;

    // Jet Web flight
    if (this.jetActive) {
      this.jetTimer -= dt;
      if (this.jetTimer <= 0) { this.jetActive = false; }
      else {
        // Full directional movement at jet speed
        const targetVX = this.moveDir * C.JET_SPEED;
        const targetVY = (this.climbInput !== 0) ? this.climbInput * C.JET_SPEED : -40;
        this.vx += (targetVX - this.vx) * Math.min(1, dt * 8);
        this.vy += (targetVY - this.vy) * Math.min(1, dt * 8);
        // Disable web while jetting
        if (w.isAttached) w.release();
        this.onGround = false; this.onCeiling = false; this.isSwinging = false;
      }
    }

    // Laser Vision
    if (this.laserActive) {
      this.laserTimer -= dt;
      if (this.laserTimer <= 0) this.laserActive = false;
      this.laserAngle = Math.atan2(cursorWorldY - this.cy, cursorWorldX - this.cx);
    }

    if (!this.jetActive) {
      // Web shooting / flying
      if (w.isFlying) {
        const travelX = w.endX - w.startX;
        const travelY = w.endY - w.startY;
        const travelLen = Math.sqrt(travelX*travelX + travelY*travelY);
        if (travelLen >= C.WEB_MAX_LEN) {
          w.release();
        } else {
          w.endX += w.velX * dt;
          w.endY += w.velY * dt;
          // Check platform/boundary hits
          const hit = this._webHitPlatforms(w.startX, w.startY, w.endX, w.endY, platforms);
          if (hit) {
            w.attach(hit.x, hit.y, this.cx, this.cy);
            this.isSwinging = true;
          }
        }
        // Update start to follow player hand
        w.startX = this.cx; w.startY = this.cy;
      }

      if (w.isAttached) {
        w.startX = this.cx; w.startY = this.cy;

        // Swing physics — pendulum from anchor
        const ax = w.endX, ay = w.endY;
        // Vector from anchor to player
        const dx = this.cx - ax, dy = this.cy - ay;
        const dist = Math.sqrt(dx*dx + dy*dy) || 0.01;

        // Rope climb
        if (this.climbInput !== 0) {
          w.ropeLength = Math.max(C.ROPE_MIN, Math.min(C.ROPE_MAX, w.ropeLength + this.climbInput * C.CLIMB_SPEED * dt));
        }

        // Angle from anchor (down = 0)
        const angle = Math.atan2(dx, dy); // angle in XY plane, 0 = straight down
        const angAccel = -(C.GRAVITY / w.ropeLength) * Math.sin(angle) + this.moveDir * C.SWING_PUMP * dt;
        w.ropeAngularVel += angAccel * dt;
        w.ropeAngularVel *= Math.pow(C.SWING_DAMP, dt * 60);

        const newAngle = angle + w.ropeAngularVel * dt;
        const newPX = ax + Math.sin(newAngle) * w.ropeLength;
        const newPY = ay + Math.cos(newAngle) * w.ropeLength;

        this.vx = (newPX - this.cx) / dt;
        this.vy = (newPY - this.cy) / dt;

        // Release if player lands on ground/ceiling naturally
      } else {
        // Normal physics
        if (!this.onCeiling) {
          this.vy += C.GRAVITY * dt;
        }
        this.vx = this.moveDir * C.MOVE_SPEED;
      }

      // Jump
      if (this.jumpPressed && !this.jumpConsumed) {
        this.jumpConsumed = true;
        if (this.onCeiling) {
          // Drop from ceiling
          this.onCeiling = false;
          this.ceilingImmuneTimer = 0.5;
          this.vy = 200;
        } else if (this.onGround) {
          const jumpV = this.springReady ? C.SUPER_JUMP_VEL : C.JUMP_VEL;
          this.vy = jumpV;
          if (this.springReady) { this.springReady = false; this.useWeapon('SPRING_WEB'); }
          this.onGround = false;
        } else if (w.isAttached) {
          // Release web and launch with tangential velocity
          const launch = w.getLaunchVelocity();
          this.vx = launch.vx;
          this.vy = launch.vy - 300; // bonus upward kick
          w.release();
          this.isSwinging = false;
          this.ceilingImmuneTimer = 0.2;
        }
      }
    }

    // Integration
    this.x += this.vx * dt;
    this.y += this.vy * dt;

    // Face direction
    if (this.vx > 10) this.facingRight = true;
    else if (this.vx < -10) this.facingRight = false;

    // Platform collision
    this.onGround = false;
    const wasOnCeiling = this.onCeiling;
    if (!this.onCeiling) { /* handled below */ }

    let newOnCeiling = false;
    for (const p of platforms) {
      if (!rectsOverlap(this.x, this.y, this.w, this.h, p.x, p.y, p.w, p.h)) continue;
      const ox = this._overlapX(p), oy = this._overlapY(p);
      if (Math.abs(ox) < Math.abs(oy)) {
        this.x += ox;
        this.vx = 0;
        if (w.isAttached) { w.ropeAngularVel *= -0.3; }
      } else {
        if (oy > 0) {
          // Pushed down from above = ceiling
          this.y += oy;
          if (this.vy < 0) this.vy = 0;
          if (this.ceilingImmuneTimer <= 0) {
            newOnCeiling = true;
            this.vy = 0; this.vx = this.moveDir * C.MOVE_SPEED;
          }
          if (w.isAttached) { w.release(); this.isSwinging = false; }
        } else {
          // Landed on top
          this.y += oy;
          this.vy = 0;
          this.onGround = true;
          this.onCeiling = false;
          if (w.isAttached) {
            w.release();
            this.isSwinging = false;
          }
        }
      }
    }
    this.onCeiling = newOnCeiling;
    if (!newOnCeiling && wasOnCeiling && this.ceilingImmuneTimer <= 0) {
      // fell off ceiling edge
    }
    if (!w.isAttached) this.isSwinging = false;

    // Update animation
    if (this.onGround && Math.abs(this.vx) > 10) {
      this.runFrameTimer += dt;
      if (this.runFrameTimer >= 1 / C.RUN_FPS) {
        this.runFrameTimer = 0;
        this.runFrame = (this.runFrame + 1) % C.RUN_FRAMES;
      }
    }
  }

  _overlapX(p) {
    const cx = this.cx, pcx = p.centerX;
    return cx < pcx ? (p.x - this.right) : (p.right - this.x);
  }
  _overlapY(p) {
    const cy = this.cy, pcy = p.centerY;
    return cy < pcy ? (p.y - (this.y + this.h)) : (p.y + p.h - this.y);
  }

  _webHitPlatforms(sx, sy, ex, ey, platforms) {
    const dx = ex - sx, dy = ey - sy;
    let bestT = 1, bestX = null, bestY = null;
    for (const p of platforms) {
      const t = rayAABB(sx, sy, dx, dy, p.x, p.y, p.w, p.h);
      if (t !== null && t < bestT) {
        bestT = t;
        bestX = sx + dx * t;
        bestY = sy + dy * t;
      }
    }
    return bestX !== null ? { x: bestX, y: bestY } : null;
  }

  getAnimFrame() {
    if (this.onCeiling) return 'user_stick';
    if (this.isSwinging) return 'user_swing';
    if (this.web.isFlying || this.web.isAttached) {
      return this.onGround ? 'user_web' : 'user_web_jump';
    }
    if (!this.onGround) return 'user_jump';
    if (Math.abs(this.vx) > 10) return 'user_run';
    return 'user_stand';
  }

  draw(ctx, camX, camY, colorMatrix) {
    if (!this.alive) return;
    const sx = this.x - camX, sy = this.y - camY;
    const frame = this.getAnimFrame();

    // Draw web rope
    if (!this.web.isNone) {
      ctx.save();
      ctx.strokeStyle = C.COL_WEB;
      ctx.lineWidth = 2;
      ctx.globalAlpha = 0.85;
      ctx.beginPath();
      ctx.moveTo(this.cx - camX, this.cy - camY);
      ctx.lineTo(this.web.endX - camX, this.web.endY - camY);
      ctx.stroke();
      ctx.restore();
    }

    // Draw laser
    if (this.laserActive) {
      ctx.save();
      ctx.strokeStyle = '#ff4444';
      ctx.lineWidth = 3;
      ctx.shadowColor = '#ff0000';
      ctx.shadowBlur = 12;
      ctx.globalAlpha = 0.9;
      ctx.beginPath();
      ctx.moveTo(this.cx - camX, this.cy - camY);
      const laserLen = 900;
      ctx.lineTo(this.cx - camX + Math.cos(this.laserAngle) * laserLen,
                 this.cy - camY + Math.sin(this.laserAngle) * laserLen);
      ctx.stroke();
      ctx.restore();
    }

    ctx.save();
    if (!this.facingRight) {
      ctx.translate(sx + this.w, sy);
      ctx.scale(-1, 1);
    } else {
      ctx.translate(sx, sy);
    }
    if (this.onCeiling) {
      ctx.translate(0, this.h);
      ctx.scale(1, -1);
    }

    if (frame === 'user_run') {
      const img = Assets.get('user_run');
      if (img && img.complete) {
        const fw = img.width / C.RUN_FRAMES;
        ctx.drawImage(img, this.runFrame * fw, 0, fw, img.height, 0, 0, this.w, this.h);
      } else { this._drawFallback(ctx); }
    } else {
      const img = Assets.get(frame);
      if (img && img.complete) {
        ctx.drawImage(img, 0, 0, this.w, this.h);
      } else { this._drawFallback(ctx); }
    }
    ctx.restore();

    // Iron Spider glow
    if (this.ironSpiderActive) {
      ctx.save();
      ctx.strokeStyle = '#aaaaaa';
      ctx.lineWidth = 2;
      ctx.shadowColor = '#ffffff';
      ctx.shadowBlur = 8;
      ctx.strokeRect(sx, sy, this.w, this.h);
      ctx.restore();
    }

    // I-frames flash
    if (this.iframesTimer > 0 && Math.floor(this.iframesTimer * 10) % 2 === 0) {
      ctx.save();
      ctx.globalAlpha = 0.4;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(sx, sy, this.w, this.h);
      ctx.restore();
    }
  }

  _drawFallback(ctx) {
    ctx.fillStyle = C.COL_RED;
    ctx.fillRect(0, 0, this.w, this.h);
    ctx.fillStyle = '#fff';
    ctx.font = '20px Arial';
    ctx.fillText('🕷', 10, 30);
  }
}
