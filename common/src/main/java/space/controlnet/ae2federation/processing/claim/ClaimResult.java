package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;

public sealed interface ClaimResult permits ClaimResult.Acquired, ClaimResult.Retained, ClaimResult.Rejected {
    ClaimState state();

    record Acquired(ClaimState.Owned state) implements ClaimResult {
        public Acquired {
            Objects.requireNonNull(state);
        }
    }

    record Retained(ClaimState.Owned state) implements ClaimResult {
        public Retained {
            Objects.requireNonNull(state);
        }
    }

    record Rejected(ClaimState state, ClaimRejection reason) implements ClaimResult {
        public Rejected {
            Objects.requireNonNull(state);
            Objects.requireNonNull(reason);
        }
    }
}
