package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;
import java.util.Optional;

public sealed interface ClaimState permits ClaimState.Unclaimed, ClaimState.Owned {
    ClaimKey key();

    ClaimEpoch epoch();

    Optional<EndpointOwnerIdentity> owner();

    record Unclaimed(ClaimKey key, ClaimEpoch epoch) implements ClaimState {
        public Unclaimed {
            Objects.requireNonNull(key);
            Objects.requireNonNull(epoch);
        }

        @Override
        public Optional<EndpointOwnerIdentity> owner() {
            return Optional.empty();
        }
    }

    record Owned(ClaimKey key, ClaimEpoch epoch, EndpointOwnerIdentity ownerIdentity) implements ClaimState {
        public Owned {
            Objects.requireNonNull(key);
            Objects.requireNonNull(epoch);
            Objects.requireNonNull(ownerIdentity);
            if (epoch.equals(ClaimEpoch.NONE)) {
                throw new IllegalArgumentException("Owned Claim requires a positive epoch");
            }
        }

        @Override
        public Optional<EndpointOwnerIdentity> owner() {
            return Optional.of(ownerIdentity);
        }
    }
}
