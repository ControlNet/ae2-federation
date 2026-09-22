package space.controlnet.ae2federation.neoforge.network;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.observability.ObservationSnapshotSink;
import space.controlnet.ae2federation.observability.ObservationDeltaSink;
import space.controlnet.ae2federation.observability.ObservationSessionLifecycleSink;
import space.controlnet.ae2federation.observability.state.ObservationClientStates;

public final class ObservationPayloads {
    private static final ObservationClientStates CLIENT_STATES = new ObservationClientStates();

    private ObservationPayloads() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ObservationPayloads::registerPayloads);
        ObservationSnapshotSink.register((player, snapshot) ->
                PacketDistributor.sendToPlayer(player, new FabricStateSnapshotPayload(snapshot)));
        ObservationDeltaSink.register((player, delta) ->
                PacketDistributor.sendToPlayer(player, new FabricStateDeltaPayload(delta)));
        ObservationSessionLifecycleSink.register(
                (player, session) -> PacketDistributor.sendToPlayer(player,
                        new ObservationSessionPayload(ObservationSessionPayload.Operation.OPEN, session)),
                (player, session) -> PacketDistributor.sendToPlayer(player,
                        new ObservationSessionPayload(ObservationSessionPayload.Operation.CLOSE, session)));
    }

    public static ObservationClientStates clientStates() {
        return CLIENT_STATES;
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(ObservationSessionPayload.TYPE,
                ObservationSessionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    switch (payload.operation()) {
                        case OPEN -> CLIENT_STATES.open(payload.session());
                        case CLOSE -> CLIENT_STATES.close(payload.session());
                    }
                }));
        event.registrar("1").playToClient(FabricStateSnapshotPayload.TYPE,
                FabricStateSnapshotPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CLIENT_STATES.apply(payload.envelope())));
        event.registrar("1").playToClient(FabricStateDeltaPayload.TYPE,
                FabricStateDeltaPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> CLIENT_STATES.apply(payload.envelope())));
    }
}
