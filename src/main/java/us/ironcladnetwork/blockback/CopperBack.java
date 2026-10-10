package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** One synchronous, permission-gated copper gesture on the interaction's region thread. */
final class CopperBack {
    enum Result { PASS_THROUGH, CONSUMED, CHANGED, FAILED }

    private static final Map<Material, Material> NEXT = new EnumMap<>(Material.class);
    private static final Set<Material> COPPER = EnumSet.noneOf(Material.class);

    static {
        for (Material exposed : Material.values()) {
            if (!exposed.name().startsWith("EXPOSED_") || !exposed.isBlock()) continue;
            String base = exposed.name().substring("EXPOSED_".length());
            // Copper block has a legacy base name; lightning rods have no COPPER token.
            if (!base.contains("COPPER") && !base.equals("LIGHTNING_ROD")) continue;
            addFamily(base.equals("COPPER") ? "COPPER_BLOCK" : base, exposed,
                    Material.getMaterial("WEATHERED_" + base), Material.getMaterial("OXIDIZED_" + base));
        }
    }

    private static void addFamily(String base, Material exposed, Material weathered, Material oxidized) {
        Material[] stages = {Material.getMaterial(base), exposed, weathered, oxidized};
        for (Material stage : stages) if (stage == null || !stage.isBlock()) return;
        for (int i = 0; i < stages.length; i++) {
            COPPER.add(stages[i]);
            Material waxed = Material.getMaterial("WAXED_" + stages[i].name());
            if (waxed != null && waxed.isBlock()) COPPER.add(waxed);
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
        }, Bukkit::createBlockData, CopperBack::playSound);
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
        if (event.getAction() != Action.LEFT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || !ownsGesture(player, block, event.getItem())) return Result.PASS_THROUGH;

        // Even a vanilla no-op prediction is conservatively respected: it is indistinguishable
        // from another plugin's denial. Never turn an existing DENY into ALLOW.
        if (event.useInteractedBlock() == Event.Result.DENY
                || event.useItemInHand() == Event.Result.DENY) return Result.PASS_THROUGH;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        Material next = NEXT.get(block.getType());
        if (next == null) return Result.CONSUMED;
        Result result = convert(block, next);
        if (result == Result.CHANGED) {
            try { changed.accept(player); }
            catch (RuntimeException failure) { plugin.getLogger().warning("Could not play CopperBack sound."); }
        }
        return result;
    }

    void guardBreak(BlockBreakEvent event) {
        // Mining started before the player sneaked can finish without another interact event.
        // Guard only this owned gesture, and never advance oxidation from a break event.
        if (!event.isCancelled() && ownsGesture(event.getPlayer(), event.getBlock(),
                event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    private boolean ownsGesture(Player player, Block block, ItemStack item) {
        return block != null && isAxe(item) && player.isSneaking()
                && player.hasPermission("blockback.copper") && enabled.test(player)
                && COPPER.contains(block.getType());
    }

    private static boolean isAxe(ItemStack item) {
        return item != null && EventListener.isAxe(item.getType());
    }

    private Result convert(Block block, Material next) {
        List<BlockState> originals = new ArrayList<>(2);
        List<BlockState> replacements = new ArrayList<>(2);
        int attempted = 0;
        try {
            prepare(block, next, originals, replacements);
            Block partner = pairedBlock(block, originals.getFirst().getBlockData());
            if (partner != null) prepare(partner, next, originals, replacements);
            for (BlockState replacement : replacements) {
                FoliaCompat.assertOwnedByCurrentRegion(replacement.getBlock(), plugin);
                attempted++;
                if (!replacement.update(true, false)
                        || !sameData(replacement.getBlock().getBlockData(), replacement.getBlockData())) {
                    throw new IllegalStateException("snapshot update rejected or failed readback");
                }
            }
            return Result.CHANGED;
        } catch (RuntimeException failure) {
            // A failed update may already have changed a block. Restore all attempted snapshots.
            for (int i = attempted - 1; i >= 0; i--) restore(originals.get(i));
            plugin.getLogger().warning("CopperBack could not convert " + block.getType()
                    + ": " + failure.getMessage());
            return Result.FAILED;
        }
    }

    private void prepare(Block block, Material next, List<BlockState> originals, List<BlockState> replacements) {
        BlockState original = block.getState();
        BlockState replacement = block.getState();
        replacement.setBlockData(replacementData(original.getBlockData(), next));
        originals.add(original);
        replacements.add(replacement);
    }

    private Block pairedBlock(Block block, BlockData data) {
        if (data instanceof Door door) {
            Block partner = block.getRelative(door.getHalf() == Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
            if (partner == null || partner.getType() != block.getType()
                    || !(partner.getBlockData() instanceof Door other) || door.getHalf() == other.getHalf()
                    || !doorProperties(data).equals(doorProperties(other))) {
                throw new IllegalStateException("missing or mismatched copper door half");
            }
            return partner;
        }
        if (data instanceof Chest chest && chest.getType() != Chest.Type.SINGLE) {
            BlockFace direction = chestPartnerDirection(chest);
            // A paired chest can cross a chunk boundary. Never load a chunk to find its partner.
            if (!block.getWorld().isChunkLoaded((block.getX() + direction.getModX()) >> 4,
                    (block.getZ() + direction.getModZ()) >> 4)) {
                throw new IllegalStateException("copper chest partner chunk is not loaded");
            }
            Block partner = block.getRelative(direction);
            if (partner == null || partner.getType() != block.getType()
                    || !(partner.getBlockData() instanceof Chest other)
                    || other.getType() == Chest.Type.SINGLE || other.getType() == chest.getType()
                    || other.getFacing() != chest.getFacing()) {
                throw new IllegalStateException("missing or mismatched copper chest half");
            }
            return partner;
        }
        return null;
    }

    private static String doorProperties(BlockData data) {
        return data.getAsString().replace("half=lower", "half=paired").replace("half=upper", "half=paired");
    }

    private static BlockFace chestPartnerDirection(Chest chest) {
        BlockFace clockwise = switch (chest.getFacing()) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> throw new IllegalArgumentException("invalid copper chest facing");
        };
        return chest.getType() == Chest.Type.LEFT ? clockwise : clockwise.getOppositeFace();
    }

    private static boolean sameData(BlockData first, BlockData second) {
        return first.getAsString().equals(second.getAsString());
    }

    private static void playSound(Player player) {
        SoundConfig config = SoundConfig.getInstance();
        if (config == null) return;
        SoundConfig.SoundSettings settings = config.getCopperBackSettings();
        if (settings.enabled) player.playSound(player.getLocation(), settings.sound, settings.category,
                settings.volume, settings.pitch);
    }

    private BlockData replacementData(BlockData original, Material next) {
        String serialized = original.getAsString();
        int properties = serialized.indexOf('[');
        String suffix = properties < 0 ? "" : serialized.substring(properties);
        BlockData parsed = parse.apply(next.getKey().toString() + suffix);
        if (parsed.getMaterial() != next) throw new IllegalArgumentException("unexpected parsed material");
        return parsed;
    }

    private void restore(BlockState original) {
        try {
            FoliaCompat.assertOwnedByCurrentRegion(original.getBlock(), plugin);
            if (!original.update(true, false)
                    || !sameData(original.getBlock().getBlockData(), original.getBlockData())) {
                throw new IllegalStateException("rollback update rejected or failed readback");
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("CopperBack rollback failed for " + original.getType()
                    + ": " + failure.getMessage() + ". Check this block before using it again.");
        }
    }
}
