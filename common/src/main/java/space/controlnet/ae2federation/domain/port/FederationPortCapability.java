package space.controlnet.ae2federation.domain.port;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

public final class FederationPortCapability {
    public static final BlockCapability<FederationPort, net.minecraft.core.Direction> BLOCK =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("ae2federation", "domain_port"),
                    FederationPort.class);

    private FederationPortCapability() {
    }
}
