package space.controlnet.ae2federation.processing.provider;

import java.util.Objects;

public sealed interface ProviderTargetResolution permits ProviderTargetResolution.Authorized,
        ProviderTargetResolution.Paused {
    ProviderTargetState state();

    record Authorized(AuthorizedNativeTarget target) implements ProviderTargetResolution {
        public Authorized {
            Objects.requireNonNull(target);
        }

        @Override
        public ProviderTargetState state() {
            return ProviderTargetState.ACTIVE;
        }
    }

    record Paused(ProviderTargetState state) implements ProviderTargetResolution {
        public Paused {
            Objects.requireNonNull(state);
            if (state == ProviderTargetState.ACTIVE) {
                throw new IllegalArgumentException("Paused target cannot be active");
            }
        }
    }
}
