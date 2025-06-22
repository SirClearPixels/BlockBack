package us.ironcladnetwork.blockback;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Manages customizable sound settings for BlockBack features.
 * Allows server admins to configure sounds, volumes, and pitches for each feature.
 */
public class SoundConfig {
    
    private static SoundConfig instance;
    private final JavaPlugin plugin;
    private final File configFile;
    private FileConfiguration config;
    
    // Sound settings for each feature
    private SoundSettings barkBackSettings;
    private SoundSettings pathBackSettings;
    private SoundSettings farmBackSettings;
    
    /**
     * Represents sound configuration for a specific feature
     */
    public static class SoundSettings {
        public final Sound sound;
        public final SoundCategory category;
        public final float volume;
        public final float pitch;
        public final boolean enabled;
        
        public SoundSettings(Sound sound, SoundCategory category, float volume, float pitch, boolean enabled) {
            this.sound = sound;
            this.category = category;
            this.volume = volume;
            this.pitch = pitch;
            this.enabled = enabled;
        }
    }
    
    /**
     * Initialize the SoundConfig. This must be called from the main plugin class.
     * @param plugin the JavaPlugin instance
     */
    public static void init(JavaPlugin plugin) {
        instance = new SoundConfig(plugin);
    }
    
    /**
     * Get the SoundConfig instance
     * @return the SoundConfig instance
     */
    public static SoundConfig getInstance() {
        return instance;
    }
    
    private SoundConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        this.configFile = new File(dataFolder, "sounds.yml");
        loadConfig();
    }
    
    /**
     * Load the sound configuration from file
     */
    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
        loadSoundSettings();
    }
    
    /**
     * Create default sound configuration file
     */
    private void createDefaultConfig() {
        try {
            configFile.createNewFile();
            config = new YamlConfiguration();
            
            // BarkBack settings
            config.set("barkback.sound", "ITEM_AXE_STRIP");
            config.set("barkback.category", "BLOCKS");
            config.set("barkback.volume", 1.0);
            config.set("barkback.pitch", 0.1);
            config.set("barkback.enabled", true);
            
            // PathBack settings
            config.set("pathback.sound", "ITEM_SHOVEL_FLATTEN");
            config.set("pathback.category", "BLOCKS");
            config.set("pathback.volume", 1.0);
            config.set("pathback.pitch", 1.0);
            config.set("pathback.enabled", true);
            
            // FarmBack settings
            config.set("farmback.sound", "ITEM_HOE_TILL");
            config.set("farmback.category", "BLOCKS");
            config.set("farmback.volume", 1.0);
            config.set("farmback.pitch", 1.0);
            config.set("farmback.enabled", true);
            
            // Add configuration header comments
            config.options().setHeader(java.util.Arrays.asList(
                "BlockBack Sound Configuration",
                "Configure sounds, volumes, and pitches for each feature.",
                "",
                "Sound Options: Any valid Bukkit Sound enum value",
                "Category Options: MASTER, MUSIC, RECORD, WEATHER, BLOCK, HOSTILE, NEUTRAL, PLAYER, AMBIENT, VOICE",
                "Volume: 0.0 to 10.0 (1.0 = normal volume)",
                "Pitch: 0.5 to 2.0 (1.0 = normal pitch, higher = higher pitch)",
                "Enabled: true/false to enable/disable sounds for each feature"
            ));
            
            config.save(configFile);
            plugin.getLogger().info("Created default sounds.yml configuration file");
            
        } catch (IOException e) {
            plugin.getLogger().severe("Could not create sounds.yml: " + e.getMessage());
        }
    }
    
    /**
     * Load sound settings from configuration
     */
    private void loadSoundSettings() {
        try {
            // Load BarkBack settings
            barkBackSettings = new SoundSettings(
                Sound.valueOf(config.getString("barkback.sound", "ITEM_AXE_STRIP")),
                SoundCategory.valueOf(config.getString("barkback.category", "BLOCKS")),
                (float) config.getDouble("barkback.volume", 1.0),
                (float) config.getDouble("barkback.pitch", 0.1),
                config.getBoolean("barkback.enabled", true)
            );
            
            // Load PathBack settings
            pathBackSettings = new SoundSettings(
                Sound.valueOf(config.getString("pathback.sound", "ITEM_SHOVEL_FLATTEN")),
                SoundCategory.valueOf(config.getString("pathback.category", "BLOCKS")),
                (float) config.getDouble("pathback.volume", 1.0),
                (float) config.getDouble("pathback.pitch", 1.0),
                config.getBoolean("pathback.enabled", true)
            );
            
            // Load FarmBack settings
            farmBackSettings = new SoundSettings(
                Sound.valueOf(config.getString("farmback.sound", "ITEM_HOE_TILL")),
                SoundCategory.valueOf(config.getString("farmback.category", "BLOCKS")),
                (float) config.getDouble("farmback.volume", 1.0),
                (float) config.getDouble("farmback.pitch", 1.0),
                config.getBoolean("farmback.enabled", true)
            );
            
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound configuration detected: " + e.getMessage());
            plugin.getLogger().warning("Using default sound settings");
            loadDefaultSettings();
        }
    }
    
    /**
     * Load default sound settings if configuration is invalid
     */
    private void loadDefaultSettings() {
        barkBackSettings = new SoundSettings(Sound.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0f, 0.1f, true);
        pathBackSettings = new SoundSettings(Sound.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0f, 1.0f, true);
        farmBackSettings = new SoundSettings(Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f, true);
    }
    
    /**
     * Reload the sound configuration from file
     */
    public void reloadConfig() {
        loadConfig();
        plugin.getLogger().info("Sound configuration reloaded");
    }
    
    /**
     * Get BarkBack sound settings
     * @return BarkBack sound settings
     */
    public SoundSettings getBarkBackSettings() {
        return barkBackSettings;
    }
    
    /**
     * Get PathBack sound settings
     * @return PathBack sound settings
     */
    public SoundSettings getPathBackSettings() {
        return pathBackSettings;
    }
    
    /**
     * Get FarmBack sound settings
     * @return FarmBack sound settings
     */
    public SoundSettings getFarmBackSettings() {
        return farmBackSettings;
    }
}