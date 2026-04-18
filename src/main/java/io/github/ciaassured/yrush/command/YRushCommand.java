package io.github.ciaassured.yrush.command;

import io.github.ciaassured.yrush.game.GameController;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class YRushCommand implements CommandExecutor, TabCompleter {
    private final GameController gameController;

    public YRushCommand(GameController gameController) {
        this.gameController = gameController;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        Optional<Subcommand> sub = Subcommand.fromName(args[0].toLowerCase(Locale.ROOT));
        if (sub.isEmpty()) {
            sendUsage(sender);
            return true;
        }

        if (!sender.hasPermission(sub.get().permission)) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        sub.get().execute(gameController, sender, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.stream(Subcommand.values())
                .filter(s -> sender.hasPermission(s.permission))
                .map(s -> s.name)
                .filter(name -> name.startsWith(prefix))
                .toList();
        }

        if (args.length == 2 && Subcommand.START.matches(args[0]) && sender.hasPermission(Subcommand.START.permission)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return List.of("auto").stream().filter(s -> s.startsWith(prefix)).toList();
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /yrush <start [auto]|stop|status|setspawn>");
    }

    // ── Dispatch table ────────────────────────────────────────────────────────

    private enum Subcommand {
        START("start", "yrush.start") {
            @Override void execute(GameController gc, CommandSender sender, String[] args) {
                gc.start(sender, args.length > 1 && args[1].equalsIgnoreCase("auto"));
            }
        },
        STOP("stop", "yrush.stop") {
            @Override void execute(GameController gc, CommandSender sender, String[] args) {
                gc.stop(sender);
            }
        },
        STATUS("status", "yrush.status") {
            @Override void execute(GameController gc, CommandSender sender, String[] args) {
                gc.sendStatus(sender);
            }
        },
        SETSPAWN("setspawn", "yrush.setspawn") {
            @Override void execute(GameController gc, CommandSender sender, String[] args) {
                gc.setLobby(sender);
            }
        };

        final String name;
        final String permission;

        Subcommand(String name, String permission) {
            this.name = name;
            this.permission = permission;
        }

        abstract void execute(GameController gc, CommandSender sender, String[] args);

        boolean matches(String input) {
            return name.equalsIgnoreCase(input);
        }

        private static final Map<String, Subcommand> BY_NAME = new HashMap<>();
        static {
            for (Subcommand s : values()) BY_NAME.put(s.name, s);
        }

        static Optional<Subcommand> fromName(String name) {
            return Optional.ofNullable(BY_NAME.get(name));
        }
    }
}
