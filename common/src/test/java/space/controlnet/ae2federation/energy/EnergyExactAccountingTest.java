package space.controlnet.ae2federation.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EnergyExactAccountingTest {
    @Test
    void convertsOnlyExactlyRepresentableNanoAeAmounts() {
        assertEquals(1_250_000_000L, EnergyBindingService.nanoAe(1.25));
        assertThrows(ArithmeticException.class, () -> EnergyBindingService.nanoAe(0.0000000001));
        assertThrows(ArithmeticException.class, () -> EnergyBindingService.nanoAe(Double.MAX_VALUE));
    }
}
