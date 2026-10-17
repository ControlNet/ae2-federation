package space.controlnet.ae2federation.router;

import net.minecraft.world.level.BlockGetter;

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
}
