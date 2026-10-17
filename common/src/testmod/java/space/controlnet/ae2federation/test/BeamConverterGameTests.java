package space.controlnet.ae2federation.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.BeamConverterBlockEntity;
import space.controlnet.ae2federation.power.CableCrossSection;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * A Beam Converter forms only at a converter's size and facing one way; formed, it links Federation cable through its
 * front alone and takes FE through every other face.
 */
@PrefixGameTestTemplate(false)
public final class BeamConverterGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private BeamConverterGameTests() {
    }

    /**
     * Each of the six sizes forms, facing east in a row of its own; a 2x2x3 body (a block short) and a 1x1x2 body whose
     * blocks face opposite ways do not.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterShapes(GameTestHelper helper) {
        var block = PowerRegistration.BEAM_CONVERTER.get();
        var formed = new ArrayList<List<BlockPos>>();
        int z = 2;
        for (var section : CableCrossSection.values()) {
            var cells = box(new BlockPos(2, 2, z), section.converterLength(), section.wide(), section.narrow());
            cells.forEach(cell -> place(helper, cell, Direction.EAST));
            formed.add(cells);
            z += section.narrow() + 1;
        }
        var short2x2 = box(new BlockPos(20, 2, 2), 3, 2, 2);
        short2x2.forEach(cell -> place(helper, cell, Direction.EAST));
        var mixed = List.of(new BlockPos(20, 2, 8), new BlockPos(21, 2, 8));
        place(helper, mixed.get(0), Direction.EAST);
        place(helper, mixed.get(1), Direction.WEST);
        helper.succeedWhen(() -> {
            for (var cells : formed) {
                for (var cell : cells) {
                    helper.assertTrue(RouterBlock.formed(helper.getBlockState(cell)), "converter member " + cell + " forms");
                }
            }
            for (var cell : short2x2) {
                helper.assertTrue(helper.getBlockState(cell).is(block) && !RouterBlock.formed(helper.getBlockState(cell)),
                        "a 2x2x3 body is not a converter: " + cell);
            }
            for (var cell : mixed) {
                helper.assertTrue(!RouterBlock.formed(helper.getBlockState(cell)), "a body facing two ways stays unformed");
            }
            RouterEvidence.write("beamconvertershapes", 8, Map.of(
                    "1x1x2", "formed", "1x2x4", "formed", "1x3x6", "formed", "2x2x4", "formed", "2x3x6", "formed",
                    "3x3x6", "formed", "2x2x3", "unformed", "mixedFacing", "unformed"));
        });
    }

    /**
     * A 1x1x2 converter facing east, its back to the west: a cable at its front joins its domain, cables at its side
     * and back do not. FE enters through the side, not the front.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void beamConverterLinksThroughItsFront(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(2, 1, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        var front = CENTER;
        var back = CENTER.west();
        place(helper, front, Direction.EAST);
        place(helper, back, Direction.EAST);
        fixtures.placeFederationCable(front.east());
        fixtures.placeFederationCable(front.north());
        fixtures.placeFederationCable(back.west());
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var converterNode = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(front));
        var inserted = new int[] {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(RouterBlock.formed(helper.getBlockState(front)), "the 1x1x2 converter forms");
            var domain = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level,
                    helper.absolutePos(front.east())));
            helper.assertTrue(domain.isPresent() && domain.get().nodes().contains(converterNode)
                    && domain.get().nodes().contains(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(back))),
                    "the cable at the front joins the whole converter");
            for (var cable : new BlockPos[] {front.north(), back.west()}) {
                var other = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(cable)));
                helper.assertTrue(other.isEmpty() || !other.get().nodes().contains(converterNode),
                        "a cable at the side or back links nothing: " + cable);
            }
            helper.assertTrue(level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(front),
                    Direction.EAST) == null, "the front takes no FE");
            var side = level.getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(front), Direction.UP);
            helper.assertTrue(side != null, "a side takes FE");
            if (inserted[0] < 0) inserted[0] = side.receiveEnergy(1000, false);
            helper.assertValueEqual(inserted[0], 1000, "FE accepted by the side");
            var converter = (BeamConverterBlockEntity) helper.getBlockEntity(front);
            helper.assertValueEqual(converter.stored(), 1000.0, "the block holds what it took in");
            RouterEvidence.write("beamconverterlinksthroughitsfront", 6, Map.of(
                    "formed", "1x1x2", "front", "linked", "sideAndBack", "unlinked", "frontFe", "none",
                    "sideFe", "accepted", "stored", "1000"));
            fixtures.close();
        });
    }

    private static List<BlockPos> box(BlockPos min, int sx, int sy, int sz) {
        var cells = new ArrayList<BlockPos>();
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int zz = 0; zz < sz; zz++) cells.add(min.offset(x, y, zz));
        return cells;
    }

    private static void place(GameTestHelper helper, BlockPos position, Direction facing) {
        helper.setBlock(position, PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                .setValue(BeamConverterBlock.FACING, facing));
    }
}
