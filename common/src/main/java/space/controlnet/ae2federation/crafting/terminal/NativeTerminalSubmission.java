package space.controlnet.ae2federation.crafting.terminal;

import appeng.api.networking.crafting.ICraftingSubmitResult;
import java.util.Objects;

public sealed interface NativeTerminalSubmission {
    record Native(ICraftingSubmitResult result) implements NativeTerminalSubmission {
        public Native {
            Objects.requireNonNull(result);
        }
    }

    record StaleBinding() implements NativeTerminalSubmission {
    }
}
