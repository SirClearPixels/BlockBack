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

        if (command.getName().equalsIgnoreCase("barkback")) {
            if (!player.hasPermission("blockback.bark")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }
            // Toggle barkback setting
            boolean current = PlayerDataManager.getInstance().isBarkBackEnabled(player);
            PlayerDataManager.getInstance().setBarkBack(player, !current);
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
            boolean current = PlayerDataManager.getInstance().isPathBackEnabled(player);
            PlayerDataManager.getInstance().setPathBack(player, !current);
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
            boolean current = PlayerDataManager.getInstance().isFarmBackEnabled(player);
            PlayerDataManager.getInstance().setFarmBack(player, !current);
            if (!current)
                player.sendMessage(ChatColor.YELLOW + "FarmBack is now " + ChatColor.GREEN + "enabled.");
            else
                player.sendMessage(ChatColor.YELLOW + "FarmBack is now " + ChatColor.RED + "disabled.");
            return true;
        }

        return false;
    }

    // Delegate static getter methods to PlayerDataManager for use in EventListener

    public static boolean isBarkBackEnabled(Player player) {
        return PlayerDataManager.getInstance().isBarkBackEnabled(player);
    }

    public static boolean isPathBackEnabled(Player player) {
        return PlayerDataManager.getInstance().isPathBackEnabled(player);
    }

    public static boolean isFarmBackEnabled(Player player) {
        return PlayerDataManager.getInstance().isFarmBackEnabled(player);
    }
}