package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleShape;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;

/** A Beam Converter is sized by the largest cable cross-section of its domain. */
final class CableCrossSectionTest {
    @Test
    void everyCapacityFactorNamesExactlyOneCrossSection() {
        var factors = new HashSet<Integer>();
        for (var section : CableCrossSection.values()) {
            assertTrue(factors.add(section.capacityFactor()), "factor " + section.capacityFactor() + " is taken twice");
            assertEquals(Optional.of(section), CableCrossSection.ofCapacityFactor(section.capacityFactor()));
        }
        assertEquals(Optional.empty(), CableCrossSection.ofCapacityFactor(2), "no cable carries two cables' capacity");
    }

    @Test
    void theFactorsAreTheBundlesOwn() {
        assertEquals(1, CableCrossSection.ONE_BY_ONE.capacityFactor());
        for (var section : CableCrossSection.values()) {
            if (section == CableCrossSection.ONE_BY_ONE) continue;
            var shape = BundleShape.of(section.narrow(), section.wide()).orElseThrow();
            assertEquals(shape.multiplier().orElse(FederationDomainNodeEvidence.UNLIMITED), section.capacityFactor(),
                    section + " must carry what its bundle carries");
        }
    }

    @Test
    void theOrderIsByCapacity() {
        assertEquals(List.of(CableCrossSection.ONE_BY_ONE, CableCrossSection.ONE_BY_TWO, CableCrossSection.ONE_BY_THREE,
                CableCrossSection.TWO_BY_TWO, CableCrossSection.TWO_BY_THREE, CableCrossSection.THREE_BY_THREE),
                List.of(CableCrossSection.values()));
        assertEquals(Optional.of(CableCrossSection.TWO_BY_TWO),
                CableCrossSection.largest(List.of(4, 1, 6, 3)), "a 2x2 bundle outranks a 1x3 one");
        assertEquals(Optional.of(CableCrossSection.THREE_BY_THREE),
                CableCrossSection.largest(List.of(9, FederationDomainNodeEvidence.UNLIMITED)));
        assertEquals(Optional.empty(), CableCrossSection.largest(List.of()), "a domain without cable has no size");
    }

    @Test
    void aConverterIsTwiceItsWiderSideLong() {
        assertEquals(2, CableCrossSection.ONE_BY_ONE.converterLength());
        assertEquals(6, CableCrossSection.ONE_BY_THREE.converterLength());
        assertEquals(4, CableCrossSection.TWO_BY_TWO.converterLength());
        assertEquals(54, CableCrossSection.THREE_BY_THREE.converterBlocks());
        assertEquals(Optional.of(CableCrossSection.ONE_BY_THREE), CableCrossSection.of(3, 1), "either order");
        assertEquals(Optional.empty(), CableCrossSection.of(1, 4));
    }
}
