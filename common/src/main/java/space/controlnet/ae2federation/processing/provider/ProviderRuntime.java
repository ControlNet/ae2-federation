package space.controlnet.ae2federation.processing.provider;

import appeng.api.networking.IManagedGridNode;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;

public final class ProviderRuntime {
    private final ServerLevel level;
    private final IManagedGridNode sourceNode;
    private final IntFunction<ProviderTargetRequest> requestSupplier;
    private final NativeTargetDomainRegistry domains;
    private final ProviderNodeWiring wiring;
    private final MappedPatternProvider provider;
    private ProviderTargetResolution lastResolution = new ProviderTargetResolution.Paused(
            ProviderTargetState.ROTATION_PENDING);

    public ProviderRuntime(ServerLevel level, IManagedGridNode sourceNode, MappedPatternProvider provider,
            ProviderIdentity identity, ProviderOrientation orientation, Supplier<ProviderTargetRequest> requestSupplier,
            NativeTargetDomainRegistry domains) {
        this(level, sourceNode, provider, identity, orientation, ignored -> requestSupplier.get(), domains);
    }

    public ProviderRuntime(ServerLevel level, IManagedGridNode sourceNode, MappedPatternProvider provider,
            ProviderIdentity identity, ProviderOrientation orientation, IntFunction<ProviderTargetRequest> requestSupplier,
            NativeTargetDomainRegistry domains) {
        this.level = Objects.requireNonNull(level);
        this.sourceNode = Objects.requireNonNull(sourceNode);
        this.requestSupplier = Objects.requireNonNull(requestSupplier);
        this.domains = Objects.requireNonNull(domains);
        wiring = new ProviderNodeWiring(sourceNode, Objects.requireNonNull(identity), Objects.requireNonNull(orientation));
        this.provider = Objects.requireNonNull(provider);
        var laneCount = provider.nativeLanes().size();
        for (int laneIndex = 0; laneIndex < laneCount; laneIndex++) {
            bindTarget(laneIndex, 1);
        }
        ProviderObservationRegistry.register(level, provider, identity, this);
    }

    /**
     * Binds (or rebinds with a new revision) the authorized target resolver of one Lane. A new revision gives the Lane
     * a new identity, so returns authorized for an earlier Endpoint binding of the same Lane index no longer match.
     */
    public void bindLane(int laneIndex, long revision) {
        bindTarget(laneIndex, revision);
        ProviderObservationRegistry.registerLane(level, provider, wiring.identity(), laneIndex);
    }

    private void bindTarget(int laneIndex, long revision) {
        var provenance = new ProviderLogicProvenance(provider.nativeLane(laneIndex),
                new ProviderLaneIdentity(wiring.identity(), laneIndex, revision));
        provider.bindTarget(laneIndex, provenance, () -> resolveTarget(provenance));
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

    private ProviderTargetResolution resolveTarget(ProviderLogicProvenance provenance) {
        var nativeNode = sourceNode.getNode();
        if (nativeNode == null) {
            lastResolution = new ProviderTargetResolution.Paused(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
        } else {
            var supplied = requestSupplier.apply(provenance.lane().laneIndex());
            if (supplied == null) {
                // A Lane with no mapped Endpoint has no authorized destination; it never falls back to adjacency.
                lastResolution = new ProviderTargetResolution.Paused(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
            } else if (!supplied.provider().equals(wiring.identity())
                    || !provenance.lane().provider().equals(wiring.identity())) {
                lastResolution = new ProviderTargetResolution.Paused(ProviderTargetState.CLAIM_MISMATCH);
            } else {
                var current = new ProviderTargetRequest(supplied.provider(), supplied.endpoint(), supplied.claimEpoch(),
                        supplied.endpointPosition(), supplied.endpointSide(),
                        supplied.rotationSettled() && wiring.settled());
                lastResolution = ProviderTargetAuthorization.resolve(
                        new ProviderAuthorizationContext(level, nativeNode, current, domains, provenance));
            }
        }
        return lastResolution;
    }
}
