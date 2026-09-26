package space.controlnet.ae2federation.energy;

import space.controlnet.ae2federation.policy.BindingDiagnostic;

final class EnergyBackendUnavailableException extends IllegalStateException {
    private final BindingDiagnostic.Reason reason;

    EnergyBackendUnavailableException(BindingDiagnostic.Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    BindingDiagnostic.Reason reason() { return reason; }
}
