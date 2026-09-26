package space.controlnet.ae2federation.neoforge.network;

import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** Navigation is bound to the server's currently open, usable native provider menu. */
public record ProviderMenuNavigationPayload(int containerId, boolean returning) implements CustomPacketPayload {
    public static final Type<ProviderMenuNavigationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "provider_menu_navigation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProviderMenuNavigationPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> { buffer.writeVarInt(payload.containerId()); buffer.writeBoolean(payload.returning()); },
                    buffer -> new ProviderMenuNavigationPayload(buffer.readVarInt(), buffer.readBoolean()));

    public void handle(ServerPlayer player) {
        if (!player.getServer().isSameThread() || containerId < 0 || containerId > 100) return;
        if (returning) {
            FederationDomainPolicyMenu.returnToProvider(player, containerId);
            return;
        }
        if (player.containerMenu instanceof PatternProviderMenu menu && menu.containerId == containerId
                && menu.stillValid(player) && menu.getBlockEntity() instanceof FederationPatternProviderBlockEntity provider
                && provider.getLevel() == player.level()
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(provider.getBlockPos())) <= 64) {
            FederationDomainPolicyMenu.openDevice(player, provider.getBlockPos());
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
