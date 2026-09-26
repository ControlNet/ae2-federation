package space.controlnet.ae2federation.domain.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

public final class CableFacePort {
    private final BlockPos cablePosition;
    private final Direction face;
    private final Runnable topologyInvalidator;
    private @Nullable BlockCapabilityCache<FederationPort, Direction> cache;
    private @Nullable ServerLevel level;
    private @Nullable FederationPort peer;
    private boolean dirty = true;
    private boolean active;

    public CableFacePort(BlockPos cablePosition, Direction face, Runnable topologyInvalidator) {
        this.cablePosition = cablePosition.immutable();
        this.face = face;
        this.topologyInvalidator = topologyInvalidator;
    }

    public void initialize(ServerLevel serverLevel) {
        level = serverLevel;
        active = true;
        cache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel,
                cablePosition.relative(face), face.getOpposite(), () -> active, this::invalidate);
        invalidate();
    }

    public boolean tick() {
        if (!dirty) {
            return false;
        }
        dirty = false;
        var resolved = resolve();
        var changed = !java.util.Objects.equals(peer, resolved);
        peer = resolved;
        return changed;
    }

    public void invalidate() {
        peer = null;
        dirty = true;
        topologyInvalidator.run();
    }

    public void destroy() {
        active = false;
        peer = null;
        dirty = false;
    }

    public @Nullable FederationPort peer() {
        return peer;
    }

    private @Nullable FederationPort resolve() {
        var neighborPosition = cablePosition.relative(face);
        if (level == null || !level.isLoaded(neighborPosition) || cache == null) {
            return null;
        }
        var candidate = cache.getCapability();
        var local = new FederationPort(cablePosition, face);
        return candidate != null && candidate.ownerPosition().equals(neighborPosition)
                && candidate.outwardFace() == face.getOpposite() && local.connectsTo(candidate) ? candidate : null;
    }
}
