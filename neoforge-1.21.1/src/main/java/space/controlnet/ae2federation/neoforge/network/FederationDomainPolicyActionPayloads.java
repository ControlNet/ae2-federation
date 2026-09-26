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
        FederationDomainPolicyActionSink.registerReturn(containerId ->
                PacketDistributor.sendToServer(new ProviderMenuNavigationPayload(containerId, true)));
        FederationDomainPolicyActionSink.register((request, requestId) ->
                PacketDistributor.sendToServer(new FederationDomainPolicyActionPayload(request, requestId)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(ProviderMenuNavigationPayload.TYPE, ProviderMenuNavigationPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) payload.handle(player);
                }));
        event.registrar("2").playToClient(FederationDomainPolicyReplyPayload.TYPE, FederationDomainPolicyReplyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> FederationDomainPolicyMenu.acceptReply(context.player(),
                        payload.containerId(), payload.menuNonce(), payload.requestId(), payload.sequence(), payload.result())));
        event.registrar("2").playToServer(FederationDomainPolicyActionPayload.TYPE, FederationDomainPolicyActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        var result = FederationDomainPolicyActionPayloads.handle(player, payload);
                        var request = payload.request();
                        PacketDistributor.sendToPlayer(player, new FederationDomainPolicyReplyPayload(request.containerId(),
                                request.menuNonce(), payload.requestId(), request.menuSequence(), result));
                    }
                }));
    }

    public static FederationDomainPolicyActionResult handle(ServerPlayer player, FederationDomainPolicyActionPayload payload) {
        return FederationDomainPolicyMenu.dispatch(player, payload.request());
    }
}
