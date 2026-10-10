package us.ironcladnetwork.blockback;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Real event objects and production handlers; block snapshots are API-contract fakes, not a server. */
class CopperBackTest {
    @Test void freshCopperAdvancesThroughProductionListenerAndConsumesBothUses() {
        Fixture f = new Fixture("COPPER_BLOCK");
        PlayerInteractEvent event = f.click(EquipmentSlot.HAND);
        f.listener.onCopperBlockClick(event);
        assertEquals(Material.EXPOSED_COPPER, f.block.type());
        assertEquals(1, f.block.writes);
        assertConsumed(event);
    }

    @Test void ordinaryClicksAndIneligiblePlayersPassThroughUnchanged() {
        for (String reason : new String[]{"ordinary", "left", "tool", "permission", "preference", "other"}) {
            Fixture f = new Fixture(reason.equals("other") ? "STONE" : "EXPOSED_COPPER");
            if (reason.equals("ordinary")) f.sneaking = false;
            if (reason.equals("tool")) f.main = Material.DIAMOND_PICKAXE;
            if (reason.equals("permission")) f.permitted = false;
            if (reason.equals("preference")) f.enabled = false;
            PlayerInteractEvent event = f.event(reason.equals("left") ? Action.LEFT_CLICK_BLOCK : Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            Event.Result blockUse = event.useInteractedBlock(), itemUse = event.useItemInHand();
            f.listener.onCopperBlockClick(event);
            assertEquals(0, f.block.writes, reason);
            assertEquals(blockUse, event.useInteractedBlock(), reason);
            assertEquals(itemUse, event.useItemInHand(), reason);
        }
    }

    @Test void everyDenialPreventsMutationAndRemainsDenied() {
        for (String denial : new String[]{"cancel", "block", "item"}) {
            Fixture f = new Fixture("COPPER_BLOCK");
            PlayerInteractEvent event = f.click(EquipmentSlot.HAND);
            if (denial.equals("cancel")) event.setCancelled(true);
            if (denial.equals("block")) event.setUseInteractedBlock(Event.Result.DENY);
            if (denial.equals("item")) event.setUseItemInHand(Event.Result.DENY);
            Event.Result blockUse = event.useInteractedBlock(), itemUse = event.useItemInHand();
            f.listener.onCopperBlockClick(event);
            assertEquals(0, f.block.writes);
            assertEquals(blockUse, event.useInteractedBlock());
            assertEquals(itemUse, event.useItemInHand());
        }
    }

    @Test void terminalAndWaxedCopperConsumeWithoutMutationOrSound() {
        for (String name : new String[]{"OXIDIZED_COPPER", "WAXED_COPPER_BLOCK", "WAXED_EXPOSED_COPPER", "WAXED_WEATHERED_COPPER", "WAXED_OXIDIZED_COPPER"}) {
            Fixture f = new Fixture(name);
            PlayerInteractEvent event = f.click(EquipmentSlot.HAND);
            f.listener.onCopperBlockClick(event);
            assertConsumed(event);
            assertEquals(0, f.block.writes, name);
            assertEquals(0, f.sounds.get());
        }
    }

    @Test void parseAndUpdateFailuresConsumeAndRestoreWithoutSuccess() {
        for (boolean parseFailure : new boolean[]{true, false}) {
            Fixture f = new Fixture("EXPOSED_COPPER");
            f.parseFailure = parseFailure;
            f.block.failNextUpdate = !parseFailure;
            PlayerInteractEvent event = f.click(EquipmentSlot.HAND);
            f.listener.onCopperBlockClick(event);
            assertConsumed(event);
            assertEquals(Material.EXPOSED_COPPER, f.block.type());
            assertEquals(0, f.sounds.get());
        }
    }

    @Test void copperPreferenceDefaultsOffEvenInEmergencySettings() {
        assertFalse(new PlayerDataManager.PlayerSettings("New").copperback);
        PlayerDataManager.PlayerSettings old = new PlayerDataManager.PlayerSettings("Old", false, true, false);
        assertFalse(old.copperback);
        assertFalse(old.barkback);
        assertTrue(old.pathback);
        assertFalse(old.farmback);
    }

    static void assertConsumed(PlayerInteractEvent event) {
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(Event.Result.DENY, event.useItemInHand());
    }

    static final class Fixture {
        final FakeBlock block;
        boolean sneaking = true, permitted = true, enabled = true, parseFailure;
        Material main = Material.DIAMOND_AXE, off = Material.AIR;
        final UUID uuid = UUID.randomUUID();
        final AtomicInteger sounds = new AtomicInteger();
        final Plugin plugin = proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getLogger" -> Logger.getLogger("CopperBackTest");
            default -> defaultValue(m.getReturnType());
        });
        final PlayerInventory inventory = proxy(PlayerInventory.class, (p, m, a) -> switch (m.getName()) {
            case "getItemInMainHand" -> new ItemStack(main);
            case "getItemInOffHand" -> new ItemStack(off);
            default -> defaultValue(m.getReturnType());
        });
        final Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "isSneaking" -> sneaking;
            case "hasPermission" -> permitted;
            case "getInventory" -> inventory;
            case "getUniqueId" -> uuid;
            case "getName" -> "CopperTester";
            default -> defaultValue(m.getReturnType());
        });
        final EventListener listener;

        Fixture(String material) { this(material, ""); }
        Fixture(String material, String properties) {
            block = new FakeBlock("minecraft:" + material.toLowerCase(java.util.Locale.ROOT) + properties);
            CopperBack copper = new CopperBack(plugin, p -> enabled, serialized -> {
                if (parseFailure) throw new IllegalArgumentException("fake parser rejection");
                return data(serialized);
            }, p -> sounds.incrementAndGet());
            listener = new EventListener(plugin, copper);
        }

        PlayerInteractEvent click(EquipmentSlot hand) { return event(Action.RIGHT_CLICK_BLOCK, hand); }
        PlayerInteractEvent event(Action action, EquipmentSlot hand) {
            return new PlayerInteractEvent(player, action, new ItemStack(hand == EquipmentSlot.HAND ? main : off), block.block, BlockFace.UP, hand);
        }
    }

    static final class FakeBlock {
        String serialized;
        int writes;
        boolean failNextUpdate;
        Map<String, Object> tile = new HashMap<>();
        FakeBlock partner;
        final Block block = proxy(Block.class, (p, m, a) -> switch (m.getName()) {
            case "getType" -> type();
            case "getBlockData" -> data(serialized);
            case "getState" -> snapshot();
            case "getRelative" -> partner == null ? null : partner.block;
            case "getX", "getY", "getZ" -> 0;
            default -> defaultValue(m.getReturnType());
        });

        FakeBlock(String serialized) { this.serialized = serialized; }
        Material type() { return material(serialized); }
        BlockState snapshot() {
            String[] captured = {serialized};
            Map<String, Object> capturedTile = new HashMap<>(tile);
            return proxy(BlockState.class, (p, m, a) -> switch (m.getName()) {
                case "getBlock" -> block;
                case "getType" -> material(captured[0]);
                case "getBlockData" -> data(captured[0]);
                case "setBlockData" -> { captured[0] = ((BlockData) a[0]).getAsString(); yield null; }
                case "update" -> {
                    assertArrayEquals(new Object[]{true, false}, a, "snapshot updates must force the new material and suppress physics");
                    writes++;
                    // Simulate material replacement losing tile data; the captured snapshot must reapply it.
                    serialized = captured[0];
                    tile.clear();
                    if (failNextUpdate) { failNextUpdate = false; yield false; }
                    tile.putAll(capturedTile);
                    yield true;
                }
                default -> defaultValue(m.getReturnType());
            });
        }
    }

    static Material material(String serialized) {
        String key = serialized.split("\\[", 2)[0];
        return Material.valueOf(key.substring(key.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT));
    }

    static BlockData data(String serialized) {
        return proxy(BlockData.class, (p, m, a) -> switch (m.getName()) {
            case "getAsString" -> serialized;
            case "getMaterial" -> material(serialized);
            case "clone" -> data(serialized);
            default -> defaultValue(m.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getName().equals("toString")) return "Fake" + type.getSimpleName();
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return handler.invoke(p, m, a);
        });
    }

    static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
