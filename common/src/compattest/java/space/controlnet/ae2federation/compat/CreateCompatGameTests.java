package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation processing through Create's own machines, powered as players power them. */
@PrefixGameTestTemplate(false)
public final class CreateCompatGameTests {
    private CreateCompatGameTests() {
    }

    /**
     * The consumer requests gravel; the provider network's Pattern Provider pushes cobblestone into a Millstone that a
     * Creative Motor turns, and the gravel it mills goes back into the provider network.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void millstoneProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", List.of("ae2:1k_crafting_storage"),
                new Millstone());
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Federation Pattern Provider sends cobblestone through an Endpoint into a pair of Crushing Wheels. The
     * controller hands the gravel to a Create Chute under it, which pushes it down into the Endpoint. The controller
     * gives its product only to Create's own belts and chutes; otherwise it drops it as items in the world.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointCrushingWheels(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CrushingWheels());
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's Create example as its player builds and tries it: the Endpoint powers the wheels' subnet, and a job
     * waits while the wheels stand still, then finishes once they turn again. The wheels crush a stone in well under
     * 300 ticks, so 300 ticks without gravel show that none was crushed.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1400)
    public static void endpointCrushingWheelsStopped(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CrushingWheels()).poweredThroughEndpoint().interruptedBy(300,
                (test, controller) -> CrushingWheels.turn(test, controller, 0),
                (test, controller) -> CrushingWheels.turn(test, controller, 256));
        helper.succeedWhen(scene::tick);
    }

    /** Two Crushing Wheels north and south of the controller position, each turned by its own Creative Motor. */
    private static final class CrushingWheels implements EndpointMachineScene.Machine {
        @Override
        public String blockId() {
            return "create:crushing_wheel_controller";
        }

        @Override
        public AEKey input() {
            return AEItemKey.of(Items.COBBLESTONE);
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.GRAVEL);
        }

        @Override
        public net.minecraft.world.level.block.state.BlockState collector() {
            return AddonCraftingScene.block("create:chute").defaultBlockState();
        }

        /** The wheels form the controller between them; the motors spin them in opposite directions, inwards. */
        @Override
        public void place(GameTestHelper helper, BlockPos position) {
            var wheel = AddonCraftingScene.block("create:crushing_wheel").defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.X);
            placeMotor(helper, position.north().west(), 256);
            placeMotor(helper, position.south().west(), -256);
            helper.setBlock(position.north(), wheel);
            helper.setBlock(position.south(), wheel);
        }

        /** Sets both motors to {@code speed}, inwards, as a player sets them; 0 stops the wheels. */
        static void turn(GameTestHelper helper, BlockPos controller, int speed) {
            setSpeed(helper, controller.north().west(), speed);
            setSpeed(helper, controller.south().west(), -speed);
        }

        @Override
        public String state(GameTestHelper helper, BlockPos position) {
            var level = helper.getLevel();
            var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(position), Direction.UP);
            var held = new StringBuilder();
            for (int slot = 0; handler != null && slot < handler.getSlots(); slot++) {
                held.append(handler.getStackInSlot(slot)).append(';');
            }
            var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(position)).inflate(3)).stream()
                    .map(entity -> entity.getItem() + "@" + entity.blockPosition()).toList();
            return "controller=" + level.getBlockState(helper.absolutePos(position)) + " held=" + held
                    + " speeds=" + speed(helper, position.north()) + "/" + speed(helper, position.south())
                    + " items=" + items;
        }
    }

    private static String speed(GameTestHelper helper, BlockPos position) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        try {
            return String.valueOf(entity.getClass().getMethod("getSpeed").invoke(entity));
        } catch (ReflectiveOperationException | NullPointerException exception) {
            return "?";
        }
    }

    private static void placeMotor(GameTestHelper helper, BlockPos position, int speed) {
        helper.setBlock(position, AddonCraftingScene.block("create:creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        setSpeed(helper, position, speed);
    }

    private static void setSpeed(GameTestHelper helper, BlockPos position, int speed) {
        var entity = helper.getBlockEntity(position);
        try {
            var value = entity.getClass().getField("generatedSpeed").get(entity);
            value.getClass().getMethod("setValue", int.class).invoke(value, speed);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The Creative Motor's speed could not be set", exception);
        }
    }

    private static final class Millstone implements AddonCraftingScene.Machine {
        private static final AEItemKey GRAVEL = AEItemKey.of(Items.GRAVEL);

        @Override
        public String blockId() {
            return "create:millstone";
        }

        @Override
        public AEItemKey output() {
            return GRAVEL;
        }

        /** A Creative Motor under the Millstone, its shaft facing up into it, at full speed. */
        @Override
        public void placeAround(GameTestHelper helper, BlockPos position) {
            var motor = position.below();
            helper.setBlock(motor, AddonCraftingScene.block("create:creative_motor").defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP));
            var entity = helper.getBlockEntity(motor);
            try {
                var speed = entity.getClass().getField("generatedSpeed").get(entity);
                speed.getClass().getMethod("setValue", int.class).invoke(speed, 256);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The Creative Motor's speed could not be set", exception);
            }
        }

        /** Takes the milled gravel out of the Millstone, as an Import Bus beside it would. */
        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(position),
                    null);
            helper.assertTrue(handler != null, "The Millstone exposes no item handler");
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!handler.getStackInSlot(slot).is(Items.GRAVEL)) continue;
                var taken = handler.extractItem(slot, 64, false);
                network.insert(GRAVEL, taken.getCount(), Actionable.MODULATE, IActionSource.empty());
            }
        }
    }

    /** The provider network shares iron in a Create Item Vault through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusItemVault(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "create:item_vault",
                AEItemKey.of(Items.IRON_INGOT), 9, 4, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }

    /** The provider network shares water in a Create Fluid Tank through a Storage Bus. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void storageBusCreateFluidTank(GameTestHelper helper) {
        var scene = AddonStorageScene.storageBus(helper, "ae2:storage_bus", "create:fluid_tank",
                AEFluidKey.of(Fluids.WATER), 4 * AEFluidKey.AMOUNT_BUCKET, AEFluidKey.AMOUNT_BUCKET, bus -> {
                });
        helper.succeedWhen(scene::tick);
    }
}
