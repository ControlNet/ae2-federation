package space.controlnet.ae2federation.identity;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;

public final class IdentityReconciler {
    private IdentityReconciler() {
    }

    public static IdentitySettlement reconcile(Collection<NodeLineage> evidence, boolean copiedLiveIdentity,
            boolean ambiguousSplit, boolean completeObservation) {
        if (!completeObservation) {
            return unresolved(IdentityStatus.PARTIAL_LOAD);
        }
        if (copiedLiveIdentity) {
            return unresolved(IdentityStatus.COPIED_LIVE_IDENTITY);
        }
        if (ambiguousSplit) {
            return unresolved(IdentityStatus.AMBIGUOUS_SPLIT);
        }
        if (evidence.isEmpty()) {
            return unresolved(IdentityStatus.NEW_NETWORK);
        }

        var networkIds = new HashSet<NetworkId>();
        var nodeIds = new HashSet<>();
        for (var lineage : evidence) {
            networkIds.add(lineage.networkId());
            if (!nodeIds.add(lineage.nodeId())) {
                return unresolved(IdentityStatus.CONFLICTING_NODE_DATA);
            }
        }
        if (networkIds.size() > 1) {
            return unresolved(IdentityStatus.AMBIGUOUS_MERGE);
        }
        return new IdentitySettlement(IdentityStatus.SETTLED, Optional.of(networkIds.iterator().next()));
    }

    private static IdentitySettlement unresolved(IdentityStatus status) {
        return new IdentitySettlement(status, Optional.empty());
    }
}
