package space.controlnet.ae2federation.compat;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * What a player does with Mekanism's Configurator and a filled Creative Energy Cube, done through Mekanism's own
 * classes by reflection, since this test mod does not build against Mekanism. A newly placed Mekanism machine has every
 * side disabled until its player configures it, and a Creative Energy Cube placed as a bare block is empty.
 */
final class MekanismSetup {
    private MekanismSetup() {
    }

    /**
     * Sets one side of a machine's side configuration, as the Configurator does: {@code transmission} is a Mekanism
     * {@code TransmissionType} (ITEM, ENERGY, FLUID, CHEMICAL) and {@code dataType} a {@code DataType} (INPUT, OUTPUT,
     * ENERGY, ...). {@code ejecting} turns on the machine's auto-eject for that transmission type.
     */
    static void configure(GameTestHelper helper, BlockPos position, String transmission, String dataType,
            Direction side, boolean ejecting) {
        var tile = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        try {
            var loader = tile.getClass().getClassLoader();
            var transmissionType = enumConstant(loader, "mekanism.common.lib.transmitter.TransmissionType", transmission);
            var type = enumConstant(loader, "mekanism.common.tile.component.config.DataType", dataType);
            var relativeSide = loader.loadClass("mekanism.api.RelativeSide").getMethod("fromDirections",
                    Direction.class, Direction.class).invoke(null, facing(helper, position), side);
            var config = method(tile.getClass(), "getConfig").invoke(tile);
            var info = config.getClass().getMethod("getConfig", transmissionType.getClass()).invoke(config,
                    transmissionType);
            helper.assertTrue(info != null, position + " has no " + transmission + " side configuration");
            helper.assertTrue((boolean) info.getClass().getMethod("supports", type.getClass()).invoke(info, type),
                    position + " has no " + dataType + " for " + transmission + ": " + describe(helper, position));
            var set = (boolean) info.getClass().getMethod("setDataType", type.getClass(), relativeSide.getClass())
                    .invoke(info, type, relativeSide);
            var current = info.getClass().getMethod("getDataType", relativeSide.getClass()).invoke(info, relativeSide);
            helper.assertTrue(set || current == type, position + " refused " + dataType + " on " + side);
            if (ejecting) info.getClass().getMethod("setEjecting", boolean.class).invoke(info, true);
            config.getClass().getMethod("sideChanged", transmissionType.getClass(), relativeSide.getClass())
                    .invoke(config, transmissionType, relativeSide);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Mekanism side configuration failed at " + position, exception);
        }
    }

    /** Mekanism's chemical handler capability on one side of a block, or null when it has none. */
    static Object chemicalHandler(GameTestHelper helper, BlockPos position, Direction side) {
        var capability = net.neoforged.neoforge.capabilities.BlockCapability.getAll().stream()
                .filter(candidate -> candidate.name().toString().equals("mekanism:chemical_handler")
                        && candidate.contextClass() == Direction.class)
                .findFirst().orElse(null);
        if (capability == null) return "no mekanism:chemical_handler capability";
        @SuppressWarnings("unchecked")
        var typed = (net.neoforged.neoforge.capabilities.BlockCapability<Object, Direction>) capability;
        return helper.getLevel().getCapability(typed, helper.absolutePos(position), side);
    }

    /** Fills an energy cube, as a cube charged before it is placed, or the filled creative cube, arrives. */
    static void fill(GameTestHelper helper, BlockPos position) {
        var tile = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        try {
            var container = method(tile.getClass(), "getEnergyContainer").invoke(tile);
            var max = (long) method(container.getClass(), "getMaxEnergy").invoke(container);
            container.getClass().getMethod("setEnergy", long.class).invoke(container, max);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The energy cube at " + position + " could not be filled", exception);
        }
    }

    private static Direction facing(GameTestHelper helper, BlockPos position) {
        var state = helper.getLevel().getBlockState(helper.absolutePos(position));
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                : state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING)
                : Direction.NORTH;
    }

    private static Object enumConstant(ClassLoader loader, String type, String name) throws ClassNotFoundException {
        for (var constant : loader.loadClass(type).getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
        }
        throw new IllegalArgumentException(type + " has no " + name);
    }

    private static Method method(Class<?> type, String name) throws NoSuchMethodException {
        return type.getMethod(name);
    }

    /** Every transmission type's supported data types and side configuration, for a failure message. */
    static String describe(GameTestHelper helper, BlockPos position) {
        var tile = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        try {
            var config = method(tile.getClass(), "getConfig").invoke(tile);
            var text = new StringBuilder();
            for (var type : (java.util.List<?>) config.getClass().getMethod("getTransmissions").invoke(config)) {
                var info = config.getClass().getMethod("getConfig", type.getClass()).invoke(config, type);
                text.append(type).append(info.getClass().getMethod("getSupportedDataTypes").invoke(info))
                        .append(info.getClass().getMethod("getSideConfig").invoke(info)).append(' ');
            }
            return text.toString();
        } catch (ReflectiveOperationException exception) {
            return "no side configuration: " + exception;
        }
    }
}
