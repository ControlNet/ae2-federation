package space.controlnet.ae2federation.test.p2p;

import appeng.core.definitions.AEBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * TEST-ONLY: one ME network, the carrier, that spans the overworld and a nether site over a real Quantum Network
 * Bridge, with a Federation P2P tunnel on each side. Each side has its own creative energy cell, as each half of a
 * Quantum Bridge needs power.
 * <p>
 * Overworld, along x at y 2 and z 3: the tunnel's bus at x 4 with the tunnel on its west side, a cable at x 5 over the
 * energy cell, and the bridge around x 7. Its front, x 3, is where a Federation block goes. Nether site: the bridge
 * around (1, 0, 1), the energy cell east of it and the tunnel's bus south of it, with the tunnel on its south side; its
 * front is (1, 0, 4).
 */
public final class QuantumP2PCarrier {
    public static final BlockPos OVERWORLD_BUS = new BlockPos(4, 2, 3);
    public static final BlockPos OVERWORLD_FRONT = OVERWORLD_BUS.west();
    public static final Direction OVERWORLD_SIDE = Direction.WEST;
    private static final BlockPos OVERWORLD_CABLE = OVERWORLD_BUS.east();
    private static final BlockPos OVERWORLD_POWER = OVERWORLD_CABLE.below();
    private static final BlockPos OVERWORLD_CHAMBER = OVERWORLD_CABLE.east(2);
    private static final BlockPos NETHER_CHAMBER = new BlockPos(1, 0, 1);
    private static final BlockPos NETHER_POWER = NETHER_CHAMBER.east(2);
    public static final BlockPos NETHER_BUS = NETHER_CHAMBER.south(2);
    public static final BlockPos NETHER_FRONT = NETHER_BUS.south();
    public static final Direction NETHER_SIDE = Direction.SOUTH;

    private final GameTestHelper helper;
    private final OtherDimensionSite site;
    private final P2PTunnels tunnels;
    private final long frequency;

    public QuantumP2PCarrier(GameTestHelper helper, OtherDimensionSite site) {
        this.helper = helper;
        this.site = site;
        tunnels = new P2PTunnels(helper.makeMockPlayer(GameType.CREATIVE));
        frequency = QuantumBridges.randomFrequency(helper);
    }

    /** Builds both halves and attunes both tunnels; call once the site is ready. */
    public void build() {
        helper.assertTrue(P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(OVERWORLD_BUS))
                && P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(OVERWORLD_CABLE))
                && P2PTunnels.placeCable(site.level(), site.absolute(NETHER_BUS)), "The carrier's cables must be placed");
        helper.setBlock(OVERWORLD_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
        site.setBlock(NETHER_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        QuantumBridges.build(OVERWORLD_CHAMBER, frequency, null, (position, state) -> {
            helper.setBlock(position, state);
            return helper.getLevel().getBlockEntity(helper.absolutePos(position));
        });
        QuantumBridges.build(NETHER_CHAMBER, frequency, null, (position, state) -> {
            site.setBlock(position, state);
            return site.getBlockEntity(position);
        });
        helper.assertTrue(tunnels.attune(helper.getLevel(), helper.absolutePos(OVERWORLD_BUS), OVERWORLD_SIDE) != null
                && tunnels.attune(site.level(), site.absolute(NETHER_BUS), NETHER_SIDE) != null,
                "A Federation cable must attune both tunnels");
    }

    /** Whether the bridge has joined both halves into one powered network on which both tunnels have a channel. */
    public boolean linked() {
        var overworld = overworldTunnel();
        var nether = netherTunnel();
        return P2PTunnels.online(overworld, nether) && overworld.getGridNode().getGrid() == nether.getGridNode().getGrid();
    }

    /** Pairs the overworld tunnel, as the input, with the nether tunnel. */
    public void pair() {
        helper.assertTrue(tunnels.pair(overworldTunnel(), List.of(netherTunnel())),
                "The memory card must pair the tunnels across the bridge");
    }

    public FederationP2PTunnelPart overworldTunnel() {
        return P2PTunnels.tunnel(helper.getLevel(), helper.absolutePos(OVERWORLD_BUS), OVERWORLD_SIDE);
    }

    public FederationP2PTunnelPart netherTunnel() {
        return P2PTunnels.tunnel(site.level(), site.absolute(NETHER_BUS), NETHER_SIDE);
    }
}
