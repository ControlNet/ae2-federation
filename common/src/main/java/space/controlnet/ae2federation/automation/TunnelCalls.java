package space.controlnet.ae2federation.automation;

import appeng.api.networking.IGrid;
import appeng.api.parts.IPartItem;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.mixin.P2PTunnelPartAccess;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.RecordValue;
import space.controlnet.ae2federation.script.RunError;

/**
 * The library calls for P2P tunnels: the domain's tunnels, and linking an output to an input or unlinking a tunnel, as
 * a player does with a memory card.
 *
 * <p>Tunnels are found as AE2 groups them: on each network of the domain, every tunnel part whose own node is on that
 * network, an addon's tunnels and Federation P2P tunnels among them. A tunnel is named in later calls by its ID (its
 * block and side), looked up again among the domain's tunnels at each call; one no longer there gives False.
 *
 * <p>Assistant choices, following the memory card: linking makes the input an input first, with a new frequency if it
 * had none or was an output, and turns the output into the input's kind of tunnel if it is another; unlinking gives a
 * tunnel frequency 0 as an input, as a tunnel is when placed, so an unlinked input leaves its outputs without one.
 * Tunnels link only on one network, as AE2 keeps frequencies per network. Relinking a Federation P2P tunnel changes the
 * domain's cables, so it can put a domain over its cable capacity, as a player's relink can.
 */
final class TunnelCalls {
    private final MinecraftServer server;
    private final boolean simulate;
    private final DomainCalls domain;

    TunnelCalls(MinecraftServer server, boolean simulate, DomainCalls domain) {
        this.server = server;
        this.simulate = simulate;
        this.domain = domain;
    }

    /** A tunnel on a network of the domain, and where it is. */
    private record Found(P2PTunnelPart<?> tunnel, IGrid grid, NetworkId network, ServerLevel level, BlockPos position,
            Direction side) {
        /** {@code minecraft:overworld 12 64 -3 north}. */
        String id() {
            return level.dimension().location() + " " + position.getX() + " " + position.getY() + " "
                    + position.getZ() + " " + side.getName();
        }
    }

    Object call(ProgramHost host, String call, List<Object> args) {
        return switch (call) {
            case "ae2.tunnels" -> {
                var found = found(domain.networks(args.get(0)));
                var byTunnel = byTunnel(found);
                yield new ListValue(new ArrayList<>(found.stream().map(each -> (Object) record(each, byTunnel))
                        .toList()));
            }
            case "ae2.tunnel_at" -> tunnelAt(host, args.get(0));
            case "ae2.link_tunnel" -> link((RecordValue) args.get(0), (RecordValue) args.get(1));
            case "ae2.unlink_tunnel" -> unlink((RecordValue) args.get(0));
            default -> throw new RunError("NotImplementedError", call + " is not available yet");
        };
    }

    // ---- finding tunnels ----

    /** The tunnels of the networks, network by network, each by position and side. */
    private static List<Found> found(List<Map.Entry<NetworkId, IGrid>> networks) {
        var out = new ArrayList<Found>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<P2PTunnelPart<?>, Boolean>());
        for (var entry : networks) {
            var grid = entry.getValue();
            var here = new ArrayList<Found>();
            for (var machineClass : grid.getMachineClasses()) {
                if (!P2PTunnelPart.class.isAssignableFrom(machineClass)) {
                    continue;
                }
                for (var node : grid.getMachineNodes(machineClass)) {
                    // An ME tunnel's outer node is on the network it carries; the tunnel belongs to its own node's.
                    if (node.getOwner() instanceof P2PTunnelPart<?> tunnel && tunnel.getMainNode().getNode() == node
                            && seen.add(tunnel)) {
                        locate(tunnel, grid, entry.getKey()).ifPresent(here::add);
                    }
                }
            }
            here.sort(Comparator.comparing((Found found) -> found.level().dimension().location().toString())
                    .thenComparing(Found::position).thenComparing(Found::side));
            out.addAll(here);
        }
        return out;
    }

    private static Optional<Found> locate(P2PTunnelPart<?> tunnel, IGrid grid, NetworkId network) {
        var entity = tunnel.getBlockEntity();
        if (entity == null || entity.isRemoved() || tunnel.getSide() == null
                || !(entity.getLevel() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return Optional.of(new Found(tunnel, grid, network, level, entity.getBlockPos(), tunnel.getSide()));
    }

    /** The tunnel a {@code Tunnel} record names, looked for on the record's network first, then on the others. */
    private Optional<Found> resolve(RecordValue tunnel) {
        var id = tunnel.field("id");
        var network = NetworkShortIds.get(server).network((String) tunnel.field("network")).orElse(null);
        var all = domain.networks(null);
        var own = all.stream().filter(entry -> entry.getKey().equals(network)).toList();
        return found(own).stream().filter(found -> found.id().equals(id)).findFirst()
                .or(() -> found(all).stream().filter(found -> found.id().equals(id)).findFirst());
    }

    private @Nullable RecordValue tunnelAt(ProgramHost host, Object block) {
        var device = host.place(block).filter(place -> !ProgramHost.own(place))
                .flatMap(place -> DeviceCalls.device(place, P2PTunnelPart.class));
        if (device.isEmpty()) {
            return null;
        }
        var byTunnel = byTunnel(found(domain.networks(null)));
        var found = byTunnel.get(device.get());
        return found == null ? null : record(found, byTunnel);
    }

    private static Map<P2PTunnelPart<?>, Found> byTunnel(List<Found> found) {
        var out = new IdentityHashMap<P2PTunnelPart<?>, Found>();
        found.forEach(each -> out.put(each.tunnel(), each));
        return out;
    }

    // ---- the Tunnel record ----

    private RecordValue record(Found found, Map<P2PTunnelPart<?>, Found> byTunnel) {
        var tunnel = found.tunnel();
        var fields = new LinkedHashMap<String, Object>();
        fields.put("id", found.id());
        fields.put("type", BuiltInRegistries.ITEM.getKey(tunnel.getPartItem().asItem()).toString());
        fields.put("name", tunnel.hasCustomName() && tunnel.getCustomName() != null
                ? tunnel.getCustomName().getString() : null);
        fields.put("frequency", (long) Short.toUnsignedInt(tunnel.getFrequency()));
        fields.put("output", tunnel.isOutput());
        fields.put("linked", new ListValue(linked(found, byTunnel)));
        fields.put("active", tunnel.getMainNode().isActive());
        fields.put("network", domain.shortId(found.network()));
        fields.put("x", (long) found.position().getX());
        fields.put("y", (long) found.position().getY());
        fields.put("z", (long) found.position().getZ());
        fields.put("dimension", found.level().dimension().location().toString());
        fields.put("side", found.side().getName());
        return new RecordValue("Tunnel", fields, true);
    }

    /** The IDs of the tunnels at the other end, as AE2 links them: an input's outputs, an output's input. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Object> linked(Found found, Map<P2PTunnelPart<?>, Found> byTunnel) {
        var tunnel = found.tunnel();
        var frequency = tunnel.getFrequency();
        if (frequency == 0) {
            return List.of();
        }
        var p2p = P2PService.get(found.grid());
        var input = p2p.getInput(frequency);
        var out = new ArrayList<Object>();
        if (tunnel.isOutput()) {
            if (input != null && input.getClass() == tunnel.getClass() && byTunnel.containsKey(input)) {
                out.add(byTunnel.get(input).id());
            }
        } else if (input == tunnel) {
            p2p.getOutputs(frequency, (Class) tunnel.getClass()).forEach(other -> {
                var at = byTunnel.get(other);
                if (at != null) {
                    out.add(at.id());
                }
            });
            out.sort(Comparator.comparing(Object::toString));
        }
        return out;
    }

    // ---- linking ----

    private boolean link(RecordValue inputRecord, RecordValue outputRecord) {
        if (inputRecord.field("id").equals(outputRecord.field("id"))) {
            throw new RunError("ValueError", "a tunnel cannot link to itself");
        }
        var input = resolve(inputRecord);
        var output = resolve(outputRecord);
        if (input.isEmpty() || output.isEmpty()) {
            return false;
        }
        if (input.get().grid() != output.get().grid()) {
            throw new RunError("ValueError", "tunnels link only on one network; the input is on "
                    + domain.shortId(input.get().network()) + ", the output on "
                    + domain.shortId(output.get().network()));
        }
        if (simulate) {
            return true;
        }
        var in = input.get().tunnel();
        var p2p = P2PService.get(input.get().grid());
        if (in.isOutput() || in.getFrequency() == 0) {
            ((P2PTunnelPartAccess) in).ae2federation$setOutput(false);
            p2p.updateFreq(in, p2p.newFrequency());
            in.onTunnelConfigChange();
        }
        P2PTunnelPart<?> out = output.get().tunnel();
        if (out.getPartItem() != in.getPartItem()) {
            if (!(replace(out, in.getPartItem()) instanceof P2PTunnelPart<?> replaced)) {
                return false;
            }
            out = replaced;
        }
        if (out.isOutput() && out.getFrequency() == in.getFrequency()) {
            return true;
        }
        ((P2PTunnelPartAccess) out).ae2federation$setOutput(true);
        var grid = out.getMainNode().getGrid();
        if (grid != null) {
            P2PService.get(grid).updateFreq(out, in.getFrequency());
        } else {
            // Kept for when the new part joins its network, as AE2 does for a memory card.
            out.setFrequency(in.getFrequency());
            out.onTunnelNetworkChange();
        }
        return true;
    }

    /** Puts a tunnel of the other kind in the tunnel's place, as a memory card of that kind does. */
    private static Object replace(P2PTunnelPart<?> tunnel, IPartItem<?> kind) {
        return tunnel.getHost().replacePart(kind, tunnel.getSide(), null, null);
    }

    private boolean unlink(RecordValue record) {
        var found = resolve(record);
        if (found.isEmpty()) {
            return false;
        }
        if (simulate) {
            return true;
        }
        var tunnel = found.get().tunnel();
        if (!tunnel.isOutput() && tunnel.getFrequency() == 0) {
            return true;
        }
        ((P2PTunnelPartAccess) tunnel).ae2federation$setOutput(false);
        P2PService.get(found.get().grid()).updateFreq(tunnel, (short) 0);
        tunnel.onTunnelConfigChange();
        return true;
    }
}
