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

    /**
     * True when {@code endpoint} has no mapped Pattern left but its Claim and return path are retained, because native
     * work already sent to the machine may still return to its Lane.
     */
    boolean retained(EndpointIdentity endpoint);

    Set<EndpointIdentity> retainedEndpoints();

    /** Captures the local binding and the currently observed remote Claim for two-step UI confirmation. */
    Optional<ReleaseConfirmation> releaseConfirmation(EndpointIdentity endpoint);

    record ReleaseConfirmation(ProviderMappingController provider, int lane, long revision, long mappingRevision,
            EndpointIdentity endpoint, space.controlnet.ae2federation.processing.claim.ClaimEpoch epoch,
            Object endpointInstance, String observation) {
    }


    /**
     * Explicitly releases a retained Endpoint: the Claim is released and the return path of its Lane is closed, so
     * products of work still inside the machine no longer return to this Provider and the native crafting job waiting
     * for them does not complete until it is cancelled. Returns {@code released-<lane>} or {@code rejected-<reason>}.
     */
    String releaseEndpoint(EndpointIdentity endpoint);
}
