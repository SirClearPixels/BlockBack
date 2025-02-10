package us.ironcladnetwork.blockback;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Manages persistent player settings for barkback, pathback, and farmback toggles.
 * Each player's data is stored in players.yml under their UUID. By default, all options are enabled.
 */
public class PlayerDataManager {

    private static PlayerDataManager instance;
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;

    /**
     * Initialize the PlayerDataManager. This must be called from the main plugin class.
     * @param plugin the JavaPlugin instance
     */
    public static void init(JavaPlugin plugin) {
        instance = new PlayerDataManager(plugin);
    }

    /**
     * Retrieve the PlayerDataManager instance.
     * @return the instance
     */
    public static PlayerDataManager getInstance() {
        return instance;
    }

    private PlayerDataManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.configFile = new File(dataFolder, "players.yml");
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create players.yml: " + e.getMessage());
            }
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    // Set default values for a new player
    private void setDefaults(String uuid, String name) {
        config.set(uuid + ".name", name);
        config.set(uuid + ".barkback", true);
        config.set(uuid + ".pathback", true);
        config.set(uuid + ".farmback", true);
    }

    /**
     * @return true if the player's barkback is enabled (defaults to true)
     */
    public boolean isBarkBackEnabled(Player player) {
        String uuid = player.getUniqueId().toString();
        if (!config.contains(uuid)) {
            setDefaults(uuid, player.getName());
            saveConfig();
            return true;
        }
        return config.getBoolean(uuid + ".barkback", true);
    }

    /**
     * @return true if the player's pathback is enabled (defaults to true)
     */
    public boolean isPathBackEnabled(Player player) {
        String uuid = player.getUniqueId().toString();
        if (!config.contains(uuid)) {
            setDefaults(uuid, player.getName());
            saveConfig();
            return true;
        }
        return config.getBoolean(uuid + ".pathback", true);
    }

    /**
     * @return true if the player's farmback is enabled (defaults to true)
     */
    public boolean isFarmBackEnabled(Player player) {
        String uuid = player.getUniqueId().toString();
        if (!config.contains(uuid)) {
            setDefaults(uuid, player.getName());
            saveConfig();
            return true;
        }
        return config.getBoolean(uuid + ".farmback", true);
    }

    /// Setter methods
    public void setBarkBack(Player player, boolean enabled) {
        String uuid = player.getUniqueId().toString();
        config.set(uuid + ".name", player.getName());
        config.set(uuid + ".barkback", enabled);
        saveConfig();
    }

    public void setPathBack(Player player, boolean enabled) {
        String uuid = player.getUniqueId().toString();
        config.set(uuid + ".name", player.getName());
        config.set(uuid + ".pathback", enabled);
        saveConfig();
    }

    public void setFarmBack(Player player, boolean enabled) {
        String uuid = player.getUniqueId().toString();
        config.set(uuid + ".name", player.getName());
        config.set(uuid + ".farmback", enabled);
        saveConfig();
    }

    // Save the configuration to players.yml asynchronously to avoid blocking the main thread.
    private void saveConfig() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save players.yml asynchronously: " + e.getMessage());
            }
        });
    }

    /**
     * Reloads the players.yml configuration.
     */
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }
} 