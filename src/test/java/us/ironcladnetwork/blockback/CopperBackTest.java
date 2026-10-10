package us.ironcladnetwork.blockback;

import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.Registry;
import org.bukkit.UnsafeValues;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Real event objects and production handlers; block snapshots are API-contract fakes, not a server. */
class CopperBackTest {
    @Test void commandPermissionToggleAndStatusUseDurablePlayerPreferences(@TempDir Path directory) throws Exception {
        Fixture f = new Fixture("COPPER_BLOCK");
        PlayerDataManager settings = new PlayerDataManager(filePlugin(directory), false);
        CommandManager commands = new CommandManager(() -> settings);
        f.permitted = false;
        assertTrue(commands.onCommand(f.player, command("copperback"), "copperback", new String[0]));
        assertFalse(settings.isCopperBackEnabled(f.player));
        assertTrue(f.messages.stream().anyMatch(message -> message.contains("do not have permission")));
        f.permitted = true;
        commands.onCommand(f.player, command("copperback"), "copperback", new String[0]);
        assertTrue(settings.isCopperBackEnabled(f.player));
        commands.onCommand(f.player, command("blockback"), "blockback", new String[0]);
        assertTrue(f.messages.stream().anyMatch(message -> message.contains("CopperBack:") && message.contains("Enabled")));
        assertTrue(f.messages.stream().anyMatch(message -> message.contains("/copperback")));
        settings.reloadConfig();
        assertTrue(settings.isCopperBackEnabled(f.player));
        commands.onCommand(f.player, command("copperback"), "copperback", new String[0]);
        settings.removeFromCache(f.player);
        assertFalse(settings.isCopperBackEnabled(f.player));
        assertFalse(new PlayerDataManager(filePlugin(directory), false).isCopperBackEnabled(f.player));
    }

    @Test void oldAndNewYamlAndCacheMissesPreserveEveryFeature(@TempDir Path directory) throws Exception {
        Fixture f = new Fixture("COPPER_BLOCK");
        Path file = directory.resolve("players.yml");
        for (String copper : new String[]{"", "  copperback: false\n", "  copperback: true\n"}) {
            Files.writeString(file, f.uuid + ":\n  name: CopperTester\n  barkback: false\n  pathback: true\n  farmback: false\n" + copper);
            PlayerDataManager settings = new PlayerDataManager(filePlugin(directory), false);
            assertEquals(copper.contains("true"), settings.isCopperBackEnabled(f.player));
            assertOldPreferences(settings, f.player);
            settings.clearCache();
            // Direct setter after eviction must load other saved features, not recreate their defaults.
            settings.setCopperBack(f.player, true);
            assertOldPreferences(settings, f.player);
            assertTrue(settings.isCopperBackEnabled(f.player));
            settings.reloadConfig();
            assertTrue(settings.isCopperBackEnabled(f.player));
            assertOldPreferences(settings, f.player);
            settings.setCopperBack(f.player, false);
            settings.clearCache();
            assertFalse(settings.isCopperBackEnabled(f.player));
            assertOldPreferences(settings, f.player);
            var emergency = PlayerDataManager.class.getDeclaredMethod("getEmergencyDefaults", Player.class);
            emergency.setAccessible(true);
            assertFalse(((PlayerDataManager.PlayerSettings) emergency.invoke(settings, f.player)).copperback);
        }
        Fixture fresh = new Fixture("COPPER_BLOCK");
        PlayerDataManager settings = new PlayerDataManager(filePlugin(directory), false);
        assertFalse(settings.isCopperBackEnabled(fresh.player));
        assertFalse(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile()).getBoolean(fresh.uuid + ".copperback", true));
    }

    static void assertOldPreferences(PlayerDataManager settings, Player player) {
        assertFalse(settings.isBarkBackEnabled(player));
        assertTrue(settings.isPathBackEnabled(player));
        assertFalse(settings.isFarmBackEnabled(player));
    }

    static Command command(String name) {
        return new Command(name) {
            @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
        };
    }

    @Test void eachHandCombinationAdvancesOncePerGestureUntilTerminal() {
        for (String hands : new String[]{"main", "off", "both"}) {
            Fixture f = new Fixture("COPPER_BLOCK");
            if (hands.equals("off")) f.main = Material.STICK;
            if (!hands.equals("main")) f.off = Material.IRON_AXE;
            for (Material expected : new Material[]{Material.EXPOSED_COPPER, Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER, Material.OXIDIZED_COPPER}) {
                f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
                PlayerInteractEvent offhand = f.click(EquipmentSlot.OFF_HAND);
                f.listener.onCopperBlockClick(offhand);
                assertEquals(expected, f.block.type(), hands);
                if (!hands.equals("main")) assertConsumed(offhand);
            }
            assertEquals(3, f.block.writes, hands);
            assertEquals(3, f.sounds.get(), hands);
        }
    }

    @Test void ordinaryClicksLeaveBothHandsAndAllCopperVanillaActionsUntouched() {
        for (String material : new String[]{"COPPER_BLOCK", "EXPOSED_COPPER", "WAXED_COPPER_BLOCK", "COPPER_CHEST", "OXIDIZED_COPPER_GOLEM_STATUE"}) {
            if (Material.getMaterial(material) == null) continue;
            Fixture f = new Fixture(material);
            f.off = Material.IRON_AXE;
            f.sneaking = false;
            for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                PlayerInteractEvent event = f.click(hand);
                Event.Result blockUse = event.useInteractedBlock(), itemUse = event.useItemInHand();
                f.listener.onCopperBlockClick(event);
                assertEquals(blockUse, event.useInteractedBlock());
                assertEquals(itemUse, event.useItemInHand());
                assertEquals(0, f.block.writes);
            }
        }
    }

    @Test void successfulBooleanWithoutActualConversionIsNotSuccess() {
        Fixture f = new Fixture("COPPER_BLOCK");
        f.block.ignoreNextUpdate = true;
        f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
        assertEquals(Material.COPPER_BLOCK, f.block.type());
        assertEquals(0, f.sounds.get());
    }

    @Test void existingSoundFileKeepsSettingsAndLoadsCopperDefaults(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("sounds.yml");
        Files.writeString(file, "barkback:\n  enabled: false\n  volume: 0.25\ncopperback:\n  enabled: false\n  volume: 0.4\n  pitch: 1.5\n");
        String before = Files.readString(file);
        SoundConfig sounds = new SoundConfig(filePlugin(directory));
        assertFalse(sounds.getBarkBackSettings().enabled);
        assertEquals(0.25f, sounds.getBarkBackSettings().volume);
        assertFalse(sounds.getCopperBackSettings().enabled);
        assertEquals(0.4f, sounds.getCopperBackSettings().volume);
        assertEquals(1.5f, sounds.getCopperBackSettings().pitch);
        assertEquals(before, Files.readString(file));
        Files.writeString(file, "barkback:\n  enabled: false\n");
        sounds.reloadConfig();
        assertTrue(sounds.getCopperBackSettings().enabled);
        assertNotNull(sounds.getCopperBackSettings().sound);
        assertFalse(sounds.getBarkBackSettings().enabled);
        Path fresh = directory.resolve("fresh");
        SoundConfig defaults = new SoundConfig(filePlugin(fresh));
        assertTrue(Files.readString(fresh.resolve("sounds.yml")).contains("copperback:"));
        assertTrue(defaults.getCopperBackSettings().enabled);
    }

    static Plugin filePlugin(Path directory) {
        return proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getDataFolder" -> directory.toFile();
            case "getLogger" -> Logger.getLogger("CopperBackTest");
            case "getServer" -> Bukkit.getServer();
            default -> defaultValue(m.getReturnType());
        });
    }

    @BeforeAll static void installMinimalRegistry() {
        // Recent Spigot Material.isBlock delegates to a server registry, even in the 1.21.1 cache.
        // This supplies only API objects, not Minecraft block parsing or world behavior.
        Bukkit.setServer(proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getLogger" -> Logger.getLogger("CopperBackTest");
            case "getName", "getVersion", "getBukkitVersion" -> "CopperBack API-contract fake";
            case "getUnsafe" -> proxy(UnsafeValues.class, (unsafe, method, args) ->
                    method.getName().equals("get") ? ((Registry<?>) args[0]).get((NamespacedKey) args[1])
                            : defaultValue(method.getReturnType()));
            case "getScheduler" -> proxy(BukkitScheduler.class, (scheduler, method, args) -> {
                if (method.getName().equals("runTaskAsynchronously")) ((Runnable) args[1]).run();
                return method.getReturnType() == BukkitTask.class
                        ? proxy(BukkitTask.class, (task, taskMethod, taskArgs) -> defaultValue(taskMethod.getReturnType()))
                        : defaultValue(method.getReturnType());
            });
            case "getRegistry" -> proxy(Registry.class, (r, method, args) -> {
                if (!method.getName().equals("get")) return defaultValue(method.getReturnType());
                Class<?> entryType = (Class<?>) a[0];
                if (!entryType.isInterface()) return null;
                return proxy(entryType, (entry, entryMethod, entryArgs) ->
                        entryMethod.getName().equals("getKey") ? args[0] : defaultValue(entryMethod.getReturnType()));
            });
            default -> defaultValue(m.getReturnType());
        }));
    }

    @Test void allIndependentExpectedFamiliesAdvanceThreeTimesAndStop() {
        String[] bases = {"COPPER_BLOCK", "CUT_COPPER", "CHISELED_COPPER", "COPPER_GRATE", "COPPER_BULB",
                "CUT_COPPER_SLAB", "CUT_COPPER_STAIRS", "COPPER_TRAPDOOR", "COPPER_DOOR", "COPPER_CHEST",
                "COPPER_BARS", "COPPER_CHAIN", "COPPER_LANTERN", "COPPER_GOLEM_STATUE", "LIGHTNING_ROD"};
        for (String base : bases) {
            String staged = base.equals("COPPER_BLOCK") ? "COPPER" : base;
            if (Material.getMaterial("EXPOSED_" + staged) == null) continue;
            String props = base.endsWith("DOOR") && !base.endsWith("TRAPDOOR")
                    ? "[facing=north,half=lower,hinge=left,open=true,powered=true]" : "";
            Fixture f = new Fixture(base, props);
            if (base.equals("COPPER_DOOR")) f.block.partner = new FakeBlock(f.block.serialized.replace("lower", "upper"));
            for (String stage : new String[]{"EXPOSED_", "WEATHERED_", "OXIDIZED_"}) {
                f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
                assertEquals(Material.valueOf(stage + staged), f.block.type(), base);
            }
            assertEquals(3, f.block.writes, base);
            assertEquals(3, f.sounds.get(), base);
            f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
            assertEquals(3, f.block.writes, base);
            for (String name : new String[]{base, "EXPOSED_" + staged, "WEATHERED_" + staged, "OXIDIZED_" + staged}) {
                Fixture wax = new Fixture("WAXED_" + name, props);
                PlayerInteractEvent event = wax.click(EquipmentSlot.HAND);
                wax.listener.onCopperBlockClick(event);
                assertConsumed(event);
                assertEquals(0, wax.block.writes, name);
            }
        }
    }

    @Test void completePropertySuffixIsPreservedForEveryBlockShape() {
        Map<String, String> examples = Map.ofEntries(
                Map.entry("CUT_COPPER_SLAB", "[type=top,waterlogged=true]"),
                Map.entry("CUT_COPPER_STAIRS", "[facing=west,half=top,shape=inner_left,waterlogged=true]"),
                Map.entry("COPPER_BULB", "[lit=true,powered=true]"),
                Map.entry("COPPER_TRAPDOOR", "[facing=east,half=top,open=true,powered=true,waterlogged=true]"),
                Map.entry("COPPER_CHAIN", "[axis=x,waterlogged=true]"),
                Map.entry("COPPER_LANTERN", "[hanging=true,waterlogged=true]"),
                Map.entry("COPPER_CHEST", "[facing=south,type=single,waterlogged=true]"),
                Map.entry("COPPER_GOLEM_STATUE", "[facing=west,copper_golem_pose=running]"),
                Map.entry("LIGHTNING_ROD", "[facing=down,powered=true,waterlogged=true]"));
        examples.forEach((base, properties) -> {
            if (Material.getMaterial("EXPOSED_" + base) == null) return;
            Fixture f = new Fixture(base, properties);
            f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
            assertEquals("minecraft:exposed_" + base.toLowerCase(java.util.Locale.ROOT) + properties, f.block.serialized);
        });
    }

    @Test void eitherDoorHalfUpdatesThePairAndSecondFailureRestoresBoth() {
        for (String half : new String[]{"lower", "upper"}) {
            Fixture f = new Fixture("COPPER_DOOR", "[facing=east,half=" + half + ",hinge=right,open=true,powered=true]");
            f.block.expectedRelative = half.equals("lower") ? BlockFace.UP : BlockFace.DOWN;
            f.block.partner = new FakeBlock(f.block.serialized.replace("half=" + half, "half=" + (half.equals("lower") ? "upper" : "lower")));
            String first = f.block.serialized, second = f.block.partner.serialized;
            f.block.partner.failNextUpdate = true;
            PlayerInteractEvent failed = f.click(EquipmentSlot.HAND);
            f.listener.onCopperBlockClick(failed);
            assertConsumed(failed);
            assertEquals(first, f.block.serialized);
            assertEquals(second, f.block.partner.serialized);
            assertEquals(0, f.sounds.get());
            f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
            assertEquals(first.replace("minecraft:copper", "minecraft:exposed_copper"), f.block.serialized);
            assertEquals(second.replace("minecraft:copper", "minecraft:exposed_copper"), f.block.partner.serialized);
            assertEquals(1, f.sounds.get());
        }
    }

    @Test void invalidDoorPartnersFailBeforeAnyWrite() {
        for (String partner : new String[]{null, "minecraft:copper_door[half=lower]", "minecraft:exposed_copper_door[half=upper]", "minecraft:stone"}) {
            Fixture f = new Fixture("COPPER_DOOR", "[half=lower]");
            f.block.partner = partner == null ? null : new FakeBlock(partner);
            PlayerInteractEvent event = f.click(EquipmentSlot.HAND);
            f.listener.onCopperBlockClick(event);
            assertConsumed(event);
            assertEquals(0, f.block.writes);
            if (f.block.partner != null) assertEquals(0, f.block.partner.writes);
        }
    }

    @Test void capturedTileSnapshotReappliesAllOpaqueDataAfterReplacement() {
        for (String material : new String[]{"COPPER_CHEST", "COPPER_GOLEM_STATUE"}) {
            if (Material.getMaterial(material) == null) continue;
            Fixture f = new Fixture(material);
            Map<String, Object> original = Map.of("inventory", Map.of(0, "diamond[name=Gift,pdc=opaque]", 26, "emerald*12"),
                    "name", "Named copper", "lock", "Secret", "loot", "example:loot/42", "pdc", Map.of("plugin:key", "opaque"));
            f.block.tile.putAll(original);
            f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
            assertEquals(Material.valueOf("EXPOSED_" + material), f.block.type());
            assertEquals(original, f.block.tile);
        }
    }

    @Test void doubleChestRetainsBothInventoriesAndRollsBackTogether() {
        if (Material.getMaterial("COPPER_CHEST") == null) return;
        Fixture f = new Fixture("COPPER_CHEST", "[facing=north,type=left,waterlogged=true]");
        f.block.partner = new FakeBlock("minecraft:copper_chest[facing=north,type=right,waterlogged=true]");
        f.block.tile.put("inventory", "left: named enchanted items");
        f.block.partner.tile.put("inventory", "right: other items");
        f.block.partner.failNextUpdate = true;
        f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
        assertEquals(Material.getMaterial("COPPER_CHEST"), f.block.type());
        assertEquals(Material.getMaterial("COPPER_CHEST"), f.block.partner.type());
        assertEquals("left: named enchanted items", f.block.tile.get("inventory"));
        assertEquals("right: other items", f.block.partner.tile.get("inventory"));
        assertEquals(0, f.sounds.get());
        f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
        assertEquals(Material.getMaterial("EXPOSED_COPPER_CHEST"), f.block.type());
        assertEquals(Material.getMaterial("EXPOSED_COPPER_CHEST"), f.block.partner.type());
        assertEquals("left: named enchanted items", f.block.tile.get("inventory"));
        assertEquals("right: other items", f.block.partner.tile.get("inventory"));
        assertEquals(1, f.sounds.get());
    }

    @Test void doubleChestFindsEachHalfInEveryFacingWithoutLoadingChunks() {
        if (Material.getMaterial("COPPER_CHEST") == null) return;
        Map<String, BlockFace> leftDirections = Map.of("north", BlockFace.EAST, "east", BlockFace.SOUTH,
                "south", BlockFace.WEST, "west", BlockFace.NORTH);
        leftDirections.forEach((facing, direction) -> {
            for (String type : new String[]{"left", "right"}) {
                Fixture f = new Fixture("COPPER_CHEST", "[facing=" + facing + ",type=" + type + ",waterlogged=false]");
                f.block.expectedRelative = type.equals("left") ? direction : direction.getOppositeFace();
                f.block.partner = new FakeBlock(f.block.serialized.replace("type=" + type,
                        "type=" + (type.equals("left") ? "right" : "left")));
                f.block.chunkLoaded = false;
                PlayerInteractEvent unloaded = f.click(EquipmentSlot.HAND);
                f.listener.onCopperBlockClick(unloaded);
                assertConsumed(unloaded);
                assertEquals(0, f.block.relativeCalls);
                assertEquals(0, f.block.writes);
                f.block.chunkLoaded = true;
                f.listener.onCopperBlockClick(f.click(EquipmentSlot.HAND));
                assertEquals(1, f.block.relativeCalls);
                assertEquals(Material.getMaterial("EXPOSED_COPPER_CHEST"), f.block.type());
                assertEquals(Material.getMaterial("EXPOSED_COPPER_CHEST"), f.block.partner.type());
            }
        });
    }

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
        final List<String> messages = new ArrayList<>();
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
            case "sendMessage" -> { if (a[0] instanceof String message) messages.add(message); yield null; }
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
        boolean ignoreNextUpdate;
        Map<String, Object> tile = new HashMap<>();
        FakeBlock partner;
        BlockFace expectedRelative;
        int relativeCalls;
        boolean chunkLoaded = true;
        final World world = proxy(World.class, (p, m, a) -> m.getName().equals("isChunkLoaded") ? chunkLoaded : defaultValue(m.getReturnType()));
        final Block block = proxy(Block.class, (p, m, a) -> switch (m.getName()) {
            case "getType" -> type();
            case "getBlockData" -> data(serialized);
            case "getState" -> snapshot();
            case "getRelative" -> {
                relativeCalls++;
                if (expectedRelative != null) assertEquals(expectedRelative, a[0]);
                yield partner == null ? null : partner.block;
            }
            case "getWorld" -> world;
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
                    if (ignoreNextUpdate) { ignoreNextUpdate = false; yield true; }
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
        Material material = material(serialized);
        Class<? extends BlockData> type = material.name().endsWith("_DOOR") ? Door.class
                : material.name().endsWith("_CHEST") ? Chest.class : BlockData.class;
        return proxy(type, (p, m, a) -> switch (m.getName()) {
            case "getAsString" -> serialized;
            case "getMaterial" -> material(serialized);
            case "clone" -> data(serialized);
            case "getHalf" -> serialized.contains("half=upper") ? Bisected.Half.TOP : Bisected.Half.BOTTOM;
            case "getType" -> serialized.contains("type=left") ? Chest.Type.LEFT
                    : serialized.contains("type=right") ? Chest.Type.RIGHT : Chest.Type.SINGLE;
            case "getFacing" -> {
                java.util.regex.Matcher facing = java.util.regex.Pattern.compile("facing=([a-z]+)").matcher(serialized);
                yield facing.find() ? BlockFace.valueOf(facing.group(1).toUpperCase(java.util.Locale.ROOT)) : BlockFace.NORTH;
            }
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
