package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class BindingDiagnosticTest {
    @Test
    void aPolicyEditInvalidatesTheOldBackendReason() {
        var diagnostic = new BindingDiagnostic(BindingDiagnostic.Reason.CRAFTING_CPU_MISSING, new PolicyRevision(4), 9);
        assertTrue(diagnostic.matches(new PolicyRevision(4), 9));
        assertFalse(diagnostic.matches(new PolicyRevision(5), 9));
    }

    @Test
    void topologyChangesInvalidateTheOldBackendReason() {
        var diagnostic = new BindingDiagnostic(BindingDiagnostic.Reason.ENERGY_SOURCE_MISSING, new PolicyRevision(4), 9);
        assertFalse(diagnostic.matches(new PolicyRevision(4), 10));
        assertFalse(diagnostic.matches(new PolicyRevision(4), 8));
    }
}
