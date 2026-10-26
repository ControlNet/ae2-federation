package space.controlnet.ae2federation.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.BeamConverterShape;
import space.controlnet.ae2federation.quantum.QuantumCubeShape;
import space.controlnet.ae2federation.router.RouterBlock;

/** Derives client render bounds from the server's synchronized formed connection flags. */
public record RefinedDeviceBody(BlockPos min, BlockPos max, int section, Direction facing, boolean laser) {
    public static @Nullable RefinedDeviceBody find(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (!RouterBlock.formed(state)) return null;
        var min=pos.mutable(); var max=pos.mutable();
        for (var side:Direction.values()) {
            var cursor=pos.mutable();
            for (int step=0;step<6;step++) {
                var current=level.getBlockState(cursor);
                if (!current.is(state.getBlock()) || !RouterBlock.formed(current)) return null;
                if ((RouterBlock.connections(current)&1<<side.get3DDataValue())==0) break;
                cursor.move(side);
            }
            var bound=side.getAxisDirection()==Direction.AxisDirection.NEGATIVE ? min : max;
            switch (side.getAxis()) {
                case X -> bound.setX(cursor.getX());
                case Y -> bound.setY(cursor.getY());
                case Z -> bound.setZ(cursor.getZ());
            }
        }
        boolean laser=state.getBlock() instanceof BeamConverterBlock;
        var section=laser ? BeamConverterShape.of(min,max) : QuantumCubeShape.of(min,max);
        if (section.isEmpty()) return null;
        return new RefinedDeviceBody(min.immutable(),max.immutable(),section.get().wide(),
                laser ? state.getValue(BeamConverterBlock.FACING) : Direction.SOUTH,laser);
    }
    public double centre(Direction.Axis axis) { return (min.get(axis)+max.get(axis)+1)/2.0; }
}
