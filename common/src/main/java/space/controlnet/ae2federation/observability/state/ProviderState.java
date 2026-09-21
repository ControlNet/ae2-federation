package space.controlnet.ae2federation.observability.state;

import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.observability.id.ProviderId;

public record ProviderState(FabricReference scope, ProviderId id, String status) implements ObservationState {
    public ProviderState {
        ObservationStateSupport.validate(scope, id, status);
    }
}
