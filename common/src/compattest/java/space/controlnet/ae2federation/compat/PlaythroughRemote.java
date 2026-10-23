package space.controlnet.ae2federation.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.quantum.QuantumLinkService;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/** A real nether factory site connected by paired production Quantum Cubes. */
final class PlaythroughRemote implements AutoCloseable {
    private static final BlockPos HERE = new BlockPos(14, 11, 13);
    private static final BlockPos THERE = new BlockPos(12, 11, 13);
    final OtherDimensionSite site;
    private final GameTestHelper helper;
    private boolean paired;

    PlaythroughRemote(GameTestHelper helper) {
        this.helper = helper;
        site = OtherDimensionSite.nether(helper, new BlockPos(35, 30, 30));
    }

    boolean contains(BlockPos pos) { return pos.getX() >= 16; }

    void initialize() {
        for (var pos : BlockPos.betweenClosed(HERE, HERE.offset(2, 2, 2)))
            helper.setBlock(pos, QuantumRegistration.QUANTUM_CUBE.get());
        for (var pos : BlockPos.betweenClosed(THERE, THERE.offset(2, 2, 2)))
            site.setBlock(pos, QuantumRegistration.QUANTUM_CUBE.get().defaultBlockState());
        site.setBlock(new BlockPos(15, 12, 14), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
    }

    boolean ready() {
        if (paired) return true;
        if (!RouterBlock.formed(helper.getBlockState(HERE)) || !RouterBlock.formed(site.getBlockState(THERE))) return false;
        var result = QuantumLinkService.get(helper.getLevel().getServer()).bind(
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(HERE)),
                GlobalPos.of(site.level().dimension(), site.absolute(THERE)));
        paired = result == QuantumLinkService.Bind.BOUND;
        if (!paired) throw new IllegalStateException("Quantum Cube pairing refused: " + result);
        return true;
    }

    @Override public void close() {
        site.close();
        for (var pos : BlockPos.betweenClosed(HERE, HERE.offset(2, 2, 2))) helper.setBlock(pos, Blocks.AIR);
    }
}
