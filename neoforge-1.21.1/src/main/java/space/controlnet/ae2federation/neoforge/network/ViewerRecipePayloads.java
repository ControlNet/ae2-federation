package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.client.automation.ViewerScanClient;

/**
 * A recipe viewer's recipes sent to the server ({@link ViewerScans}): the client says hello with its categories, the
 * server leases it a task with its bucket digests, the client sends the fingerprints that differ, the server asks for
 * those it lacks, and the client sends their records.
 */
public final class ViewerRecipePayloads {
    private ViewerRecipePayloads() {
    }

    public record Hello(ViewerScans.Hello hello) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> STREAM_CODEC =
                ViewerScans.Hello.STREAM_CODEC.map(Hello::new, Hello::hello);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Task(ViewerScans.Task task) implements CustomPacketPayload {
        public static final Type<Task> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_task"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Task> STREAM_CODEC =
                ViewerScans.Task.STREAM_CODEC.map(Task::new, Task::task);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Prints(ViewerScans.Prints prints) implements CustomPacketPayload {
        public static final Type<Prints> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_prints"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Prints> STREAM_CODEC =
                ViewerScans.Prints.STREAM_CODEC.map(Prints::new, Prints::prints);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Reads(ViewerScans.Reads reads) implements CustomPacketPayload {
        public static final Type<Reads> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_reads"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reads> STREAM_CODEC =
                ViewerScans.Reads.STREAM_CODEC.map(Reads::new, Reads::reads);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Records(ViewerScans.Records records) implements CustomPacketPayload {
        public static final Type<Records> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_records"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Records> STREAM_CODEC =
                ViewerScans.Records.STREAM_CODEC.map(Records::new, Records::records);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ViewerRecipePayloads::registerPayloads);
        ViewerScans.register(new ViewerScans.Sender() {
            @Override
            public void task(ServerPlayer player, ViewerScans.Task task) {
                PacketDistributor.sendToPlayer(player, new Task(task));
            }

            @Override
            public void reads(ServerPlayer player, ViewerScans.Reads reads) {
                PacketDistributor.sendToPlayer(player, new Reads(reads));
            }
        });
    }

    /** On the client only: how the client's side reaches the server. */
    public static void registerClient() {
        ViewerScanClient.register(hello -> PacketDistributor.sendToServer(new Hello(hello)),
                prints -> PacketDistributor.sendToServer(new Prints(prints)),
                records -> PacketDistributor.sendToServer(new Records(records)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Hello.TYPE, Hello.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewerScans.hello(player, payload.hello());
            }
        }));
        registrar.playToClient(Task.TYPE, Task.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ViewerScanClient.task(payload.task())));
        registrar.playToServer(Prints.TYPE, Prints.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewerScans.prints(player, payload.prints());
            }
        }));
        registrar.playToClient(Reads.TYPE, Reads.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ViewerScanClient.reads(payload.reads())));
        registrar.playToServer(Records.TYPE, Records.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewerScans.records(player, payload.records());
            }
        }));
    }
}
