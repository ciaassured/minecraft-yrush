# YRush

YRush is a Paper Minecraft server plugin for a simple race minigame: players start together, get sent to a random location, and race to reach a random Y coordinate first.

YRush requires Paper 26.2 or newer and Java 25 or newer.

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

## Local Development

| Command           | Description                                                                  |
| ----------------- | ---------------------------------------------------------------------------- |
| `nix develop`     | Enter the Java 25 and Gradle development shell.                              |
| `nix build`       | Build and test the plugin; the JAR is available under `result/share/yrush/`. |
| `nix run .#paper` | Run Paper with debug logging and bot packets enabled.                        |
| `nix run .#smoke` | Run the disposable Paper lifecycle smoke test.                               |
| `nix fmt`         | Format the Nix sources with nixfmt.                                          |

The Paper runner keeps local state in `.yrush-paper/`; set `YRUSH_PAPER_STATE_DIR` to override
that location. For a fresh state. For a fresh local sever, stop Paper and delete `.yrush-paper/` before running `nix run .#paper`.

After changing Gradle dependencies, refresh their Nix hashes with
`nix run .#update-gradle-deps`.

## Configuration

YRush reads its settings from `plugins/YRush/config.yml`. On startup, it creates that file from
the default configuration packaged in the plugin JAR if it does not already exist. Existing files
are not replaced. Restart the server after editing the configuration.

## Gameplay

- Inventories are cleared and health is reset for YRush rounds.
- If nobody reaches the target before the timeout, the round is a draw.
- Night vision is given for underground starts or dig-down targets.
- A wooden pickaxe is given for underground starts.

## Bot State Packets

YRush can send round information to bot clients over a Paper plugin messaging channel.

Enable it in `config.yml`:

```yaml
bot-packets:
  enabled: true
```

Channel:

```text
yrush:bot_state
```

Clients opt in by registering a receiver for this channel. YRush sends packets only while the
client advertises `yrush:bot_state` as a listening plugin channel; no subscription message is
required. Payloads are raw UTF-8 JSON bytes. The packet schema is versioned with `schema_version`.

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

| Phase              | Description                                                      |
|--------------------|------------------------------------------------------------------|
| `LOCKED_COUNTDOWN` | Players are at the start but locked while the countdown runs.    |
| `ACTIVE`           | The race is running and eligible players can move, act, and win. |
| `INACTIVE`         | No round is active; sent after completion, stopping, or cleanup. |
