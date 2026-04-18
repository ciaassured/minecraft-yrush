# AGENTS.md

## Project Overview

This repository is for **YRush**, a Paper Minecraft server plugin.

YRush is a server-side minigame where players gather at a lobby, are teleported to a random safe starting location, and race to reach a randomly selected Y coordinate first. The plugin should work with normal Minecraft clients; no client mod is required.

## Target Platform

- Platform: Paper Minecraft server plugin
- Plugin name: `YRush`
- Main command: `/yrush`
- Java package: `io.github.ciaassured.yrush`
- Prefer stable Bukkit/Paper APIs where practical.
- Do not implement Fabric, Forge, NeoForge, or client-side mod behavior for V1.

## Commands

V1 commands:

```text
/yrush start
/yrush start auto
/yrush stop
/yrush status
/yrush setspawn
```

Command behavior:

- `/yrush start` starts one round.
- `/yrush start auto` starts auto mode. Rounds repeat until `/yrush stop`.
- `/yrush stop` cancels countdowns, active rounds, and pending auto rounds.
- `/yrush status` reports the current YRush state.
- `/yrush setspawn` stores the lobby location used between rounds.

Do not add bare `/start` or `/stop` aliases by default.

Recommended permissions:

```text
yrush.start      op only
yrush.stop       op only
yrush.status     everyone
yrush.setspawn   op only
```

## Visible Config

Keep the V1 config simple:

```yaml
round:
  countdown-seconds: 5
  between-rounds-seconds: 5
  timeout-seconds: 600

target-y:
  minimum-distance: 10
  maximum-distance: 96

start-location:
  radius: 3000
```

When `/yrush setspawn` is used, the plugin may add:

```yaml
lobby:
  world: world
  x: 123.5
  y: 64.0
  z: -88.5
  yaw: 90.0
  pitch: 0.0
```

If no lobby is configured, fall back to the world's spawn location.

Keep these as internal defaults for V1 rather than exposing them:

```text
Water starts: allowed
Underground starts: allowed
Chunk preload radius: 1
Player spread radius: 4
Max start attempts: 150
Y scan per column: scan the valid vertical range from a randomized offset
Biome preference: none
Kits: none
Death elimination: always on
Timeout result: draw
All players dead/quit result: draw
Start weighting: prefer surface starts 70% of rounds and underground starts 30% of rounds
Underground start item: give each player one stone pickaxe for that round only
```

## Game Flow

Single round flow:

```text
1. A player runs /yrush start.
2. Eligible players are gathered.
3. Players are reset and sent to the lobby/spawn.
4. The lobby countdown begins.
5. During countdown, prepare:
   - random safe start location
   - nearby safe player spawn positions
   - random target Y
6. At zero:
   - teleport players to the random start
   - immediately announce the target Y
   - start win detection
7. First active player to reach the target Y wins.
8. Winner is announced.
9. Players are reset and returned to lobby.
```

Auto mode flow:

```text
1. A player runs /yrush start auto.
2. First round starts normally.
3. After each win or draw, players return to lobby.
4. Wait round.between-rounds-seconds.
5. Start the next countdown.
6. Repeat until /yrush stop.
```

## Target Y Rules

Each round chooses a random target Y from the round's random start Y.

Rules:

- Target Y must be inside the world's valid height limits.
- Target Y must be at least `target-y.minimum-distance` blocks away from start Y.
- Target Y must be no more than `target-y.maximum-distance` blocks away from start Y.
- Target Y may be above or below the start.
- If a valid target cannot be selected, fail the round setup cleanly.

Direction and win logic:

```text
If targetY > startY:
  message: CLIMB TO Y <targetY>
  win condition: currentY >= targetY

If targetY < startY:
  message: DIG DOWN TO Y <targetY>
  win condition: currentY <= targetY
```

Do not allow target Y equal to start Y.

## Random Start Rules

Each round chooses a random start location inside `start-location.radius`.

V1 requirements:

- Starts may be above ground or underground.
- Start selection should prefer surface starts 70% of rounds and underground starts 30% of rounds.
- Water starts are allowed.
- Lava starts are not allowed.
- Players must not spawn inside solid blocks.
- Players must have safe feet and head space.
- The block below must be safe solid ground or water.
- Nearby immediate lethal blocks should be rejected.
- Destination chunks should be prepared during the lobby countdown before teleport.
- Players should be spread around the start when possible.

Reject starts involving obviously dangerous or invalid blocks, including:

```text
LAVA
FIRE
SOUL_FIRE
CACTUS
MAGMA_BLOCK
CAMPFIRE
SOUL_CAMPFIRE
SWEET_BERRY_BUSH
POWDER_SNOW
BEDROCK cages or impossible spaces
BARRIER
END_PORTAL
NETHER_PORTAL
COMMAND_BLOCK variants
```

The safe-location search should retry random locations and fail gracefully if it cannot find a valid start.

Do not implement biome preference in V1.

## Player State Rules

Before a round starts:

- Clear inventory.
- Clear armor and offhand.
- Clear potion effects.
- Reset health.
- Reset hunger and saturation.
- Reset fire ticks.
- Reset fall distance.
- Reset air supply.
- Store each participant's original game mode.
- Teleport players to lobby for countdown.

During a round:

- Players race with empty inventories.
- If the round starts underground, each player gets one stone pickaxe.
- Players may break/place blocks normally.
- World changes remain between rounds.

After a round:

- Clear inventory again.
- Reset health, hunger, effects, fire, fall distance, and air.
- Restore original game mode.
- Teleport everyone involved back to lobby.

Do not preserve or restore inventories. Inventory wiping is intentional game behavior.

## Death, Quit, Draw, And Stop Rules

Death elimination is always enabled in V1.

If a player dies during an active round:

- Remove them from the round.
- They cannot win that round.
- Put them in spectator mode until the round ends.
- Restore their original game mode at round end.

A player does not win automatically because everyone else died.

Round results:

- A player reaching target Y first is a win.
- Timeout is a draw.
- All active players dying or quitting before a win is a draw.

On timeout or all-eliminated draw:

```text
DRAW
```

Then reset players, restore spectators, and return everyone involved to lobby.

On `/yrush stop` while a game/countdown/auto loop is active:

- Cancel all scheduled tasks.
- Disable auto mode.
- Restore spectator players.
- Reset player state.
- Teleport involved players to lobby.
- Broadcast: `YRush stopped. Returning players to lobby.`

On `/yrush stop` while idle:

- Only tell the sender: `YRush is not currently running.`

## Messages And Actionbar

At round start, announce direction clearly:

```text
CLIMB TO Y 180
DIG DOWN TO Y -32
```

During active rounds, show an actionbar with compact live stats, for example:

```text
DIG DOWN TO Y -32 | Active 4/7 | 08:42
CLIMB TO Y 180 | Active 4/7 | 08:42
```

The actionbar should include:

- Direction
- Target Y
- Active players remaining
- Time remaining

## State Management

Use an explicit game state model. Suggested states:

```text
IDLE
COUNTDOWN
ACTIVE
BETWEEN_ROUNDS
STOPPING
```

Be strict about task cancellation. `/yrush stop`, plugin disable, timeout, win, and draw should not leave scheduled countdown, actionbar, win-check, timeout, or auto-loop tasks running.

Suggested implementation components:

```text
YRushPlugin
GameManager
YRushCommand
GameState enum
RoundContext
RoundResult
StartLocationService
SafeLocationValidator
TargetYSelector
PlayerStateService
MessageService
```

The exact class layout may change to fit the codebase, but keep responsibilities separated.

## Future Features

Score tracking is a likely future feature, but do not implement it in V1.

Keep round-end logic structured so scores can be added later. A future `RoundResult` should be able to expose:

```text
WIN or DRAW
winner UUID if any
target Y
duration
participants
```

Possible future commands:

```text
/yrush scores
/yrush leaderboard
```

## Agent Working Notes

- Discuss before writing code if the user explicitly asks to discuss.
- Keep V1 focused. Do not add kits, biome searching, scoreboards, or inventory preservation unless the user asks.
- Use the simplified config unless there is a strong reason to expose another setting.
- Prefer clear, recoverable behavior over surprising teleports or unsafe starts.
- When editing, preserve unrelated user changes.
- Before implementing against a Minecraft/Paper version, verify the currently supported Paper API version because "latest Minecraft" changes over time.
- Local Paper dev deployment is configured through ignored `local.properties`; prefer `paperServerDir=/absolute/path/to/server` and let Gradle infer `plugins/` and the Paper jar where possible.
- Use Conventional Commits for commit messages, for example `feat: scaffold yrush paper plugin`.
