package space.controlnet.ae2federation.bridge;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import space.controlnet.ae2federation.ae2.NativeAttachment;

public final class BridgeTopology {
    private BridgeTopology() {
    }

    public static BridgeStatus classify(NativeAttachment mainAttachment, NativeAttachment outerAttachment) {
        if (mainAttachment == null) {
            return BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
        }
        if (outerAttachment == null) {
            return BridgeStatus.invalid(BridgeOperationalReason.MISSING_OUTER_ATTACHMENT);
        }
        IGrid mainGrid = mainAttachment.grid();
        IGrid outerGrid = outerAttachment.grid();
        if (!(mainAttachment.grid() != outerAttachment.grid())) {
            return BridgeStatus.invalid(BridgeOperationalReason.SAME_GRID);
        }
        return BridgeStatus.valid(new BridgeMembershipCandidate(mainGrid, outerGrid));
    }

    public static BridgeStatus classify(IGridNode mainNode, NativeAttachment outerAttachment) {
        if (mainNode == null || mainNode.getGrid() == null || mainNode.getConnections().isEmpty()) {
            return BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
        }
        if (outerAttachment == null) {
            return BridgeStatus.invalid(BridgeOperationalReason.MISSING_OUTER_ATTACHMENT);
        }
        if (mainNode.getGrid() == outerAttachment.grid()) {
            return BridgeStatus.invalid(BridgeOperationalReason.SAME_GRID);
        }
        return BridgeStatus.valid(new BridgeMembershipCandidate(mainNode.getGrid(), outerAttachment.grid()));
    }
}
