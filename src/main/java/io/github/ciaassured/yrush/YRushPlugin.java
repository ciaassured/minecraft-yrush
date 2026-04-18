package io.github.ciaassured.yrush;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class YRushPlugin extends JavaPlugin {
    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        gameManager = new GameManager(this);
        YRushCommand command = new YRushCommand(gameManager);

        PluginCommand yrushCommand = getCommand("yrush");
        if (yrushCommand == null) {
            throw new IllegalStateException("Command 'yrush' is missing from plugin.yml");
        }
        yrushCommand.setExecutor(command);
        yrushCommand.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(gameManager, this);
        getLogger().info("YRush enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }
}

