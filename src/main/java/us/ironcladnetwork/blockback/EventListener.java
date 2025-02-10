package us.ironcladnetwork.blockback;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public class EventListener implements Listener {

    private static final EnumSet<Material> AXES = EnumSet.of(
            Material.WOODEN_AXE, Material.STONE_AXE, Material.GOLDEN_AXE,
            Material.IRON_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private static final EnumSet<Material> SHOVELS = EnumSet.of(
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
    );

    private static final EnumSet<Material> HOES = EnumSet.of(
            Material.WOODEN_HOE, Material.STONE_HOE, Material.GOLDEN_HOE,
            Material.IRON_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
    );

    private static final Map<Material, Material> STRIPPED_TO_UNSTRIPPED = new HashMap<>();

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
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent e) {
        // Only proceed if right-click on block
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = e.getClickedBlock();
        Player player = e.getPlayer();
        ItemStack item = e.getItem();

        if (block == null || item == null) return;

        // --------------------------
        // 1) BarkBack (Stripped Logs)
        // --------------------------
        if (CommandManager.isBarkBackEnabled(player)
                && AXES.contains(item.getType())
                && block.getBlockData() instanceof Orientable) {

            Material unstrippedMaterial = STRIPPED_TO_UNSTRIPPED.get(block.getType());
            if (unstrippedMaterial != null) {
                Orientable orientable = (Orientable) block.getBlockData();
                Axis axis = orientable.getAxis();

                // Replace block but preserve axis
                setBlockWithAxis(block, unstrippedMaterial, axis);

                player.playSound(player.getLocation(),
                        Sound.ITEM_AXE_STRIP,
                        SoundCategory.BLOCKS, 1.0F, 0.1F);
                e.setCancelled(true);
                return;
            }
        }

        // -----------------------------
        // 2) PathBack (Path -> Dirt)
        // -----------------------------
        if (CommandManager.isPathBackEnabled(player)
                && SHOVELS.contains(item.getType())
                && block.getType() == Material.DIRT_PATH) {

            block.setType(Material.DIRT);
            player.playSound(player.getLocation(),
                    Sound.ITEM_SHOVEL_FLATTEN,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            e.setCancelled(true);
            return;
        }

        // -----------------------------
        // 3) FarmBack (Farmland -> Dirt)
        // -----------------------------
        if (CommandManager.isFarmBackEnabled(player)
                && HOES.contains(item.getType())
                && block.getType() == Material.FARMLAND) {
            block.setType(Material.DIRT);
            player.playSound(player.getLocation(),
                    Sound.ITEM_HOE_TILL,
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            e.setCancelled(true);
            return;
        }
    }
    /**
     * Helper to preserve the axis of logs/hyphae-like blocks after changing type.
     */
    private void setBlockWithAxis(Block block, Material material, Axis axis) {
        block.setType(material);
        BlockData newData = block.getBlockData();
        if (newData instanceof Orientable) {
            Orientable orientable = (Orientable) newData;
            orientable.setAxis(axis);
            block.setBlockData(orientable);
        }
    }

}