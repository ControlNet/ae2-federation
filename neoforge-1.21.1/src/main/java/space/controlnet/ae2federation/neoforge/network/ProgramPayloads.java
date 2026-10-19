package space.controlnet.ae2federation.neoforge.network;

import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.automation.ChunkAssembler;
import space.controlnet.ae2federation.automation.ClientPrograms;
import space.controlnet.ae2federation.automation.ProgramSync;

/**
 * The Automation tab's traffic (see {@link ProgramSync} and {@link ClientPrograms}). Serverbound payloads stay under
 * the 32 KiB limit: program text travels in chunks of {@link ChunkAssembler#CHUNK} characters.
 */
public final class ProgramPayloads {
    /** Names, IDs, versions and words of a request: generous, and far below the payload limit. */
    private static final int SHORT = 256;
    private static final int MAX_ARGS = 32;

    private ProgramPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ae2federation", path));
    }

    public record Index(ProgramSync.Index index) implements CustomPacketPayload {
        public static final Type<Index> TYPE = payloadType("program_index");
        public static final StreamCodec<RegistryFriendlyByteBuf, Index> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> write(buffer, payload.index()), buffer -> new Index(readIndex(buffer)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Text(ProgramSync.Text text) implements CustomPacketPayload {
        public static final Type<Text> TYPE = payloadType("program_text");
        public static final StreamCodec<RegistryFriendlyByteBuf, Text> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    var text = payload.text();
                    buffer.writeUtf(text.name(), SHORT);
                    buffer.writeUtf(text.version(), SHORT);
                    buffer.writeVarInt(text.index());
                    buffer.writeVarInt(text.count());
                    buffer.writeUtf(text.part(), ChunkAssembler.CHUNK);
                },
                buffer -> new Text(new ProgramSync.Text(buffer.readUtf(SHORT), buffer.readUtf(SHORT),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(ChunkAssembler.CHUNK))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Answer(ProgramSync.Answer answer) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = payloadType("program_answer");
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.answer().request());
                    buffer.writeBoolean(payload.answer().ok());
                    buffer.writeUtf(payload.answer().message());
                },
                buffer -> new Answer(new ProgramSync.Answer(buffer.readVarInt(), buffer.readBoolean(),
                        buffer.readUtf())));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Open(boolean open) implements CustomPacketPayload {
        public static final Type<Open> TYPE = payloadType("program_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeBoolean(payload.open()), buffer -> new Open(buffer.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Fetch(String name) implements CustomPacketPayload {
        public static final Type<Fetch> TYPE = payloadType("program_fetch");
        public static final StreamCodec<RegistryFriendlyByteBuf, Fetch> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> buffer.writeUtf(payload.name(), SHORT),
                buffer -> new Fetch(buffer.readUtf(SHORT)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Upload(int request, String name, String base, int index, int count, String part)
            implements CustomPacketPayload {
        public static final Type<Upload> TYPE = payloadType("program_upload");
        public static final StreamCodec<RegistryFriendlyByteBuf, Upload> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.request());
                    buffer.writeUtf(payload.name(), SHORT);
                    buffer.writeUtf(payload.base(), SHORT);
                    buffer.writeVarInt(payload.index());
                    buffer.writeVarInt(payload.count());
                    buffer.writeUtf(payload.part(), ChunkAssembler.CHUNK);
                },
                buffer -> new Upload(buffer.readVarInt(), buffer.readUtf(SHORT), buffer.readUtf(SHORT),
                        buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(ChunkAssembler.CHUNK)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Request(int request, String action, List<String> args) implements CustomPacketPayload {
        public static final Type<Request> TYPE = payloadType("program_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.of(
                (buffer, payload) -> {
                    buffer.writeVarInt(payload.request());
                    buffer.writeUtf(payload.action(), SHORT);
                    buffer.writeCollection(payload.args(), (out, arg) -> out.writeUtf(arg, SHORT));
                },
                buffer -> new Request(buffer.readVarInt(), buffer.readUtf(SHORT),
                        buffer.readCollection(size -> {
                            if (size > MAX_ARGS) {
                                throw new IllegalArgumentException("too many arguments");
                            }
                            return new java.util.ArrayList<>(size);
                        }, in -> in.readUtf(SHORT))));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void write(FriendlyByteBuf buffer, ProgramSync.Index index) {
        buffer.writeCollection(index.files(), (out, file) -> {
            out.writeUtf(file.name());
            out.writeUtf(file.version());
            out.writeBoolean(file.current());
            out.writeBoolean(file.pushed());
            out.writeVarInt(file.problems());
            out.writeCollection(file.functions(), (fout, function) -> {
                fout.writeUtf(function.name());
                fout.writeCollection(function.params(), FriendlyByteBuf::writeUtf);
                fout.writeUtf(function.result());
                fout.writeBoolean(function.acts());
            });
        });
        buffer.writeCollection(index.devices(), ProgramPayloads::writeDevice);
        buffer.writeCollection(index.interfaces(), ProgramPayloads::writeDevice);
        buffer.writeCollection(index.networks(), (out, network) -> {
            out.writeUtf(network.id());
            out.writeUtf(network.name());
            out.writeUtf(network.domain());
        });
        buffer.writeCollection(index.installations(), (out, installation) -> {
            out.writeUtf(installation.id());
            out.writeUtf(installation.device());
            out.writeUtf(installation.file());
            out.writeUtf(installation.function());
            out.writeUtf(installation.args());
            out.writeUtf(installation.trigger());
            out.writeVarInt(installation.interval());
            out.writeUtf(installation.overlap());
            out.writeVarInt(installation.running());
        });
    }

    static ProgramSync.Index readIndex(FriendlyByteBuf buffer) {
        var files = buffer.readList(in -> new ProgramSync.FileInfo(in.readUtf(), in.readUtf(), in.readBoolean(),
                in.readBoolean(), in.readVarInt(), in.readList(fin -> new ProgramSync.FunctionInfo(fin.readUtf(),
                        fin.readList(FriendlyByteBuf::readUtf), fin.readUtf(), fin.readBoolean()))));
        var devices = buffer.readList(ProgramPayloads::readDevice);
        var interfaces = buffer.readList(ProgramPayloads::readDevice);
        var networks = buffer.readList(in -> new ProgramSync.NetworkInfo(in.readUtf(), in.readUtf(), in.readUtf()));
        var installations = buffer.readList(in -> new ProgramSync.InstallationInfo(in.readUtf(), in.readUtf(),
                in.readUtf(), in.readUtf(), in.readUtf(), in.readUtf(), in.readVarInt(), in.readUtf(),
                in.readVarInt()));
        return new ProgramSync.Index(List.copyOf(files), List.copyOf(devices), List.copyOf(interfaces),
                List.copyOf(networks), List.copyOf(installations));
    }

    private static void writeDevice(FriendlyByteBuf buffer, ProgramSync.DeviceInfo device) {
        buffer.writeUtf(device.id());
        buffer.writeUtf(device.alias());
        buffer.writeUtf(device.dimension());
        buffer.writeBlockPos(device.position());
        buffer.writeUtf(device.domain());
    }

    private static ProgramSync.DeviceInfo readDevice(FriendlyByteBuf buffer) {
        return new ProgramSync.DeviceInfo(buffer.readUtf(), buffer.readUtf(), buffer.readUtf(), buffer.readBlockPos(),
                buffer.readUtf());
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ProgramPayloads::registerPayloads);
        ProgramSync.register(new ProgramSync.ToClient() {
            @Override
            public void index(ServerPlayer player, ProgramSync.Index index) {
                PacketDistributor.sendToPlayer(player, new Index(index));
            }

            @Override
            public void text(ServerPlayer player, ProgramSync.Text text) {
                PacketDistributor.sendToPlayer(player, new Text(text));
            }

            @Override
            public void answer(ServerPlayer player, ProgramSync.Answer answer) {
                PacketDistributor.sendToPlayer(player, new Answer(answer));
            }
        });
        ClientPrograms.register(new ClientPrograms.ToServer() {
            @Override
            public void open(boolean open) {
                PacketDistributor.sendToServer(new Open(open));
            }

            @Override
            public void fetch(String name) {
                PacketDistributor.sendToServer(new Fetch(name));
            }

            @Override
            public void upload(int request, String name, String base, int index, int count, String part) {
                PacketDistributor.sendToServer(new Upload(request, name, base, index, count, part));
            }

            @Override
            public void request(int request, String action, List<String> args) {
                PacketDistributor.sendToServer(new Request(request, action, args));
            }
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(Index.TYPE, Index.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPrograms.get().receive(payload.index())));
        registrar.playToClient(Text.TYPE, Text.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPrograms.get().receive(payload.text())));
        registrar.playToClient(Answer.TYPE, Answer.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPrograms.get().receive(payload.answer())));
        registrar.playToServer(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgramSync.get(player.getServer()).subscribe(player, payload.open());
            }
        }));
        registrar.playToServer(Fetch.TYPE, Fetch.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgramSync.get(player.getServer()).fetch(player, payload.name());
            }
        }));
        registrar.playToServer(Upload.TYPE, Upload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgramSync.get(player.getServer()).upload(player, payload.request(), payload.name(), payload.base(),
                        payload.index(), payload.count(), payload.part());
            }
        }));
        registrar.playToServer(Request.TYPE, Request.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgramSync.get(player.getServer()).request(player, payload.request(), payload.action(),
                        payload.args());
            }
        }));
    }
}
