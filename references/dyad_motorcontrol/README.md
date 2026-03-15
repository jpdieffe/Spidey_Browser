# Web Swinger 🕸️

A 2D Android platformer game featuring a spider-themed character with web-swinging mechanics.

## How to Play

The game runs in **landscape mode** with a split-screen control scheme:

### Left 2/3 — Movement Zone
- **Drag left/right** to move the character
- **Swipe up** to jump

### Right 1/3 — Web Shooter Joystick
- **Drag the joystick** in any direction to shoot a web
- If the web hits a wall, ceiling, or platform, it **pulls your character** toward the attach point
- **Release the joystick** to detach the web

## Building

1. Open the project in **Android Studio** (Arctic Fox or newer)
2. Sync Gradle
3. Run on a device or emulator (API 24+)

## Architecture

| File | Purpose |
|------|---------|
| `GameView.kt` | Main SurfaceView with 60fps game loop, rendering, touch input, collision detection, and camera |
| `Player.kt` | Spider character — physics, movement, jumping, drawing (red suit + web pattern) |
| `WebLine.kt` | Web projectile — flight, surface attachment, release states |
| `Platform.kt` | Simple rectangular platform data |
| `Vec2.kt` | 2D vector math utility |
| `MainActivity.kt` | Full-screen immersive activity host |

## Game Mechanics

- **Gravity** pulls the character down at 2200 px/s²
- **Web pull** accelerates the character toward the attach point at 2400 px/s² (gravity is reduced to 35% while webbed)
- **Jump velocity** is 1050 px/s upward
- The level is 3 screen-widths long, with auto-generated platforms and vertical pillars
- Camera smoothly follows the player
