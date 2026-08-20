package io.github.ciaassured.yrush;

import io.github.ciaassured.yrush.command.YRushCommand;
import io.github.ciaassured.yrush.game.GameController;
import io.github.ciaassured.yrush.service.TrainingStatePacketService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class YRushPlugin extends JavaPlugin {
    private GameController gameController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameController = new GameController(this);

        PluginCommand command = getCommand("yrush");
        if (command == null) {
            throw new IllegalStateException("Command 'yrush' is missing from plugin.yml");
        }
        YRushCommand executor = new YRushCommand(gameController);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getMessenger().registerOutgoingPluginChannel(this, TrainingStatePacketService.CHANNEL);

        getServer().getPluginManager().registerEvents(gameController, this);
        getLogger().info("YRush enabled.");
    }

    @Override
    public void onDisable() {
        if (gameController != null) {
            gameController.shutdown();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, TrainingStatePacketService.CHANNEL);
    }
}
