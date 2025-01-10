package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    @Override
    public void onEnable() {
        // Log plugin startup
        getLogger().info("BlockBack is starting...");

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

        // Register commands
        getCommand("barkback").setExecutor(new CommandManager());
        getCommand("pathback").setExecutor(new CommandManager());

        // Log successful load
        getLogger().info("BlockBack has loaded successfully!");
    }

    @Override
    public void onDisable() {
        // Perform any cleanup
        getLogger().info("BlockBack is unloaded...");
    }
}