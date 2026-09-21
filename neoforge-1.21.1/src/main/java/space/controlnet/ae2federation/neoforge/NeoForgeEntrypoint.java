package space.controlnet.ae2federation.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.slf4j.Logger;
import space.controlnet.ae2federation.CommonStartup;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.hub.HubRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.neoforge.network.ObservationPayloads;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(NeoForgeEntrypoint.MOD_ID)
public final class NeoForgeEntrypoint {
    public static final String MOD_ID = "ae2federation";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeEntrypoint(IEventBus modBus) {
        CommonStartup.start(LOGGER);
        BridgeRegistration.register(modBus);
        HubRegistration.register(modBus);
        ProcessingRegistration.register(modBus);
        ObservationPayloads.register(modBus);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onContainerClosed);
        NeoForge.EVENT_BUS.addListener(NeoForgeEntrypoint::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        LevelObservabilityService.sweepAll();
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) {
            CraftingBindingService.closeLevel(level);
            EnergyBindingService.closeLevel(level);
            LevelObservabilityService.closeLevel(level);
            var receipt = StorageLevelLifecycle.close(level);
            LOGGER.info("AE2F_STORAGE_LEVEL_CLOSED dimension={} servicePresentBefore={} mountedProvidersBefore={} "
                            + "mountedProvidersRemoved={} serviceRemoved={} registryPresentBefore={} registryRemoved={} "
                            + "registryAbsentAfter={}", level.dimension().location(), receipt.servicePresentBefore(),
                    receipt.mountedProvidersBefore(), receipt.mountedProvidersRemoved(), receipt.serviceRemoved(),
                    receipt.registryPresentBefore(), receipt.registryRemoved(), receipt.registryAbsentAfter());
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
    }

    private static void onContainerClosed(PlayerContainerEvent.Close event) {
        LevelObservabilityService.closePlayer(event.getEntity().getUUID());
    }
}
