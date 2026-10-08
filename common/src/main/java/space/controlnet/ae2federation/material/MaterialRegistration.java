package space.controlnet.ae2federation.material;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Crafting materials: Nexus Cores, crafted from redstone and Ender Dust, are pressed with Ender Dust and Printed Silicon
 * into the Nexus Processor that goes into the devices.
 */
public final class MaterialRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");

    public static final DeferredItem<Item> NEXUS_CORE = ITEMS.registerSimpleItem("nexus_core");
    public static final DeferredItem<Item> NEXUS_PROCESSOR = ITEMS.registerSimpleItem("nexus_processor");
    public static final DeferredItem<Item> PRINTED_NEXUS_PROCESSOR = ITEMS.registerSimpleItem("printed_nexus_processor");

    private MaterialRegistration() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
