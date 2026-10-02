package space.controlnet.ae2federation.storage.mount;

import java.util.Optional;
import space.controlnet.ae2federation.policy.BindingDiagnostic;
import space.controlnet.ae2federation.policy.PolicyActivationState;

/** Why an enabled storage rule shares nothing, from what the last refresh saw of it; the first reason found wins. */
final class StorageRuleReason {
    /** The provider network's export sources at the last refresh. */
    enum Source {
        READY,
        /** Its domain was rejected; the provenance diagnostic says why. */
        REJECTED,
        EMPTY,
        UNREADY
    }

    private StorageRuleReason() {
    }

    static Optional<BindingDiagnostic.Reason> classify(PolicyActivationState activation, boolean referencesMissing,
            boolean accessNone, Source source, boolean compileFailed) {
        if (activation != PolicyActivationState.ACTIVE) {
            return Optional.of(BindingDiagnostic.inactiveReason(activation));
        }
        if (referencesMissing) return Optional.of(BindingDiagnostic.Reason.DOMAIN_REFERENCE_MISSING);
        if (accessNone) return Optional.of(BindingDiagnostic.Reason.STORAGE_ACCESS_NONE);
        return switch (source) {
            case REJECTED -> Optional.empty();
            case EMPTY -> Optional.of(BindingDiagnostic.Reason.STORAGE_SOURCE_EMPTY);
            case UNREADY -> Optional.of(BindingDiagnostic.Reason.BACKEND_UNREADY);
            case READY -> compileFailed ? Optional.of(BindingDiagnostic.Reason.STORAGE_COMPILE_BUDGET) : Optional.empty();
        };
    }
}
