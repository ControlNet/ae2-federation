package space.controlnet.ae2federation.policy;

import java.util.Map;

public record PolicyStoreSnapshot(PolicyRevision highWatermark, Map<PolicyKey, PolicyRecord> entries) {
    public PolicyStoreSnapshot {
        entries = Map.copyOf(entries);
        if (entries.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().key())
                || entry.getValue().revision().compareTo(highWatermark) > 0)) {
            throw new IllegalArgumentException("Policy snapshot contains an invalid key or revision");
        }
    }
}
