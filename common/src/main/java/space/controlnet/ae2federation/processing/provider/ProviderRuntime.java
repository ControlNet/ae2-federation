package space.controlnet.ae2federation.processing.provider;

import appeng.api.networking.IManagedGridNode;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.ae2.processing.ProviderTargetTrace;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;

public final class ProviderRuntime {
    private final ServerLevel level;
    private final IManagedGridNode sourceNode;
    private final Supplier<ProviderTargetRequest> requestSupplier;
    private final NativeTargetDomainRegistry domains;
    private final ProviderNodeWiring wiring;
    private ProviderTargetResolution lastResolution = new ProviderTargetResolution.Paused(
            ProviderTargetState.ROTATION_PENDING);

    public ProviderRuntime(ServerLevel level, IManagedGridNode sourceNode, MappedPatternProvider provider,
            ProviderIdentity identity, ProviderOrientation orientation, Supplier<ProviderTargetRequest> requestSupplier,
            NativeTargetDomainRegistry domains) {
        this.level = Objects.requireNonNull(level);
        this.sourceNode = Objects.requireNonNull(sourceNode);
        this.requestSupplier = Objects.requireNonNull(requestSupplier);
        this.domains = Objects.requireNonNull(domains);
        wiring = new ProviderNodeWiring(sourceNode, Objects.requireNonNull(identity), Objects.requireNonNull(orientation));
        Objects.requireNonNull(provider);
        for (int laneIndex = 0; laneIndex < provider.nativeLanes().size(); laneIndex++) {
            provider.bindTarget(laneIndex, this::resolveTarget);
        }
    }

    public ProviderNodeWiring wiring() {
        return wiring;
    }

    public ProviderTargetResolution lastResolution() {
        return lastResolution;
    }

    public void settle() {
        wiring.settle();
    }

    public void rotate(ProviderOrientation orientation) {
        wiring.rotate(orientation);
    }

    private ProviderTargetResolution resolveTarget() {
        var nativeNode = sourceNode.getNode();
        if (nativeNode == null) {
            lastResolution = new ProviderTargetResolution.Paused(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
        } else {
            var supplied = requestSupplier.get();
            if (!supplied.provider().equals(wiring.identity())) {
                lastResolution = new ProviderTargetResolution.Paused(ProviderTargetState.CLAIM_MISMATCH);
            } else {
                var current = new ProviderTargetRequest(supplied.provider(), supplied.endpoint(), supplied.claimEpoch(),
                        supplied.endpointPosition(), supplied.endpointSide(),
                        supplied.rotationSettled() && wiring.settled());
                lastResolution = ProviderTargetAuthorization.resolve(
                        new ProviderAuthorizationContext(level, nativeNode, current, domains));
            }
        }
        ProviderTargetTrace.recordAuthorization(lastResolution.state(), nativeNode);
        return lastResolution;
    }
}
