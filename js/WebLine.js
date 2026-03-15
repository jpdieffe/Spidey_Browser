// WebLine — web projectile / rope state machine
const WEB_STATE = { NONE: 0, FLYING: 1, ATTACHED: 2 };

class WebLine {
  constructor() {
    this.state = WEB_STATE.NONE;
    this.startX = 0; this.startY = 0;  // anchor (player hand)
    this.endX = 0;   this.endY = 0;    // tip position
    this.velX = 0;   this.velY = 0;    // tip velocity when flying
    this.ropeLength = 0;
    this.ropeAngle = 0;
    this.ropeAngularVel = 0;
    this.hitEnemy = null;  // enemy hit by web tip
  }

  get isNone()     { return this.state === WEB_STATE.NONE; }
  get isFlying()   { return this.state === WEB_STATE.FLYING; }
  get isAttached() { return this.state === WEB_STATE.ATTACHED; }

  fire(ox, oy, tx, ty) {
    this.state = WEB_STATE.FLYING;
    this.startX = ox; this.startY = oy;
    this.endX = ox;   this.endY = oy;
    const dx = tx - ox, dy = ty - oy;
    const len = Math.sqrt(dx*dx + dy*dy) || 1;
    this.velX = (dx / len) * C.WEB_SPEED;
    this.velY = (dy / len) * C.WEB_SPEED;
    this.hitEnemy = null;
  }

  attach(ax, ay, px, py) {
    this.state = WEB_STATE.ATTACHED;
    this.endX = ax; this.endY = ay;
    const dx = px - ax, dy = py - ay;
    this.ropeLength = Math.sqrt(dx*dx + dy*dy);
    this.ropeLength = Math.max(C.ROPE_MIN, Math.min(C.ROPE_MAX, this.ropeLength));
    this.ropeAngle = Math.atan2(dx, -dy); // angle from anchor to player
    this.ropeAngularVel = 0;
  }

  release() {
    this.state = WEB_STATE.NONE;
    this.hitEnemy = null;
  }

  // Returns tangential velocity when rope releases
  getLaunchVelocity() {
    const tanSpeed = this.ropeAngularVel * this.ropeLength;
    return {
      vx: tanSpeed * Math.cos(this.ropeAngle),
      vy: tanSpeed * Math.sin(this.ropeAngle)
    };
  }
}
