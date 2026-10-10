package us.ironcladnetwork.blockback;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
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
        boolean permitted = true, ignoreWrite, failWrite;
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
        final EventListener listener = new EventListener(filePlugin(Path.of("target")));
        PlayerInteractEvent click() { return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, tool, block, BlockFace.UP, hand); }
    }
}
