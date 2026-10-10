package space.controlnet.ae2federation.material;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Crafting materials, made the way AE2 makes its processors and cores: the Nexus Press prints an Ender Pearl into a
 * Printed Nexus Circuit, pressed with redstone and Printed Silicon into a Nexus Processor, crafted into the Nexus Cores
 * that go into the devices.
 */
public final class MaterialRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");

    public static final DeferredItem<Item> NEXUS_CORE = ITEMS.registerSimpleItem("nexus_core");
    public static final DeferredItem<Item> NEXUS_PROCESSOR = ITEMS.registerSimpleItem("nexus_processor");
    public static final DeferredItem<Item> NEXUS_PROCESSOR_PRESS = ITEMS.registerSimpleItem("nexus_processor_press");
    public static final DeferredItem<Item> PRINTED_NEXUS_CIRCUIT = ITEMS.registerSimpleItem("printed_nexus_circuit");

    private MaterialRegistration() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
