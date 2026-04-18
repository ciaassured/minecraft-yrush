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

debug:
  enabled: false
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
Underground start item: give each player one wooden pickaxe for that round only
Recent start memory: avoid repeating the same broad start category and avoid back-to-back water starts when possible
```

## Game Flow

Single round flow:

```text
1. A player runs /yrush start.
2. Eligible players are gathered.
3. Players are registered for the round; they are expected to already be at the lobby from the previous round or server setup.
4. Round preparation begins:
   - random safe start location
   - nearby safe player spawn positions
   - random target Y
5. After preparation succeeds:
   - show players a center-screen "round starting soon" title
   - wait briefly so players know the teleport is coming
   - teleport players to the random start
   - apply blindness and lock movement/actions while clients load the destination
6. The countdown begins after players have been teleported and locked.
7. At zero:
   - remove blindness and unlock players
   - announce the objective as direction plus blocks away, not the exact target Y
   - start win detection
8. First active player to reach the target Y wins.
9. Winner is announced.
10. Players are reset and returned to lobby.
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
- Water starts must use a downward target because players may have no blocks/resources for climbing.
- If a candidate water start cannot produce a valid downward target, reject that candidate and search for another start.
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
- Track broad recent start categories: surface dry, surface water, underground dry, underground water.
- Avoid repeating the previous broad start category and avoid back-to-back water starts where possible, but fall back to any safe start rather than failing the round.
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

When a round is being prepared:

- Store each participant's original game mode.
- Do not reset health, hunger, or inventory yet.

When players teleport to the random start and the round begins:

- Clear inventory.
- Clear armor and offhand.
- Clear potion effects.
- Reset health.
- Reset hunger and saturation.
- Reset fire ticks.
- Reset fall distance.
- Reset air supply.

During a round:

- Players race with empty inventories.
- If the target direction is down, the round starts underground, or the round starts at night, each player gets night vision for the round.
- If the round starts underground, each player gets one wooden pickaxe.
- The underground pickaxe is intentionally wooden: it prevents sealed underground starts from bricking the round, but mining stays slower than running when a natural route downward exists.
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
CLIMB 42 BLOCKS
DIG DOWN 37 BLOCKS
```

During active rounds, show an actionbar with compact live stats, for example:

```text
DIG DOWN | Away 18 | Active 4/7 | 08:42
CLIMB | Away 27 | Active 4/7 | 08:42
```

The actionbar should include:

- Direction
- Blocks away from the target for that player
- Active players remaining
- Time remaining

## Architecture And State Management

The project has been refactored around explicit object ownership and separation of concerns. Preserve this structure when making changes.

Current package responsibilities:

```text
io.github.ciaassured.yrush
  YRushPlugin                 Bukkit plugin entrypoint only

command/
  YRushCommand                command parsing, permissions, tab completion

config/
  YRushConfig                 config parsing, defaults, and value coercion

game/
  GameController              high-level orchestration, auto mode, lobby, status
  Round                       owns one complete round lifecycle
  RoundCompletionHandler      callback from Round to GameController
  RoundContext                immutable round setup/runtime context
  RoundResult                 round completion result model
  RoundDirection              target direction model
  RoundResultType             result type model

location/
  StartLocationService        async start search and chunk preparation
  SafeLocationValidator       safe block/material checks
  TargetYSelector             target Y selection rules
  StartLocation               start result model
  StartCategory               broad start category model
  StartType                   surface/underground model
  TargetDirectionPreference   target direction preference model

service/
  MessageService              stateless message/title/actionbar helpers
  PlayerStateService          stateless player reset/restore helpers
```

Lifecycle responsibilities:

- `YRushPlugin` should only wire Bukkit lifecycle concerns: config defaults, command registration, listener registration, and shutdown delegation.
- `YRushCommand` should only parse and dispatch `/yrush` subcommands. Do not put game rules or round state there.
- `GameController` should orchestrate high-level game state: start/stop/status/lobby, auto mode, between-round scheduling, and offline restore handoff.
- `Round` owns all mutable state for a single round: participants, active/eliminated players, original game modes, preparation, countdown, active tasks, death/quit/join listeners, cleanup, and result emission.
- `Round` implements `AutoCloseable`; `close()` must stay idempotent and safe from every path: stop command, plugin disable, preparation failure, win, draw, timeout, or all-eliminated draw.
- `Round` should keep the post-teleport countdown locked: players are blind, cannot move/actions/damage, and are unlocked only when the active round starts.
- `RoundCompletionHandler` is the handoff point from `Round` back to `GameController`.
- `MessageService` and `PlayerStateService` are intentionally stateless utility services.

State and task rules:

- Avoid shared mutable round state in `GameController`; per-round mutable state belongs in `Round`.
- Be strict about task cancellation. `/yrush stop`, plugin disable, preparation failure, timeout, win, and draw must not leave scheduled countdown, actionbar, win-check, timeout, preparation, or auto-loop tasks running.
- Async chunk preparation must use Paper async chunk APIs. Do not reintroduce synchronous chunk loading for random start generation.
- Async callbacks must guard against stale/disposed rounds before mutating state.
- Preserve offline restore behavior. If a participant is offline during cleanup, keep their original game mode in `GameController` and restore them on join.
- Debug logging should stay console-only and behind `debug.enabled`. Prefer focused lifecycle/failure logs over noisy per-event logs.

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
