package space.controlnet.ae2federation.neoforge.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.ClientStartup;
import space.controlnet.ae2federation.neoforge.network.ObservationPayloads;

@Mod(value = "ae2federation", dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeClientEntrypoint(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(space.controlnet.ae2federation.client.CableBakedModel::register);
        modBus.addListener(space.controlnet.ae2federation.client.CableBakedModel::bake);
        modBus.addListener(space.controlnet.ae2federation.client.CableFlowRenderer::register);
        modBus.addListener(NeoForgeClientEntrypoint::onLoadBuiltinResource);
        modBus.addListener(NeoForgeClientEntrypoint::onAddPackFinders);
        ClientStartup.start(LOGGER);
        if (Boolean.getBoolean("ae2federation.artifactProof")) {
            NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onArtifactJoin);
        }
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onScreenInit);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.client.WorldHighlight::render);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.client.FederationGuiScale::onOpening);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.client.FederationGuiScale::onFrame);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.client.FederationScreenSwitch::onClosing);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.client.FederationScreenSwitch::onInit);
    }

    @SuppressWarnings("unchecked")
    private static void onLoadBuiltinResource(com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == com.lowdragmc.lowdraglib2.editor.resource.TexturesResource.INSTANCE) {
            space.controlnet.ae2federation.client.menu.FederationTheme.register(
                    (com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance<com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture>) event.resourceInstance);
        }
    }

    private static void onAddPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        for (var pack : space.controlnet.ae2federation.client.guide.GuideExamplePacks.active(
                net.neoforged.fml.ModList.get()::isLoaded)) {
            event.addPackFinders(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2federation", pack.path()),
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    net.minecraft.network.chat.Component.translatable(pack.nameKey()),
                    net.minecraft.server.packs.repository.PackSource.BUILT_IN, true,
                    net.minecraft.server.packs.repository.Pack.Position.TOP);
        }
    }

    private static void onScreenInit(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof appeng.client.gui.implementations.PatternProviderScreen<?> screen
                && screen.getMenu().getBlockEntity() instanceof space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity) {
            event.addListener(net.minecraft.client.gui.components.Button.builder(
                    net.minecraft.network.chat.Component.translatable("ae2federation.ui.workspace.open_mapping"),
                    button -> net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new space.controlnet.ae2federation.neoforge.network.ProviderMenuNavigationPayload(screen.getMenu().containerId, false)))
                    .bounds(screen.getGuiLeft() + 28, screen.getGuiTop() - 22, 120, 20).build());
        }
    }

    private static void onArtifactJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        LOGGER.info("AE2F_ARTIFACT_JOIN player={}", event.getPlayer().getGameProfile().getName());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ObservationPayloads.clientStates().reset();
    }
}
