package space.controlnet.ae2federation.power;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;

/**
 * A Federation cable's cross-section, narrow side first, in order of capacity: a plain cable and the five bundles. A Beam
 * Converter's front must be the largest cross-section of its domain, and the converter is twice its wider side long.
 */
public enum CableCrossSection {
    ONE_BY_ONE(1, 1, 1),
    ONE_BY_TWO(1, 2, 3),
    ONE_BY_THREE(1, 3, 4),
    TWO_BY_TWO(2, 2, 6),
    TWO_BY_THREE(2, 3, 9),
    THREE_BY_THREE(3, 3, FederationDomainNodeEvidence.UNLIMITED);

    /**
     * The cross-sections players can build today: a wrench forms bundles of these only, and only these converters form.
     * The others stay supported throughout, so adding one back here brings it back.
     */
    public static final Set<CableCrossSection> BUILDABLE = Set.copyOf(EnumSet.of(ONE_BY_ONE, TWO_BY_TWO,
            THREE_BY_THREE));

    private final int narrow;
    private final int wide;
    private final int capacityFactor;

    CableCrossSection(int narrow, int wide, int capacityFactor) {
        this.narrow = narrow;
        this.wide = wide;
        this.capacityFactor = capacityFactor;
    }

    /** The cross-section of two side lengths in either order, or empty when no cable has it. */
    public static Optional<CableCrossSection> of(int first, int second) {
        for (var section : values()) {
            if (section.narrow == Math.min(first, second) && section.wide == Math.max(first, second)) {
                return Optional.of(section);
            }
        }
        return Optional.empty();
    }

    /** The cross-section of a cable node that carries {@code capacityFactor} cables' capacity. */
    public static Optional<CableCrossSection> ofCapacityFactor(int capacityFactor) {
        for (var section : values()) {
            if (section.capacityFactor == capacityFactor) return Optional.of(section);
        }
        return Optional.empty();
    }

    /** The largest cross-section among cable nodes' capacity factors, or empty when there is no cable. */
    public static Optional<CableCrossSection> largest(Iterable<Integer> capacityFactors) {
        CableCrossSection largest = null;
        for (var factor : capacityFactors) {
            var section = ofCapacityFactor(factor).orElse(null);
            if (section != null && (largest == null || section.compareTo(largest) > 0)) largest = section;
        }
        return Optional.ofNullable(largest);
    }

    /** Whether players can build it today ({@link #BUILDABLE}). */
    public boolean buildable() {
        return BUILDABLE.contains(this);
    }

    public int narrow() {
        return narrow;
    }

    public int wide() {
        return wide;
    }

    /** How many cables' capacity it carries, or {@link FederationDomainNodeEvidence#UNLIMITED}. */
    public int capacityFactor() {
        return capacityFactor;
    }

    /** The length of the Beam Converter whose front is this cross-section. */
    public int converterLength() {
        return 2 * wide;
    }

    public int converterBlocks() {
        return narrow * wide * converterLength();
    }

    @Override
    public String toString() {
        return narrow + "x" + wide;
    }
}
