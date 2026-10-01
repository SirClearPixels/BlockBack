package us.ironcladnetwork.blockback;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WoodCompatibilityTest {
    @Test void everyAvailableStrippedBlockHasItsRestoration() throws Exception {
        Field field = EventListener.class.getDeclaredField("STRIPPED_TO_UNSTRIPPED");
        field.setAccessible(true);
        Map<?, ?> mappings = (Map<?, ?>) field.get(null);
        for (Material stripped : Material.values()) {
            if (!stripped.name().startsWith("STRIPPED_")) continue;
            Material original = Material.getMaterial(stripped.name().substring(9));
            if (original != null) assertEquals(original, mappings.get(stripped), stripped.name());
        }
        assertEquals(Material.OAK_LOG, mappings.get(Material.STRIPPED_OAK_LOG));
        if (Material.getMaterial("POPLAR_LOG") != null) {
            assertEquals(Material.getMaterial("POPLAR_LOG"), mappings.get(Material.getMaterial("STRIPPED_POPLAR_LOG")));
            assertEquals(Material.getMaterial("POPLAR_WOOD"), mappings.get(Material.getMaterial("STRIPPED_POPLAR_WOOD")));
        }
    }
}
