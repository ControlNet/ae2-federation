package space.controlnet.ae2federation.processing.claim;

import java.util.Objects;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

public final class NativeTargetSeparation {
    private NativeTargetSeparation() {
    }

    public static ProviderTargetState classify(Object sourceGrid, Object targetGrid, boolean targetAvailable) {
        Objects.requireNonNull(sourceGrid);
        Objects.requireNonNull(targetGrid);
        if (!targetAvailable) {
            return ProviderTargetState.NATIVE_TARGET_UNAVAILABLE;
        }
        return sourceGrid == targetGrid ? ProviderTargetState.SAME_SOURCE_GRID : ProviderTargetState.ACTIVE;
    }
}
