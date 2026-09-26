package space.controlnet.ae2federation.bridge;

import appeng.api.networking.IGrid;

public record BridgeMembershipCandidate(IGrid mainGrid, IGrid outerGrid) {
    public BridgeMembershipCandidate {
        if (mainGrid == outerGrid) {
            throw new IllegalArgumentException("Bridge membership requires distinct native Grids");
        }
    }
}
