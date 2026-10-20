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
final class DeviceBudget {
    private final ComputingHost.Capacity capacity;
    private final boolean paused;
    private final Map<String, Long> perCall = new HashMap<>();

    private DeviceBudget(ComputingHost.Capacity capacity, Map<String, InstallationCost> costs) {
        this.capacity = capacity;
        long compute = 0;
        long fixed = 0;
        long shares = 0;
        for (var cost : costs.values()) {
            compute = InstallationCost.plus(compute, cost.compute());
            fixed = InstallationCost.plus(fixed, cost.bandwidth());
            shares += cost.shares();
        }
        this.paused = !capacity.unlimited() && (compute > capacity.compute() || fixed > capacity.bandwidth()
                || costs.size() > capacity.installations());
        if (!capacity.unlimited() && shares > 0) {
            var share = Math.max(0, capacity.bandwidth() - fixed) / shares;
            costs.forEach((id, cost) -> {
                if (cost.elastic()) {
                    perCall.put(id, share / InstallationCost.TICKS);
                }
            });
        }
    }

    static DeviceBudget of(ComputingHost.Capacity capacity, Map<String, InstallationCost> costs) {
        return new DeviceBudget(capacity, costs);
    }

    ComputingHost.Capacity capacity() {
        return capacity;
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
        if (before == null && others.size() + 1 > capacity.installations()) {
            return Optional.of("the device holds " + capacity.installations() + " installations and has none free");
        }
        long compute = 0;
        long fixed = 0;
        var elastic = after.elastic();
        for (var cost : others.values()) {
            compute = InstallationCost.plus(compute, cost.compute());
            fixed = InstallationCost.plus(fixed, cost.bandwidth());
            elastic |= cost.elastic();
        }
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
