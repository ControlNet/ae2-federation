package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.bundle.BundleFormSink;
import space.controlnet.ae2federation.bundle.BundleInteraction;

/** A wrench's three clicks on Federation Cables; the server checks them again before it forms the bundle. */
public record BundleFormPayload(BlockPos first, BlockPos second, BlockPos third, Direction face)
        implements CustomPacketPayload {
    public static final Type<BundleFormPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "bundle_form"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BundleFormPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBlockPos(payload.first());
                buffer.writeBlockPos(payload.second());
                buffer.writeBlockPos(payload.third());
                buffer.writeEnum(payload.face());
            },
            buffer -> new BundleFormPayload(buffer.readBlockPos(), buffer.readBlockPos(), buffer.readBlockPos(),
                    buffer.readEnum(Direction.class)));

    public static void register(IEventBus modBus) {
        modBus.addListener(BundleFormPayload::registerPayloads);
        BundleFormSink.register((first, second, third, face) ->
                PacketDistributor.sendToServer(new BundleFormPayload(first, second, third, face)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof net.minecraft.server.level.ServerPlayer player) {
                BundleInteraction.form(player, payload.first(), payload.second(), payload.third(), payload.face());
            }
        }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
