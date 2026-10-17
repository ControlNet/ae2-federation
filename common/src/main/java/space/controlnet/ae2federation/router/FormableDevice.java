package space.controlnet.ae2federation.router;

import java.util.function.UnaryOperator;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block whose touching blocks of the same type form one solid-cuboid body, as Routers and Switches do. Its states
 * carry {@link RouterBlock#FORMED} and the six connection properties, which {@link DeviceStructureUpdater} keeps.
 */
public interface FormableDevice {
    /**
     * The verdict on a cuboid that passed the common rules: a device with rules of its own, such as an exact size, may
     * reject it.
     */
    default DeviceStructure.Result judge(BlockGetter level, DeviceStructure.Valid body) {
        return body;
    }

    /**
     * What else a body's members take on as it forms, decided once for the whole body before any member changes: a Beam
     * Converter turns them all one way.
     */
    default UnaryOperator<BlockState> forming(BlockGetter level, DeviceStructure.Valid body) {
        return UnaryOperator.identity();
    }
}
