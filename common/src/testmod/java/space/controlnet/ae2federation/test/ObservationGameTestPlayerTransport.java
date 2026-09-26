package space.controlnet.ae2federation.test;

import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

final class ObservationGameTestPlayerTransport {
    private ObservationGameTestPlayerTransport() {
    }

    static void install(ServerPlayer player) {
        player.connection = new HeadlessPacketListener(player);
    }

    private static final class HeadlessPacketListener extends ServerGamePacketListenerImpl {
        private HeadlessPacketListener(ServerPlayer player) {
            super(player.server, new HeadlessConnection(), player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet) {
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
        }

        @Override
        public void disconnect(DisconnectionDetails details) {
        }

        @Override
        public boolean hasChannel(ResourceLocation channel) {
            return false;
        }

        @Override
        public boolean hasChannel(CustomPacketPayload.Type<?> channel) {
            return false;
        }

        @Override
        public boolean hasChannel(CustomPacketPayload payload) {
            return false;
        }
    }

    private static final class HeadlessConnection extends Connection {
        private HeadlessConnection() {
            super(PacketFlow.SERVERBOUND);
        }

        @Override
        public void setListenerForServerboundHandshake(PacketListener listener) {
        }
    }
}
