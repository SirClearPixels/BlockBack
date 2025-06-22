package us.ironcladnetwork.blockback;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Manages persistent player settings for barkback, pathback, and farmback toggles.
 * Each player's data is stored in players.yml under their UUID. By default, all options are enabled.
 * Uses in-memory caching for improved performance.
 */
public class PlayerDataManager {

    /**
     * Represents cached player settings in memory
     */
    public static class PlayerSettings {
        public boolean barkback = true;
        public boolean pathback = true;
        public boolean farmback = true;
        public String name;
        
        public PlayerSettings(String name) {
            this.name = name;
        }
        
        public PlayerSettings(String name, boolean barkback, boolean pathback, boolean farmback) {
            this.name = name;
            this.barkback = barkback;
            this.pathback = pathback;
            this.farmback = farmback;
        }
    }

    private static PlayerDataManager instance;
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private volatile boolean pendingSave = false;
    private volatile CountDownLatch shutdownLatch;
    
    // In-memory cache for player settings - thread-safe concurrent map
    private final ConcurrentHashMap<UUID, PlayerSettings> playerCache = new ConcurrentHashMap<>();

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
        loadConfiguration();
    }

    // Set default values for a new player
    private void setDefaults(String uuid, String name) {
        config.set(uuid + ".name", name);
        config.set(uuid + ".barkback", true);
        config.set(uuid + ".pathback", true);
        config.set(uuid + ".farmback", true);
    }
    
    /**
     * Load configuration with error handling and recovery
     */
    private void loadConfiguration() {
        try {
            config = YamlConfiguration.loadConfiguration(configFile);
            
            // Validate the loaded configuration
            if (!validateConfiguration()) {
                plugin.getLogger().warning("Configuration validation failed, attempting recovery...");
                if (!recoverFromBackup()) {
                    plugin.getLogger().warning("Recovery failed, creating new configuration with defaults");
                    createEmptyConfiguration();
                }
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load players.yml: " + e.getMessage());
            plugin.getLogger().warning("Attempting to recover from backup...");
            
            if (!recoverFromBackup()) {
                plugin.getLogger().warning("Recovery failed, creating new configuration");
                createEmptyConfiguration();
            }
        }
    }
    
    /**
     * Validate the loaded configuration structure
     * @return true if configuration is valid
     */
    private boolean validateConfiguration() {
        try {
            // Check if the config object is valid
            if (config == null) {
                return false;
            }
            
            // Try to access configuration methods to ensure it's not corrupted
            config.getKeys(false);
            
            // If we have player data, validate a few entries
            for (String key : config.getKeys(false)) {
                if (key != null && !key.isEmpty()) {
                    // Try to access player data to ensure structure is valid
                    config.getConfigurationSection(key);
                }
            }
            
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Configuration validation failed: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Create a backup of the current players.yml file
     */
    private void createBackup() {
        if (!configFile.exists()) {
            return;
        }
        
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backupFile = new File(configFile.getParent(), "players.yml.backup." + timestamp);
            Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().info("Created backup: " + backupFile.getName());
            
            // Keep only the last 5 backups
            cleanupOldBackups();
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create backup: " + e.getMessage());
        }
    }
    
    /**
     * Clean up old backup files, keeping only the last 5
     */
    private void cleanupOldBackups() {
        File dataFolder = configFile.getParentFile();
        File[] backups = dataFolder.listFiles((dir, name) -> name.startsWith("players.yml.backup."));
        
        if (backups != null && backups.length > 5) {
            // Sort by modification time (oldest first)
            java.util.Arrays.sort(backups, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            
            // Delete oldest backups, keep only 5 most recent
            for (int i = 0; i < backups.length - 5; i++) {
                if (backups[i].delete()) {
                    plugin.getLogger().info("Deleted old backup: " + backups[i].getName());
                }
            }
        }
    }
    
    /**
     * Attempt to recover from the most recent backup
     * @return true if recovery was successful
     */
    private boolean recoverFromBackup() {
        File dataFolder = configFile.getParentFile();
        File[] backups = dataFolder.listFiles((dir, name) -> name.startsWith("players.yml.backup."));
        
        if (backups == null || backups.length == 0) {
            plugin.getLogger().warning("No backup files found for recovery");
            return false;
        }
        
        // Sort by modification time (newest first)
        java.util.Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        
        for (File backup : backups) {
            try {
                plugin.getLogger().info("Attempting recovery from: " + backup.getName());
                
                // Try to load the backup file
                FileConfiguration backupConfig = YamlConfiguration.loadConfiguration(backup);
                backupConfig.getKeys(false); // Test if it's readable
                
                // If successful, copy backup to main file
                Files.copy(backup.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                config = YamlConfiguration.loadConfiguration(configFile);
                
                plugin.getLogger().info("Successfully recovered from backup: " + backup.getName());
                return true;
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to recover from " + backup.getName() + ": " + e.getMessage());
                // Continue to next backup
            }
        }
        
        plugin.getLogger().severe("All backup recovery attempts failed");
        return false;
    }
    
    /**
     * Create a new empty configuration with proper structure
     */
    private void createEmptyConfiguration() {
        config = new YamlConfiguration();
        plugin.getLogger().info("Created new empty configuration file");
        saveConfig(); // Save the empty config to disk
    }
    
    /**
     * Load and validate player settings from configuration
     * @param uuid the player's UUID as string
     * @param playerName the player's current name
     * @return validated PlayerSettings object
     */
    private PlayerSettings loadAndValidatePlayerSettings(String uuid, String playerName) {
        try {
            // Attempt to load player data with validation
            String name = config.getString(uuid + ".name");
            Object barkbackObj = config.get(uuid + ".barkback");
            Object pathbackObj = config.get(uuid + ".pathback");
            Object farmbackObj = config.get(uuid + ".farmback");
            
            // Validate and convert values
            boolean barkback = validateBooleanSetting(barkbackObj, true, "barkback", uuid);
            boolean pathback = validateBooleanSetting(pathbackObj, true, "pathback", uuid);
            boolean farmback = validateBooleanSetting(farmbackObj, true, "farmback", uuid);
            
            // Use current player name if stored name is invalid
            if (name == null || name.trim().isEmpty()) {
                name = playerName;
                config.set(uuid + ".name", name);
                saveConfig();
            }
            
            return new PlayerSettings(name, barkback, pathback, farmback);
            
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load settings for player " + uuid + ": " + e.getMessage());
            plugin.getLogger().info("Using default settings for player " + playerName);
            
            // Create and save default settings
            PlayerSettings defaults = new PlayerSettings(playerName);
            setDefaults(uuid, playerName);
            saveConfig();
            
            return defaults;
        }
    }
    
    /**
     * Validate a boolean setting value
     * @param value the value to validate
     * @param defaultValue the default value to use if invalid
     * @param settingName the name of the setting for logging
     * @param uuid the player's UUID for logging
     * @return validated boolean value
     */
    private boolean validateBooleanSetting(Object value, boolean defaultValue, String settingName, String uuid) {
        if (value == null) {
            plugin.getLogger().warning("Missing " + settingName + " setting for player " + uuid + ", using default: " + defaultValue);
            return defaultValue;
        }
        
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        
        if (value instanceof String) {
            String strValue = ((String) value).toLowerCase().trim();
            if ("true".equals(strValue) || "yes".equals(strValue) || "1".equals(strValue)) {
                return true;
            } else if ("false".equals(strValue) || "no".equals(strValue) || "0".equals(strValue)) {
                return false;
            }
        }
        
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        
        plugin.getLogger().warning("Invalid " + settingName + " value '" + value + "' for player " + uuid + ", using default: " + defaultValue);
        return defaultValue;
    }
    
    /**
     * Emergency fallback method when all other recovery attempts fail
     * @param player the player needing settings
     * @return safe default settings
     */
    private PlayerSettings getEmergencyDefaults(Player player) {
        plugin.getLogger().warning("Using emergency defaults for player " + player.getName() + " due to data corruption");
        return new PlayerSettings(player.getName(), true, true, true);
    }
    
    /**
     * Check if the configuration system is healthy
     * @return true if the system can safely save/load data
     */
    public boolean isConfigurationHealthy() {
        try {
            return config != null && configFile != null && configFile.getParentFile().canWrite();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generic method to get a player's feature setting with caching
     * @param player the player
     * @param featureName the feature name (e.g., "barkback", "pathback", "farmback")
     * @return true if the feature is enabled (defaults to true)
     */
    private boolean getFeatureSetting(Player player, String featureName) {
        UUID uuid = player.getUniqueId();
        
        // Check cache first
        PlayerSettings cached = playerCache.get(uuid);
        if (cached != null) {
            switch (featureName) {
                case "barkback": return cached.barkback;
                case "pathback": return cached.pathback;
                case "farmback": return cached.farmback;
                default: return true;
            }
        }
        
        // Load from file if not in cache
        String uuidStr = uuid.toString();
        PlayerSettings settings;
        
        try {
            if (!config.contains(uuidStr)) {
                // New player - create defaults
                settings = new PlayerSettings(player.getName());
                setDefaults(uuidStr, player.getName());
                saveConfig();
            } else {
                // Load existing settings with validation
                settings = loadAndValidatePlayerSettings(uuidStr, player.getName());
            }
            
            // Cache the settings
            playerCache.put(uuid, settings);
            
        } catch (Exception e) {
            plugin.getLogger().severe("Critical error loading player settings for " + player.getName() + ": " + e.getMessage());
            // Use emergency defaults and cache them
            settings = getEmergencyDefaults(player);
            playerCache.put(uuid, settings);
        }
        
        // Return the requested setting
        switch (featureName) {
            case "barkback": return settings.barkback;
            case "pathback": return settings.pathback;
            case "farmback": return settings.farmback;
            default: return true;
        }
    }

    /**
     * Generic method to set a player's feature setting with cache update
     * @param player the player
     * @param featureName the feature name (e.g., "barkback", "pathback", "farmback")
     * @param enabled whether the feature should be enabled
     */
    private void setFeatureSetting(Player player, String featureName, boolean enabled) {
        UUID uuid = player.getUniqueId();
        String uuidStr = uuid.toString();
        
        // Update file configuration
        config.set(uuidStr + ".name", player.getName());
        config.set(uuidStr + "." + featureName, enabled);
        saveConfig();
        
        // Update cache
        PlayerSettings cached = playerCache.get(uuid);
        if (cached == null) {
            // Create new cache entry if doesn't exist
            cached = new PlayerSettings(player.getName());
            playerCache.put(uuid, cached);
        }
        
        // Update the specific setting in cache
        switch (featureName) {
            case "barkback": cached.barkback = enabled; break;
            case "pathback": cached.pathback = enabled; break;
            case "farmback": cached.farmback = enabled; break;
        }
        
        // Update name in cache
        cached.name = player.getName();
    }

    /**
     * @return true if the player's barkback is enabled (defaults to true)
     */
    public boolean isBarkBackEnabled(Player player) {
        return getFeatureSetting(player, "barkback");
    }

    /**
     * @return true if the player's pathback is enabled (defaults to true)
     */
    public boolean isPathBackEnabled(Player player) {
        return getFeatureSetting(player, "pathback");
    }

    /**
     * @return true if the player's farmback is enabled (defaults to true)
     */
    public boolean isFarmBackEnabled(Player player) {
        return getFeatureSetting(player, "farmback");
    }

    /// Setter methods
    public void setBarkBack(Player player, boolean enabled) {
        setFeatureSetting(player, "barkback", enabled);
    }

    public void setPathBack(Player player, boolean enabled) {
        setFeatureSetting(player, "pathback", enabled);
    }

    public void setFarmBack(Player player, boolean enabled) {
        setFeatureSetting(player, "farmback", enabled);
    }

    // Save the configuration to players.yml asynchronously to avoid blocking the main thread.
    // Uses atomic boolean to prevent race conditions and queue pending saves.
    private void saveConfig() {
        pendingSave = true;
        
        // If a save is already in progress, just mark that we have a pending save
        if (!saveInProgress.compareAndSet(false, true)) {
            return;
        }
        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Keep saving while there are pending changes
                do {
                    pendingSave = false;
                    
                    // Create a temporary file for atomic write operations
                    File tempFile = new File(configFile.getAbsolutePath() + ".tmp");
                    
                    try {
                        // Create backup before saving if file exists and has content
                        if (configFile.exists() && configFile.length() > 0) {
                            createBackup();
                        }
                        
                        config.save(tempFile);
                        
                        // Atomic rename to replace the original file
                        if (!tempFile.renameTo(configFile)) {
                            // Fallback to direct save if rename fails
                            plugin.getLogger().warning("Atomic rename failed, falling back to direct save");
                            config.save(configFile);
                        }
                    } catch (IOException saveException) {
                        plugin.getLogger().severe("Failed to save to temporary file: " + saveException.getMessage());
                        // Try direct save as last resort
                        try {
                            config.save(configFile);
                        } catch (IOException directSaveException) {
                            plugin.getLogger().severe("Direct save also failed: " + directSaveException.getMessage());
                            throw directSaveException;
                        }
                    } finally {
                        // Clean up temporary file if it exists
                        if (tempFile.exists() && !tempFile.delete()) {
                            plugin.getLogger().warning("Failed to delete temporary file: " + tempFile.getAbsolutePath());
                        }
                    }
                    
                } while (pendingSave);
                
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save players.yml asynchronously: " + e.getMessage());
            } finally {
                saveInProgress.set(false);
                
                // Notify shutdown latch if waiting
                CountDownLatch latch = shutdownLatch;
                if (latch != null) {
                    latch.countDown();
                }
                
                // Check if another save was requested while we were finishing
                if (pendingSave) {
                    saveConfig();
                }
            }
        });
    }

    /**
     * Reloads the players.yml configuration and clears cache with error recovery.
     */
    public void reloadConfig() {
        // Clear cache first to ensure fresh data
        playerCache.clear();
        
        try {
            loadConfiguration();
            plugin.getLogger().info("Player configuration reloaded successfully");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to reload player configuration: " + e.getMessage());
            plugin.getLogger().warning("Player data may be using fallback defaults until next restart");
        }
    }
    
    /**
     * Removes a player from the cache (typically called on player disconnect)
     * @param player the player to remove from cache
     */
    public void removeFromCache(Player player) {
        playerCache.remove(player.getUniqueId());
    }
    
    /**
     * Removes a player from the cache by UUID
     * @param uuid the UUID of the player to remove from cache
     */
    public void removeFromCache(UUID uuid) {
        playerCache.remove(uuid);
    }
    
    /**
     * Gets the current cache size (for monitoring/debugging)
     * @return number of players currently cached
     */
    public int getCacheSize() {
        return playerCache.size();
    }
    
    /**
     * Clears all cached player data
     */
    public void clearCache() {
        playerCache.clear();
    }
    
    /**
     * Ensures all pending saves complete before plugin shutdown.
     * This method should be called from the plugin's onDisable method.
     * @param timeoutSeconds maximum time to wait for saves to complete
     * @return true if all saves completed, false if timeout occurred
     */
    public boolean shutdown(int timeoutSeconds) {
        if (!saveInProgress.get() && !pendingSave) {
            return true; // No saves pending
        }
        
        shutdownLatch = new CountDownLatch(1);
        
        try {
            // Wait for the current save to complete
            boolean completed = shutdownLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("Timeout waiting for player data save to complete during shutdown");
            }
            return completed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().warning("Interrupted while waiting for player data save during shutdown");
            return false;
        } finally {
            shutdownLatch = null;
        }
    }
} 