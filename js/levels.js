// Level generator — all 30 levels
// Returns: { worldWidth, platforms, enemies, keys, gates, door, crates, lavas, weaponPickups }

// Adds platforms spread across 4 height tiers so the full vertical range is traversable
function addScaffoldPlatforms(ww, floorY) {
  const extra = [];
  // Four height fractions from ceiling down to just above floor-tier
  const fracs = [0.08, 0.25, 0.44, 0.63];
  for (let ti = 0; ti < fracs.length; ti++) {
    const baseY = floorY * fracs[ti];
    let x = 160;
    while (x < ww - 160) {
      // Deterministic jitter so layout is stable across reloads
      const seed = Math.floor(x) + ti * 99991;
      const pw = 140 + (seed * 37) % 120;
      const yOff = ((seed * 53) % 200) - 100;
      extra.push(new Platform(x, baseY + yOff, pw, 20));
      x += 300 + (seed * 29) % 160;
    }
  }
  return extra;
}

function buildLevel(levelNum, screenW, screenH) {
  const n = ((levelNum - 1) % 30) + 1;
  const FLOOR_H = 24, WALL_W = 24, CEIL_H = 24;

  const floorY = screenH - FLOOR_H;
  const platforms = [];
  const enemies = [];
  const keys = [];
  const gates = [];
  const lavas = [];
  const crates = [];
  const weaponPickups = [];
  let worldWidth;
  let doorX;

  // Boundaries added after per-level config
  function addBounds(ww) {
    // Floor
    platforms.push(new Platform(-200, floorY, ww + 400, FLOOR_H + 200, 'boundary'));
    // Ceiling
    platforms.push(new Platform(-200, -200, ww + 400, CEIL_H + 200, 'boundary'));
    // Left wall
    platforms.push(new Platform(-WALL_W - 200, 0, WALL_W + 200, screenH, 'boundary'));
    // Right wall
    platforms.push(new Platform(ww, 0, WALL_W + 200, screenH, 'boundary'));
  }

  function plat(x, y, w, h) { platforms.push(new Platform(x, y, w, h)); }
  function enemy(x, lb, rb) { const e = new Enemy(x, floorY - 120, lb, rb); enemies.push(e); }
  function crawler(x, y, lb, rb) { const e = new CeilingCrawler(x, y, lb, rb); enemies.push(e); }
  function shooter(x, si) { const e = new ShooterEnemy(x, floorY - 100, si); enemies.push(e); }
  function bat(x, lb, rb) { const b = new Bat(x, floorY * 0.4, lb, rb); enemies.push(b); }
  function jumpbot(x, lb, rb) { const j = new JumpingRobot(x, floorY - 90, lb, rb); enemies.push(j); }
  function pickup(x, y, type) { weaponPickups.push(new WeaponPickup(x - 18, y - 18, type)); }
  function key_(x, color) { keys.push(new Key(x, floorY - 200, color)); }
  function gate_(x, y, w, h, color) { gates.push(new Gate(x, y, w, h, color)); }
  function lava(x, w) { lavas.push(new Lava(x, floorY - 30, w, 60)); }
  function crate(x) { crates.push(new Crate(x, floorY - 140)); }

  switch (n) {
    case 1: { // Tutorial
      worldWidth = screenW * 2.5; doorX = worldWidth - 200;
      plat(300, floorY - 160, 200, 20);
      plat(700, floorY - 260, 200, 20);
      plat(1100, floorY - 180, 180, 20);
      enemy(500, 400, 700);
    } break;

    case 2: { // Blue key + gate, CeilingCrawlers
      worldWidth = screenW * 3; doorX = worldWidth - 200;
      plat(280, floorY - 150, 180, 20);
      plat(600, floorY - 280, 200, 20);
      plat(950, floorY - 200, 180, 20);
      plat(1300, floorY - 320, 200, 20);
      key_(400, 'blue');
      gate_(800, floorY - screenH, 30, screenH, 'blue');
      crawler(250, CEIL_H, 100, 500);
      crawler(900, CEIL_H, 700, 1100);
      enemy(600, 500, 800);
    } break;

    case 3: { // Crate puzzle, dual keys
      worldWidth = screenW * 3; doorX = worldWidth - 200;
      plat(260, floorY - 150, 180, 20);
      plat(550, floorY - 280, 160, 20);
      plat(850, floorY - 200, 180, 20);
      plat(1150, floorY - 320, 200, 20);
      key_(300, 'blue'); key_(700, 'red');
      gate_(900, floorY - screenH * 0.5, 30, screenH * 0.5, 'blue');
      gate_(1300, floorY - screenH * 0.5, 30, screenH * 0.5, 'red');
      crate(500); crate(800);
      enemy(600, 450, 800);
    } break;

    case 4: { // ShooterEnemies intro
      worldWidth = screenW * 3.5; doorX = worldWidth - 200;
      plat(300, floorY - 180, 180, 20);
      plat(650, floorY - 280, 200, 20);
      plat(1050, floorY - 200, 180, 20);
      plat(1400, floorY - 320, 200, 20);
      shooter(900, 1.5); shooter(1300, 1.2);
      enemy(500, 350, 700);
    } break;

    case 5: { // Gauntlet
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 12; i++) {
        const x = 250 + i * 280, y = floorY - 150 - (i%3) * 80;
        plat(x, y, 160, 20);
      }
      crawler(300, CEIL_H, 100, 600);
      crawler(700, CEIL_H, 500, 900);
      crawler(1200, CEIL_H, 900, 1500);
      crawler(1800, CEIL_H, 1500, 2100);
      enemy(500, 350, 700); enemy(900, 750, 1100);
      enemy(1300, 1100, 1500); enemy(1700, 1500, 1900);
    } break;

    case 6: { // Cryo Web intro
      worldWidth = screenW * 3.5; doorX = worldWidth - 200;
      plat(300, floorY - 180, 180, 20);
      plat(600, floorY - 280, 180, 20);
      plat(950, floorY - 200, 200, 20);
      plat(1300, floorY - 320, 180, 20);
      pickup(500, floorY - 80, 'CRYO_WEB');
      enemy(700, 550, 900); enemy(1100, 900, 1300);
      crawler(600, CEIL_H, 400, 800);
    } break;

    case 7: { // Spider Bot, enemy swarm
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 8; i++) plat(300 + i * 340, floorY - 160 - (i%2)*100, 180, 20);
      pickup(400, floorY - 80, 'SPIDER_BOT');
      for (let i = 0; i < 5; i++) enemy(500 + i * 400, 400 + i*400 - 200, 600 + i*400);
      crawler(700, CEIL_H, 500, 900);
      crawler(1300, CEIL_H, 1100, 1500);
    } break;

    case 8: { // Web Turret + Boom Web, 4 ShooterEnemies
      worldWidth = screenW * 4.5; doorX = worldWidth - 200;
      plat(280, floorY-180, 180, 20); plat(600, floorY-300, 200, 20);
      plat(1000, floorY-200, 200, 20); plat(1400, floorY-320, 200, 20);
      plat(1800, floorY-220, 200, 20); plat(2200, floorY-300, 200, 20);
      pickup(350, floorY-80, 'WEB_TURRET');
      pickup(1100, floorY-80, 'BOOM_WEB');
      shooter(700, 1.3); shooter(1100, 1.2); shooter(1500, 1.4); shooter(1900, 1.1);
      enemy(500, 350, 700); enemy(900, 750, 1100);
    } break;

    case 9: { // Jet Web, large gaps
      worldWidth = screenW * 5; doorX = worldWidth - 200;
      const gapPositions = [300, 650, 1100, 1550, 2000, 2450, 2900];
      for (const gx of gapPositions) plat(gx, floorY - 120 - Math.random()*160, 140, 20);
      pickup(500, floorY-80, 'JET_WEB');
      enemy(900, 750, 1050); enemy(1400, 1250, 1550);
      bat(1800, 1600, 2000); bat(2200, 2000, 2400);
    } break;

    case 10: { // Ultimate — all weapon pickups
      worldWidth = screenW * 5.5; doorX = worldWidth - 200;
      for (let i = 0; i < 14; i++) plat(250 + i*300, floorY - 150 - (i%4)*70, 160, 20);
      const wNames = Object.keys(WEAPONS);
      wNames.forEach((w, i) => pickup(300 + i*350, floorY-80, w));
      for (let i = 0; i < 4; i++) enemy(500+i*600, 400+i*600-200, 600+i*600);
      for (let i = 0; i < 2; i++) crawler(600+i*1200, CEIL_H, 400+i*1200, 800+i*1200);
      for (let i = 0; i < 2; i++) shooter(1000+i*800, 1.2);
    } break;

    case 11: { // Bat introduction
      worldWidth = screenW * 3; doorX = worldWidth - 200;
      plat(300, floorY-180, 180, 20); plat(650, floorY-280, 200, 20);
      plat(1050, floorY-200, 180, 20); plat(1400, floorY-320, 200, 20);
      bat(400, 250, 650); bat(800, 600, 1000); bat(1200, 1000, 1400);
    } break;

    case 12: { // Jumping Robots
      worldWidth = screenW * 3.5; doorX = worldWidth - 200;
      plat(300, floorY-180, 180, 20); plat(700, floorY-300, 200, 20);
      plat(1100, floorY-200, 180, 20); plat(1500, floorY-320, 200, 20);
      jumpbot(400, 250, 650); jumpbot(800, 600, 1000);
      jumpbot(1200, 950, 1350); jumpbot(1600, 1400, 1750);
    } break;

    case 13: { // Lava Run + Jet Web
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 9; i++) plat(200 + i*300, floorY-140-(i%3)*80, 160, 20);
      lava(350, 150); lava(750, 120); lava(1200, 180); lava(1700, 130);
      pickup(600, floorY-80, 'JET_WEB');
      enemy(500, 400, 650); enemy(1000, 850, 1100);
    } break;

    case 14: { // Bat swarm (8 bats)
      worldWidth = screenW * 3.5; doorX = worldWidth - 200;
      for (let i = 0; i < 8; i++) plat(250+i*250, floorY-160-(i%3)*70, 155, 20);
      for (let i = 0; i < 8; i++) bat(300+i*230, 200+i*230, 400+i*230);
    } break;

    case 15: { // Mixed chaos
      worldWidth = screenW * 4.5; doorX = worldWidth - 200;
      for (let i = 0; i < 10; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      lava(400, 120); lava(900, 130); lava(1500, 150); lava(2100, 140);
      bat(500, 350, 700); bat(900, 750, 1050); bat(1400, 1250, 1550);
      jumpbot(700, 550, 900); jumpbot(1200, 1000, 1400);
      shooter(1100, 1.4); shooter(1700, 1.3);
    } break;

    case 16: { // Laser Vision intro
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 9; i++) plat(260+i*280, floorY-160-(i%3)*80, 165, 20);
      pickup(400, floorY-80, 'LASER_VISION');
      for (let i = 0; i < 4; i++) enemy(450+i*500, 300+i*500, 550+i*500);
      crawler(600, CEIL_H, 400, 800); crawler(1400, CEIL_H, 1200, 1600);
    } break;

    case 17: { // Iron Spider intro, lava + all enemy types
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 9; i++) plat(260+i*280, floorY-160-(i%3)*80, 165, 20);
      lava(500, 130); lava(1000, 140); lava(1600, 120); lava(2100, 130);
      pickup(350, floorY-80, 'IRON_SPIDER');
      enemy(600, 450, 750); crawler(700, CEIL_H, 500, 900);
      shooter(1200, 1.3); bat(1500, 1300, 1700); jumpbot(1900, 1750, 2100);
    } break;

    case 18: { // Clone Wars — massive army
      worldWidth = screenW * 5; doorX = worldWidth - 200;
      for (let i = 0; i < 12; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      pickup(400, floorY-80, 'CLONE');
      for (let i = 0; i < 5; i++) enemy(500+i*480, 350+i*480, 550+i*480);
      for (let i = 0; i < 3; i++) shooter(800+i*700, 1.2);
      for (let i = 0; i < 2; i++) crawler(900+i*1200, CEIL_H, 700+i*1200, 1100+i*1200);
    } break;

    case 19: { // Plasma Web
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 9; i++) plat(260+i*280, floorY-160-(i%3)*80, 165, 20);
      // Blocking walls
      plat(600, floorY-screenH*0.5, 20, screenH*0.5);
      plat(1200, floorY-screenH*0.5, 20, screenH*0.5);
      pickup(350, floorY-80, 'PLASMA_WEB');
      for (let i = 0; i < 4; i++) enemy(500+i*500, 350+i*500, 600+i*500);
    } break;

    case 20: { // Gauntlet II
      worldWidth = screenW * 5.5; doorX = worldWidth - 200;
      for (let i = 0; i < 14; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      lava(400, 120); lava(900, 130); lava(1500, 150); lava(2200, 140);
      for (let i = 0; i < 4; i++) enemy(500+i*600, 350+i*600, 600+i*600);
      for (let i = 0; i < 2; i++) crawler(700+i*1400, CEIL_H, 500+i*1400, 900+i*1400);
      for (let i = 0; i < 3; i++) shooter(1000+i*700, 1.2);
      bat(1500, 1350, 1650); bat(2000, 1850, 2150);
      jumpbot(1800, 1650, 1950); jumpbot(2400, 2250, 2550);
    } break;

    case 21: { // Lava Caves
      worldWidth = screenW * 4.5; doorX = worldWidth - 200;
      for (let i = 0; i < 10; i++) plat(250+i*290, floorY-160-(i%3)*80, 160, 20);
      for (let i = 0; i < 5; i++) lava(300+i*500, 180);
      enemy(600, 450, 750); enemy(1200, 1000, 1350); enemy(1800, 1600, 1950);
    } break;

    case 22: { // Sky Bats
      worldWidth = screenW * 4; doorX = worldWidth - 200;
      for (let i = 0; i < 9; i++) plat(260+i*280, floorY-160-(i%3)*80, 165, 20);
      pickup(400, floorY-80, 'LASER_VISION');
      for (let i = 0; i < 10; i++) bat(300+i*220, 200+i*220, 400+i*220);
    } break;

    case 23: { // Robot Factory
      worldWidth = screenW * 5; doorX = worldWidth - 200;
      for (let i = 0; i < 12; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      for (let i = 0; i < 6; i++) jumpbot(400+i*500, 250+i*500, 550+i*500);
    } break;

    case 24: { // Lava Gauntlet
      worldWidth = screenW * 5; doorX = worldWidth - 200;
      for (let i = 0; i < 12; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      for (let i = 0; i < 8; i++) lava(250+i*380, 140);
      for (let i = 0; i < 4; i++) enemy(500+i*600, 350+i*600, 650+i*600);
    } break;

    case 25: { // Dark Tower — pillar corridors
      worldWidth = screenW * 4.5; doorX = worldWidth - 200;
      // Tall vertical pillars to navigate around
      for (let i = 0; i < 8; i++) {
        plat(350+i*350, floorY-220-30, 20, 220); // floor pillar
      }
      for (let i = 0; i < 6; i++) plat(280+i*400, floorY-300, 160, 20);
      for (let i = 0; i < 4; i++) enemy(450+i*500, 300+i*500, 600+i*500);
      shooter(1000, 1.3); shooter(1600, 1.2);
    } break;

    case 26: { // Bat Caves — 12 bats
      worldWidth = screenW * 4.5; doorX = worldWidth - 200;
      for (let i = 0; i < 10; i++) plat(250+i*290, floorY-160-(i%3)*80, 160, 20);
      for (let i = 0; i < 12; i++) bat(300+i*200, 200+i*200, 400+i*200);
    } break;

    case 27: { // Mech Arena — 8 jumping robots
      worldWidth = screenW * 5; doorX = worldWidth - 200;
      for (let i = 0; i < 12; i++) plat(250+i*300, floorY-160-(i%4)*70, 160, 20);
      for (let i = 0; i < 8; i++) jumpbot(350+i*450, 200+i*450, 500+i*450);
    } break;

    case 28: { // Inferno — 10 lava zones
      worldWidth = screenW * 5.5; doorX = worldWidth - 200;
      for (let i = 0; i < 14; i++) plat(250+i*280, floorY-160-(i%4)*80, 155, 20);
      for (let i = 0; i < 10; i++) lava(280+i*320, 130);
      for (let i = 0; i < 4; i++) enemy(500+i*600, 350+i*600, 650+i*600);
      bat(1000, 800, 1200); bat(1600, 1400, 1800);
    } break;

    case 29: { // Sky Fortress
      worldWidth = screenW * 5.5; doorX = worldWidth - 200;
      for (let i = 0; i < 14; i++) plat(250+i*290, floorY-160-(i%4)*80, 155, 20);
      shooter(700, 1.3); shooter(1400, 1.2); shooter(2100, 1.4);
      for (let i = 0; i < 5; i++) bat(450+i*450, 300+i*450, 600+i*450);
      crawler(600, CEIL_H, 400, 800); crawler(1500, CEIL_H, 1200, 1800);
    } break;

    case 30: default: { // The Finale
      worldWidth = screenW * 6.5; doorX = worldWidth - 200;
      for (let i = 0; i < 16; i++) plat(250+i*290, floorY-160-(i%4)*80, 155, 20);
      lava(400, 130); lava(900, 140); lava(1450, 120); lava(2100, 150);
      const allW = Object.keys(WEAPONS);
      allW.forEach((w, i) => pickup(350+i*380, floorY-80, w));
      for (let i = 0; i < 4; i++) enemy(550+i*700, 400+i*700, 650+i*700);
      for (let i = 0; i < 5; i++) jumpbot(450+i*600, 300+i*600, 600+i*600);
      for (let i = 0; i < 8; i++) bat(500+i*380, 350+i*380, 650+i*380);
      for (let i = 0; i < 4; i++) shooter(800+i*700, 1.2);
      for (let i = 0; i < 4; i++) crawler(700+i*900, CEIL_H, 500+i*900, 900+i*900);
      key_(600, 'blue'); key_(1800, 'red');
      gate_(1200, floorY-screenH*0.5, 30, screenH*0.5, 'blue');
      gate_(2400, floorY-screenH*0.5, 30, screenH*0.5, 'red');
    } break;
  }

  // Sprinkle platforms across the full vertical range of every level
  platforms.push(...addScaffoldPlatforms(worldWidth, floorY));

  addBounds(worldWidth);
  const door = new Door(doorX, floorY - 90);
  return { worldWidth, platforms, enemies, keys, gates, door, crates, lavas, weaponPickups };
}
