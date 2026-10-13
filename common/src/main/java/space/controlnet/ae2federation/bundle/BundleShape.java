package space.controlnet.ae2federation.bundle;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A bundled cable's cross-section, narrow side first: 1x2, 1x3, 2x2, 2x3 or 3x3. Each carries a fixed multiple of one
 * cable's capacity; a 3x3 bundle is unlimited.
 */
public record BundleShape(int narrow, int wide) {
    public static final int MAX_SIDE = 3;

    public BundleShape {
        if (narrow < 1 || narrow > wide || wide > MAX_SIDE || narrow * wide < 2) {
            throw new IllegalArgumentException("Not a bundle cross-section: " + narrow + "x" + wide);
        }
    }

    /** The cross-section of two side lengths in either order, or empty when they make no bundle. */
    public static Optional<BundleShape> of(int first, int second) {
        int narrow = Math.min(first, second);
        int wide = Math.max(first, second);
        if (narrow < 1 || wide > MAX_SIDE || narrow * wide < 2) return Optional.empty();
        return Optional.of(new BundleShape(narrow, wide));
    }

    public int area() {
        return narrow * wide;
    }

    /** How many cables' capacity the bundle carries, or empty when it is unlimited. */
    public OptionalInt multiplier() {
        return switch (area()) {
            case 2 -> OptionalInt.of(3);
            case 3 -> OptionalInt.of(4);
            case 4 -> OptionalInt.of(6);
            case 6 -> OptionalInt.of(9);
            default -> OptionalInt.empty();
        };
    }

    @Override
    public String toString() {
        return narrow + "x" + wide;
    }
}
