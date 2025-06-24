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
            config.set("barkback.pitch", 1.0);
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
        // Load BarkBack settings
        try {
            String soundName = config.getString("barkback.sound", "ITEM_AXE_STRIP");
            String categoryName = config.getString("barkback.category", "BLOCKS");
            
            Sound sound;
            SoundCategory category;
            
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid BarkBack sound '" + soundName + "', using default ITEM_AXE_STRIP");
                sound = Sound.ITEM_AXE_STRIP;
            }
            
            try {
                category = SoundCategory.valueOf(categoryName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid BarkBack sound category '" + categoryName + "', using default BLOCKS");
                category = SoundCategory.BLOCKS;
            }
            
            barkBackSettings = new SoundSettings(
                sound,
                category,
                validateVolume(config.getDouble("barkback.volume", 1.0), "BarkBack"),
                validatePitch(config.getDouble("barkback.pitch", 1.0), "BarkBack"),
                config.getBoolean("barkback.enabled", true)
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load BarkBack sound settings: " + e.getMessage());
            barkBackSettings = new SoundSettings(Sound.ITEM_AXE_STRIP, SoundCategory.BLOCKS, 1.0f, 1.0f, true);
        }
        
        // Load PathBack settings
        try {
            String soundName = config.getString("pathback.sound", "ITEM_SHOVEL_FLATTEN");
            String categoryName = config.getString("pathback.category", "BLOCKS");
            
            Sound sound;
            SoundCategory category;
            
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid PathBack sound '" + soundName + "', using default ITEM_SHOVEL_FLATTEN");
                sound = Sound.ITEM_SHOVEL_FLATTEN;
            }
            
            try {
                category = SoundCategory.valueOf(categoryName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid PathBack sound category '" + categoryName + "', using default BLOCKS");
                category = SoundCategory.BLOCKS;
            }
            
            pathBackSettings = new SoundSettings(
                sound,
                category,
                validateVolume(config.getDouble("pathback.volume", 1.0), "PathBack"),
                validatePitch(config.getDouble("pathback.pitch", 1.0), "PathBack"),
                config.getBoolean("pathback.enabled", true)
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load PathBack sound settings: " + e.getMessage());
            pathBackSettings = new SoundSettings(Sound.ITEM_SHOVEL_FLATTEN, SoundCategory.BLOCKS, 1.0f, 1.0f, true);
        }
        
        // Load FarmBack settings
        try {
            String soundName = config.getString("farmback.sound", "ITEM_HOE_TILL");
            String categoryName = config.getString("farmback.category", "BLOCKS");
            
            Sound sound;
            SoundCategory category;
            
            try {
                sound = Sound.valueOf(soundName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid FarmBack sound '" + soundName + "', using default ITEM_HOE_TILL");
                sound = Sound.ITEM_HOE_TILL;
            }
            
            try {
                category = SoundCategory.valueOf(categoryName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid FarmBack sound category '" + categoryName + "', using default BLOCKS");
                category = SoundCategory.BLOCKS;
            }
            
            farmBackSettings = new SoundSettings(
                sound,
                category,
                validateVolume(config.getDouble("farmback.volume", 1.0), "FarmBack"),
                validatePitch(config.getDouble("farmback.pitch", 1.0), "FarmBack"),
                config.getBoolean("farmback.enabled", true)
            );
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load FarmBack sound settings: " + e.getMessage());
            farmBackSettings = new SoundSettings(Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0f, 1.0f, true);
        }
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
    
    /**
     * Validates and clamps volume to the valid range (0.0 to 10.0)
     * @param value the volume value to validate
     * @param featureName the name of the feature for logging
     * @return validated volume value
     */
    private float validateVolume(double value, String featureName) {
        if (value < 0.0) {
            plugin.getLogger().warning(featureName + " volume " + value + " is below minimum (0.0), using 0.0");
            return 0.0f;
        } else if (value > 10.0) {
            plugin.getLogger().warning(featureName + " volume " + value + " is above maximum (10.0), using 10.0");
            return 10.0f;
        }
        return (float) value;
    }
    
    /**
     * Validates and clamps pitch to the valid range (0.5 to 2.0)
     * @param value the pitch value to validate
     * @param featureName the name of the feature for logging
     * @return validated pitch value
     */
    private float validatePitch(double value, String featureName) {
        if (value < 0.5) {
            plugin.getLogger().warning(featureName + " pitch " + value + " is below minimum (0.5), using 0.5");
            return 0.5f;
        } else if (value > 2.0) {
            plugin.getLogger().warning(featureName + " pitch " + value + " is above maximum (2.0), using 2.0");
            return 2.0f;
        }
        return (float) value;
    }
}