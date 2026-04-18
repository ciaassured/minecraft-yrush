package io.github.ciaassured.yrush;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class YRushCommand implements CommandExecutor, TabCompleter {
    private final GameManager gameManager;

    public YRushCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "start" -> {
                if (!sender.hasPermission("yrush.start")) {
                    sender.sendMessage("You do not have permission to start YRush.");
                    return true;
                }
                boolean auto = args.length > 1 && args[1].equalsIgnoreCase("auto");
                gameManager.start(sender, auto);
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("yrush.stop")) {
                    sender.sendMessage("You do not have permission to stop YRush.");
                    return true;
                }
                gameManager.stop(sender);
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("yrush.status")) {
                    sender.sendMessage("You do not have permission to view YRush status.");
                    return true;
                }
                gameManager.sendStatus(sender);
                return true;
            }
            case "setspawn" -> {
                if (!sender.hasPermission("yrush.setspawn")) {
                    sender.sendMessage("You do not have permission to set the YRush lobby.");
                    return true;
                }
                gameManager.setLobby(sender);
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            if (sender.hasPermission("yrush.start")) {
                values.add("start");
            }
            if (sender.hasPermission("yrush.stop")) {
                values.add("stop");
            }
            if (sender.hasPermission("yrush.status")) {
                values.add("status");
            }
            if (sender.hasPermission("yrush.setspawn")) {
                values.add("setspawn");
            }
            return filter(values, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("yrush.start")) {
            return filter(List.of("auto"), args[1]);
        }

        return List.of();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /yrush <start|stop|status|setspawn>");
        sender.sendMessage("Auto mode: /yrush start auto");
    }

    private List<String> filter(List<String> values, String prefix) {
        String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.startsWith(normalizedPrefix))
            .toList();
    }
}

