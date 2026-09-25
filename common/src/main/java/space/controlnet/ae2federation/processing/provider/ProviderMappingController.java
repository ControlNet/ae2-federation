package space.controlnet.ae2federation.processing.provider;

import java.util.Optional;
import java.util.Set;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;

/**
 * Server-side mapping entry of a placed Federation Pattern Provider. The Domain management UI uses it to map a physical
 * Pattern slot to an Endpoint; the Provider owns the Claim and allocates the Endpoint's native Lane.
 */
public interface ProviderMappingController {
    /**
     * Adds or removes {@code endpoint} for the slot identified by {@code handle}. Returns a short status code such as
     * {@code accepted-<slot>-<generation>} or {@code rejected-<reason>}.
     */
    String toggleEndpoint(PatternSlotHandle handle, EndpointTargetBinding endpoint);

    Set<EndpointIdentity> endpointsForSlot(int slot);

    Optional<EndpointIdentity> laneEndpoint(int laneIndex);
}
