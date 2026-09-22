package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.client.menu.FabricPolicyActionSink;
import space.controlnet.ae2federation.client.menu.FabricPolicyActionResult;
import space.controlnet.ae2federation.client.menu.FabricPolicyMenu;

public final class FabricPolicyActionPayloads {
    private FabricPolicyActionPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(FabricPolicyActionPayloads::registerPayloads);
        FabricPolicyActionSink.register(request ->
                PacketDistributor.sendToServer(new FabricPolicyActionPayload(request)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(FabricPolicyActionPayload.TYPE, FabricPolicyActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        FabricPolicyActionPayloads.handle(player, payload);
                    }
                }));
    }

    public static FabricPolicyActionResult handle(ServerPlayer player, FabricPolicyActionPayload payload) {
        return FabricPolicyMenu.dispatch(player, payload.request());
    }
}
