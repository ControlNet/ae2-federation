package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.state.ObservationSession;

public record ObservationSessionPayload(Operation operation, ObservationSession session) implements CustomPacketPayload {
    public static final Type<ObservationSessionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "observation_session"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ObservationSessionPayload> STREAM_CODEC =
            StreamCodec.ofMember(ObservationSessionPayload::encode, ObservationSessionPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        buffer.writeByte(operation.wireId);
        FabricStateSnapshotCodec.encodeSession(buffer, session);
        if (buffer.writerIndex() - start > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation session payload is oversized");
        }
    }

    private static ObservationSessionPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation session payload is oversized");
        }
        var operation = Operation.fromWireId(buffer.readUnsignedByte());
        var payload = new ObservationSessionPayload(operation, FabricStateSnapshotCodec.decodeSession(buffer));
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Observation session payload has trailing data");
        }
        return payload;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Operation {
        OPEN(0),
        CLOSE(1);

        private final int wireId;

        Operation(int wireId) {
            this.wireId = wireId;
        }

        private static Operation fromWireId(int wireId) {
            return switch (wireId) {
                case 0 -> OPEN;
                case 1 -> CLOSE;
                default -> throw new IllegalArgumentException("Unknown observation session operation");
            };
        }
    }
}
