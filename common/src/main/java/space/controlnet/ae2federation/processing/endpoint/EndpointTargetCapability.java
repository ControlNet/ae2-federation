package space.controlnet.ae2federation.processing.endpoint;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class EndpointTargetCapability {
    public static final BlockCapability<EndpointTargetAccess, Direction> BLOCK = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "processing_endpoint_target"),
            EndpointTargetAccess.class);

    private EndpointTargetCapability() {
    }
}
