package us.ironcladnetwork.blockback;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import org.bukkit.plugin.Plugin;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles player interaction events to provide BlockBack functionality.
 * Restores blocks on right-click and advances copper oxidation on sneak-left-click,
 * based on player permissions and settings.
 */
public class EventListener implements Listener {

    private static final EnumSet<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.GOLDEN_AXE,
            Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );
    // COPPER_AXE was added in 1.21.9; resolved by name so the plugin still
    // compiles against the 1.21.1 API jar and runs on older 1.21.x servers.

    private static final EnumSet<Material> SHOVELS = EnumSet.of(
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
    );

    private static final EnumSet<Material> HOES = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.GOLDEN_HOE,
            Material.IRON_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );

    private static final Map<Material, Material> STRIPPED_TO_UNSTRIPPED = new HashMap<>();

    private final Plugin plugin;
    private final CopperBack copperBack;
    private final ToolDurability toolDurability;

    // bStats usage counters — incremented on each successful restoration.
    // Read via pollAndReset*Count(), which atomically returns the count and resets to 0.
    // Concurrent-safe for Folia: increments may originate from any region thread.
    private final AtomicLong barkbackCount = new AtomicLong();
    private final AtomicLong pathbackCount = new AtomicLong();
    private final AtomicLong farmbackCount = new AtomicLong();

    public EventListener(Plugin plugin) {
        this(plugin, new CopperBack(plugin));
    }

    EventListener(Plugin plugin, CopperBack copperBack) {
        this(plugin, copperBack, new ToolDurability());
    }

    EventListener(Plugin plugin, CopperBack copperBack, ToolDurability toolDurability) {
        this.plugin = plugin;
        this.copperBack = copperBack;
        this.toolDurability = toolDurability;
    }

    static boolean isAxe(Material material) {
        return AXES.contains(material);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCopperBlockClick(PlayerInteractEvent event) {
        ItemStack operated = event.getItem();
        if (copperBack.handle(event) == CopperBack.Result.CHANGED) {
            toolDurability.charge(event.getPlayer(), event.getHand(), operated, ToolDurability.Feature.COPPERBACK);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCopperBlockBreak(BlockBreakEvent event) {
        copperBack.guardBreak(event);
    }

    public long pollAndResetBarkbackCount() {
        return barkbackCount.getAndSet(0L);
    }

    public long pollAndResetPathbackCount() {
        return pathbackCount.getAndSet(0L);
    }

    public long pollAndResetFarmbackCount() {
        return farmbackCount.getAndSet(0L);
    }

    static {
        // --------------------------------------------------
        // 1) BarkBack Logic: Mapping Stripped -> Unstripped
        // --------------------------------------------------
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_OAK_LOG, Material.OAK_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_OAK_WOOD, Material.OAK_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_SPRUCE_WOOD, Material.SPRUCE_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_BIRCH_LOG, Material.BIRCH_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_BIRCH_WOOD, Material.BIRCH_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_JUNGLE_LOG, Material.JUNGLE_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_JUNGLE_WOOD, Material.JUNGLE_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_ACACIA_LOG, Material.ACACIA_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_ACACIA_WOOD, Material.ACACIA_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_DARK_OAK_LOG, Material.DARK_OAK_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_DARK_OAK_WOOD, Material.DARK_OAK_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_MANGROVE_LOG, Material.MANGROVE_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_MANGROVE_WOOD, Material.MANGROVE_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_CHERRY_LOG, Material.CHERRY_LOG);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_CHERRY_WOOD, Material.CHERRY_WOOD);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_CRIMSON_HYPHAE, Material.CRIMSON_HYPHAE);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_CRIMSON_STEM, Material.CRIMSON_STEM);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_WARPED_HYPHAE, Material.WARPED_HYPHAE);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_WARPED_STEM, Material.WARPED_STEM);
        STRIPPED_TO_UNSTRIPPED.put(Material.STRIPPED_BAMBOO_BLOCK, Material.BAMBOO_BLOCK);

        // Copper Axe strips logs in vanilla; mirror that here when available.
        Material copperAxe = Material.getMaterial("COPPER_AXE");
        if (copperAxe != null) {
            AXES.add(copperAxe);
        }

        // 1.21.3+ — Pale Oak (Pale Garden). Resolved reflectively because the
        // enum constants don't exist in the 1.21.1 API jar this plugin builds against.
        addStrippedMapping("STRIPPED_PALE_OAK_LOG", "PALE_OAK_LOG");
        addStrippedMapping("STRIPPED_PALE_OAK_WOOD", "PALE_OAK_WOOD");

        // Discover all wood sets supplied by the running server, including Poplar.
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.startsWith("STRIPPED_") && (name.endsWith("_LOG") || name.endsWith("_WOOD")
                    || name.endsWith("_STEM") || name.endsWith("_HYPHAE") || name.equals("STRIPPED_BAMBOO_BLOCK"))) {
                addStrippedMapping(name, name.substring("STRIPPED_".length()));
            }
        }
        addTool(SHOVELS, "COPPER_SHOVEL");
        addTool(HOES, "COPPER_HOE");
    }

    private static void addTool(EnumSet<Material> tools, String name) {
        Material material = Material.getMaterial(name);
        if (material != null) tools.add(material);
    }

    private static void addStrippedMapping(String strippedName, String unstrippedName) {
        Material stripped = Material.getMaterial(strippedName);
        Material unstripped = Material.getMaterial(unstrippedName);
        if (stripped != null && unstripped != null) {
            STRIPPED_TO_UNSTRIPPED.put(stripped, unstripped);
        }
    }

    /**
     * Handles player right-click events on blocks to provide BlockBack functionality.
     * Checks for appropriate tools, permissions, and block types to determine
     * if a block should be reverted.
     * 
     * @param e the player interact event
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockClick(PlayerInteractEvent e) {
        // Only proceed if right-click on block
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Predictive block-only denial has no provenance in Bukkit. Respect both channels
        // conservatively; never use isCancelled alone or turn a denied channel into ALLOW.
        if (e.useInteractedBlock() == Event.Result.DENY || e.useItemInHand() == Event.Result.DENY) return;

        Block block = e.getClickedBlock();
        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        if (block == null || item == null) return;
        
        // Cache instances to avoid multiple getInstance() calls
        PlayerDataManager playerData = PlayerDataManager.getInstance();
        SoundConfig soundConfig = SoundConfig.getInstance();
        
        // Defensive null checks - if instances are null, plugin wasn't initialized properly
        if (playerData == null || soundConfig == null) {
            return; // Silently fail to avoid spam in logs during event processing
        }
        
        // Cache item type to avoid multiple getType() calls and improve performance
        Material itemType = item.getType();

        // --------------------------
        // 1) BarkBack (Stripped Logs)
        // --------------------------
        if (player.hasPermission("blockback.bark")
                && playerData.isBarkBackEnabled(player)
                && AXES.contains(itemType)
                && block.getBlockData() instanceof Orientable) {

            Material unstrippedMaterial = STRIPPED_TO_UNSTRIPPED.get(block.getType());
            if (unstrippedMaterial != null) {
                Orientable orientable = (Orientable) block.getBlockData();
                Axis axis = orientable.getAxis();

                // Replace block but preserve axis
                if (axis != null) {
                    FoliaCompat.assertOwnedByCurrentRegion(block, plugin);
                    setBlockWithAxis(block, unstrippedMaterial, axis);
                } else {
                    // If axis is null, just set the block type without preserving orientation
                    FoliaCompat.assertOwnedByCurrentRegion(block, plugin);
                    block.setType(unstrippedMaterial);
                }

                if (block.getType() != unstrippedMaterial || (axis != null
                        && (!(block.getBlockData() instanceof Orientable readback) || readback.getAxis() != axis))) return;
                barkbackCount.incrementAndGet();
                e.setCancelled(true);
                toolDurability.charge(player, e.getHand(), item, ToolDurability.Feature.BARKBACK);

                // Sound failure must not undo the completed action or its durability use.
                playSound(player, soundConfig.getBarkBackSettings(), "BarkBack");
                return;
            }
        }

        // -----------------------------
        // 2) PathBack (Path -> Dirt)
        // -----------------------------
        if (player.hasPermission("blockback.path")
                && playerData.isPathBackEnabled(player)
                && SHOVELS.contains(itemType)
                && block.getType() == Material.DIRT_PATH) {

            FoliaCompat.assertOwnedByCurrentRegion(block, plugin);
            block.setType(Material.DIRT);
            if (block.getType() != Material.DIRT) return;
            pathbackCount.incrementAndGet();
            e.setCancelled(true);
            toolDurability.charge(player, e.getHand(), item, ToolDurability.Feature.PATHBACK);

            // Play configurable sound
            playSound(player, soundConfig.getPathBackSettings(), "PathBack");
            return;
        }

        // -----------------------------
        // 3) FarmBack (Farmland -> Dirt)
        // -----------------------------
        if (player.hasPermission("blockback.farm")
                && playerData.isFarmBackEnabled(player)
                && HOES.contains(itemType)
                && block.getType() == Material.FARMLAND) {

            FoliaCompat.assertOwnedByCurrentRegion(block, plugin);
            block.setType(Material.DIRT);
            if (block.getType() != Material.DIRT) return;
            farmbackCount.incrementAndGet();
            e.setCancelled(true);
            toolDurability.charge(player, e.getHand(), item, ToolDurability.Feature.FARMBACK);

            // Play configurable sound
            playSound(player, soundConfig.getFarmBackSettings(), "FarmBack");
            return;
        }
    }
    private void playSound(Player player, SoundConfig.SoundSettings settings, String feature) {
        if (!settings.enabled) return;
        try {
            player.playSound(player.getLocation(), settings.sound, settings.category, settings.volume, settings.pitch);
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Could not play " + feature + " sound.");
        }
    }

    /**
     * Helper to preserve the axis of logs/hyphae-like blocks after changing type.
     */
    private void setBlockWithAxis(Block block, Material material, Axis axis) {
        block.setType(material);
        BlockData newData = block.getBlockData();
        if (newData instanceof Orientable && axis != null) {
            Orientable orientable = (Orientable) newData;
            orientable.setAxis(axis);
            block.setBlockData(orientable);
        }
    }

    /**
     * Removes player from cache when they disconnect to prevent memory leaks.
     * This ensures the player cache doesn't grow indefinitely with offline players.
     * 
     * @param event the player quit event
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PlayerDataManager playerData = PlayerDataManager.getInstance();
        if (playerData != null) {
            playerData.removeFromCache(event.getPlayer());
        }
    }

}
