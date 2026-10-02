package space.controlnet.ae2federation.storage.mount;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.policy.BindingDiagnostic.Reason;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.storage.mount.StorageRuleReason.Source;

class StorageRuleReasonTest {
    @Test
    void aWorkingRuleHasNoReason() {
        assertEquals(Optional.empty(), classify(PolicyActivationState.ACTIVE, false, false, Source.READY, false));
    }

    @Test
    void anInactiveRuleNamesItsActivation() {
        assertEquals(Optional.of(Reason.NETWORK_PAIR_DISCONNECTED),
                classify(PolicyActivationState.DISCONNECTED, true, true, Source.EMPTY, true));
        assertEquals(Optional.of(Reason.BACKEND_UNREADY),
                classify(PolicyActivationState.BACKEND_UNREADY, false, false, Source.READY, false));
    }

    @Test
    void referencesComeBeforeAccessAndSource() {
        assertEquals(Optional.of(Reason.DOMAIN_REFERENCE_MISSING),
                classify(PolicyActivationState.ACTIVE, true, true, Source.EMPTY, true));
    }

    @Test
    void aRuleAllowingNothingSaysSoBeforeTheSource() {
        assertEquals(Optional.of(Reason.STORAGE_ACCESS_NONE),
                classify(PolicyActivationState.ACTIVE, false, true, Source.EMPTY, true));
    }

    @Test
    void aRejectedSourceIsReportedByItsDiagnosticAlone() {
        assertEquals(Optional.empty(), classify(PolicyActivationState.ACTIVE, false, false, Source.REJECTED, true));
    }

    @Test
    void theProviderSourceStateFollows() {
        assertEquals(Optional.of(Reason.STORAGE_SOURCE_EMPTY),
                classify(PolicyActivationState.ACTIVE, false, false, Source.EMPTY, true));
        assertEquals(Optional.of(Reason.BACKEND_UNREADY),
                classify(PolicyActivationState.ACTIVE, false, false, Source.UNREADY, true));
    }

    @Test
    void anExhaustedCompileIsLast() {
        assertEquals(Optional.of(Reason.STORAGE_COMPILE_BUDGET),
                classify(PolicyActivationState.ACTIVE, false, false, Source.READY, true));
    }

    private static Optional<Reason> classify(PolicyActivationState activation, boolean referencesMissing,
            boolean accessNone, Source source, boolean compileFailed) {
        return StorageRuleReason.classify(activation, referencesMissing, accessNone, source, compileFailed);
    }
}
