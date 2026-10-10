package space.controlnet.ae2federation.test.p2p;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;

/**
 * TEST-ONLY: a carrier ME network of Federation P2P tunnels with nothing in front of them, built as a player would,
 * with real channels. With a Controller, a dense cable from one Controller face (32 channels) has glass cable buses
 * above and below it, three tunnels on each; without one, the buses form an ad-hoc network, which AE2 shuts down when
 * more than eight devices want a channel.
 */
public final class TunnelCarrier {
    private static final List<Direction> ABOVE = List.of(Direction.NORTH, Direction.SOUTH, Direction.UP);
    private static final List<Direction> BELOW = List.of(Direction.NORTH, Direction.SOUTH, Direction.DOWN);

    private final GameTestHelper helper;
    private final P2PTunnels cards;
    private final BlockPos cell;
    private final List<BlockPos> buses = new ArrayList<>();
    private final List<FederationP2PTunnelPart> tunnels = new ArrayList<>();

    private TunnelCarrier(GameTestHelper helper, BlockPos cell) {
        this.helper = helper;
        this.cell = cell;
        cards = new P2PTunnels(helper.makeMockPlayer(GameType.CREATIVE));
    }

    /** {@code count} tunnels on a Controller's network; the creative energy cell sits at {@code origin}. */
    public static TunnelCarrier withController(GameTestHelper helper, BlockPos origin, int count) {
        var carrier = new TunnelCarrier(helper, origin);
        helper.setBlock(origin, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(origin.east(), AEBlocks.CONTROLLER.block());
        int trunk = (count + 5) / 6;
        for (int x = 0; x < trunk; x++) {
            var dense = origin.east(2 + x);
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(dense), null, null,
                    AEParts.SMART_DENSE_CABLE.item(AEColor.TRANSPARENT)) != null, "A dense cable must be placed");
            carrier.buses.add(dense.above());
            carrier.buses.add(dense.below());
        }
        carrier.attune(count);
        return carrier;
    }

    /**
     * {@code count} tunnels on an ad-hoc network: glass cable buses in a row east of the creative energy cell at
     * {@code origin}, three tunnels on each.
     */
    public static TunnelCarrier adHoc(GameTestHelper helper, BlockPos origin, int count) {
        var carrier = new TunnelCarrier(helper, origin);
        helper.setBlock(origin, AEBlocks.CREATIVE_ENERGY_CELL.block());
        for (int x = 0; x < (count + 2) / 3; x++) {
            carrier.buses.add(origin.east(1 + x).above());
        }
        helper.assertTrue(P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(origin.east())),
                "A cable must join the energy cell to the buses");
        carrier.attune(count);
        return carrier;
    }

    private void attune(int count) {
        var level = helper.getLevel();
        for (var bus : buses) {
            helper.assertTrue(P2PTunnels.placeCable(level, helper.absolutePos(bus)), "A bus must be placed");
            for (var side : bus.getY() < cell.getY() ? BELOW : ABOVE) {
                if (tunnels.size() < count) {
                    tunnels.add(Objects.requireNonNull(cards.attune(level, helper.absolutePos(bus), side),
                            "A Federation cable must attune the tunnel"));
                }
            }
        }
    }

    public List<FederationP2PTunnelPart> tunnels() {
        return tunnels;
    }

    public FederationP2PTunnelPart tunnel(int index) {
        return tunnels.get(index);
    }

    /** Pairs {@code outputs} with {@code input} with the memory card, as a player does. */
    public void pair(int input, int... outputs) {
        var paired = new ArrayList<FederationP2PTunnelPart>();
        for (int output : outputs) paired.add(tunnels.get(output));
        helper.assertTrue(cards.pair(tunnels.get(input), paired),
                "The memory card must pair every output with the input");
    }

    /** Takes tunnel {@code index} off its bus, as a player wrenching it off does. */
    public void remove(int index) {
        var tunnel = tunnels.get(index);
        tunnel.getHost().removePart(tunnel);
    }

    /** Puts a fresh tunnel where tunnel {@code index} was, attuned with a Federation cable, and returns it. */
    public FederationP2PTunnelPart replace(int index) {
        var old = tunnels.get(index);
        var position = old.getBlockEntity().getBlockPos();
        var tunnel = Objects.requireNonNull(cards.attune(helper.getLevel(), position, old.getSide()),
                "A Federation cable must attune the new tunnel");
        tunnels.set(index, tunnel);
        return tunnel;
    }

    public void powerOff() {
        helper.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR);
    }

    public void powerOn() {
        helper.setBlock(cell, AEBlocks.CREATIVE_ENERGY_CELL.block());
    }

    public boolean online(int... indexes) {
        for (int index : indexes) {
            if (!P2PTunnels.online(tunnels.get(index))) return false;
        }
        return true;
    }

    /** The Federation Domain holding tunnel {@code index}'s node, if any. */
    public @Nullable FederationDomainSnapshot domain(int index) {
        var level = helper.getLevel();
        var tunnel = tunnels.get(index);
        var port = new FederationPort(tunnel.getBlockEntity().getBlockPos(), tunnel.getSide(),
                tunnel.getSide().getSerializedName());
        return FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, port)).orElse(null);
    }

    /** Whether the tunnels {@code indexes} make up one Domain of their own, with no other node. */
    public boolean domainOf(int... indexes) {
        var first = domain(indexes[0]);
        if (first == null || first.nodes().size() != indexes.length) return false;
        for (int index : indexes) {
            var domain = domain(index);
            if (domain == null || !domain.federationDomainId().equals(first.federationDomainId())) return false;
        }
        return true;
    }

    /** Whether tunnel {@code index} is linked to nothing: no Domain, or one of its node alone. */
    public boolean alone(int index) {
        var domain = domain(index);
        return domain == null || domain.nodes().size() == 1;
    }
}
