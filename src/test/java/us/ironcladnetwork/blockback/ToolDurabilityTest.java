package us.ironcladnetwork.blockback;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static us.ironcladnetwork.blockback.CopperBackTest.*;

/** Production events/handlers with API-contract fakes, not live Minecraft evidence. */
class ToolDurabilityTest {
    @BeforeAll static void registry() { CopperBackTest.installMinimalRegistry(); }
    @TempDir Path directory;
    Object previousPlayers, previousSounds;
    @BeforeEach void managers() throws Exception {
        Files.writeString(directory.resolve("sounds.yml"), "barkback:\n  enabled: false\npathback:\n  enabled: false\nfarmback:\n  enabled: false\n");
        previousPlayers = singleton(PlayerDataManager.class, new PlayerDataManager(filePlugin(directory), false));
        previousSounds = singleton(SoundConfig.class, new SoundConfig(filePlugin(directory)));
    }
    @AfterEach void restore() throws Exception {
        singleton(PlayerDataManager.class, previousPlayers);
        singleton(SoundConfig.class, previousSounds);
    }
    static Object singleton(Class<?> type, Object value) throws Exception {
        var field = type.getDeclaredField("instance"); field.setAccessible(true);
        Object old = field.get(null); field.set(null, value); return old;
    }
    @Test void enabledBarkBackWearsTheOperatedToolAfterRealRestoration() {
        Restoration f = new Restoration();
        f.listener.onBlockClick(f.click());
        assertEquals(Material.OAK_LOG, f.material);
        assertEquals(Axis.X, f.axis);
        assertEquals(1, f.tool.damage, "enabled successful BarkBack must use one durability");
    }
    @Test void missingDisabledAndReloadedSettingsAreIndependent() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> warnings = new ArrayList<>();
        assertEquals(ToolDurability.Settings.DISABLED, ToolDurability.Settings.load(config, warnings::add));
        for (ToolDurability.Feature feature : ToolDurability.Feature.values()) {
            config = new YamlConfiguration();
            config.set("tool-durability." + feature.name().toLowerCase(Locale.ROOT), true);
            var settings = ToolDurability.Settings.load(config, warnings::add);
            for (ToolDurability.Feature other : ToolDurability.Feature.values()) assertEquals(other == feature, settings.enabled(other));
        }
        config.set("tool-durability.barkback", "true");
        config.set("tool-durability.pathback", 1);
        config.set("tool-durability.farmback", List.of(true));
        assertEquals(new ToolDurability.Settings(false, false, false, true), ToolDurability.Settings.load(config, warnings::add));
        assertEquals(3, warnings.size());
        Restoration free = new Restoration();
        free.durability.publish(ToolDurability.Settings.DISABLED);
        free.listener.onBlockClick(free.click());
        assertEquals(Material.OAK_LOG, free.material); assertEquals(0, free.tool.damage);
        free.material = Material.STRIPPED_OAK_LOG;
        free.durability.publish(new ToolDurability.Settings(true, true, true, true));
        free.listener.onBlockClick(free.click());
        assertEquals(1, free.tool.damage);
    }
    @Test void creativeUnbreakableAndUnbreakingMatchOneNormalUse() {
        for (String reason : new String[]{"creative", "unbreakable", "kept", "worn"}) {
            Restoration f = new Restoration();
            f.tool.unbreaking = 3;
            if (reason.equals("creative")) f.mode = GameMode.CREATIVE;
            if (reason.equals("unbreakable")) f.tool.unbreakable = true;
            List<Event> events = new ArrayList<>();
            ToolDurability wear = new ToolDurability(() -> reason.equals("kept") ? 0.25 : 0.249, events::add);
            wear.publish(new ToolDurability.Settings(true, false, false, false));
            wear.charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
            assertEquals(reason.equals("worn") ? 1 : 0, f.tool.damage, reason);
            assertEquals(reason.equals("worn") ? 1 : 0, events.size(), reason);
        }
    }
    @Test void damageEventCancellationNonpositiveAndModifiedDamageAreHonored() {
        for (int damage : new int[]{-1, 0, 3}) {
            Restoration f = new Restoration();
            ToolDurability wear = enabledWear(event -> ((PlayerItemDamageEvent)event).setDamage(damage));
            wear.charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
            assertEquals(Math.max(0, damage), f.tool.damage);
            assertEquals("Kept metadata", f.tool.name);
            assertEquals(100, ((Damageable)f.tool.getItemMeta()).getMaxDamage());
        }
        Restoration f = new Restoration();
        enabledWear(event -> ((PlayerItemDamageEvent)event).setCancelled(true))
                .charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
        assertEquals(0, f.tool.damage);
    }
    @Test void breakUsesCustomMaximumOnceResetsRemainingStackAndHandlesOverflow() {
        for (int amount : new int[]{1, 3}) {
            Restoration f = new Restoration(); f.tool.damage = 99; f.tool.setAmount(amount);
            List<Event> events = new ArrayList<>();
            ToolDurability wear = enabledWear(event -> {
                events.add(event);
                if (event instanceof PlayerItemDamageEvent damage) damage.setDamage(Integer.MAX_VALUE);
                if (event instanceof PlayerItemBreakEvent broken) assertEquals(amount, broken.getBrokenItem().getAmount());
            });
            wear.charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
            assertEquals(2, events.size()); assertInstanceOf(PlayerItemBreakEvent.class, events.get(1));
            if (amount == 1) assertNull(f.main);
            else {assertEquals(2, f.main.getAmount()); assertEquals(0, ((Tool)f.main).damage); assertEquals("Kept metadata", ((Tool)f.main).name);}
            assertEquals(List.of(EntityEffect.BREAK_EQUIPMENT_MAIN_HAND), f.effects);
        }
    }
    @Test void damageCallbacksCannotOverwriteReplacementRemovalOrSelectedSlot() {
        for (String mutation : new String[]{"replace", "remove", "slot", "metadata", "amount"}) {
            Restoration f = new Restoration(); Tool replacement = new Tool(Material.IRON_SHOVEL);
            enabledWear(event -> {
                switch (mutation) {
                    case "replace" -> f.main = replacement;
                    case "remove" -> f.main = null;
                    case "slot" -> f.slot++;
                    case "metadata" -> f.tool.name = "Callback edit";
                    case "amount" -> f.tool.setAmount(2);
                }
            }).charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
            assertEquals(0, f.tool.damage, mutation);
            if (mutation.equals("replace")) assertSame(replacement, f.main);
            if (mutation.equals("remove")) assertNull(f.main);
            assertTrue(f.effects.isEmpty());
        }
    }
    @Test void breakCallbacksCannotOverwriteReplacementRemovalOrSelectedSlot() {
        for (String mutation : new String[]{"replace", "remove", "slot"}) {
            Restoration f = new Restoration(); f.tool.damage = 99;
            Tool replacement = new Tool(Material.IRON_SHOVEL);
            enabledWear(event -> {
                if (event instanceof PlayerItemBreakEvent) {
                    if (mutation.equals("replace")) f.main = replacement;
                    if (mutation.equals("remove")) f.main = null;
                    if (mutation.equals("slot")) f.slot++;
                }
            }).charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
            if (mutation.equals("replace")) assertSame(replacement, f.main);
            if (mutation.equals("remove")) assertNull(f.main);
            if (mutation.equals("slot")) assertEquals(1, f.tool.getAmount());
            assertTrue(f.effects.isEmpty());
        }
    }
    @Test void offhandChargeLeavesMainHandAloneIncludingBreakFeedback() {
        Restoration f = new Restoration(); f.off = f.tool; f.main = new Tool(Material.IRON_SHOVEL);
        f.hand = EquipmentSlot.OFF_HAND; ItemStack originalMain = f.main;
        f.listener.onBlockClick(f.click());
        assertEquals(1, f.tool.damage); assertSame(originalMain, f.main);
        f.tool.damage = 99;
        f.durability.charge(f.player, f.hand, f.tool, ToolDurability.Feature.BARKBACK);
        assertNull(f.off); assertSame(originalMain, f.main);
        assertEquals(List.of(EntityEffect.BREAK_EQUIPMENT_OFF_HAND), f.effects);
    }
    @Test void deniedChannelsFullCancellationAndPredictiveBlockDenialStayConservative() {
        for (Event.Result block : Event.Result.values()) for (Event.Result item : Event.Result.values()) {
            Restoration f = new Restoration(); PlayerInteractEvent event = f.click();
            event.setUseInteractedBlock(block); event.setUseItemInHand(item);
            f.listener.onBlockClick(event);
            boolean denied = block == Event.Result.DENY || item == Event.Result.DENY;
            assertEquals(denied ? Material.STRIPPED_OAK_LOG : Material.OAK_LOG, f.material);
            assertEquals(denied ? 0 : 1, f.tool.damage);
            if (denied) {assertEquals(block, event.useInteractedBlock()); assertEquals(item, event.useItemInHand());}
        }
        Restoration f = new Restoration(); PlayerInteractEvent event = f.click(); event.setCancelled(true);
        f.listener.onBlockClick(event); assertEquals(0, f.tool.damage);
    }
    @Test void unchangedThrownAndIneligibleBarkActionsHaveNoCharge() {
        for (String reason : new String[]{"permission", "preference", "tool", "action", "unchanged", "failed"}) {
            Restoration f = new Restoration();
            if (reason.equals("permission")) f.permitted = false;
            if (reason.equals("preference")) PlayerDataManager.getInstance().setBarkBack(f.player, false);
            if (reason.equals("tool")) f.tool.setType(Material.DIAMOND_PICKAXE);
            if (reason.equals("unchanged")) f.ignoreWrite = true;
            if (reason.equals("failed")) f.failWrite = true;
            PlayerInteractEvent event = reason.equals("action")
                    ? new PlayerInteractEvent(f.player, Action.LEFT_CLICK_BLOCK, f.tool, f.block, BlockFace.UP, f.hand) : f.click();
            if (f.failWrite) assertThrows(IllegalStateException.class, () -> f.listener.onBlockClick(event));
            else f.listener.onBlockClick(event);
            assertEquals(0, f.tool.damage, reason);
        }
    }
    @Test void disabledOrBrokenSoundDoesNotMakeSuccessfulBarkRestorationFree() {
        Restoration f = new Restoration(); f.failSound = true;
        // Existing fixture disables sounds. Reload enabled sound to exercise a failing callback.
        try {Files.writeString(directory.resolve("sounds.yml"), "barkback:\n  enabled: true\n");} catch(Exception failure) {throw new AssertionError(failure);}
        SoundConfig.getInstance().reloadConfig();
        PlayerInteractEvent event = f.click();
        assertDoesNotThrow(() -> f.listener.onBlockClick(event));
        assertEquals(1, f.tool.damage); assertEquals(Event.Result.DENY, event.useItemInHand());
    }
    static ToolDurability enabledWear(java.util.function.Consumer<Event> dispatch) {
        ToolDurability wear = new ToolDurability(() -> 0, dispatch);
        wear.publish(new ToolDurability.Settings(true, true, true, true)); return wear;
    }
    @Test void pathAndFarmChargeOnlyTheirMatchingSwitchAndActualHand() {
        for (boolean farm : new boolean[]{false, true}) for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            Restoration f = new Restoration(); f.material = farm ? Material.FARMLAND : Material.DIRT_PATH;
            f.tool.setType(farm ? Material.IRON_HOE : Material.IRON_SHOVEL); f.hand = hand;
            if (hand == EquipmentSlot.OFF_HAND) {f.off = f.tool; f.main = new Tool(Material.IRON_AXE);}
            ItemStack other = hand == EquipmentSlot.HAND ? f.off : f.main;
            f.durability.publish(new ToolDurability.Settings(true, !farm, farm, true));
            f.listener.onBlockClick(f.click());
            assertEquals(Material.DIRT, f.material); assertEquals(1, f.tool.damage);
            assertSame(other, hand == EquipmentSlot.HAND ? f.off : f.main);
            f.material = farm ? Material.FARMLAND : Material.DIRT_PATH;
            f.durability.publish(new ToolDurability.Settings(true, farm, !farm, true));
            f.listener.onBlockClick(f.click()); assertEquals(1, f.tool.damage);
        }
    }
    @Test void copperChangedDoorPairCostsOnceAndRollbacksCostNothing() {
        for (boolean failed : new boolean[]{false, true}) {
            CopperFixture f = new CopperFixture("COPPER_DOOR", "[facing=north,half=lower,hinge=left,open=true,powered=false]");
            f.block.partner = new FakeBlock(f.block.serialized.replace("lower", "upper"));
            f.block.partner.failNextUpdate = failed;
            f.listener.onCopperBlockClick(f.click(Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND));
            assertEquals(failed ? Material.COPPER_DOOR : Material.EXPOSED_COPPER_DOOR, f.block.type());
            assertEquals(failed ? Material.COPPER_DOOR : Material.EXPOSED_COPPER_DOOR, f.block.partner.type());
            assertEquals(failed ? 0 : 1, f.inventory.tool.damage);
        }
    }
    static final class CopperFixture {
        final Restoration inventory = new Restoration();
        final FakeBlock block;
        final EventListener listener;
        boolean enabled = true;
        CopperFixture(String material, String properties) {
            block = new FakeBlock("minecraft:" + material.toLowerCase(Locale.ROOT) + properties);
            var plugin = filePlugin(Path.of("target"));
            CopperBack copper = new CopperBack(plugin, player -> enabled, CopperBackTest::data, player -> {});
            inventory.durability.publish(new ToolDurability.Settings(true, true, true, true));
            listener = new EventListener(plugin, copper, inventory.durability);
        }
        PlayerInteractEvent click(Action action, EquipmentSlot hand) {
            return new PlayerInteractEvent(inventory.player, action, inventory.tool, block.block, BlockFace.UP, hand);
        }
    }
    static final class Tool extends ItemStack {
        int damage, max = 100, unbreaking;
        boolean unbreakable;
        String name = "Kept metadata";
        Tool(Material material) { super(material); }
        @Override public ItemMeta getItemMeta() {
            int[] value = {damage};
            return proxy(Damageable.class, (p,m,a) -> switch(m.getName()) {
                case "getDamage" -> value[0];
                case "setDamage" -> { value[0] = (int)a[0]; yield null; }
                case "hasMaxDamage" -> max > 0;
                case "getMaxDamage" -> max;
                case "isUnbreakable" -> unbreakable;
                case "getEnchantLevel" -> unbreaking;
                case "getDisplayName" -> name;
                default -> defaultValue(m.getReturnType());
            });
        }
        @Override public boolean setItemMeta(ItemMeta meta) {
            damage = ((Damageable)meta).getDamage(); name = meta.getDisplayName(); return true;
        }
        @Override public Tool clone() {
            Tool clone = new Tool(getType()); clone.damage = damage; clone.max = max;
            clone.unbreaking = unbreaking; clone.unbreakable = unbreakable; clone.name = name;
            clone.setAmount(getAmount()); return clone;
        }
        @Override public boolean equals(Object value) {
            return value instanceof Tool other && getType() == other.getType() && getAmount() == other.getAmount()
                    && damage == other.damage && max == other.max && unbreaking == other.unbreaking
                    && unbreakable == other.unbreakable && name.equals(other.name);
        }
        @Override public int hashCode() { return Objects.hash(getType(), damage, getAmount(), name); }
    }
    static final class Restoration {
        Material material = Material.STRIPPED_OAK_LOG;
        Axis axis = Axis.X;
        Tool tool = new Tool(Material.DIAMOND_AXE);
        ItemStack main = tool, off = new Tool(Material.IRON_AXE);
        EquipmentSlot hand = EquipmentSlot.HAND;
        int slot;
        boolean permitted = true, ignoreWrite, failWrite, failSound;
        final List<EntityEffect> effects = new ArrayList<>();
        GameMode mode = GameMode.SURVIVAL;
        final UUID uuid = UUID.randomUUID();
        final PlayerInventory inventory = proxy(PlayerInventory.class, (p,m,a) -> switch(m.getName()) {
            case "getHeldItemSlot" -> slot;
            case "getItemInMainHand" -> main;
            case "getItemInOffHand" -> off;
            case "setItemInMainHand" -> {main = (ItemStack)a[0]; yield null;}
            case "setItemInOffHand" -> {off = (ItemStack)a[0]; yield null;}
            default -> defaultValue(m.getReturnType());
        });
        final Player player = proxy(Player.class, (p,m,a) -> switch(m.getName()) {
            case "getInventory" -> inventory;
            case "getGameMode" -> mode;
            case "hasPermission" -> permitted;
            case "getUniqueId" -> uuid;
            case "getName" -> "DurabilityTester";
            case "isSneaking" -> true;
            case "playEffect" -> {effects.add((EntityEffect)a[0]); yield null;}
            case "playSound" -> {if(failSound) throw new IllegalStateException("sound failed"); yield null;}
            default -> defaultValue(m.getReturnType());
        });
        final Block block = proxy(Block.class, (p,m,a) -> switch(m.getName()) {
            case "getType" -> material;
            case "getBlockData" -> proxy(Orientable.class, (data,method,args) -> switch(method.getName()) {
                case "getAxis" -> axis;
                case "setAxis" -> {axis = (Axis)args[0]; yield null;}
                default -> defaultValue(method.getReturnType());
            });
            case "setType" -> {if(failWrite) throw new IllegalStateException("rejected write"); if(!ignoreWrite) material = (Material)a[0]; yield null;}
            default -> defaultValue(m.getReturnType());
        });
        final ToolDurability durability = new ToolDurability(() -> 0, event -> {});
        final EventListener listener;
        Restoration() {
            durability.publish(new ToolDurability.Settings(true, false, false, false));
            var plugin = filePlugin(Path.of("target"));
            listener = new EventListener(plugin, new CopperBack(plugin), durability);
        }
        PlayerInteractEvent click() { return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, tool, block, BlockFace.UP, hand); }
    }
}
