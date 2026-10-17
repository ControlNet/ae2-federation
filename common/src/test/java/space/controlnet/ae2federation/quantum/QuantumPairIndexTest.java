package space.controlnet.ae2federation.quantum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.power.CableCrossSection;
import space.controlnet.ae2federation.quantum.QuantumPairIndex.Cube;
import space.controlnet.ae2federation.quantum.QuantumPairIndex.Status;

/** Two loaded cubes of one pair id and one size link; anything else leaves each cube with a reason. */
final class QuantumPairIndexTest {
    private static Cube cube(String dimension, int x, CableCrossSection section, long pairId) {
        return new Cube(dimension, new BlockPos(x, 64, 0), section, pairId);
    }

    @Test
    void twoCubesOfOnePairLinkAcrossDimensions() {
        var index = new QuantumPairIndex();
        var here = cube("minecraft:overworld", 0, CableCrossSection.TWO_BY_TWO, 7);
        var there = cube("minecraft:the_nether", 0, CableCrossSection.TWO_BY_TWO, 7);
        assertEquals(Set.of(7L), index.put(here));
        assertEquals(new Status(Status.Kind.PARTNER_NOT_LOADED, null), index.status(here));
        index.put(there);
        assertEquals(new Status(Status.Kind.LINKED, there), index.status(here));
        assertEquals(new Status(Status.Kind.LINKED, here), index.status(there));
    }

    @Test
    void aCubeWithoutAPairIsNotBound() {
        var index = new QuantumPairIndex();
        var cube = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 0);
        assertEquals(Set.of(), index.put(cube), "pair 0 is no pair");
        assertEquals(new Status(Status.Kind.NOT_BOUND, null), index.status(cube));
    }

    @Test
    void anotherSizeDoesNotLink() {
        var index = new QuantumPairIndex();
        var small = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 3);
        var large = cube("minecraft:overworld", 20, CableCrossSection.THREE_BY_THREE, 3);
        index.put(small);
        index.put(large);
        assertEquals(new Status(Status.Kind.WRONG_SIZE, null), index.status(small));
        assertEquals(new Status(Status.Kind.WRONG_SIZE, null), index.status(large));
    }

    @Test
    void aThirdCubeOfOnePairLinksNone() {
        var index = new QuantumPairIndex();
        var first = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 5);
        var second = cube("minecraft:overworld", 10, CableCrossSection.ONE_BY_ONE, 5);
        var third = cube("minecraft:overworld", 20, CableCrossSection.ONE_BY_ONE, 5);
        index.put(first);
        index.put(second);
        index.put(third);
        assertEquals(new Status(Status.Kind.MORE_THAN_TWO, null), index.status(first));
        assertEquals(Set.of(5L), index.remove("minecraft:overworld", third.min()));
        assertEquals(new Status(Status.Kind.LINKED, second), index.status(first), "the third gone, the two link");
    }

    @Test
    void aCubeThatChangesPairOrSizeIsReplaced() {
        var index = new QuantumPairIndex();
        var cube = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 1);
        var partner = cube("minecraft:overworld", 10, CableCrossSection.ONE_BY_ONE, 1);
        index.put(cube);
        index.put(partner);
        var rebound = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 2);
        assertEquals(Set.of(1L, 2L), index.put(rebound), "both the old and the new pair change");
        assertEquals(new Status(Status.Kind.PARTNER_NOT_LOADED, null), index.status(partner));
        assertEquals(Set.of(), index.put(rebound), "the same cube again changes nothing");
        assertEquals(Set.of(), index.remove("minecraft:overworld", new BlockPos(99, 64, 0)), "unknown cube");
    }

    @Test
    void aDimensionThatUnloadsTakesItsCubesAlong() {
        var index = new QuantumPairIndex();
        var here = cube("minecraft:overworld", 0, CableCrossSection.ONE_BY_ONE, 4);
        var there = cube("minecraft:the_nether", 0, CableCrossSection.ONE_BY_ONE, 4);
        index.put(here);
        index.put(there);
        assertEquals(Set.of(4L), index.removeDimension("minecraft:the_nether"));
        assertEquals(new Status(Status.Kind.PARTNER_NOT_LOADED, null), index.status(here));
        assertEquals(Set.of(), index.removeDimension("minecraft:the_end"));
    }
}
