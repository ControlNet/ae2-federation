package space.controlnet.ae2federation.test;

import appeng.core.definitions.AEItems;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * Shift+right-click with a wrench takes every Federation block straight into the player's inventory, as AE2's wrench
 * does for its own blocks; one member of a formed body comes out and the rest unforms. A plain wrench click removes
 * nothing.
 */
@PrefixGameTestTemplate(false)
public final class WrenchDismantleGameTests {
    private WrenchDismantleGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void wrenchDismantlesEveryFederationBlock(GameTestHelper helper) {
        var blocks = new LinkedHashMap<BlockPos, Item>();
        place(helper, blocks, new BlockPos(4, 2, 4), RouterRegistration.ROUTER.get(), RouterRegistration.ROUTER_ITEM.get());
        place(helper, blocks, new BlockPos(8, 2, 4), RouterRegistration.SWITCH.get(), RouterRegistration.SWITCH_ITEM.get());
        place(helper, blocks, new BlockPos(12, 2, 4), RouterRegistration.FEDERATION_CABLE.get(),
                RouterRegistration.FEDERATION_CABLE_ITEM.get());
        place(helper, blocks, new BlockPos(16, 2, 4), ProcessingRegistration.PROVIDER.get(),
                ProcessingRegistration.PROVIDER_ITEM.get());
        place(helper, blocks, new BlockPos(20, 2, 4), ProcessingRegistration.ENDPOINT.get(),
                ProcessingRegistration.ENDPOINT_ITEM.get());
        place(helper, blocks, new BlockPos(24, 2, 4), BeamRegistration.BEAM_FORMER.get(),
                BeamRegistration.BEAM_FORMER_ITEM.get());
        place(helper, blocks, new BlockPos(28, 2, 4), QuantumRegistration.QUANTUM_CUBE.get(),
                QuantumRegistration.QUANTUM_CUBE_ITEM.get());
        // A formed 1x1x2 converter: its east member comes out, the west one stays and unforms.
        var converter = new BlockPos(4, 2, 12);
        for (var cell : new BlockPos[] {converter, converter.west()}) {
            helper.setBlock(cell, PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(BeamConverterBlock.FACING, Direction.EAST));
        }
        blocks.put(converter, PowerRegistration.BEAM_CONVERTER_ITEM.get());

        var player = MockServerPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, AEItems.CERTUS_QUARTZ_WRENCH.stack());
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(RouterBlock.formed(helper.getBlockState(converter)), "Waiting for the converter to form");
                // A plain wrench click on a cable only selects for a bundle.
                use(helper, player, new BlockPos(12, 2, 4), false);
                helper.assertTrue(helper.getBlockState(new BlockPos(12, 2, 4))
                        .is(RouterRegistration.FEDERATION_CABLE.get()), "a plain wrench click removes nothing");
                for (var cell : blocks.keySet()) use(helper, player, cell, true);
                phase[0] = 1;
                throw new GameTestAssertException("Waiting for the rest of the converter to unform");
            }
            for (var entry : blocks.entrySet()) {
                helper.assertTrue(helper.getBlockState(entry.getKey()).isAir(), entry.getValue() + " was dismantled");
                helper.assertValueEqual(player.getInventory().countItem(entry.getValue()), 1,
                        entry.getValue() + " went into the inventory");
            }
            var rest = helper.getBlockState(converter.west());
            helper.assertTrue(rest.is(PowerRegistration.BEAM_CONVERTER.get()) && !RouterBlock.formed(rest),
                    "the converter's other member stays, unformed");
            RouterEvidence.write("wrenchdismantleseveryfederationblock", 3, Map.of(
                    "blocks", Integer.toString(blocks.size()), "intoInventory", "true", "plainClick", "kept"));
        });
    }

    private static void place(GameTestHelper helper, Map<BlockPos, Item> blocks, BlockPos cell, Block block, Item item) {
        helper.setBlock(cell, block);
        blocks.put(cell, item);
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative, boolean sneaking) {
        var absolute = helper.absolutePos(relative);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP, absolute, false);
        player.setShiftKeyDown(sneaking);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
    }
}
