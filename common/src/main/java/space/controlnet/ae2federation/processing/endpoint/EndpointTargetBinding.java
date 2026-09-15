package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.networking.IGridNode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public final class EndpointTargetBinding implements EndpointTargetAccess, AutoCloseable {
    private static final Map<ServerLevel, Map<Key, EndpointTargetBinding>> BINDINGS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Key key;
    private final EndpointClaimAuthority claims;
    private final IGridNode subnetNode;
    private boolean closed;

    public EndpointTargetBinding(ServerLevel level, BlockPos position, Direction side,
            EndpointClaimAuthority claims, IGridNode subnetNode) {
        this.level = Objects.requireNonNull(level);
        key = new Key(position, side);
        this.claims = Objects.requireNonNull(claims);
        this.subnetNode = Objects.requireNonNull(subnetNode);
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
            return BINDINGS.getOrDefault(level, Map.of()).get(new Key(position, side));
        }
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

    private record Key(BlockPos position, Direction side) {
        private Key {
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(side);
        }
    }
}
