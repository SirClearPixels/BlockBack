package us.ironcladnetwork.blockback;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

/** One normal tool use on the caller's owning region thread; no asynchronous inventory access. */
final class ToolDurability {
    enum Feature { BARKBACK, PATHBACK, FARMBACK, COPPERBACK }
    record Settings(boolean barkback, boolean pathback, boolean farmback, boolean copperback) {
        static final Settings DISABLED = new Settings(false, false, false, false);
        boolean enabled(Feature feature) {
            return switch (feature) {
                case BARKBACK -> barkback;
                case PATHBACK -> pathback;
                case FARMBACK -> farmback;
                case COPPERBACK -> copperback;
            };
        }
        static Settings load(FileConfiguration config, Consumer<String> warning) {
            return new Settings(read(config, "barkback", warning), read(config, "pathback", warning),
                    read(config, "farmback", warning), read(config, "copperback", warning));
        }
        private static boolean read(FileConfiguration config, String feature, Consumer<String> warning) {
            String key = "tool-durability." + feature;
            Object value = config.get(key);
            if (value == null) return false;
            if (value instanceof Boolean enabled) return enabled;
            warning.accept(key + " must be true or false; keeping durability disabled.");
            return false;
        }
    }

    private volatile Settings settings = Settings.DISABLED;
    private final DoubleSupplier random;
    private final Consumer<Event> dispatch;

    ToolDurability() {
        this(() -> ThreadLocalRandom.current().nextDouble(), event -> Bukkit.getPluginManager().callEvent(event));
    }
    ToolDurability(DoubleSupplier random, Consumer<Event> dispatch) {
        this.random = random;
        this.dispatch = dispatch;
    }
    void publish(Settings settings) { this.settings = java.util.Objects.requireNonNull(settings); }
    Settings settings() { return settings; }

    void charge(Player player, EquipmentSlot hand, ItemStack operated, Feature feature) {
        if (!settings.enabled(feature) || player.getGameMode() == GameMode.CREATIVE
                || operated == null || (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)) return;
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        ItemStack current = held(inventory, hand);
        if (current == null || current.getAmount() <= 0 || !current.equals(operated)
                || !(current.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) return;
        int maximum = meta.hasMaxDamage() ? meta.getMaxDamage() : current.getType().getMaxDurability();
        if (maximum <= 0 || meta.getDamage() < 0) return;
        int unbreaking = Math.max(0, meta.getEnchantLevel(Enchantment.UNBREAKING));
        if (random.getAsDouble() >= 1.0 / ((double) unbreaking + 1.0)) return;

        ItemStack before = current.clone();
        PlayerItemDamageEvent damageEvent = new PlayerItemDamageEvent(player, current, 1);
        dispatch.accept(damageEvent);
        if (damageEvent.isCancelled() || damageEvent.getDamage() <= 0
                || !unchanged(inventory, hand, slot, before)) return;
        // Fetch fresh metadata after callbacks, never overwrite a replacement with an old clone.
        current = held(inventory, hand);
        meta = (Damageable) current.getItemMeta();
        long damage = (long) meta.getDamage() + damageEvent.getDamage();
        meta.setDamage((int) Math.min(Integer.MAX_VALUE, damage));
        current.setItemMeta(meta);
        put(inventory, hand, current);
        if (damage < maximum) return;

        before = current.clone();
        dispatch.accept(new PlayerItemBreakEvent(player, current));
        if (!unchanged(inventory, hand, slot, before)) return;
        current = held(inventory, hand);
        if (current.getAmount() == 1) {
            put(inventory, hand, null);
        } else {
            current.setAmount(current.getAmount() - 1);
            meta = (Damageable) current.getItemMeta();
            meta.setDamage(0);
            current.setItemMeta(meta);
            put(inventory, hand, current);
        }
        player.playEffect(hand == EquipmentSlot.HAND ? EntityEffect.BREAK_EQUIPMENT_MAIN_HAND
                : EntityEffect.BREAK_EQUIPMENT_OFF_HAND);
    }

    private static boolean unchanged(PlayerInventory inventory, EquipmentSlot hand, int slot, ItemStack before) {
        return (hand != EquipmentSlot.HAND || inventory.getHeldItemSlot() == slot)
                && before.equals(held(inventory, hand));
    }
    private static ItemStack held(PlayerInventory inventory, EquipmentSlot hand) {
        return hand == EquipmentSlot.HAND ? inventory.getItemInMainHand() : inventory.getItemInOffHand();
    }
    private static void put(PlayerInventory inventory, EquipmentSlot hand, ItemStack item) {
        if (hand == EquipmentSlot.HAND) inventory.setItemInMainHand(item);
        else inventory.setItemInOffHand(item);
    }
}
