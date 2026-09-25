package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionSink;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionResult;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;

public final class FederationDomainPolicyActionPayloads {
    private FederationDomainPolicyActionPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FederationDomainPolicyActionPayloads::registerPayloads);
        FederationDomainPolicyActionSink.register(request ->
                PacketDistributor.sendToServer(new FederationDomainPolicyActionPayload(request)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FederationDomainPolicyActionPayload.TYPE, FederationDomainPolicyActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FederationDomainPolicyActionPayloads.handle(player, payload);
                    }
                }));
    }

    public static FederationDomainPolicyActionResult handle(ServerPlayer player, FederationDomainPolicyActionPayload payload) {
        return FederationDomainPolicyMenu.dispatch(player, payload.request());
    }
}
