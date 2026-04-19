package io.github.ciaassured.yrush;

import io.github.ciaassured.yrush.command.YRushCommand;
import io.github.ciaassured.yrush.game.GameController;
import io.github.ciaassured.yrush.service.DebugService;
import io.github.ciaassured.yrush.service.TrainingStatePacketService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class YRushPlugin extends JavaPlugin {
    private GameController gameController;
    private DebugService debug;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        debug = new DebugService(this);
        gameController = new GameController(this);

        PluginCommand command = getCommand("yrush");
        if (command == null) {
            throw new IllegalStateException("Command 'yrush' is missing from plugin.yml");
        }
        YRushCommand executor = new YRushCommand(gameController);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getMessenger().registerOutgoingPluginChannel(this, TrainingStatePacketService.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, TrainingStatePacketService.CHANNEL,
            (channel, player, message) -> TrainingStatePacketService.receiveSubscriptionMessage(channel, player, message, debug));

        getServer().getPluginManager().registerEvents(gameController, this);
        getLogger().info("YRush enabled.");
    }

    @Override
    public void onDisable() {
        if (gameController != null) {
            gameController.shutdown();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, TrainingStatePacketService.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, TrainingStatePacketService.CHANNEL);
    }
}
