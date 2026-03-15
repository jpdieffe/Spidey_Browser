// Debug panel — toggle with tilde (~), live-edits C values

const DEBUG_DEFAULTS = {
  pump:     8.0,
  damp:     0.999,
  gravity:  2200,
  move:     600,
  jump:     -1050,
  webspeed: 2800,
};

const DebugPanel = {
  open: false,

  init() {
    const panel = document.getElementById('debug-panel');

    // Prevent debug-panel clicks from reaching the canvas
    panel.addEventListener('mousedown', e => e.stopPropagation());
    panel.addEventListener('mouseup',   e => e.stopPropagation());
    panel.addEventListener('click',     e => e.stopPropagation());

    // Tilde toggles panel
    window.addEventListener('keydown', e => {
      if (e.code === 'Backquote') {
        this.open = !this.open;
        panel.classList.toggle('open', this.open);
        if (this.open) this._sync();
        e.preventDefault();
      }
    });

    // Sliders
    this._bind('dbg-pump',     'val-pump',     v => { C.SWING_PUMP = v; },     1);
    this._bind('dbg-damp',     'val-damp',     v => { C.SWING_DAMP = v; },     3);
    this._bind('dbg-gravity',  'val-gravity',  v => { C.GRAVITY = v; },        0);
    this._bind('dbg-move',     'val-move',     v => { C.MOVE_SPEED = v; },     0);
    this._bind('dbg-jump',     'val-jump',     v => { C.JUMP_VEL = v; },       0);
    this._bind('dbg-webspeed', 'val-webspeed', v => { C.WEB_SPEED = v; },      0);

    // Reset button
    document.getElementById('dbg-reset').addEventListener('click', () => {
      C.SWING_PUMP  = DEBUG_DEFAULTS.pump;
      C.SWING_DAMP  = DEBUG_DEFAULTS.damp;
      C.GRAVITY     = DEBUG_DEFAULTS.gravity;
      C.MOVE_SPEED  = DEBUG_DEFAULTS.move;
      C.JUMP_VEL    = DEBUG_DEFAULTS.jump;
      C.WEB_SPEED   = DEBUG_DEFAULTS.webspeed;
      this._sync();
    });

    this._sync();
  },

  _bind(sliderId, valId, setter, decimals) {
    const slider = document.getElementById(sliderId);
    const val    = document.getElementById(valId);
    slider.addEventListener('input', () => {
      const v = parseFloat(slider.value);
      setter(v);
      val.textContent = v.toFixed(decimals);
    });
  },

  _sync() {
    this._set('dbg-pump',     'val-pump',     C.SWING_PUMP,  1);
    this._set('dbg-damp',     'val-damp',     C.SWING_DAMP,  3);
    this._set('dbg-gravity',  'val-gravity',  C.GRAVITY,     0);
    this._set('dbg-move',     'val-move',     C.MOVE_SPEED,  0);
    this._set('dbg-jump',     'val-jump',     C.JUMP_VEL,    0);
    this._set('dbg-webspeed', 'val-webspeed', C.WEB_SPEED,   0);
  },

  _set(sliderId, valId, value, decimals) {
    document.getElementById(sliderId).value = value;
    document.getElementById(valId).textContent = value.toFixed(decimals);
  },
};
