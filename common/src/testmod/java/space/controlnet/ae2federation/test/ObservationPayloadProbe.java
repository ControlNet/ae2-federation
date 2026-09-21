package space.controlnet.ae2federation.test;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import space.controlnet.ae2federation.neoforge.network.FabricStateDeltaPayload;
import space.controlnet.ae2federation.neoforge.network.FabricStateSnapshotPayload;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;
import space.controlnet.ae2federation.observability.state.ObservationSnapshotEnvelope;

final class ObservationPayloadProbe {
    private ObservationPayloadProbe() {
    }

    static ObservationSnapshotEnvelope roundTrip(ObservationSnapshotEnvelope envelope) {
        var buffer = buffer();
        FabricStateSnapshotPayload.STREAM_CODEC.encode(buffer, new FabricStateSnapshotPayload(envelope));
        return FabricStateSnapshotPayload.STREAM_CODEC.decode(buffer).envelope();
    }

    static ObservationDeltaEnvelope roundTrip(ObservationDeltaEnvelope envelope) {
        var buffer = buffer();
        FabricStateDeltaPayload.STREAM_CODEC.encode(buffer, new FabricStateDeltaPayload(envelope));
        return FabricStateDeltaPayload.STREAM_CODEC.decode(buffer).envelope();
    }

    static byte[] encoded(ObservationSnapshotEnvelope envelope) {
        var buffer = buffer();
        FabricStateSnapshotPayload.STREAM_CODEC.encode(buffer, new FabricStateSnapshotPayload(envelope));
        var bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return bytes;
    }

    static boolean rejectsTrailing(ObservationDeltaEnvelope envelope) {
        var buffer = buffer();
        FabricStateDeltaPayload.STREAM_CODEC.encode(buffer, new FabricStateDeltaPayload(envelope));
        buffer.writeByte(1);
        try {
            FabricStateDeltaPayload.STREAM_CODEC.decode(buffer);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }
}
