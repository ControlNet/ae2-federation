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

    /**
     * Registers the guide example packs as AddPackFindersEvent#addPackFinders would, but hidden, the way NeoForge hides
     * each mod's own resources: they are required, so they stay active, and the Resource Packs screen does not list them.
     */
    private static void onAddPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        if (event.getPackType() != net.minecraft.server.packs.PackType.CLIENT_RESOURCES) {
            return;
        }
        var mod = net.neoforged.fml.ModList.get().getModContainerById("ae2federation").orElseThrow().getModInfo();
        for (var pack : space.controlnet.ae2federation.client.guide.GuideExamplePacks.active(
                net.neoforged.fml.ModList.get()::isLoaded)) {
            var root = mod.getOwningFile().getFile().findResource(pack.path());
            var id = "mod/ae2federation:" + pack.path();
            var created = net.minecraft.server.packs.repository.Pack.readMetaAndCreate(
                    new net.minecraft.server.packs.PackLocationInfo(id,
                            net.minecraft.network.chat.Component.translatable(pack.nameKey()),
                            net.minecraft.server.packs.repository.PackSource.BUILT_IN,
                            java.util.Optional.of(new net.minecraft.server.packs.repository.KnownPack("neoforge", id,
                                    mod.getVersion().toString()))),
                    net.minecraft.server.packs.repository.BuiltInPackSource.fromName(
                            path -> new net.minecraft.server.packs.PathPackResources(path, root)),
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    new net.minecraft.server.packs.PackSelectionConfig(true,
                            net.minecraft.server.packs.repository.Pack.Position.TOP, false));
            if (created != null) {
                var hidden = created.hidden();
                event.addRepositorySource(packs -> packs.accept(hidden));
            }
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
