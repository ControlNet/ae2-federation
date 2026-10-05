package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.Objects;
import java.util.Optional;
import space.controlnet.ae2federation.processing.provider.AuthorizedLaneIdentity;

/**
 * Whose return inventory takes a machine's output: a Federated Lane, or the pattern provider of a Local Endpoint, which
 * may be an addon's with its own logic and return inventory.
 */
public record EndpointReturnOwner(EndpointModeGeneration mode, ICraftingProvider logic,
        GenericInternalInventory inventory, Optional<AuthorizedLaneIdentity> lane) {
    public EndpointReturnOwner {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(logic);
        Objects.requireNonNull(inventory);
        Objects.requireNonNull(lane);
        if ((mode instanceof EndpointModeGeneration.Federated) != lane.isPresent()) {
            throw new IllegalArgumentException("Only Federated return owners require an authorized Lane identity");
        }
        if (mode instanceof EndpointModeGeneration.Federated
                && !(logic instanceof PatternProviderLogic nativeLogic && nativeLogic.getReturnInv() == inventory)) {
            throw new IllegalArgumentException("Endpoint return context must retain the exact native Provider inventory");
        }
    }
}
