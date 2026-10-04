package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
}
