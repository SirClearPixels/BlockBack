package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Blockback extends JavaPlugin {

    @Override
    public void onEnable() {
        // Log plugin startup
        getLogger().info("BlockBack is starting...");

        // Initialize PlayerDataManager for persistent settings
        PlayerDataManager.init(this);

        // Register the event listener
        try {
            Bukkit.getPluginManager().registerEvents(new EventListener(), this);
            getLogger().info("EventListener registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register EventListener: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Create one instance of CommandManager and register for all commands.
        CommandManager commandManager = new CommandManager();
        getCommand("barkback").setExecutor(commandManager);
        getCommand("pathback").setExecutor(commandManager);
        getCommand("farmback").setExecutor(commandManager);

        // Log successful load
        getLogger().info("BlockBack has loaded successfully!");
    }

    @Override
    public void onDisable() {
        // Perform any cleanup
        getLogger().info("BlockBack is unloaded...");
    }
}