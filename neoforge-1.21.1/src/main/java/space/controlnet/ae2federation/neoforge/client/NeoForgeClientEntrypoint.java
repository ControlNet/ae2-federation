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
        modBus.addListener(NeoForgeClientEntrypoint::onLoadBuiltinResource);
        ClientStartup.start(LOGGER);
        if (Boolean.getBoolean("ae2federation.artifactProof")) {
            NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onArtifactJoin);
        }
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onScreenInit);
    }

    @SuppressWarnings("unchecked")
    private static void onLoadBuiltinResource(com.lowdragmc.lowdraglib2.editor.resource.EditorResourceEvent.LoadBuiltin event) {
        if (event.resourceInstance.resource == com.lowdragmc.lowdraglib2.editor.resource.TexturesResource.INSTANCE) {
            space.controlnet.ae2federation.client.menu.FederationTheme.register(
                    (com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance<com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture>) event.resourceInstance);
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
