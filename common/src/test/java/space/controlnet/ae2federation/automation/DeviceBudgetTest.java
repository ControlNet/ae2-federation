package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.function.Cost;
import space.controlnet.ae2federation.function.Expr;

/**
 * What installations reserve on a computing device and what the device lets them do (user decisions 2026-10-20):
 * compute and fixed bandwidth must fit, unbounded installations share what bandwidth is left, and a device whose fixed
 * reservations no longer fit starts nothing.
 */
final class DeviceBudgetTest {
    private static final Expr ITEM = new Expr.StrLit("n0", "minecraft:iron_ingot");
    private static final Expr FLUID = new Expr.StrLit("n0", "minecraft:water");
    private static final Expr ANY = new Expr.Name("n1", "resource");

    /** Items 8 per byte, fluids 8,000 per byte, and an unknown resource the most bytes of all: 8. */
    private static long perByte(Expr resource) {
        return resource == FLUID ? 8_000 : 8;
    }

    private static Cost.Move move(Expr resource, long most) {
        return new Cost.Move("ae2.move", resource, OptionalLong.of(most));
    }

    private static Cost.Move unbounded() {
        return new Cost.Move("ae2.import_from", ANY, OptionalLong.empty());
    }

    private static InstallationCost cost(int nodes, Installation.Overlap overlap, Cost.Move... moves) {
        return InstallationCost.of(nodes, List.of(moves), overlap, DeviceBudgetTest::perByte);
    }

    private static InstallationCost cost(int nodes, Cost.Move... moves) {
        return cost(nodes, Installation.Overlap.IGNORE, moves);
    }

    @Test
    void computeIsTheNodesAndBandwidthTwentyTimesTheLargestMoveInBytes() {
        var cost = cost(12, move(ITEM, 64), move(ITEM, 16), move(FLUID, 16_000));
        assertEquals(12, cost.compute());
        assertEquals(20 * 8, cost.bandwidth());
        assertFalse(cost.elastic());
    }

    @Test
    void aMoveOfPartOfAByteCostsAWholeByte() {
        assertEquals(20, cost(1, move(ITEM, 1)).bandwidth());
        assertEquals(0, cost(1, move(ITEM, 0)).bandwidth());
        assertEquals(0, cost(1).bandwidth());
    }

    @Test
    void parallelReservesFourTimesEverything() {
        var cost = cost(12, Installation.Overlap.PARALLEL, move(ITEM, 64));
        assertEquals(48, cost.compute());
        assertEquals(4 * 20 * 8, cost.bandwidth());
    }

    @Test
    void anUnboundedMoveMakesTheInstallationElasticWithNoFixedBandwidth() {
        var cost = cost(9, move(ITEM, 64), unbounded());
        assertTrue(cost.elastic());
        assertEquals(0, cost.bandwidth());
        assertEquals(1, cost.shares());
        assertEquals(4, cost(9, Installation.Overlap.PARALLEL, unbounded()).shares());
    }

    @Test
    void aHugeBoundSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, cost(1, Installation.Overlap.PARALLEL, move(ITEM, Long.MAX_VALUE - 1))
                .bandwidth());
    }

    private static final ComputingHost.Capacity DEVICE = new ComputingHost.Capacity(64, 1_000);

    @Test
    void anInstallationFitsWhenComputeAndBandwidthFit() {
        var others = Map.of("a", cost(40, move(ITEM, 8 * 20)));
        assertEquals(Optional.empty(), DeviceBudget.refusal(DEVICE, others, null, cost(24, move(ITEM, 8 * 30))));
        assertTrue(DeviceBudget.refusal(DEVICE, others, null, cost(25)).orElseThrow().contains("CU"));
        assertTrue(DeviceBudget.refusal(DEVICE, others, null, cost(1, move(ITEM, 8 * 31))).orElseThrow()
                .contains("B/s"));
    }

    @Test
    void anElasticInstallationFitsUnlessFixedBandwidthTakesItAll() {
        var half = Map.of("a", cost(1, move(ITEM, 8 * 25)));
        var full = Map.of("a", cost(1, move(ITEM, 8 * 50)));
        assertEquals(Optional.empty(), DeviceBudget.refusal(DEVICE, half, null, cost(1, unbounded())));
        assertTrue(DeviceBudget.refusal(DEVICE, full, null, cost(1, unbounded())).isPresent());
    }

    /** Assistant choice: elastic installations keep some bandwidth, so a fixed one may not take the last of it. */
    @Test
    void aFixedInstallationMayNotTakeTheBandwidthElasticOnesShare() {
        var elastic = Map.of("a", cost(1, unbounded()));
        assertEquals(Optional.empty(), DeviceBudget.refusal(DEVICE, elastic, null, cost(1, move(ITEM, 8 * 49))));
        assertTrue(DeviceBudget.refusal(DEVICE, elastic, null, cost(1, move(ITEM, 8 * 50))).isPresent());
    }

    /** A device already over its capacity still takes a change that asks for no more. */
    @Test
    void aChangeThatAsksForNoMoreIsAlwaysTaken() {
        var over = Map.of("a", cost(100));
        var before = cost(10, Installation.Overlap.PARALLEL, move(ITEM, 8));
        assertEquals(Optional.empty(), DeviceBudget.refusal(DEVICE, over, before, cost(10, move(ITEM, 8))));
        assertTrue(DeviceBudget.refusal(DEVICE, over, before, cost(41)).isPresent());
        assertTrue(DeviceBudget.refusal(DEVICE, over, before, cost(1, unbounded())).isPresent());
    }

    @Test
    void anUnlimitedDeviceTakesEverything() {
        var others = Map.of("a", cost(Integer.MAX_VALUE, move(ITEM, Long.MAX_VALUE)));
        assertEquals(Optional.empty(), DeviceBudget.refusal(ComputingHost.Capacity.UNLIMITED, others, null,
                cost(Integer.MAX_VALUE, Installation.Overlap.PARALLEL, move(ITEM, Long.MAX_VALUE))));
        var budget = DeviceBudget.of(ComputingHost.Capacity.UNLIMITED, Map.of("a", cost(1, unbounded())));
        assertFalse(budget.paused());
        assertEquals(Long.MAX_VALUE, budget.perCall("a"));
    }

    @Test
    void aDeviceIsPausedWhileItsFixedReservationsDoNotFit() {
        assertFalse(DeviceBudget.of(DEVICE, Map.of("a", cost(64, move(ITEM, 8 * 50)))).paused());
        assertTrue(DeviceBudget.of(DEVICE, Map.of("a", cost(65))).paused());
        assertTrue(DeviceBudget.of(DEVICE, Map.of("a", cost(1, move(ITEM, 8 * 51)))).paused());
        assertTrue(DeviceBudget.of(ComputingHost.Capacity.NONE, Map.of("a", cost(1))).paused());
        assertFalse(DeviceBudget.of(ComputingHost.Capacity.NONE, Map.of()).paused());
    }

    /** Elastic ones never pause a device: they share what is left, which may be nothing. */
    @Test
    void elasticInstallationsShareWhatIsLeftEvenlyByTheirShares() {
        var costs = new LinkedHashMap<String, InstallationCost>();
        costs.put("fixed", cost(1, move(ITEM, 8 * 20)));
        costs.put("one", cost(1, unbounded()));
        costs.put("four", cost(1, Installation.Overlap.PARALLEL, unbounded()));
        var budget = DeviceBudget.of(DEVICE, costs);
        assertFalse(budget.paused());
        // 1,000 - 400 = 600 B/s left, in five shares of 120 B/s: 6 bytes a call, for each of the parallel runs too.
        assertEquals(6, budget.perCall("one"));
        assertEquals(6, budget.perCall("four"));
        assertEquals(Long.MAX_VALUE, budget.perCall("fixed"));
        assertEquals(0, DeviceBudget.of(new ComputingHost.Capacity(64, 400), costs).perCall("one"));
    }
}
