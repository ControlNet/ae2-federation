package space.controlnet.ae2federation;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;

public final class FederationCreativeTab {
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "ae2federation");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2federation.main"))
                    .icon(() -> RouterRegistration.ROUTER_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(RouterRegistration.ROUTER_ITEM.get());
                        output.accept(RouterRegistration.FEDERATION_CABLE_ITEM.get());
                        output.accept(ProcessingRegistration.PROVIDER_ITEM.get());
                        output.accept(ProcessingRegistration.ENDPOINT_ITEM.get());
                        output.accept(BridgeRegistration.BRIDGE.get());
                    })
                    .build());

    private FederationCreativeTab() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
