package us.ironcladnetwork.blockback;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CommandManager implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        
        // Cache instances to avoid multiple getInstance() calls
        PlayerDataManager playerData = PlayerDataManager.getInstance();

        if (command.getName().equalsIgnoreCase("barkback")) {
            if (!player.hasPermission("blockback.bark")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            // Toggle barkback setting
            boolean current = playerData.isBarkBackEnabled(player);
            playerData.setBarkBack(player, !current);
            if (!current)
                player.sendMessage(ChatColor.YELLOW + "BarkBack is now " + ChatColor.GREEN + "enabled.");
            else
                player.sendMessage(ChatColor.YELLOW + "BarkBack is now " + ChatColor.RED + "disabled.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("pathback")) {
            if (!player.hasPermission("blockback.path")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            // Toggle pathback setting
            boolean current = playerData.isPathBackEnabled(player);
            playerData.setPathBack(player, !current);
            if (!current)
                player.sendMessage(ChatColor.YELLOW + "PathBack is now " + ChatColor.GREEN + "enabled.");
            else
                player.sendMessage(ChatColor.YELLOW + "PathBack is now " + ChatColor.RED + "disabled.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("farmback")) {
            if (!player.hasPermission("blockback.farm")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            // Toggle farmback setting
            boolean current = playerData.isFarmBackEnabled(player);
            playerData.setFarmBack(player, !current);
            if (!current)
                player.sendMessage(ChatColor.YELLOW + "FarmBack is now " + ChatColor.GREEN + "enabled.");
            else
                player.sendMessage(ChatColor.YELLOW + "FarmBack is now " + ChatColor.RED + "disabled.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("blockback")) {
            // Main command with subcommands
            if (args.length == 0) {
                // Show help/status
                player.sendMessage(ChatColor.GOLD + "=== BlockBack Status ===");
                player.sendMessage(ChatColor.YELLOW + "BarkBack: " + 
                    (playerData.isBarkBackEnabled(player) ? 
                        ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
                player.sendMessage(ChatColor.YELLOW + "PathBack: " + 
                    (playerData.isPathBackEnabled(player) ? 
                        ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
                player.sendMessage(ChatColor.YELLOW + "FarmBack: " + 
                    (playerData.isFarmBackEnabled(player) ? 
                        ChatColor.GREEN + "Enabled" : ChatColor.RED + "Disabled"));
                player.sendMessage(ChatColor.GRAY + "Use /barkback, /pathback, /farmback to toggle features");
                if (player.hasPermission("blockback.reload")) {
                    player.sendMessage(ChatColor.GRAY + "Use /blockback reload to reload sound configuration");
                }
                return true;
            }
            
            if (args[0].equalsIgnoreCase("reload")) {
                if (!player.hasPermission("blockback.reload")) {
                    player.sendMessage(ChatColor.RED + "You do not have permission to reload configuration.");
                    return true;
                }
                
                // Reload configurations
                SoundConfig.getInstance().reloadConfig();
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
}