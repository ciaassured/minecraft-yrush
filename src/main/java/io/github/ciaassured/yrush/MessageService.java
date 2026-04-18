package io.github.ciaassured.yrush;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;

public final class MessageService {
    public void broadcast(String message) {
        Bukkit.broadcast(component(message, NamedTextColor.GOLD));
    }

    public void broadcastDraw() {
        Bukkit.broadcast(component("DRAW! Nobody reached the target Y.", NamedTextColor.YELLOW));
    }

    public void broadcastWinner(Player winner, int targetY) {
        Bukkit.broadcast(
            Component.text(winner.getName(), NamedTextColor.GREEN)
                .append(Component.text(" wins! Reached Y " + targetY + " first.", NamedTextColor.GOLD))
        );
    }

    public void countdown(Collection<Player> players, int seconds) {
        Component title = component("Round starts in " + seconds, NamedTextColor.YELLOW);
        Title countdownTitle = Title.title(
            title,
            Component.empty(),
            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))
        );
        players.forEach(player -> {
            player.showTitle(countdownTitle);
            player.sendActionBar(title);
        });
    }

    public void roundStart(Collection<Player> players, RoundContext context) {
        String objective = context.direction().titlePrefix() + " Y " + context.targetY();
        Title title = Title.title(
            component(objective, NamedTextColor.GREEN),
            component("First player there wins", NamedTextColor.GRAY),
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
        );

        players.forEach(player -> {
            player.showTitle(title);
            player.sendMessage(component(objective, NamedTextColor.GREEN));
        });
    }

    public void actionBar(Player player, RoundContext context, int activePlayers, int totalPlayers) {
        long remaining = context.remainingSeconds(java.time.Instant.now());
        String time = "%02d:%02d".formatted(remaining / 60, remaining % 60);
        String message = "%s Y %d | Away %d | Active %d/%d | %s".formatted(
            context.direction().titlePrefix(),
            context.targetY(),
            blocksAway(player, context),
            activePlayers,
            totalPlayers,
            time
        );
        player.sendActionBar(component(message, NamedTextColor.AQUA));
    }

    private int blocksAway(Player player, RoundContext context) {
        double currentY = player.getLocation().getY();
        double remaining = context.direction() == RoundDirection.UP
            ? context.targetY() - currentY
            : currentY - context.targetY();
        return (int) Math.ceil(Math.max(0.0, remaining));
    }

    public Component component(String message, NamedTextColor color) {
        return Component.text(message, color);
    }
}
