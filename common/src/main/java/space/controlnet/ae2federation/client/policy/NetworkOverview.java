package space.controlnet.ae2federation.client.policy;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.parts.AEBasePart;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import space.controlnet.ae2federation.identity.IdentitySettlement;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityRegistry;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.identity.NodeLineage;

/**
 * Per-network facts for the workspace: identity state, a representative location and AE2's own service figures.
 * Everything is read from services AE2 already maintains, so one overview costs a pass over the live identity claims
 * plus a few getters per member network; callers throttle how often they build one.
 */
public final class NetworkOverview {
    /** Enough to outline a base on the location map and in the world without flooding the menu sync. */
    static final int MAX_BLOCKS = 256;

    private NetworkOverview() {
    }

    public static JsonArray describe(ServerLevel level, Collection<NetworkId> members) {
        var grids = gridsByNetwork(level);
        var out = new JsonArray();
        for (var member : members) {
            out.add(describe(member, grids.getOrDefault(member, List.of())));
        }
        return out;
    }

    /** The live Grids claiming each network; more than one only while a split is unresolved. */
    static Map<NetworkId, List<IGrid>> gridsByNetwork(ServerLevel level) {
        var byNetwork = new HashMap<NetworkId, List<IGrid>>();
        NetworkIdentityRegistry.get(level).liveClaims().forEach((grid, lineages) -> lineages.stream()
                .map(NodeLineage::networkId).distinct()
                .forEach(network -> byNetwork.computeIfAbsent(network, ignored -> new ArrayList<>()).add(grid)));
        return byNetwork;
    }

    private static JsonObject describe(NetworkId network, List<IGrid> grids) {
        var settlements = grids.stream().map(grid -> grid.getService(NetworkIdentityService.class).settlement()).toList();
        var state = NetworkIdentityState.of(network, settlements);
        var json = new JsonObject();
        json.addProperty("id", network.value().toString());
        json.addProperty("identity", state.key());
        json.addProperty("parts", grids.size());
        primaryGrid(network, grids, settlements).ifPresent(grid -> addGridFacts(json, grid));
        if (state.hasParts()) json.add("identityParts", parts(state, grids));
        return json;
    }

    /**
     * The parts a player has to tell apart while the identity is in doubt, each with its blocks: for a merge, the
     * histories joined in one Grid (by the network their nodes came from); for a split, each Grid claiming it.
     */
    private static JsonArray parts(NetworkIdentityState state, List<IGrid> grids) {
        var groups = new java.util.LinkedHashMap<String, List<IGridNode>>();
        for (var index = 0; index < grids.size(); index++) {
            var grid = grids.get(index);
            if (state == NetworkIdentityState.SPLIT) {
                var nodes = new ArrayList<IGridNode>();
                grid.getNodes().forEach(nodes::add);
                groups.put("part" + index, nodes);
                continue;
            }
            var service = grid.getService(NetworkIdentityService.class);
            for (var node : grid.getNodes()) {
                try {
                    groups.computeIfAbsent(service.lineage(node).networkId().value().toString(),
                            ignored -> new ArrayList<>()).add(node);
                } catch (IllegalArgumentException untracked) {
                    // A node the identity service does not track yet carries no history of its own.
                }
            }
        }
        var out = new JsonArray();
        int perPart = Math.max(1, MAX_BLOCKS / Math.max(1, groups.size()));
        groups.forEach((key, nodes) -> {
            var part = new JsonObject();
            if (state == NetworkIdentityState.MERGE) part.addProperty("network", key);
            nodes.stream().map(NetworkOverview::located).flatMap(Optional::stream).findFirst().ifPresent(first -> {
                part.addProperty("dimension", first.level().dimension().location().toString());
                part.add("blocks", blocks(nodes, first.level(), perPart));
            });
            out.add(part);
        });
        return out;
    }

    private static Optional<IGrid> primaryGrid(NetworkId network, List<IGrid> grids, List<IdentitySettlement> settlements) {
        for (var index = 0; index < grids.size(); index++) {
            if (settlements.get(index).networkId().filter(network::equals).isPresent()) return Optional.of(grids.get(index));
        }
        return grids.stream().findFirst();
    }

    private static void addGridFacts(JsonObject json, IGrid grid) {
        location(grid).ifPresent(node -> {
            json.addProperty("dimension", node.level().dimension().location().toString());
            json.addProperty("x", node.pos().getX());
            json.addProperty("y", node.pos().getY());
            json.addProperty("z", node.pos().getZ());
            json.add("blocks", blocks(grid, node.level()));
        });
        json.add("devices", devices(grid));
        var energy = grid.getEnergyService();
        json.addProperty("energy", Math.round(energy.getStoredPower()));
        json.addProperty("energyMax", Math.round(energy.getMaxStoredPower()));
        json.addProperty("energyIn", Math.round(energy.getAvgPowerInjection() * 10) / 10d);
        json.addProperty("energyOut", Math.round(energy.getAvgPowerUsage() * 10) / 10d);
        json.addProperty("powered", energy.isNetworkPowered());
        json.addProperty("types", grid.getStorageService().getCachedInventory().size());
        var cpus = grid.getCraftingService().getCpus();
        json.addProperty("cpus", cpus.size());
        json.addProperty("cpusBusy", cpus.stream().filter(cpu -> cpu.isBusy()).count());
        var pathing = grid.getPathingService();
        json.addProperty("channels", pathing.getUsedChannels());
        json.addProperty("controller", pathing.getControllerState().name().toLowerCase(java.util.Locale.ROOT));
        json.addProperty("nodes", grid.size());
    }

    /** The mod's devices in the grid with their positions, so players can find a network by a device or where it is. */
    private static JsonArray devices(IGrid grid) {
        var out = new JsonArray();
        addDevices(out, "provider", grid.getMachineNodes(
                space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity.class));
        addDevices(out, "endpoint", grid.getMachineNodes(
                space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity.class));
        return out;
    }

    private static void addDevices(JsonArray out, String kind, Iterable<IGridNode> nodes) {
        for (var node : nodes) {
            located(node).ifPresent(located -> {
                var device = new JsonObject();
                device.addProperty("kind", kind);
                device.addProperty("x", located.pos().getX());
                device.addProperty("y", located.pos().getY());
                device.addProperty("z", located.pos().getZ());
                out.add(device);
            });
        }
    }

    /** Distinct block positions of the grid's nodes in the location's dimension, as flat {@code [x, y, z, ...]}. */
    private static JsonArray blocks(IGrid grid, ServerLevel level) {
        return blocks(grid.getNodes(), level, MAX_BLOCKS);
    }

    private static JsonArray blocks(Iterable<IGridNode> nodes, ServerLevel level, int limit) {
        var seen = new java.util.LinkedHashSet<BlockPos>();
        for (var node : nodes) {
            if (seen.size() >= limit) break;
            located(node).filter(located -> located.level() == level).ifPresent(located -> seen.add(located.pos()));
        }
        var out = new JsonArray();
        for (var pos : seen) {
            out.add(pos.getX());
            out.add(pos.getY());
            out.add(pos.getZ());
        }
        return out;
    }

    private record Located(ServerLevel level, BlockPos pos) {
    }

    /** The first controller, or any node, as a place players recognize the network by. */
    private static Optional<Located> location(IGrid grid) {
        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            var located = located(node);
            if (located.isPresent()) return located;
        }
        var pivot = grid.getPivot();
        return pivot == null ? Optional.empty() : located(pivot);
    }

    private static Optional<Located> located(IGridNode node) {
        var owner = node.getOwner();
        BlockEntity blockEntity = owner instanceof BlockEntity entity ? entity
                : owner instanceof AEBasePart part ? part.getBlockEntity() : null;
        return blockEntity == null || node.getLevel() == null ? Optional.empty()
                : Optional.of(new Located(node.getLevel(), blockEntity.getBlockPos()));
    }
}
