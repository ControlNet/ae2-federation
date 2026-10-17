package space.controlnet.ae2federation.power;

import java.util.EnumMap;
import java.util.Map;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;

/**
 * What each Federation block costs to run, in FE per tick, calibrated on AE2 (1 AE = 2 FE; a default device draws
 * 1 AE/t). Every bundle cell and every Router or Switch body block is its own node, so a domain pays per node.
 */
public final class OperatingPowerCosts {
    private OperatingPowerCosts() {
    }

    /** The cost of one node of {@code kind} at multiplier 1. */
    public static double perTick(FederationDomainNodeKind kind) {
        return switch (kind) {
            // AE2 cable is free, but here the beam is the energy's look; a bundle saves capacity, not energy.
            case CABLE -> 0.5;
            // AE2's default device and crafting-CPU block.
            case ROUTER, SWITCH, ENDPOINT, PATTERN_PROVIDER -> 2.0;
            // AE2 already charges the tunnel itself.
            case P2P_TUNNEL, OTHER -> 0.0;
        };
    }

    /** What a domain of {@code nodes} uses per tick under the current rule; nothing when power is not required. */
    public static Use use(Iterable<FederationDomainNodeEvidence> nodes) {
        var byKind = new EnumMap<FederationDomainNodeKind, Double>(FederationDomainNodeKind.class);
        if (!OperatingPowerRule.required()) return new Use(0, byKind);
        var multiplier = OperatingPowerRule.multiplier();
        var total = 0.0;
        for (var node : nodes) {
            var cost = perTick(node.kind()) * multiplier;
            if (cost == 0) continue;
            byKind.merge(node.kind(), cost, Double::sum);
            total += cost;
        }
        return new Use(total, byKind);
    }

    /** A domain's use per tick, in total and by block kind. */
    public record Use(double total, Map<FederationDomainNodeKind, Double> byKind) {
        public Use {
            byKind = Map.copyOf(byKind);
        }
    }
}
