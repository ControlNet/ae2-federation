package space.controlnet.ae2federation.processing.endpoint;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import java.util.Objects;
import java.util.Optional;
import space.controlnet.ae2federation.processing.provider.AuthorizedLaneIdentity;

public record EndpointReturnOwner(EndpointModeGeneration mode, PatternProviderLogic logic,
        PatternProviderReturnInventory inventory, Optional<AuthorizedLaneIdentity> lane) {
    public EndpointReturnOwner {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(logic);
        Objects.requireNonNull(inventory);
        Objects.requireNonNull(lane);
        if ((mode instanceof EndpointModeGeneration.Federated) != lane.isPresent()) {
            throw new IllegalArgumentException("Only Federated return owners require an authorized Lane identity");
        }
        if (logic.getReturnInv() != inventory) {
            throw new IllegalArgumentException("Endpoint return context must retain the exact native Provider inventory");
        }
    }
}
