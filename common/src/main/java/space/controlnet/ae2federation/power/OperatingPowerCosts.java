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
            // AE2 already charges the tunnel itself; a converter is the supply.
            case P2P_TUNNEL, CONVERTER, OTHER -> 0.0;
        };
    }

    /** How the statistics page groups a domain's use: bundle cells apart from plain cable, devices together. */
    public enum Group {
        CABLES, BUNDLES, ROUTERS, SWITCHES, DEVICES;

        public static java.util.Optional<Group> of(FederationDomainNodeEvidence node) {
            return switch (node.kind()) {
                case CABLE -> java.util.Optional.of(node.capacityFactor() == 1 ? CABLES : BUNDLES);
                case ROUTER -> java.util.Optional.of(ROUTERS);
                case SWITCH -> java.util.Optional.of(SWITCHES);
                case ENDPOINT, PATTERN_PROVIDER -> java.util.Optional.of(DEVICES);
                case P2P_TUNNEL, CONVERTER, OTHER -> java.util.Optional.empty();
            };
        }

        /** The group's name in statistics series. */
        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** What a domain of {@code nodes} uses per tick under the current rule; nothing when power is not required. */
    public static Use use(Iterable<FederationDomainNodeEvidence> nodes) {
        var byGroup = new EnumMap<Group, Double>(Group.class);
        if (!OperatingPowerRule.required()) return new Use(0, byGroup);
        var multiplier = OperatingPowerRule.multiplier();
        var total = 0.0;
        for (var node : nodes) {
            var cost = perTick(node.kind()) * multiplier;
            if (cost == 0) continue;
            var group = Group.of(node);
            if (group.isPresent()) byGroup.merge(group.get(), cost, Double::sum);
            total += cost;
        }
        return new Use(total, byGroup);
    }

    /** A domain's use per tick, in total and by group. */
    public record Use(double total, Map<Group, Double> byGroup) {
        public Use {
            byGroup = Map.copyOf(byGroup);
        }
    }
}
