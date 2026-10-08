package space.controlnet.ae2federation.p2p;

import appeng.api.features.P2PTunnelAttunement;
import appeng.api.parts.PartModels;
import appeng.api.parts.RegisterPartCapabilitiesEvent;
import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

public final class FederationP2PRegistration {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    public static final DeferredItem<PartItem<FederationP2PTunnelPart>> TUNNEL = ITEMS.register(
            "federation_p2p_tunnel", () -> new PartItem<>(new Item.Properties(), FederationP2PTunnelPart.class,
                    FederationP2PTunnelPart::new));

    private FederationP2PRegistration() {
    }

    public static void register(IEventBus modBus) {
        PartModels.registerModels(FederationP2PTunnelPart.FRONT_MODEL);
        ITEMS.register(modBus);
        modBus.addListener(RegisterPartCapabilitiesEvent.class, event -> event.register(
                FederationPortCapability.BLOCK, (part, side) -> part.federationPort(side),
                FederationP2PTunnelPart.class));
        // Like AE2's own tunnels, a P2P tunnel attuned with an item of the tunnel's tag becomes this tunnel; the tag
        // lists the Federation cable.
        modBus.addListener(FMLCommonSetupEvent.class,
                event -> event.enqueueWork(() -> P2PTunnelAttunement.registerAttunementTag(TUNNEL.get())));
    }
}
