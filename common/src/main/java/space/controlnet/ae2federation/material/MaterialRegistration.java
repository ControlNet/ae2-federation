package space.controlnet.ae2federation.material;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Crafting materials: the Federation Logic Processor, pressed from AE2's Logic Processor, goes into the devices. */
public final class MaterialRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");

    public static final DeferredItem<Item> FEDERATION_LOGIC_PROCESSOR = ITEMS.registerSimpleItem(
            "federation_logic_processor");

    private MaterialRegistration() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
