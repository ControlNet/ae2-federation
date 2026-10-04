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
     * The Federation Pattern Provider sends cobblestone through an Endpoint into a pair of Crushing Wheels; the gravel
     * they drop falls into a hopper, which pushes it into the Endpoint. The controller hands its product only to
     * Create's own belts and chutes, so a hopper is how a player collects it into another block.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointCrushingWheels(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CrushingWheels());
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
