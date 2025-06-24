package us.ironcladnetwork.blockback;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all commands for the BlockBack plugin including individual feature toggles
 * and the main command with status/reload functionality.
 * 
 * Supported commands:
 * - /barkback - Toggle bark restoration feature
 * - /pathback - Toggle path reversion feature  
 * - /farmback - Toggle farmland reversion feature
 * - /blockback - Main command with status and reload subcommands
 */
public class CommandManager implements CommandExecutor {

    /**
     * Processes all BlockBack commands and routes them to appropriate handlers.
     * 
     * @param sender the command sender (must be a player for most commands)
     * @param command the command being executed
     * @param label the command label used
     * @param args command arguments
     * @return true if command was handled, false otherwise
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }
        
        // Cache instances to avoid multiple getInstance() calls
        PlayerDataManager playerData = PlayerDataManager.getInstance();
        if (playerData == null) {
            sender.sendMessage(ChatColor.RED + "Plugin not properly initialized. Please contact an administrator.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("barkback")) {
            return handleToggleCommand(player, playerData, "BarkBack", "blockback.bark",
                                     playerData::isBarkBackEnabled, playerData::setBarkBack);
        }

        if (command.getName().equalsIgnoreCase("pathback")) {
            return handleToggleCommand(player, playerData, "PathBack", "blockback.path",
                                     playerData::isPathBackEnabled, playerData::setPathBack);
        }

        if (command.getName().equalsIgnoreCase("farmback")) {
            return handleToggleCommand(player, playerData, "FarmBack", "blockback.farm",
                                     playerData::isFarmBackEnabled, playerData::setFarmBack);
        }

        if (command.getName().equalsIgnoreCase("blockback")) {
            // Main command with subcommands
            if (args.length == 0) {
                // Show help/status
                player.sendMessage(ChatColor.GOLD + "=== BlockBack Status ===");
                
                String barkStatus = playerData.isBarkBackEnabled(player) ? 
                    ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled";
                String pathStatus = playerData.isPathBackEnabled(player) ? 
                    ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled";
                String farmStatus = playerData.isFarmBackEnabled(player) ? 
                    ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled";
                
                player.sendMessage(ChatColor.YELLOW + "BarkBack: " + barkStatus);
                player.sendMessage(ChatColor.YELLOW + "PathBack: " + pathStatus);
                player.sendMessage(ChatColor.YELLOW + "FarmBack: " + farmStatus);
                
                player.sendMessage(ChatColor.GRAY + "Use /barkback, /pathback, /farmback to toggle features.");
                if (player.hasPermission("blockback.reload")) {
                    player.sendMessage(ChatColor.GRAY + "Use /blockback reload to reload configuration.");
                }
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("blockback.reload")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                    return true;
                }
                
                // Reload configurations
                SoundConfig soundConfig = SoundConfig.getInstance();
                if (soundConfig == null) {
                    player.sendMessage(ChatColor.RED + "Sound configuration not initialized. Please contact an administrator.");
                    return true;
                }
                soundConfig.reloadConfig();
                playerData.reloadConfig();
                
                player.sendMessage(ChatColor.GREEN + "BlockBack configuration reloaded successfully!");
                return true;
            }
            
            // Unknown subcommand
            player.sendMessage(ChatColor.RED + "Unknown subcommand. Use /blockback for help.");
            return true;
        }

        return false;
    }
    
    /**
     * Helper method to handle feature toggle commands
     * @param player the player executing the command
     * @param playerData the PlayerDataManager instance
     * @param featureName the name of the feature (e.g., "BarkBack")
     * @param permission the permission to check (e.g., "blockback.bark")
     * @param getCurrentState function to get current state
     * @param setNewState function to set new state
     * @return true if command was handled
     */
    private boolean handleToggleCommand(Player player, PlayerDataManager playerData, 
                                      String featureName, String permission,
                                      java.util.function.Function<Player, Boolean> getCurrentState,
                                      java.util.function.BiConsumer<Player, Boolean> setNewState) {
        if (!player.hasPermission(permission)) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        
        // Toggle the setting
        boolean current = getCurrentState.apply(player);
        setNewState.accept(player, !current);
        
        // Send confirmation message
        String status = !current ? ChatColor.GREEN + "enabled" : ChatColor.RED + "disabled";
        player.sendMessage(ChatColor.YELLOW + featureName + " is now " + status + ChatColor.YELLOW + ".");
        
        return true;
    }
}