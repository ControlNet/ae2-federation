package space.controlnet.ae2federation.bridge;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;

public final class BridgeRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    public static final DeferredItem<PartItem<MultipartBridgePart>> BRIDGE = ITEMS.register(
            "bridge", () -> new PartItem<>(new Item.Properties(), MultipartBridgePart.class,
                    MultipartBridgePart::new));

    private BridgeRegistration() {
    }

    public static void register(IEventBus modBus) {
        PartModels.registerModels(MultipartBridgePart.MODEL);
        // AE2 cable buses save parts by item id; the pre-rename id keeps loading as the Bridge.
        ITEMS.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2federation",
                "multipart_bridge"), BRIDGE.getId());
        ITEMS.register(modBus);
    }
}
