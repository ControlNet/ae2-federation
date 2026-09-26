package space.controlnet.ae2federation.neoforge.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionResult;

public record FederationDomainPolicyReplyPayload(int containerId, UUID menuNonce, UUID requestId, long sequence,
        FederationDomainPolicyActionResult result) implements CustomPacketPayload {
    public static final Type<FederationDomainPolicyReplyPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_policy_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FederationDomainPolicyReplyPayload> STREAM_CODEC =
            StreamCodec.ofMember(FederationDomainPolicyReplyPayload::encode, FederationDomainPolicyReplyPayload::decode);

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeUUID(menuNonce);
        buffer.writeUUID(requestId);
        buffer.writeVarLong(sequence);
        buffer.writeEnum(result);
    }

    private static FederationDomainPolicyReplyPayload decode(RegistryFriendlyByteBuf buffer) {
        return new FederationDomainPolicyReplyPayload(buffer.readVarInt(), buffer.readUUID(), buffer.readUUID(),
                buffer.readVarLong(), buffer.readEnum(FederationDomainPolicyActionResult.class));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
