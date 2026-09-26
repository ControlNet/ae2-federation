package space.controlnet.ae2federation.processing.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.processing.provider.ProviderFace;
import space.controlnet.ae2federation.processing.provider.ProviderId;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderInstanceEpoch;
import space.controlnet.ae2federation.processing.provider.ProviderOrientation;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;

final class ProviderClaimTest {
    private static final EndpointIdentity ENDPOINT_A = endpoint(1);
    private static final EndpointIdentity ENDPOINT_B = endpoint(2);
    private static final EndpointOwnerIdentity OWNER_A = owner(3);
    private static final EndpointOwnerIdentity OWNER_B = owner(4);

    @Test
    void exposesFiveNativeFacesWhenFederationFaceIsExcluded() {
        var orientation = new ProviderOrientation(ProviderFace.EAST);

        assertEquals(5, orientation.nativeFaces().size());
        assertFalse(orientation.nativeFaces().contains(ProviderFace.EAST));
        assertTrue(orientation.nativeFaces().contains(ProviderFace.WEST));
    }

    @Test
    void acceptsExactlyOneOwnerWhenClaimsCompete() {
        var authority = new EndpointClaimAuthority(ENDPOINT_A);

        var winner = authority.compareAndSet(new ClaimRequest(ENDPOINT_A, ClaimEpoch.NONE, OWNER_A));
        var loser = authority.compareAndSet(new ClaimRequest(ENDPOINT_A, ClaimEpoch.NONE, OWNER_B));

        assertTrue(winner instanceof ClaimResult.Acquired);
        assertTrue(loser instanceof ClaimResult.Rejected);
        assertEquals(OWNER_A, authority.state().owner().orElseThrow());
        assertEquals(new ClaimEpoch(1), authority.state().epoch());
    }

    @Test
    void preservesOwnerWhileEndpointIsOffline() {
        var authority = new EndpointClaimAuthority(ENDPOINT_A);
        authority.compareAndSet(new ClaimRequest(ENDPOINT_A, ClaimEpoch.NONE, OWNER_A));

        var offline = authority.withOnline(false);
        var competitor = authority.compareAndSet(new ClaimRequest(ENDPOINT_A, authority.state().epoch(), OWNER_B));

        assertFalse(offline.online());
        assertTrue(competitor instanceof ClaimResult.Rejected rejected
                && rejected.reason() == ClaimRejection.OWNER_CONFLICT);
        assertEquals(OWNER_A, authority.state().owner().orElseThrow());
    }

    @Test
    void pausesEveryLaneWhenEndpointDomainsOverlap() {
        var domains = new NativeTargetDomainRegistry();
        var sharedNativeGrid = new Object();

        var first = domains.observe(ENDPOINT_A, sharedNativeGrid);
        var second = domains.observe(ENDPOINT_B, sharedNativeGrid);

        assertEquals(ProviderTargetState.ACTIVE, first.state(ENDPOINT_A));
        assertEquals(ProviderTargetState.OVERLAPPING_SUBNET, second.state(ENDPOINT_A));
        assertEquals(ProviderTargetState.OVERLAPPING_SUBNET, second.state(ENDPOINT_B));
    }

    @Test
    void rotationPreservesIdentityClaimAndRemainderDestination() {
        var authority = new EndpointClaimAuthority(ENDPOINT_A);
        authority.compareAndSet(new ClaimRequest(ENDPOINT_A, ClaimEpoch.NONE, OWNER_A));
        var identity = OWNER_A.provider();
        var remainderDestination = new Object();
        var wiring = new ProviderWiringState(identity, new ProviderOrientation(ProviderFace.EAST),
                authority.state(), remainderDestination);

        var rotated = wiring.rotate(new ProviderOrientation(ProviderFace.NORTH));

        assertEquals(identity, rotated.provider());
        assertEquals(authority.state(), rotated.claim());
        assertSame(remainderDestination, rotated.nativeRemainderDestination());
        assertEquals(ProviderTargetState.ROTATION_PENDING, rotated.targetState());
    }

    @Test
    void rejectsTargetWhenSourceAndTargetUseSameNativeGrid() {
        var nativeGrid = new Object();

        var result = NativeTargetSeparation.classify(nativeGrid, nativeGrid, true);

        assertEquals(ProviderTargetState.SAME_SOURCE_GRID, result);
    }

    private static EndpointIdentity endpoint(long suffix) {
        return new EndpointIdentity(new EndpointId(new UUID(0, suffix)), new EndpointInstanceEpoch(1));
    }

    private static EndpointOwnerIdentity owner(long suffix) {
        return new EndpointOwnerIdentity(new ProviderIdentity(new ProviderId(new UUID(0, suffix)),
                new ProviderInstanceEpoch(1)));
    }
}
