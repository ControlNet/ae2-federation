package space.controlnet.ae2federation.energy;

import appeng.api.networking.energy.IEnergyService;
import appeng.me.service.EnergyService;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * The energy pool a Grid is in: every Grid AE2 joins to it through Quartz Fibers or Federation energy sharing,
 * walked the way AE2 builds its energy overlay but without reconciling Federation sharing. The figures are summed
 * per Grid rather than probed with a simulated extraction, which a creative cell answers in full whatever is asked.
 *
 * @param services the pool's energy services, so a caller reading several Grids walks each pool once
 */
public record EnergyPoolReading(double stored, double max, Set<EnergyService> services) {
    /** The pool of {@code energy}, or {@code null} when it is not AE2's own energy service. */
    public static EnergyPoolReading measure(IEnergyService energy) {
        if (!(energy instanceof EnergyService start)) return null;
        var seen = Collections.newSetFromMap(new IdentityHashMap<EnergyService, Boolean>());
        var pending = new ArrayDeque<EnergyService>();
        pending.add(start);
        double stored = 0;
        double max = 0;
        while (!pending.isEmpty()) {
            var service = pending.pop();
            if (!seen.add(service)) continue;
            stored += service.getStoredPower();
            max += service.getMaxStoredPower();
            for (var connection : service.getOverlayGridConnections()) {
                // A Federation link answers without reconciling sharing; describing a pool must not change it.
                pending.addAll(connection instanceof FederationEnergyConnection federation
                        ? federation.listedEnergyServices() : connection.connectedEnergyServices());
            }
        }
        return new EnergyPoolReading(stored, max, Collections.unmodifiableSet(seen));
    }

    /** How many Grids share the pool. */
    public int grids() {
        return services.size();
    }
}
