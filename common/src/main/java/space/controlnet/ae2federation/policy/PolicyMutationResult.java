package space.controlnet.ae2federation.policy;

import java.util.Objects;

public sealed interface PolicyMutationResult permits PolicyMutationResult.Accepted, PolicyMutationResult.Rejected {
    record Accepted(PolicyRecord record) implements PolicyMutationResult {
        public Accepted {
            Objects.requireNonNull(record);
        }

        public PolicyRevision revision() {
            return record.revision();
        }
    }

    record Rejected(PolicyRevision currentRevision, PolicyRejection reason) implements PolicyMutationResult {
        public Rejected {
            Objects.requireNonNull(currentRevision);
            Objects.requireNonNull(reason);
        }
    }
}
