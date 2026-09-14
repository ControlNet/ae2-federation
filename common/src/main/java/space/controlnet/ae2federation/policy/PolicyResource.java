package space.controlnet.ae2federation.policy;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

public record PolicyResource(ResourceLocation resourceType, ResourceLocation resourceId) {
    public PolicyResource {
        Objects.requireNonNull(resourceType);
        Objects.requireNonNull(resourceId);
    }
}
