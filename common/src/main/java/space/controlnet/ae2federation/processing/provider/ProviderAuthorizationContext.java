package space.controlnet.ae2federation.processing.provider;

import appeng.api.networking.IGridNode;
import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;

public record ProviderAuthorizationContext(ServerLevel level, IGridNode sourceNode,
        ProviderTargetRequest request, NativeTargetDomainRegistry domains) {
    public ProviderAuthorizationContext {
        Objects.requireNonNull(level);
        Objects.requireNonNull(sourceNode);
        Objects.requireNonNull(request);
        Objects.requireNonNull(domains);
    }
}
