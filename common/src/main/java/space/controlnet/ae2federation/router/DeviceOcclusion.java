package space.controlnet.ae2federation.router;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Occlusion shapes of the hand-made device models. A face of a full occluding block hides its neighbour's face, so a
 * gap in the model would show the void behind it; these shapes close only the sides the models close completely,
 * pinned by {@code DeviceOcclusionContractTest}.
 */
public final class DeviceOcclusion {
    /** The Router's frame leaves gaps on all six sides; its body is inset by one pixel. */
    public static final VoxelShape ROUTER = Shapes.box(1 / 16.0, 1 / 16.0, 1 / 16.0, 15 / 16.0, 15 / 16.0, 15 / 16.0);
    private static final Map<Direction, VoxelShape> FRONT = new EnumMap<>(Direction.class);
    static {
        // The Provider's and Endpoint's front frame is recessed and notched into the four sides beside it, so only the
        // back is closed: their 13-unit body, turned so the front faces `facing`.
        for (var facing : Direction.values()) {
            double near = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : 3 / 16.0;
            double far = near + 13 / 16.0;
            FRONT.put(facing, switch (facing.getAxis()) {
                case X -> Shapes.box(near, 0, 0, far, 1, 1);
                case Y -> Shapes.box(0, near, 0, 1, far, 1);
                case Z -> Shapes.box(0, 0, near, 1, 1, far);
            });
        }
    }

    private DeviceOcclusion() {
    }

    /** The Provider or Endpoint facing {@code facing}: it hides only the neighbour behind its back. */
    public static VoxelShape front(Direction facing) {
        return FRONT.get(facing);
    }
}
