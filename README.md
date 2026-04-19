# YRush

YRush is a Paper Minecraft server plugin for a simple race minigame: players start together, get sent to a random location, and race to reach a random Y coordinate first.

## Download

Download the latest plugin jar from the GitHub Releases page.

Place the jar in your server's `plugins/` directory, then restart the server.

## Commands

| Command | Description |
| --- | --- |
| `/yrush start` | Start one round. |
| `/yrush start auto` | Start auto mode. A new round starts after each round ends. |
| `/yrush start training` | Start fast repeating rounds for bot training. |
| `/yrush stop` | Stop the current countdown, round, or auto mode. |
| `/yrush status` | Show the current YRush state. |
| `/yrush setspawn` | Set the lobby location used between rounds. If no lobby is configured, YRush uses the world's spawn location. |

## Local Deployment

For local Paper testing, create `local.properties` from `local.properties.example`.

| Command | Description |
| --- | --- |
| `./gradlew deployPlugin` | Build the plugin and copy it to the configured Paper plugins folder. |
| `./gradlew runPaperServer` | Start the configured local Paper server. |
| `./gradlew deployAndRun` | Deploy the plugin, then start the configured local Paper server. |

## Gameplay

- Inventories are cleared and health is reset for YRush rounds.
- If nobody reaches the target before the timeout, the round is a draw.
- Night vision is given for underground starts or dig-down targets.
- A wooden pickaxe is given for underground starts.

## Bot Training State

YRush can send round state to bot clients over a Paper plugin messaging channel.

Use `/yrush start training` for fast repeating rounds with human-facing delays removed.

Enable it in `config.yml`:

```yaml
training-packets:
  enabled: true
```

Channel:

```text
yrush:training_state
```

Clients must opt in by sending a plugin message on this channel before YRush sends packets to them.
Payloads are raw UTF-8 JSON bytes. The packet schema is versioned with `schema_version`.

Active round payload:

```json
{"schema_version":1,"round_active":true,"player_active":true,"phase":"ACTIVE","direction":"DOWN","target_y":39,"active_players":3,"total_players":5,"seconds_remaining":482}
```

Eliminated player payload:

```json
{"schema_version":1,"round_active":true,"player_active":false,"phase":"ACTIVE","direction":"DOWN","target_y":39,"active_players":2,"total_players":5,"seconds_remaining":421}
```

Inactive payload:

```json
{"schema_version":1,"round_active":false,"player_active":false,"phase":"INACTIVE"}
```

`phase` is `LOCKED_COUNTDOWN`, `ACTIVE`, or `INACTIVE`.
