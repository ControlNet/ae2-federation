package space.controlnet.ae2federation.compat;

import appeng.api.networking.IGrid;
import appeng.api.util.AEColor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;

/**
 * Networks joined in a chain by Bridges, as a player joins neighbouring bases: each network is a line of coloured
 * cable along z at {@link #Y}, and each Bridge sits on one network's cable with its outer side touching the next
 * network's parallel cable. Every Bridge forms a two-network domain of its own, so networks two Bridges apart share no
 * domain and no rule; they reach each other only through re-export. The Bridges go in one at a time, each once the one
 * before has joined its domain.
 */
final class BridgeChainFixture implements AutoCloseable {
    static final int Y = 4;

    /** A network's cable from {@code fromZ} to {@code toZ} at {@code x}. */
    record Network(String name, int x, int fromZ, int toZ, AEColor color) {
    }

    /** A Bridge on {@code host}'s cable at {@code z}, its outer side towards {@code outer}'s cable beside it. */
    record Link(String host, String outer, int z) {
    }

    private final GameTestHelper helper;
    private final BridgeFixtures bridges;
    private final Map<String, Network> networks = new LinkedHashMap<>();
    private final List<Link> links;
    private final List<MultipartBridgePart> placed = new ArrayList<>();
    private final Set<PolicyKey> configured = new LinkedHashSet<>();
    private boolean energyShared;

    BridgeChainFixture(GameTestHelper helper, List<Network> networks, List<Link> links) {
        this.helper = helper;
        this.links = List.copyOf(links);
        bridges = new BridgeFixtures(helper);
        for (var network : networks) {
            this.networks.put(network.name(), network);
            for (int z = network.fromZ(); z <= network.toZ(); z++) {
                bridges.nativePorts().placeCable(new BlockPos(network.x(), Y, z), network.color());
            }
        }
        for (var link : links) {
            var host = this.networks.get(link.host());
            var outer = this.networks.get(link.outer());
            if (Math.abs(host.x() - outer.x()) != 1 || link.z() < outer.fromZ() || link.z() > outer.toZ()) {
                throw new IllegalArgumentException("The Bridge " + link + " does not touch " + link.outer() + "'s cable");
            }
        }
    }

    /** {@code network}'s cable at {@code z}. */
    BlockPos cable(String network, int z) {
        return new BlockPos(networks.get(network).x(), Y, z);
    }

    /**
     * Places the next Bridge once every network has its own confirmed identity and the Bridge before has joined its
     * domain; true once every Bridge has.
     */
    boolean built() {
        var grids = new HashSet<IGrid>();
        for (var name : networks.keySet()) {
            var grid = grid(name);
            if (grid == null || !grids.add(grid) || FederationDomainRegistryAccess.confirmedNetworkId(grid).isEmpty()) {
                return false;
            }
        }
        for (int index = 0; index < placed.size(); index++) {
            if (!joined(index)) return false;
        }
        if (placed.size() < links.size()) {
            var link = links.get(placed.size());
            var host = networks.get(link.host());
            var side = networks.get(link.outer()).x() > host.x() ? Direction.EAST : Direction.WEST;
            placed.add(bridges.placeBridge(cable(link.host(), link.z()), side));
            return false;
        }
        return true;
    }

    /**
     * Switches on ME power for every Bridge's pair once, as the guide's steps do; true once every network is powered
     * from the one network with an energy cell, through the pools joining up along the chain.
     */
    boolean powered() {
        if (!energyShared) {
            for (var link : links) {
                rule(link.host(), link.outer(), PolicyCapability.ME_POWER,
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
            }
            energyShared = true;
        }
        return networks.keySet().stream().allMatch(name -> grid(name).getEnergyService().isNetworkPowered());
    }

    /** How far each Bridge has got, for a test that waits too long. */
    String readiness() {
        var states = new ArrayList<String>();
        for (int index = 0; index < placed.size(); index++) {
            states.add(links.get(index) + "=" + placed.get(index).operationalReason() + (joined(index) ? " joined" : ""));
        }
        return "bridges " + states + " of " + links.size();
    }

    IGrid grid(String network) {
        var cable = networks.get(network);
        var node = appeng.api.networking.GridHelper.getExposedNode(helper.getLevel(),
                helper.absolutePos(new BlockPos(cable.x(), Y, cable.fromZ())), Direction.UP);
        return node == null ? null : node.getGrid();
    }

    NetworkId network(String network) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid(network)).orElseThrow();
    }

    /** Whether the two networks share a Federation domain, as the two ends of one Bridge do. */
    boolean shareDomain(String first, String second) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var domains = registry.federationdomainsFor(network(first));
        return registry.federationdomainsFor(network(second)).stream().anyMatch(domains::contains);
    }

    /** The rule "{@code user} uses {@code source}'s {@code capability}". */
    PolicyKey key(String user, String source, PolicyCapability capability) {
        return new PolicyKey(network(user), network(source), capability);
    }

    /** Sets a rule as its switch in the Federation screen does; it must be accepted. */
    void rule(String user, String source, PolicyCapability capability, PolicyRule rule) {
        var key = key(user, source, capability);
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule " + key + " was refused: " + result);
        configured.add(key);
    }

    private boolean joined(int index) {
        var bridge = placed.get(index);
        bridge.onUpdateShape(bridge.getSide());
        var link = links.get(index);
        return bridge.membershipCandidate().isPresent() && shareDomain(link.host(), link.outer());
    }

    /**
     * Switches off the rules the test set: the chain's networks stay in the level after the test, and live rules would
     * keep their mounts and projections reconciled while later tests run.
     */
    @Override
    public void close() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : configured) {
            policies.configured(key).filter(record -> record.rule().enabled()).ifPresent(record -> policies.edit(
                    new PolicyEdit(key, record.revision(), record.rule().withEnabled(false))));
        }
        bridges.close();
    }
}
