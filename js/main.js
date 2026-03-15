// Entry point — loads assets then starts game loop
const SPRITE_NAMES = [
  'user_stand', 'user_run', 'user_jump',
  'user_web', 'user_web_jump', 'user_swing', 'user_stick',
  'spiderman'
];

let game = null;
let lastTime = 0;

function gameLoop(timestamp) {
  const dt = Math.min(0.05, Math.max(0.0001, (timestamp - lastTime) / 1000));
  lastTime = timestamp;
  if (game) {
    game.update(dt);
    game.draw();
  }
  requestAnimationFrame(gameLoop);
}

Assets.onReady = function () {
  const canvas = document.getElementById('gameCanvas');
  game = new Game(canvas);
  game._buildMenu();

  // Wire up buttons
  document.getElementById('retry-btn').addEventListener('click', () => {
    document.getElementById('game-over').classList.add('hidden');
    game.startLevel(game.currentLevel);
  });
  document.getElementById('menu-btn').addEventListener('click', () => {
    document.getElementById('game-over').classList.add('hidden');
    game.returnToMenu();
  });

  requestAnimationFrame(ts => { lastTime = ts; requestAnimationFrame(gameLoop); });
};

Assets.load(SPRITE_NAMES);
