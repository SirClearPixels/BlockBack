package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** One synchronous, permission-gated copper gesture on the interaction's region thread. */
final class CopperBack {
    enum Result { PASS_THROUGH, CONSUMED, CHANGED, FAILED }

    private static final Map<Material, Material> NEXT = new EnumMap<>(Material.class);
    private static final Set<Material> COPPER = EnumSet.noneOf(Material.class);

    static {
        Material[] stages = {Material.COPPER_BLOCK, Material.EXPOSED_COPPER,
                Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER};
        for (int i = 0; i < stages.length; i++) {
            COPPER.add(stages[i]);
            Material waxed = Material.getMaterial("WAXED_" + stages[i].name());
            if (waxed != null) COPPER.add(waxed);
            if (i < stages.length - 1) NEXT.put(stages[i], stages[i + 1]);
        }
    }

    private final Plugin plugin;
    private final Predicate<Player> enabled;
    private final Function<String, BlockData> parse;
    private final Consumer<Player> changed;

    CopperBack(Plugin plugin) {
        this(plugin, player -> {
            PlayerDataManager settings = PlayerDataManager.getInstance();
            return settings != null && settings.isCopperBackEnabled(player);
        }, Bukkit::createBlockData, player -> { });
    }

    // Narrow API boundaries allow behavioral tests without booting a Minecraft server.
    CopperBack(Plugin plugin, Predicate<Player> enabled, Function<String, BlockData> parse,
               Consumer<Player> changed) {
        this.plugin = plugin;
        this.enabled = enabled;
        this.parse = parse;
        this.changed = changed;
    }

    Result handle(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null
                || event.getHand() == null || !isAxe(event.getItem()) || !player.isSneaking()
                || !player.hasPermission("blockback.copper") || !enabled.test(player)
                || !COPPER.contains(block.getType())) return Result.PASS_THROUGH;

        // Even a vanilla no-op prediction is conservatively respected: it is indistinguishable
        // from another plugin's denial. Never turn an existing DENY into ALLOW.
        if (event.useInteractedBlock() == Event.Result.DENY
                || event.useItemInHand() == Event.Result.DENY) return Result.PASS_THROUGH;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        // A second axe event must not advance twice or fall through to a vanilla scrape.
        if (event.getHand() == EquipmentSlot.OFF_HAND
                && isAxe(player.getInventory().getItemInMainHand())) return Result.CONSUMED;

        Material next = NEXT.get(block.getType());
        if (next == null) return Result.CONSUMED;
        Result result = convert(block, next);
        if (result == Result.CHANGED) changed.accept(player);
        return result;
    }

    private static boolean isAxe(ItemStack item) {
        return item != null && EventListener.isAxe(item.getType());
    }

    private Result convert(Block block, Material next) {
        BlockState original = null;
        boolean attempted = false;
        try {
            original = block.getState();
            BlockState replacement = block.getState();
            replacement.setBlockData(replacementData(original.getBlockData(), next));
            FoliaCompat.assertOwnedByCurrentRegion(block, plugin);
            attempted = true;
            if (!replacement.update(true, false)) throw new IllegalStateException("snapshot update rejected");
            return Result.CHANGED;
        } catch (RuntimeException failure) {
            if (attempted && original != null) restore(original);
            plugin.getLogger().warning("CopperBack could not convert " + block.getType()
                    + ": " + failure.getMessage());
            return Result.FAILED;
        }
    }

    private BlockData replacementData(BlockData original, Material next) {
        String serialized = original.getAsString();
        int properties = serialized.indexOf('[');
        String suffix = properties < 0 ? "" : serialized.substring(properties);
        return parse.apply(next.getKey().toString() + suffix);
    }

    private void restore(BlockState original) {
        try {
            FoliaCompat.assertOwnedByCurrentRegion(original.getBlock(), plugin);
            if (!original.update(true, false)) throw new IllegalStateException("rollback update rejected");
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("CopperBack rollback failed for " + original.getType()
                    + ": " + failure.getMessage() + ". Check this block before using it again.");
        }
    }
}
