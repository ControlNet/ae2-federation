package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.client.menu.FabricPolicyAction;
import space.controlnet.ae2federation.client.menu.FabricPolicyActionRequest;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.ObservationLimits;
import space.controlnet.ae2federation.policy.PolicyRevision;

public record FabricPolicyActionPayload(FabricPolicyActionRequest request) implements CustomPacketPayload {
    public static final Type<FabricPolicyActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "fabric_policy_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FabricPolicyActionPayload> STREAM_CODEC =
            StreamCodec.ofMember(FabricPolicyActionPayload::encode, FabricPolicyActionPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        var start = buffer.writerIndex();
        buffer.writeByte(request.action().wireId());
        buffer.writeVarInt(request.containerId());
        buffer.writeUUID(request.menuNonce());
        buffer.writeVarLong(request.menuSequence());
        buffer.writeUtf(request.context().fabricId().value(), ObservationLimits.MAX_ID_LENGTH);
        buffer.writeVarLong(request.context().generation());
        buffer.writeVarLong(request.expectedRevision().value());
        if (buffer.writerIndex() - start > FabricPolicyActionRequest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Fabric policy action payload is oversized");
        }
    }

    private static FabricPolicyActionPayload decode(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > FabricPolicyActionRequest.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Fabric policy action payload is oversized");
        }
        var action = FabricPolicyAction.fromWireId(buffer.readUnsignedByte());
        var containerId = buffer.readVarInt();
        var nonce = buffer.readUUID();
        var sequence = buffer.readVarLong();
        var fabricId = new FabricId(buffer.readUtf(ObservationLimits.MAX_ID_LENGTH));
        var context = new FabricReference(fabricId, buffer.readVarLong());
        var revision = new PolicyRevision(buffer.readVarLong());
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Fabric policy action payload has trailing data");
        }
        return new FabricPolicyActionPayload(
                new FabricPolicyActionRequest(action, containerId, nonce, sequence, context, revision));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
