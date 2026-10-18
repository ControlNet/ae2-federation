package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.automation.AutomationInterfaceScreens;
import space.controlnet.ae2federation.client.automation.AutomationInterfaceScreen;

/** The Automation Interface's screen: what it shows, sent to the player, and the alias the player sends back. */
public final class AutomationInterfacePayloads {
    private AutomationInterfacePayloads() {
    }

    public record View(AutomationInterfaceScreens.View view) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "automation_interface_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, View> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    var view = payload.view();
                    buffer.writeBlockPos(view.position());
                    buffer.writeUtf(view.id());
                    buffer.writeUtf(view.alias());
                    buffer.writeBoolean(view.refused());
                },
                buffer -> new View(new AutomationInterfaceScreens.View(buffer.readBlockPos(), buffer.readUtf(),
                        buffer.readUtf(), buffer.readBoolean())));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Alias(BlockPos position, String alias) implements CustomPacketPayload {
        /** Long enough for any alias the book accepts; longer text is refused there, not cut here. */
        private static final int MAX_LENGTH = 256;
        public static final Type<Alias> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "automation_interface_alias"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Alias> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeBlockPos(payload.position());
                    buffer.writeUtf(payload.alias(), MAX_LENGTH);
                },
                buffer -> new Alias(buffer.readBlockPos(), buffer.readUtf(MAX_LENGTH)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(AutomationInterfacePayloads::registerPayloads);
        AutomationInterfaceScreens.register(
                (player, view) -> PacketDistributor.sendToPlayer(player, new View(view)),
                (position, alias) -> PacketDistributor.sendToServer(new Alias(position, alias)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(View.TYPE, View.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> AutomationInterfaceScreen.show(payload.view())));
        registrar.playToServer(Alias.TYPE, Alias.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AutomationInterfaceScreens.setAlias(player, payload.position(), payload.alias());
            }
        }));
    }
}
