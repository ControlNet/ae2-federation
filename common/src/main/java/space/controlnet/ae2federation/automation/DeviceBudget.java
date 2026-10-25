package space.controlnet.ae2federation.automation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * A device's capacity against what its installations reserve (user decisions 2026-10-20). Compute, fixed bandwidth
 * and the number of installations are hard: an installation that does not fit is refused, and a device whose
 * reservations stop fitting, because a part was broken or a cell taken out, starts no runs. Elastic installations share the bandwidth left after the fixed
 * reservations evenly, by their shares; a share only limits what each move takes, it never pauses the device. Nothing
 * depends on what runs do: the budget follows from the capacity and the installations alone.
 */
public final class DeviceBudget {
    private final ComputingHost.Capacity capacity;
    private final Reserved reserved;
    private final boolean paused;
    private final Map<String, Long> perCall = new HashMap<>();
    private final Map<String, Long> bandwidth = new HashMap<>();

    /**
     * What a device's installations reserve together: compute, fixed bandwidth and their number, and whether any of
     * them is elastic. It is all a new installation is judged by, so the Automation tab is sent it.
     */
    public record Reserved(long compute, long bandwidth, int installations, boolean elastic) {
        public static final Reserved NOTHING = new Reserved(0, 0, 0, false);

        static Reserved of(Iterable<InstallationCost> costs) {
            long compute = 0;
            long fixed = 0;
            int installations = 0;
            var elastic = false;
            for (var cost : costs) {
                compute = InstallationCost.plus(compute, cost.compute());
                fixed = InstallationCost.plus(fixed, cost.bandwidth());
                installations++;
                elastic |= cost.elastic();
            }
            return new Reserved(compute, fixed, installations, elastic);
        }
    }

    private DeviceBudget(ComputingHost.Capacity capacity, Map<String, InstallationCost> costs) {
        this.capacity = capacity;
        this.reserved = Reserved.of(costs.values());
        long shares = 0;
        for (var cost : costs.values()) {
            shares += cost.shares();
        }
        this.paused = !capacity.unlimited() && (reserved.compute() > capacity.compute()
                || reserved.bandwidth() > capacity.bandwidth() || costs.size() > capacity.installations());
        var share = capacity.unlimited() || shares == 0 ? 0
                : Math.max(0, capacity.bandwidth() - reserved.bandwidth()) / shares;
        costs.forEach((id, cost) -> {
            if (!cost.elastic()) {
                bandwidth.put(id, cost.bandwidth());
            } else if (!capacity.unlimited()) {
                perCall.put(id, share / InstallationCost.TICKS);
                bandwidth.put(id, InstallationCost.times(share, cost.shares()));
            }
        });
    }

    static DeviceBudget of(ComputingHost.Capacity capacity, Map<String, InstallationCost> costs) {
        return new DeviceBudget(capacity, costs);
    }

    ComputingHost.Capacity capacity() {
        return capacity;
    }

    Reserved reserved() {
        return reserved;
    }

    /**
     * The bytes a second an installation has: its reservation, or an elastic one's shares of what is left; no limit on
     * an unlimited device.
     */
    long bandwidth(String id) {
        return bandwidth.getOrDefault(id, Long.MAX_VALUE);
    }

    /** Whether the device starts no runs: its fixed reservations do not fit its capacity. */
    boolean paused() {
        return paused;
    }

    /**
     * The most bytes one move of an installation's run takes: its share's bytes per tick when it is elastic, no limit
     * otherwise.
     */
    long perCall(String id) {
        return perCall.getOrDefault(id, Long.MAX_VALUE);
    }

    /**
     * Why the device cannot hold an installation costing {@code after} beside {@code others}; empty when it can.
     * {@code before} is what the installation cost until now, for a change of its settings; a change that asks for
     * no more is always taken. Elastic installations need some bandwidth left after the fixed reservations, so neither
     * an elastic installation nor a fixed one may leave them none (the latter an assistant choice).
     */
    static Optional<String> refusal(ComputingHost.Capacity capacity, Map<String, InstallationCost> others,
            @Nullable InstallationCost before, InstallationCost after) {
        if (capacity.unlimited() || before != null && after.compute() <= before.compute()
                && after.bandwidth() <= before.bandwidth() && (!after.elastic() || before.elastic())) {
            return Optional.empty();
        }
        return refusal(capacity, Reserved.of(others.values()), before == null, after);
    }

    /** Why the device cannot hold one more installation costing {@code after} beside what the others reserve. */
    public static Optional<String> refusal(ComputingHost.Capacity capacity, Reserved others, InstallationCost after) {
        return capacity.unlimited() ? Optional.empty() : refusal(capacity, others, true, after);
    }

    private static Optional<String> refusal(ComputingHost.Capacity capacity, Reserved others, boolean adding,
            InstallationCost after) {
        if (adding && others.installations() + 1 > capacity.installations()) {
            return Optional.of("the device runs " + capacity.installations() + " processes and has none free");
        }
        long compute = others.compute();
        long fixed = others.bandwidth();
        var elastic = after.elastic() || others.elastic();
        if (InstallationCost.plus(compute, after.compute()) > capacity.compute()) {
            return Optional.of("it needs " + after.compute() + " CU and the device has "
                    + Math.max(0, capacity.compute() - compute) + " of " + capacity.compute() + " free");
        }
        var total = InstallationCost.plus(fixed, after.bandwidth());
        if (total > capacity.bandwidth()) {
            return Optional.of("it needs " + after.bandwidth() + " B/s and the device has "
                    + Math.max(0, capacity.bandwidth() - fixed) + " of " + capacity.bandwidth() + " free");
        }
        if (elastic && total >= capacity.bandwidth()) {
            return Optional.of(after.elastic() ? "it moves without a bound and the device has no bandwidth left"
                    : "it would leave no bandwidth for the installations that move without a bound");
        }
        return Optional.empty();
    }
}
