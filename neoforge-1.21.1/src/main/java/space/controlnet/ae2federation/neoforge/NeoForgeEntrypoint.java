package space.controlnet.ae2federation.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.slf4j.Logger;
import space.controlnet.ae2federation.CommonStartup;
import space.controlnet.ae2federation.beam.BeamLinkService;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.config.FederationServerConfig;
import space.controlnet.ae2federation.FederationCreativeTab;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.material.MaterialRegistration;
import space.controlnet.ae2federation.p2p.FederationP2PRegistration;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.power.OperatingPowerService;
import space.controlnet.ae2federation.power.PowerRegistration;
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

    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
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
        FederationServerConfig.register(container, modBus);
        BridgeRegistration.register(modBus);
        FederationP2PRegistration.register(modBus);
        RouterRegistration.register(modBus);
        ProcessingRegistration.register(modBus);
        PowerRegistration.register(modBus);
        BeamRegistration.register(modBus);
        space.controlnet.ae2federation.quantum.QuantumRegistration.register(modBus);
        space.controlnet.ae2federation.automation.AutomationRegistration.register(modBus);
        space.controlnet.ae2federation.automation.neoeco.NeoEcoSupport.register(modBus);
        MaterialRegistration.register(modBus);
        FederationCreativeTab.register(modBus);
        ObservationPayloads.register(modBus);
        FederationDomainPolicyActionPayloads.register(modBus);
        space.controlnet.ae2federation.neoforge.network.BundleFormPayload.register(modBus);
        space.controlnet.ae2federation.neoforge.network.AutomationInterfacePayloads.register(modBus);
        space.controlnet.ae2federation.neoforge.network.ProgramPayloads.register(modBus);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.crafting.projection.ReturnRecoveryCommands::register);
        NeoForge.EVENT_BUS.addListener(space.controlnet.ae2federation.automation.ProgramCommands::register);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onServerStopped);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onContainerClosed);
        // Before AE2's own end-of-tick Grid ticks (NORMAL priority), which draw energy from the shared pools: like
        // AE2's Grid services, Federation's work runs once per server tick, after every level has ticked.
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, NeoForgeEntrypoint::onServerTickBindings);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onServerTick);
        // After AE2's end-of-tick Grid ticks, where each Grid's storage service reports its native mount changes.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeEntrypoint::onServerTickNativeMounts);
    }

    private static void onArtifactPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        LOGGER.info("AE2F_ARTIFACT_SERVER_JOIN player={}", event.getEntity().getGameProfile().getName());
    }

    /**
     * First the Federation Cables that have work publish their evidence. Then binding requests from the level ticks and
     * from player actions or GameTests, then the beams and Quantum Cubes, whose links change the domains, then the
     * domains' operating power, which may stop a domain, then each service's tick. Last, the domains settle what is
     * still pending, and the cables sync what their beams show from the settled domains.
     */
    private static void onServerTickBindings(ServerTickEvent.Post event) {
        space.controlnet.ae2federation.router.FederationCableTicks.tickAll(event.getServer());
        FederationBindingRefresh.flushAll();
        BeamLinkService.tickAll(event.getServer());
        space.controlnet.ae2federation.quantum.QuantumLinkService.tickAll(event.getServer());
        OperatingPowerService.tick(event.getServer());
        StorageMountService.tick(event.getServer());
        CraftingProjectionService.tick(event.getServer());
        EnergySharingService.tickAll();
        space.controlnet.ae2federation.automation.ProgramCrafting.tickAll(event.getServer());
        space.controlnet.ae2federation.automation.ProgramScheduler.tickAll(event.getServer());
        space.controlnet.ae2federation.automation.ProgramSync.tickAll(event.getServer());
        FederationDomainRegistryAccess.settle(event.getServer());
        space.controlnet.ae2federation.router.FederationCableTicks.afterSettle(event.getServer());
    }

    private static void onServerTickNativeMounts(ServerTickEvent.Post event) {
        StorageMountService.reconcileNativeMountChanges(event.getServer());
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        // Created on the first tick, not on first use, so the statistics count every second the server runs.
        LevelObservabilityService.get(event.getServer().overworld());
        LevelObservabilityService.sweepAll();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            // The services serve the whole server: the level's nodes leave the registry, then the relationships that
            // needed them end. Each service itself goes when the server stops.
            FederationBindingRefresh.closeLevel(level);
            space.controlnet.ae2federation.router.DeviceStructureUpdater.closeLevel(level);
            space.controlnet.ae2federation.router.FederationCableTicks.closeLevel(level);
            space.controlnet.ae2federation.router.RouterTicks.closeLevel(level);
            BeamLinkService.closeLevel(level);
            space.controlnet.ae2federation.quantum.QuantumLinkService.closeLevel(level);
            var receipt = StorageLevelLifecycle.close(level);
            CraftingProjectionService.levelClosed(level);
            EnergySharingService.levelClosed(level);
            LevelObservabilityService.levelClosed(level);
            LOGGER.info("AE2F_STORAGE_LEVEL_CLOSED dimension={} servicePresentBefore={} mountedProvidersBefore={} "
                            + "mountedProvidersRemoved={} servicePersists={} dimensionNodesRemoved={} "
                            + "dimensionBridgesRemoved={} dimensionNodesLeft={}", level.dimension().location(),
                    receipt.servicePresentBefore(), receipt.mountedProvidersBefore(), receipt.mountedProvidersRemoved(),
                    receipt.servicePersists(), receipt.dimensionNodesRemoved(), receipt.dimensionBridgesRemoved(),
                    receipt.dimensionNodesLeft());
        }
    }

    /** Server-wide state outlives each level's unload, so it goes once the whole server has stopped. */
    private static void onServerStopped(ServerStoppedEvent event) {
        var server = event.getServer();
        StorageMountService.closeServer(server);
        CraftingProjectionService.closeServer(server);
        EnergySharingService.closeServer(server);
        OperatingPowerService.closeServer(server);
        space.controlnet.ae2federation.quantum.QuantumLinkService.closeServer(server);
        space.controlnet.ae2federation.automation.ProgramStore.closeServer(server);
        space.controlnet.ae2federation.automation.ProgramCrafting.closeServer(server);
        space.controlnet.ae2federation.automation.ProgramScheduler.closeServer(server);
        space.controlnet.ae2federation.automation.ProgramSync.closeServer(server);
        LevelObservabilityService.closeServer(server);
        space.controlnet.ae2federation.router.FederationCableTicks.closeServer(server);
        space.controlnet.ae2federation.router.RouterTicks.closeServer(server);
        space.controlnet.ae2federation.policy.PolicyService.closeServer(server);
        FederationDomainRegistryAccess.closeServer(server);
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
        if (event.getEntity().getServer() != null) {
            space.controlnet.ae2federation.automation.ProgramSync.playerLeft(event.getEntity().getServer(),
                    event.getEntity().getUUID());
        }
    }

    private static void onContainerClosed(PlayerContainerEvent.Close event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
    }
}
