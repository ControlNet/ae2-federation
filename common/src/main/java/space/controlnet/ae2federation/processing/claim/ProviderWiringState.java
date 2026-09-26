package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public record ProviderWiringState(ProviderIdentity provider, ProviderOrientation orientation, ClaimState claim,
        Object nativeRemainderDestination, ProviderTargetState targetState) {
    public ProviderWiringState(ProviderIdentity provider, ProviderOrientation orientation, ClaimState claim,
            Object nativeRemainderDestination) {
        this(provider, orientation, claim, nativeRemainderDestination, ProviderTargetState.ACTIVE);
    }

    public ProviderWiringState {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(orientation);
        Objects.requireNonNull(claim);
        Objects.requireNonNull(nativeRemainderDestination);
        Objects.requireNonNull(targetState);
    }

    public ProviderWiringState rotate(ProviderOrientation replacement) {
        return new ProviderWiringState(provider, replacement, claim, nativeRemainderDestination,
                ProviderTargetState.ROTATION_PENDING);
    }
}
