// Vec2 — 2D vector utility
class Vec2 {
  constructor(x = 0, y = 0) { this.x = x; this.y = y; }
  clone()            { return new Vec2(this.x, this.y); }
  set(x, y)         { this.x = x; this.y = y; return this; }
  add(v)            { return new Vec2(this.x + v.x, this.y + v.y); }
  sub(v)            { return new Vec2(this.x - v.x, this.y - v.y); }
  scale(s)          { return new Vec2(this.x * s, this.y * s); }
  length()          { return Math.sqrt(this.x * this.x + this.y * this.y); }
  normalized()      { const l = this.length(); return l > 0 ? this.scale(1 / l) : new Vec2(); }
  dot(v)            { return this.x * v.x + this.y * v.y; }
  addSelf(v)        { this.x += v.x; this.y += v.y; return this; }
  scaleSelf(s)      { this.x *= s; this.y *= s; return this; }
}
