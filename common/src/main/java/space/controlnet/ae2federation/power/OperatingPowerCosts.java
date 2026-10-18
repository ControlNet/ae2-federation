package space.controlnet.ae2federation.power;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import space.controlnet.ae2federation.beam.BeamLinkRule;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.quantum.QuantumLinkService;

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
            case ROUTER, SWITCH, ENDPOINT, PATTERN_PROVIDER, AUTOMATION_INTERFACE -> 2.0;
            // AE2's Quantum Network Bridge: 22 AE/t per block, wherever the other end is.
            case QUANTUM_CUBE -> 44.0;
            // AE2 already charges the tunnel itself; a converter is the supply; a Beam Former's block is free and its
            // domain pays for each linked beam instead.
            case P2P_TUNNEL, CONVERTER, BEAM_FORMER, OTHER -> 0.0;
        };
    }

    /**
     * The cost of one linked Beam Former pair at multiplier 1, after Neeve's AE2 Beam Former: its square's blocks times
     * max(2, L)^1.05 AE, L being the blocks between the fronts.
     */
    public static double beamLink(CableCrossSection section, int gap) {
        return section.narrow() * section.wide() * Math.pow(Math.max(2, gap), 1.05) * 2;
    }

    /**
     * The section and length of the beam a former's anchor names, counted once per pair: on the anchor of the lower node
     * id. Empty for any other node. The anchors are the bodies' lowest corners on one line, so they differ along the beam
     * only.
     */
    public static Optional<Beam> beam(FederationDomainNodeEvidence node) {
        if (node.kind() != FederationDomainNodeKind.BEAM_FORMER) return Optional.empty();
        for (var port : node.ports().entrySet()) {
            if (!port.getKey().startsWith(BeamLinkRule.PORT)
                    || !(port.getValue() instanceof FederationDomainPortEvidence.Federation federation)) {
                continue;
            }
            var partner = federation.peer().node();
            if (node.nodeId().compareTo(partner) > 0) return Optional.empty();
            var section = CableCrossSection.ofCapacityFactor(node.capacityFactor());
            if (section.isEmpty()) return Optional.empty();
            var from = BlockPos.of(node.nodeId().blockPosition());
            var to = BlockPos.of(partner.blockPosition());
            int span = Math.abs(to.getX() - from.getX()) + Math.abs(to.getY() - from.getY())
                    + Math.abs(to.getZ() - from.getZ());
            return Optional.of(new Beam(section.get(), Math.max(0, span - 1)));
        }
        return Optional.empty();
    }

    /** Whether a Quantum Cube's node names a linked partner: only a linked body's lowest block does. */
    public static boolean quantumLinked(FederationDomainNodeEvidence node) {
        return node.kind() == FederationDomainNodeKind.QUANTUM_CUBE
                && node.ports().keySet().stream().anyMatch(port -> port.startsWith(QuantumLinkService.PORT));
    }

    /** A linked beam: its square and the blocks between the fronts. */
    public record Beam(CableCrossSection section, int gap) {
    }

    /**
     * How the statistics page groups a domain's use: bundle cells apart from plain cable, devices together, Beam Former
     * links and Quantum Cubes apart.
     */
    public enum Group {
        CABLES, BUNDLES, ROUTERS, SWITCHES, DEVICES, BEAMS, QUANTUM_CUBES;

        public static java.util.Optional<Group> of(FederationDomainNodeEvidence node) {
            return switch (node.kind()) {
                case CABLE -> java.util.Optional.of(node.capacityFactor() == 1 ? CABLES : BUNDLES);
                case ROUTER -> java.util.Optional.of(ROUTERS);
                case SWITCH -> java.util.Optional.of(SWITCHES);
                case ENDPOINT, PATTERN_PROVIDER, AUTOMATION_INTERFACE -> java.util.Optional.of(DEVICES);
                case QUANTUM_CUBE -> java.util.Optional.of(QUANTUM_CUBES);
                case P2P_TUNNEL, CONVERTER, BEAM_FORMER, OTHER -> java.util.Optional.empty();
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
            var beam = beam(node);
            if (beam.isPresent()) {
                var link = beamLink(beam.get().section(), beam.get().gap()) * multiplier;
                byGroup.merge(Group.BEAMS, link, Double::sum);
                total += link;
            }
            var cost = perTick(node.kind()) * multiplier;
            // An unformed cube block links nothing and, as AE2's unformed bridge, costs nothing.
            if (node.kind() == FederationDomainNodeKind.QUANTUM_CUBE && node.ports().isEmpty()) cost = 0;
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
