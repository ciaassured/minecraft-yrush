package io.github.ciaassured.yrush.service;

import io.github.ciaassured.yrush.game.RoundContext;
import io.github.ciaassured.yrush.game.RoundDirection;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

public final class MessageService {
    private MessageService() {}

    public static void broadcast(String message) {
        Bukkit.broadcast(text(message, NamedTextColor.GOLD));
    }

    public static void broadcastWinner(Player winner, int targetY) {
        Bukkit.broadcast(
            Component.text(winner.getName(), NamedTextColor.GREEN)
                .append(Component.text(" wins! Reached Y " + targetY + " first.", NamedTextColor.GOLD))
        );
    }

    public static void broadcastDraw() {
        Bukkit.broadcast(text("DRAW! Nobody reached the target Y.", NamedTextColor.YELLOW));
    }

    public static void sendEliminated(Player player) {
        player.sendMessage(text("You are out until the next round.", NamedTextColor.RED));
    }

    public static void sendPreparationFailed(Player player) {
        player.sendMessage(text("Could not find a safe start location. Try again or increase the search radius.", NamedTextColor.RED));
    }

    public static void countdown(Collection<Player> players, int seconds) {
        Component title = text("Round starts in " + seconds, NamedTextColor.YELLOW);
        Title countdownTitle = Title.title(
            title,
            Component.empty(),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
        );
        for (Player player : players) {
            player.showTitle(countdownTitle);
            player.sendActionBar(title);
        }
    }

    public static void roundStart(Collection<Player> players, RoundContext context) {
        String objective = context.direction().titlePrefix() + " Y " + context.targetY();
        Title title = Title.title(
            text(objective, NamedTextColor.GREEN),
            text("First player there wins", NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
        );
        for (Player player : players) {
            player.showTitle(title);
            player.sendMessage(text(objective, NamedTextColor.GREEN));
        }
    }

    public static void actionBar(Player player, RoundContext context, int activePlayers, int totalPlayers) {
        long remaining = context.remainingSeconds(Instant.now());
        String time = "%02d:%02d".formatted(remaining / 60, remaining % 60);
        String message = "%s Y %d | Away %d | Active %d/%d | %s".formatted(
            context.direction().titlePrefix(),
            context.targetY(),
            blocksAway(player, context),
            activePlayers,
            totalPlayers,
            time
        );
        player.sendActionBar(text(message, NamedTextColor.AQUA));
    }

    private static int blocksAway(Player player, RoundContext context) {
        double currentY = player.getLocation().getY();
        double away = context.direction() == RoundDirection.UP
            ? context.targetY() - currentY
            : currentY - context.targetY();
        return (int) Math.ceil(Math.max(0.0, away));
    }

    public static Component text(String message, NamedTextColor color) {
        return Component.text(message, color);
    }
}
