// Game-wide constants matching Android original
const VERSION = 'v1.6';

const C = {
  GRAVITY:         2200,
  MOVE_SPEED:       600,
  JUMP_VEL:       -1050,
  SUPER_JUMP_VEL: -1800,
  PLAYER_W:          90,
  PLAYER_H:         135,

  WEB_SPEED:       2800,
  WEB_MAX_LEN:     2000,
  ROPE_MIN:          80,
  ROPE_MAX:        1200,
  CLIMB_SPEED:      400,
  SWING_PUMP:       3.5,
  SWING_DAMP:     0.995,

  JET_SPEED:        800,
  JET_DURATION:       5,
  LASER_DURATION:     5,
  CLONE_LIFETIME:    25,
  SPIDERBOT_LIFETIME:12,
  TURRET_LIFETIME:   15,
  TURRET_SHOTS:      20,
  FREEZE_NORMAL:     11,
  FREEZE_CRYO:       60,

  IRON_SPIDER_HITS:  10,
  IFRAMES:           1.0,

  RUN_FPS:           12,
  RUN_FRAMES:         6,

  ENEMY_FREEZE_NORMAL: 11,
  ENEMY_FREEZE_CRYO:   60,

  // Colors
  COL_SKY:        '#18182A',
  COL_RED:        '#C81E1E',
  COL_BLUE:       '#1E1E5A',
  COL_PLATFORM:   '#2a3a5c',
  COL_GROUND:     '#1a2a40',
  COL_WEB:        '#ffffff',
  COL_LAVA:       '#ff4400',
  COL_DOOR:       '#00cc44',
};

// Weapon definitions
const WEAPONS = {
  SPRING_WEB:  { icon: '⬆', color: '#44ff88', name: 'Spring Web',  defaultCharges: 3, price: 2 },
  CRYO_WEB:    { icon: '❄', color: '#88ddff', name: 'Cryo Web',    defaultCharges: 3, price: 2 },
  SPIDER_BOT:  { icon: '🕷', color: '#aa66ff', name: 'Spider Bot',  defaultCharges: 2, price: 3 },
  WEB_TURRET:  { icon: '⚙', color: '#44ff44', name: 'Web Turret',  defaultCharges: 2, price: 3 },
  BOOM_WEB:    { icon: '💥', color: '#ff8800', name: 'Boom Web',    defaultCharges: 3, price: 4 },
  JET_WEB:     { icon: '🚀', color: '#ffff00', name: 'Jet Web',     defaultCharges: 2, price: 5 },
  LASER_VISION:{ icon: '👁', color: '#ff4444', name: 'Laser',       defaultCharges: 3, price: 5 },
  IRON_SPIDER: { icon: '🛡', color: '#aaaaaa', name: 'Iron Spider', defaultCharges: 1, price: 6 },
  CLONE:       { icon: '👤', color: '#4488ff', name: 'Clone',       defaultCharges: 1, price: 7 },
  PLASMA_WEB:  { icon: '⚡', color: '#cc44ff', name: 'Plasma Web',  defaultCharges: 2, price: 4 },
};
