package space.controlnet.ae2federation.domain.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

public final class CableFacePort {
    private final BlockPos cablePosition;
    private final Direction face;
    private @Nullable BlockCapabilityCache<FederationPort, Direction> cache;
    private @Nullable ServerLevel level;
    private @Nullable FederationPort peer;
    private boolean dirty = true;
    private boolean active;

    public CableFacePort(BlockPos cablePosition, Direction face) {
        this.cablePosition = cablePosition.immutable();
        this.face = face;
    }

    public void initialize(ServerLevel serverLevel) {
        level = serverLevel;
        active = true;
        cache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel,
                cablePosition.relative(face), face.getOpposite(), () -> active, this::recheck);
        peer = null;
        dirty = true;
    }

    public boolean tick() {
        // A destroyed port's cache is invalid: a neighbour change may mark it dirty before the cable initializes again.
        if (!dirty || !active) {
            return false;
        }
        dirty = false;
        var resolved = resolve();
        var changed = !java.util.Objects.equals(peer, resolved);
        peer = resolved;
        return changed;
    }

    /**
     * The neighbouring block changed: resolve the peer now, as AE2 re-examines a node's in-world connections, and report
     * whether it differs, so the owner republishes only a real change. A block placed next to the port, including a new
     * Federation block that has not published yet, leaves an unchanged link alone instead of withdrawing the node, which
     * would split the domain until the next tick. The port also resolves again on the next tick, after the neighbour
     * has settled.
     */
    public boolean revalidate() {
        if (!active) {
            return false;
        }
        var resolved = resolve();
        var changed = !java.util.Objects.equals(peer, resolved);
        peer = resolved;
        dirty = true;
        return changed;
    }

    /**
     * Some capability of the neighbour changed, such as an Endpoint's item handlers when it is claimed: resolve the
     * port again on the next tick, which reports a change only when the peer differs. A removed or unloaded neighbour
     * removes its own domain node, and its block removal also reaches {@link #revalidate()}.
     */
    private void recheck() {
        dirty = true;
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
