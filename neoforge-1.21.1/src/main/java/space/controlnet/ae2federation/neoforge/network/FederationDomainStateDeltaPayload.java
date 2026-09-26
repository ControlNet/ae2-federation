package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.observability.state.FederationDomainStateDelta;
import space.controlnet.ae2federation.observability.state.ObservationDeltaEnvelope;

public record FederationDomainStateDeltaPayload(ObservationDeltaEnvelope envelope) implements CustomPacketPayload {
    public static final Type<FederationDomainStateDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_state_delta"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FederationDomainStateDeltaPayload> STREAM_CODEC =
            StreamCodec.ofMember(FederationDomainStateDeltaPayload::encode, FederationDomainStateDeltaPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        FederationDomainStateSnapshotCodec.encodeSession(buffer, envelope.session());
        buffer.writeVarLong(envelope.delta().baseDataRevision());
        buffer.writeBoolean(envelope.delta().resnapshotRequired());
        FederationDomainStateSnapshotCodec.encode(buffer, envelope.delta().replacement());
        if (buffer.writerIndex() - start > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation delta payload is oversized");
        }
    }

    private static FederationDomainStateDeltaPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > ObservationLimits.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Observation delta payload is oversized");
        }
        var session = FederationDomainStateSnapshotCodec.decodeSession(buffer);
        var baseRevision = buffer.readVarLong();
        var resnapshotRequired = buffer.readBoolean();
        var delta = new FederationDomainStateDelta(FederationDomainStateSnapshotCodec.decode(buffer), baseRevision, resnapshotRequired);
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Observation delta payload has trailing data");
        }
        return new FederationDomainStateDeltaPayload(new ObservationDeltaEnvelope(session, delta));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
