package space.controlnet.ae2federation.neoforge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.client.automation.ViewerScanClient;

/**
 * A recipe viewer's recipes sent to the server ({@link ViewerScans}): the client says its viewer is ready, the server
 * asks for a scan, the client sends it in pages.
 */
public final class ViewerRecipePayloads {
    private ViewerRecipePayloads() {
    }

    public record Ready() implements CustomPacketPayload {
        public static final Type<Ready> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ready> STREAM_CODEC = StreamCodec.unit(new Ready());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Request(int scan) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::scan, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Page(ViewerScans.Page page) implements CustomPacketPayload {
        public static final Type<Page> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "viewer_recipes_page"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Page> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ViewerScans.Page::scan,
                ByteBufCodecs.VAR_INT, ViewerScans.Page::index,
                ByteBufCodecs.BOOL, ViewerScans.Page::last,
                ViewerRecipes.Category.STREAM_CODEC.apply(ByteBufCodecs.list(ViewerRecipes.MAX_PAGE_CATEGORIES)),
                ViewerScans.Page::categories,
                ViewerRecipes.Recipe.STREAM_CODEC.apply(ByteBufCodecs.list(ViewerRecipes.MAX_PAGE_RECIPES)),
                ViewerScans.Page::recipes,
                ViewerScans.Page::new).map(Page::new, Page::page);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ViewerRecipePayloads::registerPayloads);
        ViewerScans.register((player, scan) -> PacketDistributor.sendToPlayer(player, new Request(scan)));
    }

    /** On the client only: how the client's side reaches the server. */
    public static void registerClient() {
        ViewerScanClient.register(() -> PacketDistributor.sendToServer(new Ready()),
                page -> PacketDistributor.sendToServer(new Page(page)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(Ready.TYPE, Ready.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewerScans.ready(player);
            }
        }));
        registrar.playToClient(Request.TYPE, Request.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ViewerScanClient.requested(payload.scan())));
        registrar.playToServer(Page.TYPE, Page.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ViewerScans.page(player, payload.page());
            }
        }));
    }
}
