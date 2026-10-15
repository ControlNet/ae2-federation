package space.controlnet.ae2federation.test;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;

/** Exports only real production blocks: terminal, native CPU/provider, furnace, hopper and import bus. */
@PrefixGameTestTemplate(false)
public final class ProjectionProductionExportGameTests {
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1000)
    public static void projectionProductionExport(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for native Grids");
            if (stage[0] == 0) {
                helper.setBlock(new BlockPos(6, 3, 2), Blocks.HOPPER);
                helper.setBlock(new BlockPos(6, 2, 2), Blocks.FURNACE);
                helper.setBlock(new BlockPos(6, 1, 2), Blocks.HOPPER);
                helper.<FurnaceBlockEntity>getBlockEntity(new BlockPos(6, 2, 2)).setItem(1, new ItemStack(Items.COAL, 16));
                for (var pos : new BlockPos[] {new BlockPos(7, 3, 3), new BlockPos(7, 3, 2), new BlockPos(7, 2, 2),
                        new BlockPos(7, 1, 2)}) {
                    PartHelper.setPart(helper.getLevel(), helper.absolutePos(pos), null, null, AEParts.GLASS_CABLE.item(AEColor.BLUE));
                }
                PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(5, 4, 5)), null, null, AEParts.GLASS_CABLE.item(AEColor.RED));
                PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(5, 4, 5)), Direction.UP, null, AEParts.CRAFTING_TERMINAL.asItem());
                stage[0] = 1;
                helper.fail("Waiting for the next native initialization tick");
            }
            if (stage[0] == 1) {
                var bus = (appeng.parts.automation.ImportBusPart) PartHelper.setPart(helper.getLevel(), helper.absolutePos(new BlockPos(7, 1, 2)), Direction.WEST, null, AEParts.IMPORT_BUS.asItem());
                helper.assertTrue(bus != null, "Import Bus is installed on the native source cable");
                bus.getConfig().setStack(0, new appeng.api.stacks.GenericStack(PatternProjectionFixture.stone(), 1));
                fixture.putInConsumer(PatternProjectionFixture.cobblestone(), 16);
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                stage[0] = 2;
                helper.fail("Waiting for the next native initialization tick");
            }
            helper.assertTrue(fixture.consumerService().isCraftable(PatternProjectionFixture.stone()), "Native recipe is projected");
            var template = new StructureTemplate();
            template.fillFromWorld(helper.getLevel(), helper.absolutePos(BlockPos.ZERO), new Vec3i(11, 7, 10), false, null);
            var policies = new CompoundTag();
            policies.put("data", PolicySavedData.get(helper.getLevel()).save(new CompoundTag(), helper.getLevel().registryAccess()));
            policies.putInt("DataVersion", net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
            try {
                var directory = Path.of(System.getProperty("ae2federation.nativeEvidenceFile")).toAbsolutePath().getParent();
                Files.createDirectories(directory);
                NbtIo.writeCompressed(template.save(new CompoundTag()), directory.resolve("projection_production.nbt"));
                NbtIo.writeCompressed(policies, directory.resolve("ae2federation_global_policies.dat"));
                Files.writeString(directory.resolve("projection-production.txt"), "consumer=" + fixture.consumerNetwork()
                        + "\nexecuting=" + fixture.providerNetwork() + "\nStorage sharing never configured\n");
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        });
    }
}
