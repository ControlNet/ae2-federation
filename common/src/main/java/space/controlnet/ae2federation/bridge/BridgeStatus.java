package space.controlnet.ae2federation.bridge;

import java.util.Optional;

public record BridgeStatus(BridgeOperationalReason reason, Optional<BridgeMembershipCandidate> membershipCandidate) {
    public static BridgeStatus invalid(BridgeOperationalReason reason) {
        return new BridgeStatus(reason, Optional.empty());
    }

    public static BridgeStatus valid(BridgeMembershipCandidate candidate) {
        return new BridgeStatus(BridgeOperationalReason.VALID, Optional.of(candidate));
    }
}
