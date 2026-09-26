package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionRequest;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record FederationDomainPolicyActionPayload(FederationDomainPolicyActionRequest request, java.util.UUID requestId) implements CustomPacketPayload {
    public FederationDomainPolicyActionPayload(FederationDomainPolicyActionRequest request) {
        this(request, java.util.UUID.randomUUID());
    }

    public static final Type<FederationDomainPolicyActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_policy_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FederationDomainPolicyActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(FederationDomainPolicyActionPayload::encode, FederationDomainPolicyActionPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        buffer.writeByte(request.action().wireId());
        buffer.writeVarInt(request.containerId());
        buffer.writeUUID(request.menuNonce());
        buffer.writeVarLong(request.menuSequence());
        buffer.writeUtf(request.context().federationDomainId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeVarLong(request.context().generation());
        buffer.writeVarLong(request.expectedRevision().value());
        if (request.action() == FederationDomainPolicyAction.SELECT_TARGET) {
            buffer.writeUtf(request.target(), 160);
        }
        buffer.writeUUID(requestId);
        if (buffer.writerIndex() - start > FederationDomainPolicyActionRequest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Federation Domain policy action payload is oversized");
        }
    }

    private static FederationDomainPolicyActionPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > FederationDomainPolicyActionRequest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Federation Domain policy action payload is oversized");
        }
        var action = FederationDomainPolicyAction.fromWireId(buffer.readUnsignedByte());
        var containerId = buffer.readVarInt();
        var nonce = buffer.readUUID();
        var sequence = buffer.readVarLong();
        var federationDomainId = new FederationDomainId(buffer.readUtf(ObservationLimits.MAX_ID_LENGTH));
        var context = new FederationDomainReference(federationDomainId, buffer.readVarLong());
        var revision = new PolicyRevision(buffer.readVarLong());
        var target = action == FederationDomainPolicyAction.SELECT_TARGET ? buffer.readUtf(160) : "";
        var requestId = buffer.readUUID();
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Federation Domain policy action payload has trailing data");
        }
        return new FederationDomainPolicyActionPayload(
                new FederationDomainPolicyActionRequest(action, containerId, nonce, sequence, context, revision, target), requestId);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
