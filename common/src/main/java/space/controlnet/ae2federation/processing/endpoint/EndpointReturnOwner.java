package space.controlnet.ae2federation.processing.endpoint;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import java.util.Objects;

public record EndpointReturnOwner(EndpointModeGeneration mode, PatternProviderLogic logic,
        PatternProviderReturnInventory inventory) {
    public EndpointReturnOwner {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(logic);
        Objects.requireNonNull(inventory);
        if (logic.getReturnInv() != inventory) {
            throw new IllegalArgumentException("Endpoint return context must retain the exact native Provider inventory");
        }
    }
}
