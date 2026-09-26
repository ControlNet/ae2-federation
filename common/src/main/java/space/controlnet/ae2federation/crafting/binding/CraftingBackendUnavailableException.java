package space.controlnet.ae2federation.crafting.binding;

import space.controlnet.ae2federation.policy.BindingDiagnostic;

final class CraftingBackendUnavailableException extends RuntimeException {
    private final BindingDiagnostic.Reason reason;

    CraftingBackendUnavailableException(BindingDiagnostic.Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    BindingDiagnostic.Reason reason() { return reason; }
}
