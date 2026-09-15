package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.networking.IGridNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.provider.AuthorizedNativeTarget;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeLocalProvider;

public final class EndpointTargetBinding implements EndpointTargetAccess, AutoCloseable {
    private static final Map<ServerLevel, Map<Key, EndpointTargetBinding>> BINDINGS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Key key;
    private final EndpointClaimAuthority claims;
    private final IGridNode subnetNode;
    private final EndpointRuntime runtime;
    private boolean closed;

    public EndpointTargetBinding(ServerLevel level, BlockPos position, Direction side,
            EndpointClaimAuthority claims, IGridNode subnetNode) {
        this(level, position, side.getOpposite(), claims, subnetNode, EndpointMode.FEDERATED);
    }

    public EndpointTargetBinding(ServerLevel level, BlockPos position, Direction federationFace,
            EndpointClaimAuthority claims, IGridNode subnetNode, EndpointMode initialMode) {
        this(level, position, federationFace, claims, subnetNode, initialMode, 0);
    }

    public EndpointTargetBinding(ServerLevel level, BlockPos position, Direction federationFace,
            EndpointClaimAuthority claims, IGridNode subnetNode, EndpointMode initialMode, long generation) {
        this.level = Objects.requireNonNull(level);
        key = new Key(position);
        this.claims = Objects.requireNonNull(claims);
        this.subnetNode = Objects.requireNonNull(subnetNode);
        runtime = new EndpointRuntime(level, position, subnetNode, federationFace, claims, generation);
        if (initialMode == EndpointMode.FEDERATED) {
            runtime.activateFederated();
        } else {
            runtime.activateLocal(List.of());
        }
        synchronized (BINDINGS) {
            var previous = BINDINGS.computeIfAbsent(level, ignored -> new HashMap<>()).putIfAbsent(key, this);
            if (previous != null) {
                throw new IllegalStateException("Endpoint target side is already bound");
            }
        }
        level.invalidateCapabilities(key.position());
    }

    public static @Nullable EndpointTargetAccess find(ServerLevel level, BlockPos position, Direction side) {
        synchronized (BINDINGS) {
            var binding = BINDINGS.getOrDefault(level, Map.of()).get(new Key(position));
            return binding != null && binding.runtime.logisticsFaces().contains(side) ? binding : null;
        }
    }

    public static @Nullable EndpointTargetBinding findEndpoint(ServerLevel level, BlockPos position) {
        synchronized (BINDINGS) {
            return BINDINGS.getOrDefault(level, Map.of()).get(new Key(position));
        }
    }

    public static void closeAt(ServerLevel level, BlockPos position) {
        var binding = findEndpoint(level, position);
        if (binding != null) {
            binding.close();
        }
    }

    public static boolean captureFederatedReturn(AuthorizedNativeTarget target) {
        var binding = findEndpoint(target.level(), target.position());
        return binding != null && binding.runtime.bindFederatedReturn(target);
    }

    @Override
    public EndpointIdentity endpointIdentity() {
        return claims.endpoint();
    }

    @Override
    public ClaimState claimState() {
        return claims.state();
    }

    @Override
    public IGridNode subnetNode() {
        return subnetNode;
    }

    @Override
    public java.util.Optional<EndpointModeGeneration.Federated> federatedMode(ProviderIdentity provider,
            ClaimEpoch claimEpoch) {
        return runtime.federatedMode(provider, claimEpoch);
    }

    public EndpointRuntime runtime() {
        return runtime;
    }

    public boolean activateLocal(List<NativeLocalProvider> advisoryCandidates) {
        return runtime.activateLocal(advisoryCandidates);
    }

    public boolean activateFederated() {
        return runtime.activateFederated();
    }

    public void refreshLocal() {
        if (runtime.configuredMode() == EndpointMode.LOCAL) {
            runtime.activateLocal(List.of());
        }
    }

    @Override
    public void close() {
        synchronized (BINDINGS) {
            if (closed) {
                return;
            }
            closed = true;
            var levelBindings = BINDINGS.get(level);
            if (levelBindings != null) {
                levelBindings.remove(key, this);
                if (levelBindings.isEmpty()) {
                    BINDINGS.remove(level);
                }
            }
        }
        level.invalidateCapabilities(key.position());
    }

    private record Key(BlockPos position) {
        private Key {
            position = Objects.requireNonNull(position).immutable();
        }
    }
}
