package space.controlnet.ae2federation.fabric.port;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class FederationPortCapability {
    public static final BlockCapability<FederationPort, net.minecraft.core.Direction> BLOCK =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("ae2federation", "fabric_port"),
                    FederationPort.class);

    private FederationPortCapability() {
    }
}
