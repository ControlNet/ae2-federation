package space.controlnet.ae2federation.policy;

import java.util.Objects;

public sealed interface PolicyRecord permits PolicyRecord.Configured, PolicyRecord.Deleted {
    PolicyKey key();

    PolicyRevision revision();

    record Configured(PolicyKey key, PolicyRevision revision, PolicyRule rule) implements PolicyRecord {
        public Configured {
            Objects.requireNonNull(key);
            Objects.requireNonNull(revision);
            Objects.requireNonNull(rule);
            if (revision.equals(PolicyRevision.NONE)) {
                throw new IllegalArgumentException("Configured policy requires an assigned revision");
            }
        }
    }

    record Deleted(PolicyKey key, PolicyRevision revision) implements PolicyRecord {
        public Deleted {
            Objects.requireNonNull(key);
            Objects.requireNonNull(revision);
            if (revision.equals(PolicyRevision.NONE)) {
                throw new IllegalArgumentException("Policy tombstone requires an assigned revision");
            }
        }
    }
}
