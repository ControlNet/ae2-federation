package space.controlnet.ae2federation.automation;

import java.util.List;
import java.util.function.ToLongFunction;
import space.controlnet.ae2federation.script.Cost;

/**
 * What an installation reserves on its device (user decisions 2026-10-20): compute in CU, its graph nodes, and fixed
 * bandwidth in AE2 bytes per second, 20 times its largest move, since a run makes at most one effect a tick. Parallel
 * mode reserves both for each of its runs. An installation with a move that has no bound is elastic: it reserves no
 * bandwidth and takes {@code shares} shares of what its device has left.
 */
public record InstallationCost(long compute, long bandwidth, int shares) {
    /** Ticks a second: the effects one run can make in a second. */
    static final int TICKS = 20;

    public boolean elastic() {
        return shares > 0;
    }

    /**
     * The cost of a function with {@code nodes} graph nodes and these moves; {@code amountPerByte} is how much of a
     * move's resources fits in an AE2 byte, the least of any it may move.
     */
    public static InstallationCost of(int nodes, List<Cost.Move> moves, Installation.Overlap overlap,
            ToLongFunction<Cost.Resources> amountPerByte) {
        var runs = overlap == Installation.Overlap.PARALLEL ? Installation.PARALLEL_LIMIT : 1;
        if (moves.stream().anyMatch(move -> move.most().isEmpty())) {
            return new InstallationCost(times(nodes, runs), 0, runs);
        }
        long bytes = 0;
        for (var move : moves) {
            var perByte = Math.max(1, amountPerByte.applyAsLong(move.resource()));
            var most = Math.max(0, move.most().getAsLong());
            bytes = Math.max(bytes, most / perByte + (most % perByte == 0 ? 0 : 1));
        }
        return new InstallationCost(times(nodes, runs), times(times(bytes, TICKS), runs), 0);
    }

    /** A product that stops at the largest long rather than wrapping round. */
    static long times(long a, long b) {
        var high = Math.multiplyHigh(a, b);
        var low = a * b;
        return high != 0 || low < 0 ? Long.MAX_VALUE : low;
    }

    static long plus(long a, long b) {
        var sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
