package space.controlnet.ae2federation.policy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class PolicyStore {
    private final Map<PolicyKey, PolicyRecord> entries;
    private PolicyRevision highWatermark;

    public PolicyStore() {
        this(PolicyRevision.NONE, Map.of());
    }

    private PolicyStore(PolicyRevision highWatermark, Map<PolicyKey, PolicyRecord> entries) {
        this.highWatermark = highWatermark;
        this.entries = new HashMap<>(entries);
    }

    public static PolicyStore restore(PolicyStoreSnapshot snapshot) {
        return new PolicyStore(snapshot.highWatermark(), snapshot.entries());
    }

    public PolicyMutationResult edit(PolicyEdit edit) {
        var current = revision(edit.key());
        if (!current.equals(edit.expectedRevision())) {
            return new PolicyMutationResult.Rejected(current, PolicyRejection.STALE_REVISION);
        }
        var configured = new PolicyRecord.Configured(edit.key(), advance(), edit.rule());
        entries.put(edit.key(), configured);
        return new PolicyMutationResult.Accepted(configured);
    }

    /**
     * Applies every edit or none: each is checked against its own key's revision first. The result is the first edit's,
     * or the first stale edit's rejection.
     */
    public PolicyMutationResult editAll(List<PolicyEdit> edits) {
        if (edits.isEmpty()) throw new IllegalArgumentException("No edits");
        var keys = new HashSet<PolicyKey>();
        for (var edit : edits) {
            if (!keys.add(edit.key())) throw new IllegalArgumentException("Two edits of " + edit.key());
        }
        for (var edit : edits) {
            var current = revision(edit.key());
            if (!current.equals(edit.expectedRevision())) {
                return new PolicyMutationResult.Rejected(current, PolicyRejection.STALE_REVISION);
            }
        }
        PolicyMutationResult first = null;
        for (var edit : edits) {
            var result = edit(edit);
            if (first == null) first = result;
        }
        return first;
    }

    public PolicyMutationResult delete(PolicyDelete deletion) {
        var currentRecord = entries.get(deletion.key());
        var current = currentRecord == null ? PolicyRevision.NONE : currentRecord.revision();
        if (!current.equals(deletion.expectedRevision())) {
            return new PolicyMutationResult.Rejected(current, PolicyRejection.STALE_REVISION);
        }
        if (!(currentRecord instanceof PolicyRecord.Configured)) {
            return new PolicyMutationResult.Rejected(current, PolicyRejection.NOT_CONFIGURED);
        }
        var tombstone = new PolicyRecord.Deleted(deletion.key(), advance());
        entries.put(deletion.key(), tombstone);
        return new PolicyMutationResult.Accepted(tombstone);
    }

    public Optional<PolicyRecord.Configured> configured(PolicyKey key) {
        return entries.get(key) instanceof PolicyRecord.Configured configured ? Optional.of(configured)
                : Optional.empty();
    }

    public PolicyRevision revision(PolicyKey key) {
        var record = entries.get(key);
        return record == null ? PolicyRevision.NONE : record.revision();
    }

    public PolicyRevision nextRevision() {
        return highWatermark;
    }

    public int configuredCount() {
        return (int) entries.values().stream().filter(PolicyRecord.Configured.class::isInstance).count();
    }

    public int tombstoneCount() {
        return (int) entries.values().stream().filter(PolicyRecord.Deleted.class::isInstance).count();
    }

    public int storedEntryCount() {
        return entries.size();
    }

    public PolicyStoreSnapshot snapshot() {
        return new PolicyStoreSnapshot(highWatermark, entries);
    }

    private PolicyRevision advance() {
        highWatermark = new PolicyRevision(Math.addExact(highWatermark.value(), 1));
        AuthorityEpoch.advance();
        return highWatermark;
    }
}
