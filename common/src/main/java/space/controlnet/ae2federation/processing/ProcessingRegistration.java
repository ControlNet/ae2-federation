package space.controlnet.ae2federation.processing;

import appeng.core.definitions.AEBlocks;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.ae2.processing.ProviderTargetTrace;

public final class ProcessingRegistration {
    private ProcessingRegistration() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ProcessingRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(EndpointTargetCapability.BLOCK,
                (level, position, state, blockEntity, side) -> {
                    ProviderTargetTrace.recordCapabilityLookup(blockEntity);
                    return level instanceof ServerLevel serverLevel && side != null
                            ? EndpointTargetBinding.find(serverLevel, position, side)
                            : null;
                },
                AEBlocks.INTERFACE.block());
    }
}
