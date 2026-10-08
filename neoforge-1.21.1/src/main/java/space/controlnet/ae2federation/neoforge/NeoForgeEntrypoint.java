package space.controlnet.ae2federation.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.slf4j.Logger;
import space.controlnet.ae2federation.CommonStartup;
import space.controlnet.ae2federation.FederationCreativeTab;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.material.MaterialRegistration;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.neoforge.network.ObservationPayloads;
import space.controlnet.ae2federation.neoforge.network.FederationDomainPolicyActionPayloads;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import java.nio.file.Files;
import java.security.MessageDigest;

@Mod(NeoForgeEntrypoint.MOD_ID)
public final class NeoForgeEntrypoint {
    public static final String MOD_ID = "ae2federation";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeEntrypoint(IEventBus modBus) {
        if (Boolean.getBoolean("ae2federation.artifactProof")) {
            try {
                var path = ModList.get().getModFileById(MOD_ID).getFile().getFilePath().toRealPath();
                var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
                LOGGER.info("AE2F_ARTIFACT_LOAD path={} sha256={}", path, java.util.HexFormat.of().formatHex(digest));
            } catch (Exception exception) {
                throw new IllegalStateException("Production JAR origin cannot be verified", exception);
            }
            NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onArtifactPlayerJoin);
        }
        CommonStartup.start(LOGGER);
        BridgeRegistration.register(modBus);
        RouterRegistration.register(modBus);
        ProcessingRegistration.register(modBus);
        MaterialRegistration.register(modBus);
        FederationCreativeTab.register(modBus);
        ObservationPayloads.register(modBus);
        FederationDomainPolicyActionPayloads.register(modBus);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onServerStopped);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onContainerClosed);
        // Before AE2's own end-of-tick Grid ticks (NORMAL priority), which draw energy from the shared pools: like
        // AE2's Grid services, Federation's work runs once per server tick, after every level has ticked.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, NeoForgeEntrypoint::onServerTickBindings);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onServerTick);
    }

    private static void onArtifactPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("AE2F_ARTIFACT_SERVER_JOIN player={}", event.getEntity().getGameProfile().getName());
    }

    /** Binding requests from the level ticks and from player actions or GameTests, then each service's tick. */
    private static void onServerTickBindings(ServerTickEvent.Post event) {
        FederationBindingRefresh.flushAll();
        for (var level : event.getServer().getAllLevels()) {
            StorageMountService.tick(level);
            CraftingProjectionService.tick(level);
        }
        EnergySharingService.tickAll();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        LevelObservabilityService.sweepAll();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            FederationBindingRefresh.closeLevel(level);
            CraftingProjectionService.closeLevel(level);
            EnergySharingService.closeLevel(level);
            LevelObservabilityService.closeLevel(level);
            var receipt = StorageLevelLifecycle.close(level);
            space.controlnet.ae2federation.policy.PolicyService.closeLevel(level);
            LOGGER.info("AE2F_STORAGE_LEVEL_CLOSED dimension={} servicePresentBefore={} mountedProvidersBefore={} "
                            + "mountedProvidersRemoved={} serviceRemoved={} dimensionNodesRemoved={} "
                            + "dimensionBridgesRemoved={} dimensionNodesLeft={}", level.dimension().location(),
                    receipt.servicePresentBefore(), receipt.mountedProvidersBefore(), receipt.mountedProvidersRemoved(),
                    receipt.serviceRemoved(), receipt.dimensionNodesRemoved(), receipt.dimensionBridgesRemoved(),
                    receipt.dimensionNodesLeft());
        }
    }

    /** Server-wide state outlives each level's unload, so it goes once the whole server has stopped. */
    private static void onServerStopped(ServerStoppedEvent event) {
        FederationDomainRegistryAccess.closeServer(event.getServer());
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
    }

    private static void onContainerClosed(PlayerContainerEvent.Close event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
    }
}
