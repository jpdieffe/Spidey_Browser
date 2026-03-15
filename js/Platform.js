// Platform — static rectangular collision surface
class Platform {
  constructor(x, y, w, h, type = 'solid') {
    this.x = x; this.y = y; this.w = w; this.h = h;
    this.type = type; // 'solid' | 'boundary'
  }
  get isWall()   { return this.h > this.w; }
  get right()    { return this.x + this.w; }
  get bottom()   { return this.y + this.h; }
  get centerX()  { return this.x + this.w / 2; }
  get centerY()  { return this.y + this.h / 2; }
}

// AABB overlap test
function rectsOverlap(ax, ay, aw, ah, bx, by, bw, bh) {
  return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
}

// Ray vs AABB — returns hit fraction [0..1] or null
function rayAABB(rx, ry, dx, dy, bx, by, bw, bh) {
  let tmin = 0, tmax = 1;
  const eps = 1e-8;
  for (let axis = 0; axis < 2; axis++) {
    const o = axis === 0 ? rx : ry;
    const d = axis === 0 ? dx : dy;
    const lo = axis === 0 ? bx : by;
    const hi = lo + (axis === 0 ? bw : bh);
    if (Math.abs(d) < eps) {
      if (o < lo || o > hi) return null;
    } else {
      let t1 = (lo - o) / d;
      let t2 = (hi - o) / d;
      if (t1 > t2) { const tmp = t1; t1 = t2; t2 = tmp; }
      tmin = Math.max(tmin, t1);
      tmax = Math.min(tmax, t2);
      if (tmin > tmax) return null;
    }
  }
  return tmin;
}
