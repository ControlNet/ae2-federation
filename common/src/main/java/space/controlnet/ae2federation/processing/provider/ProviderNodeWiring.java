package space.controlnet.ae2federation.processing.provider;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import java.util.EnumSet;
import java.util.Objects;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

public final class ProviderNodeWiring {
    private final IManagedGridNode node;
    private final ProviderIdentity identity;
    private ProviderOrientation orientation;
    private boolean settled;

    public ProviderNodeWiring(IManagedGridNode node, ProviderIdentity identity, ProviderOrientation orientation) {
        this.node = Objects.requireNonNull(node);
        this.identity = Objects.requireNonNull(identity);
        apply(Objects.requireNonNull(orientation));
    }

    public void rotate(ProviderOrientation replacement) {
        settled = false;
        apply(Objects.requireNonNull(replacement));
    }

    public void settle() {
        settled = true;
    }

    public ProviderIdentity identity() {
        return identity;
    }

    public ProviderOrientation orientation() {
        return orientation;
    }

    public boolean settled() {
        return settled;
    }

    public @Nullable IGridNode exposedNode(@Nullable Direction side) {
        return side != null && side != direction(orientation.federationFace()) ? node.getNode() : null;
    }

    private void apply(ProviderOrientation replacement) {
        orientation = replacement;
        var sides = EnumSet.allOf(Direction.class);
        sides.remove(direction(replacement.federationFace()));
        node.setExposedOnSides(sides);
    }

    public static Direction direction(ProviderFace face) {
        return Direction.valueOf(face.name());
    }

    public static ProviderFace face(Direction direction) {
        return ProviderFace.valueOf(direction.name());
    }
}
