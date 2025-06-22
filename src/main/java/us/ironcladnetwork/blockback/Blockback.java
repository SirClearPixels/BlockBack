package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Blockback extends JavaPlugin {

    @Override
    public void onEnable() {
        // Log plugin startup
        getLogger().info("BlockBack is starting...");

        // Initialize managers for persistent settings and sound configuration
        PlayerDataManager.init(this);
        SoundConfig.init(this);

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
        CommandManager commandManager;
        try {
            commandManager = new CommandManager();
        } catch (Exception e) {
            getLogger().severe("Failed to create CommandManager: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // Track command registration failures
        int failedCommands = 0;
        String[] requiredCommands = {"barkback", "pathback", "farmback", "blockback"};
        
        // Register commands with comprehensive null safety checks
        for (String commandName : requiredCommands) {
            if (getCommand(commandName) != null) {
                try {
                    getCommand(commandName).setExecutor(commandManager);
                    getLogger().info("Command '" + commandName + "' registered successfully.");
                } catch (Exception e) {
                    getLogger().severe("Failed to register executor for command '" + commandName + "': " + e.getMessage());
                    failedCommands++;
                }
            } else {
                getLogger().severe("Command '" + commandName + "' not found in plugin.yml!");
                failedCommands++;
            }
        }
        
        // Disable plugin if critical commands failed to register
        if (failedCommands > 0) {
            getLogger().severe("Failed to register " + failedCommands + " out of " + requiredCommands.length + " commands. Plugin functionality will be limited.");
            if (failedCommands == requiredCommands.length) {
                getLogger().severe("All commands failed to register. Disabling plugin.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Log successful load
        getLogger().info("BlockBack has loaded successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockBack is shutting down...");
        
        // Ensure all player data saves complete before shutdown
        PlayerDataManager manager = PlayerDataManager.getInstance();
        if (manager != null) {
            boolean saveCompleted = manager.shutdown(5); // 5 second timeout
            if (saveCompleted) {
                getLogger().info("All player data saved successfully during shutdown.");
            } else {
                getLogger().warning("Player data save may be incomplete due to shutdown timeout.");
            }
        }
        
        getLogger().info("BlockBack is unloaded...");
    }
}