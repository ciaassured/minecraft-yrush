# YRush Maintenance Guide

YRush is a server-side Paper minigame plugin. Read `README.md` for current commands, gameplay,
configuration, and bot packet documentation.

## Code Structure

- `YRushPlugin` wires plugin startup, commands, listeners, messaging, and shutdown only.
- `YRushCommand` parses and dispatches `/yrush`; gameplay rules do not belong there.
- `GameController` owns cross-round orchestration, run modes, lobby state, and deferred restores.
- `Round` owns all mutable state, listeners, tasks, and cleanup for one round.
- `location/` handles safe starts and target selection; `service/` contains player, messaging,
  packet, and debug helpers.

## Maintenance Rules

- Add focused JUnit tests for isolated logic.
- After changing Gradle dependencies, run `nix run .#update-gradle-deps`.
- Use Scoped Commits when creating commits.

## Development

- `nix develop` enters the Java 25 and Gradle development shell.
- `nix fmt` formats Nix files.
- `nix build` builds the plugin and runs checks.
- `nix run .#paper` runs the local Paper server in `.yrush-paper/`.
- `nix run .#smoke` runs the disposable server lifecycle test.
