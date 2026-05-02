package us.ironcladnetwork.blockback;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class for BlockBack - a Minecraft plugin that allows players to revert
 * stripped logs, dirt paths, and farmland back to their original block types.
 * 
 * Features:
 * - BarkBack: Revert stripped logs back to regular logs
 * - PathBack: Revert dirt paths back to dirt
 * - FarmBack: Revert farmland back to dirt
 * 
 * @author ClearPixels
 * @version 1.4.0
 */
public final class Blockback extends JavaPlugin {

    /** bStats plugin ID — see https://bstats.org/plugin/bukkit/BlockBack/31058 */
    private static final int BSTATS_PLUGIN_ID = 31058;

    /**
     * Called when the plugin is enabled. Initializes managers, registers events,
     * and sets up commands.
     */
    @Override
    public void onEnable() {
        // Log plugin startup
        getLogger().info("BlockBack is starting...");

        // SCHED-01: Must run before PlayerDataManager.init() (constructor reads FoliaCompat.IS_FOLIA)
        FoliaCompat.init(this);

        if (FoliaCompat.IS_FOLIA) {
            getLogger().info("[BlockBack] Folia thread-safety active: region scheduler, " +
                "volatile singletons, region ownership assertions enabled.");
        }

        // Initialize managers for persistent settings and sound configuration
        PlayerDataManager.init(this);
        SoundConfig.init(this);

        // Register the event listener
        EventListener eventListener;
        try {
            eventListener = new EventListener(this);
            Bukkit.getPluginManager().registerEvents(eventListener, this);
            getLogger().info("EventListener registered successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to register EventListener: " + e.getMessage());
            e.printStackTrace();
            // SAFE-05: Safe on Folia -- onEnable() runs on the global tick thread during server startup.
            // Do NOT add disablePlugin() calls to event handlers, commands, or async tasks.
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // bStats anonymous usage metrics. Failures here must not prevent plugin startup.
        try {
            Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("server_type",
                    () -> FoliaCompat.IS_FOLIA ? "Folia" : "Spigot/Paper"));
            metrics.addCustomChart(new SingleLineChart("barkback_uses",
                    () -> (int) Math.min(Integer.MAX_VALUE, eventListener.pollAndResetBarkbackCount())));
            metrics.addCustomChart(new SingleLineChart("pathback_uses",
                    () -> (int) Math.min(Integer.MAX_VALUE, eventListener.pollAndResetPathbackCount())));
            metrics.addCustomChart(new SingleLineChart("farmback_uses",
                    () -> (int) Math.min(Integer.MAX_VALUE, eventListener.pollAndResetFarmbackCount())));
            getLogger().info("bStats metrics initialized (plugin ID " + BSTATS_PLUGIN_ID + ").");
        } catch (Exception e) {
            getLogger().warning("Failed to initialize bStats metrics: " + e.getMessage());
        }

        // Create one instance of CommandManager and register for all commands.
        CommandManager commandManager;
        try {
            commandManager = new CommandManager();
        } catch (Exception e) {
            getLogger().severe("Failed to create CommandManager: " + e.getMessage());
            e.printStackTrace();
            // SAFE-05: Safe on Folia -- onEnable() runs on the global tick thread during server startup.
            // Do NOT add disablePlugin() calls to event handlers, commands, or async tasks.
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
                // SAFE-05: Safe on Folia -- onEnable() runs on the global tick thread during server startup.
                // Do NOT add disablePlugin() calls to event handlers, commands, or async tasks.
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        }

        // Log successful load
        getLogger().info("BlockBack has loaded successfully!");
    }

    /**
     * Called when the plugin is disabled. Ensures all player data is saved
     * and cleanup tasks are properly stopped.
     */
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