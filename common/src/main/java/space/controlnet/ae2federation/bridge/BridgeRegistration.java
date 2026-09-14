package space.controlnet.ae2federation.bridge;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class BridgeRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    public static final DeferredItem<PartItem<MultipartBridgePart>> MULTIPART_BRIDGE = ITEMS.register(
            "multipart_bridge", () -> new PartItem<>(new Item.Properties(), MultipartBridgePart.class,
                    MultipartBridgePart::new));

    private BridgeRegistration() {
    }

    public static void register(IEventBus modBus) {
        PartModels.registerModels(MultipartBridgePart.MODEL);
        ITEMS.register(modBus);
    }
}
