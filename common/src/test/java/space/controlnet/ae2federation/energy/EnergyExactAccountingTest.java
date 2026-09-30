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

    @Test
    void wholeAmountsConvertLikeTheDecimalPath() {
        for (var amount : new double[] {0, 1, 2, 250, 1_000_000_250, 9_223_372_036.0}) {
            assertEquals(java.math.BigDecimal.valueOf(amount).movePointRight(9).longValueExact(),
                    EnergyBindingService.nanoAe(amount), "amount " + amount);
        }
        assertThrows(ArithmeticException.class, () -> EnergyBindingService.nanoAe(9_223_372_037.0),
                "A whole amount beyond the nano-AE range must still be rejected");
        assertThrows(ArithmeticException.class, () -> EnergyBindingService.nanoAe(0x1p60));
    }
}
