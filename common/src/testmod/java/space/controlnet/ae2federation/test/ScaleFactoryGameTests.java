package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;
import space.controlnet.ae2federation.test.scale.ScaleFactoryProfile;
import space.controlnet.ae2federation.test.scale.ScaleFederationIdentityTarget;
import space.controlnet.ae2federation.test.scale.ScaleFederationRoute;
import space.controlnet.ae2federation.test.scale.ScaleFederationTwoTargetRoute;
import space.controlnet.ae2federation.test.scale.ScaleFederationThirdRoute;
import space.controlnet.ae2federation.test.scale.ScaleFederationFourthRoute;
import space.controlnet.ae2federation.test.scale.ScaleGridFixture;
import space.controlnet.ae2federation.test.scale.ScaleNativeProcessingProbe;
import space.controlnet.ae2federation.test.scale.ScaleDriveRetention;
import space.controlnet.ae2federation.test.scale.ScaleNativeSubnetTarget;
import space.controlnet.ae2federation.test.scale.ScaleNativeSubnetThreeTargets;
import space.controlnet.ae2federation.test.scale.ScaleOneGridInspection;
import space.controlnet.ae2federation.test.scale.ScaleProcessingCatalog;
import space.controlnet.ae2federation.test.scale.ScaleStructurePreflight;
import space.controlnet.ae2federation.test.scale.ScaleSourceHosts;
import space.controlnet.ae2federation.test.scale.ScaleLargeDirectOneTarget;
import space.controlnet.ae2federation.test.scale.ScaleLargeDirectTwoTargets;
import space.controlnet.ae2federation.test.scale.ScaleLargeDirectSixteenTargets;
import space.controlnet.ae2federation.test.scale.ScaleLargeDirect256Replay;
import space.controlnet.ae2federation.test.scale.ScaleLargeNativeSubnetSixteenTargets;
import space.controlnet.ae2federation.test.scale.ScaleLargeNativeSubnet256Replay;
import space.controlnet.ae2federation.test.scale.ScaleLargeFederationSixteenTargets;
import space.controlnet.ae2federation.test.scale.ScaleLargeFederation256Replay;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.identity.NetworkId;

@PrefixGameTestTemplate(false)
public final class ScaleFactoryGameTests {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ScaleFactoryGameTests.class);

    private ScaleFactoryGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 200, required = true, manualOnly = true)
    public static void scaleLargeStructurePreflightDevelopment(GameTestHelper helper) {
        ScaleStructurePreflight.verify(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 200, required = true, manualOnly = true)
    public static void scaleLargeStructureOldBoundsNegative(GameTestHelper helper) {
        ScaleStructurePreflight.verify(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 200, required = true, manualOnly = true)
    public static void scaleLargeStructureOccupiedNegative(GameTestHelper helper) {
        helper.setBlock(new BlockPos(6, 2, 5), net.minecraft.world.level.block.Blocks.CHEST);
        ScaleStructurePreflight.verify(helper);
        helper.succeed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleSixteenSourceHostsDevelopment(GameTestHelper helper) {
        ScaleSourceHosts.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleLargeDirectOneTargetDevelopment(GameTestHelper helper) {
        ScaleLargeDirectOneTarget.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleLargeDirectTwoTargetsDevelopment(GameTestHelper helper) {
        ScaleLargeDirectTwoTargets.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 2400, required = true, manualOnly = true)
    public static void scaleLargeDirectSixteenTargetsDevelopment(GameTestHelper helper) {
        ScaleLargeDirectSixteenTargets.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 46000, required = true, manualOnly = true)
    public static void scaleLargeDirect256ReplayDevelopment(GameTestHelper helper) {
        ScaleLargeDirect256Replay.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
               timeoutTicks = 2000000, required = true, manualOnly = true)
    public static void scaleSmallNativeBigGridTimed(GameTestHelper helper) {
        ScaleLargeDirect256Replay.runTimed(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 40000, required = true, manualOnly = true)
    public static void scaleLargeNativeSubnetSixteenTargetsDevelopment(GameTestHelper helper) {
        ScaleLargeNativeSubnetSixteenTargets.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 90000, required = true, manualOnly = true)
    public static void scaleLargeNativeSubnet256ReplayDevelopment(GameTestHelper helper) {
        ScaleLargeNativeSubnet256Replay.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
               timeoutTicks = 5000000, required = true, manualOnly = true)
    public static void scaleSmallNativeSubnetTimed(GameTestHelper helper) {
        ScaleLargeNativeSubnet256Replay.runTimed(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
               timeoutTicks = 40000, required = true, manualOnly = true)
    public static void scaleLargeFederationSixteenTargetsDevelopment(GameTestHelper helper) {
        ScaleLargeFederationSixteenTargets.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
              timeoutTicks = 90000, required = true, manualOnly = true)
    public static void scaleLargeFederation256ReplayDevelopment(GameTestHelper helper) {
        ScaleLargeFederation256Replay.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
               timeoutTicks = 5000000, required = true, manualOnly = true)
    public static void scaleSmallFederationTimed(GameTestHelper helper) {
        ScaleLargeFederation256Replay.runTimed(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
             timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleSmallGridTopology(GameTestHelper helper) {
        verifyGridTopology(helper, "small");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
             timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleLateGridTopology(GameTestHelper helper) {
        verifyGridTopology(helper, "late");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
             timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleUltraGridTopology(GameTestHelper helper) {
        verifyGridTopology(helper, "ultra");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleSmallProcessingDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
                timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeBigGridTwoPatternsDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeBigGridOneSourceGridDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeOneGridCatalogDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeOneGridCatalogBatch16Development(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 5200, required = true, manualOnly = true)
    public static void scaleNativeOneGridCatalog64TypesDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 23000, required = true, manualOnly = true)
    public static void scaleNativeOneGridCatalog256ReplayDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeSubnetCatalogBatch16Development(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 46000, required = true, manualOnly = true)
    public static void scaleNativeSubnetCatalog256ReplayDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, false, true, false, false, false,
                true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeSubnetTwoTargetsDevelopment(GameTestHelper helper) {
        runNativeSubnetTwoTargets(helper, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 2400, required = true, manualOnly = true)
    public static void scaleNativeSubnetThreeTargetsDevelopment(GameTestHelper helper) {
        ScaleNativeSubnetThreeTargets.run(helper);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 2400, required = true, manualOnly = true)
    public static void scaleNativeSubnetFourTargetsDevelopment(GameTestHelper helper) {
        ScaleNativeSubnetThreeTargets.run(helper, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 240000, required = true, manualOnly = true)
    public static void scaleNativeSubnetFourTargets256ReplayDevelopment(GameTestHelper helper) {
        ScaleNativeSubnetThreeTargets.run(helper, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 70000, required = true, manualOnly = true)
    public static void scaleNativeSubnetTwoTargets256ReplayDevelopment(GameTestHelper helper) {
        runNativeSubnetTwoTargets(helper, true);
    }

    private static void runNativeSubnetTwoTargets(GameTestHelper helper, boolean catalogReplay) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleNativeSubnetTarget east;
            ScaleNativeSubnetTarget south;
            ScaleNativeProcessingProbe processing;
            int stage;
            appeng.api.networking.IGrid sourceGrid;
            appeng.api.networking.IGrid eastGrid;
            appeng.api.networking.IGrid southGrid;
            NetworkId sourceId;
            NetworkId eastId;
            NetworkId southId;
            boolean catalogSelected;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> catalogReplay ? slot % 2 == 0 : slot == 0,
                                slot -> catalogReplay ? slot % 2 != 0 : slot == 1), false,
                        catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                        NetworkId.create(), java.util.List.of(Direction.EAST, Direction.SOUTH));
                state.stage = 1;
                helper.assertTrue(false, "Waiting for one native source Provider Grid");
                return;
            }
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                        && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for settled native source Provider Grid");
                var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid())
                        .orElseThrow();
                state.crafting = new ProcessingCraftingGrid(helper, sourceId);
                state.stage = 2;
                helper.assertTrue(false, "Waiting for physical native CPU and source cell");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for source CPU Grid boot");
            if (storage.getGrid() != source.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for source Provider/CPU connection");
                return;
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1, "One native source CPU");
            if (state.stage == 2) {
                state.provider.register();
                var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow();
                state.retention = new ScaleDriveRetention(helper, state.crafting, sourceId, catalogReplay ? 5 : 1,
                        new BlockPos(2, 2, 2));
                state.east = new ScaleNativeSubnetTarget(helper, true, true, Direction.EAST);
                state.south = new ScaleNativeSubnetTarget(helper, true, true, Direction.SOUTH);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for two physical native target Grids");
                return;
            }
            helper.assertTrue(state.retention.ready(source)
                            && state.east.ready(source) && state.south.ready(source),
                    "Waiting for source Drive and both distinct physical subnet targets");
            var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid());
            var eastId = FederationDomainRegistryAccess.confirmedNetworkId(state.east.grid());
            var southId = FederationDomainRegistryAccess.confirmedNetworkId(state.south.grid());
            helper.assertTrue(sourceId.isPresent() && eastId.isPresent() && southId.isPresent()
                            && source.getGrid() != state.east.grid() && source.getGrid() != state.south.grid()
                            && state.east.grid() != state.south.grid()
                            && !sourceId.orElseThrow().equals(eastId.orElseThrow())
                            && !sourceId.orElseThrow().equals(southId.orElseThrow())
                            && !eastId.orElseThrow().equals(southId.orElseThrow()),
                    "Source and both target Grids must have separate settled identities");
            if (state.stage == 3) {
                state.sourceGrid = source.getGrid();
                state.eastGrid = state.east.grid();
                state.southGrid = state.south.grid();
                state.sourceId = sourceId.orElseThrow();
                state.eastId = eastId.orElseThrow();
                state.southId = southId.orElseThrow();
                LOGGER.info("AE2F_SCALE_TWO_TARGET_IDS source={} east={} south={} sourceGrid={} eastGrid={} southGrid={}",
                        sourceId.orElseThrow().value(), eastId.orElseThrow().value(), southId.orElseThrow().value(),
                        System.identityHashCode(source.getGrid()), System.identityHashCode(state.east.grid()),
                        System.identityHashCode(state.south.grid()));
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting,
                        state.east, state.south, catalogReplay);
                if (!catalogReplay) state.processing.selectTwoTargetRun(state.retention);
                state.stage = 4;
                helper.assertTrue(false, "Waiting for two native Lane Processing jobs");
                return;
            }
            helper.assertTrue(source.getGrid() == state.sourceGrid && state.east.grid() == state.eastGrid
                            && state.south.grid() == state.southGrid
                            && sourceId.orElseThrow().equals(state.sourceId)
                            && eastId.orElseThrow().equals(state.eastId)
                            && southId.orElseThrow().equals(state.southId)
                            && state.retention.ready(source)
                            && state.processing.requesterNode().getGrid() == state.sourceGrid
                            && state.crafting.cpuNode().getGrid() == state.sourceGrid,
                    "Source, requester, CPU, Drive and both target Grids must retain separate settled identities");
            var eastMachine = state.east.machine();
            var southMachine = state.south.machine();
            helper.assertTrue(eastMachine != southMachine
                            && eastMachine.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(new BlockPos(6, 2, 2)), Direction.WEST)
                            && southMachine.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(new BlockPos(2, 2, 6)), Direction.NORTH)
                            && state.provider.lane(0).getReturnInv() != state.provider.lane(1).getReturnInv(),
                    "Both physical subnet machines must expose separate sided handlers and native return owners");
            var inventory = state.provider.composition().patternInventory();
            helper.assertValueEqual(inventory.size(), catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                    "Shared physical Provider catalog slots");
            var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
            var inputKeys = new java.util.HashSet<AEItemKey>();
            var outputKeys = new java.util.HashSet<AEItemKey>();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), helper.getLevel());
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                var input = AEItemKey.of(recipe.input());
                var output = AEItemKey.of(recipe.output());
                var lane = slot % 2;
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(output)
                                && decoded.getPrimaryOutput().amount() == 1 && decoded.getOutputs().size() == 1
                                && state.provider.lane(lane).getAvailablePatterns().contains(decoded)
                                && state.provider.lane(1 - lane).getAvailablePatterns().stream()
                                        .noneMatch(pattern -> pattern.equals(decoded))
                                && state.provider.publishedProviders(decoded).equals(java.util.List.of(
                                        state.provider.lane(lane)))
                                && (lane == 0 ? eastMachine : southMachine).inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && !(lane == 0 ? southMachine : eastMachine).inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && source.getGrid().getCraftingService().getCraftingFor(output).contains(decoded),
                        "Physical slot must publish exactly its assigned native Lane: " + slot);
                helper.assertTrue(inputKeys.add(input) && outputKeys.add(output),
                        "Physical catalog input/output keys must be unique: " + slot);
                selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
            }
            if (catalogReplay) {
                helper.assertValueEqual(inputKeys.size(), 256, "Decoded physical input types");
                helper.assertValueEqual(outputKeys.size(), 256, "Decoded physical output types");
                helper.assertValueEqual(state.provider.lane(0).getAvailablePatterns().size(), 128,
                        "East native Lane Pattern count");
                helper.assertValueEqual(state.provider.lane(1).getAvailablePatterns().size(), 128,
                        "South native Lane Pattern count");
                helper.assertValueEqual(eastMachine.recipeCount(), 128, "East target machine recipes");
                helper.assertValueEqual(southMachine.recipeCount(), 128, "South target machine recipes");
                helper.assertTrue(source.getGrid().getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "Native crafting service must publish all 256 physical outputs");
                if (!state.catalogSelected) {
                    var high = selections.get(ScaleProcessingCatalog.SIZE - 1);
                    state.processing.selectHighPattern(high.input(), high.output());
                    state.processing.selectCatalogRun(selections, state.retention);
                    state.catalogSelected = true;
                }
            }
            helper.assertTrue(state.processing.tick(), "Waiting for distinct east and south native Processing jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), catalogReplay ? 256 : 2,
                    "Native target jobs");
            helper.assertValueEqual(state.processing.acceptedTotal(), catalogReplay ? 4096L : 32L,
                    "Typed native requester callback units");
            helper.assertTrue(state.provider.lane(0).getReturnInv().isEmpty()
                            && state.provider.lane(1).getReturnInv().isEmpty()
                            && source.getGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Both native Lane returns and the single source CPU must be idle");
            helper.assertValueEqual(state.east.inputAmount(AEItemKey.of(Items.COBBLESTONE)), 0L,
                    "East target must consume its cobblestone");
            helper.assertValueEqual(state.south.inputAmount(AEItemKey.of(Items.DIRT)), 0L,
                    "South target must consume its dirt");
            helper.assertValueEqual(state.east.inputAmount(AEItemKey.of(Items.DIRT)), 0L,
                    "East target must not receive south Lane dirt");
            helper.assertValueEqual(state.south.inputAmount(AEItemKey.of(Items.COBBLESTONE)), 0L,
                    "South target must not receive east Lane cobblestone");
            state.processing.close();
            state.south.close();
            state.east.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeOneGridTwoMachinesDevelopment(GameTestHelper helper) {
        runOneGridTwoMachines(helper, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 23000, required = true, manualOnly = true)
    public static void scaleNativeOneGridTwoMachines256ReplayDevelopment(GameTestHelper helper) {
        runOneGridTwoMachines(helper, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeOneGridFourMachinesDevelopment(GameTestHelper helper) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleNativeProcessingProbe processing;
            appeng.api.networking.IGrid grid;
            NetworkId id;
            int stage;
            int lastReceipt = -1;
            boolean machinesReported;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> slot == 0, slot -> slot == 1,
                                slot -> slot == 2, slot -> slot == 3), false, 4,
                        NetworkId.create(), java.util.List.of(Direction.EAST, Direction.SOUTH,
                                Direction.UP, Direction.DOWN));
                state.stage = 1;
                helper.assertTrue(false, "Waiting for four-face native Provider Grid");
                return;
            }
            helper.assertValueEqual(state.provider.laneCount(), 4,
                    "Four distinct native physical Lane faces are required");
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                                && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for confirmed four-face Provider Grid");
                state.crafting = new ProcessingCraftingGrid(helper,
                        FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).orElseThrow());
                state.stage = 2;
                helper.assertTrue(false, "Waiting for native CPU and source cell");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for native source CPU boot");
            if (storage.getGrid() != source.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for source Provider/CPU connection");
                return;
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1, "One native source CPU");
            if (state.stage == 2) {
                state.grid = source.getGrid();
                state.id = FederationDomainRegistryAccess.confirmedNetworkId(state.grid).orElseThrow();
                state.provider.register();
                state.retention = new ScaleDriveRetention(helper, state.crafting, state.id, 1,
                        new BlockPos(2, 2, 2));
                state.processing = ScaleNativeProcessingProbe.oneGridFourMachines(helper, state.provider, state.crafting);
                state.processing.selectFourMachineRun(state.retention);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for four native machine capabilities and requester");
                return;
            }
            helper.assertTrue(state.grid == source.getGrid()
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.grid).filter(state.id::equals).isPresent()
                            && state.retention.ready(source)
                            && state.processing.requesterNode().getGrid() == state.grid
                            && state.crafting.cpuNode().getGrid() == state.grid,
                    "Provider, CPU, requester and physical Drive must retain one confirmed source Grid");
            var completed = state.processing.completedJobCount();
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(),
                    completed, completed != state.lastReceipt);
            state.lastReceipt = completed;
            var faces = java.util.List.of(Direction.EAST, Direction.SOUTH, Direction.UP, Direction.DOWN);
            var positions = faces.stream().map(NativeProviderLaneFixtures.HOST_POS::relative).toList();
            var machines = positions.stream().map(position ->
                    helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(position))
                    .toList();
            var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var handlers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var returns = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var inputKeys = new java.util.HashSet<AEItemKey>();
            var outputKeys = new java.util.HashSet<AEItemKey>();
            var inventory = state.provider.composition().patternInventory();
            helper.assertValueEqual(inventory.size(), 4, "Four occupied physical Pattern slots");
            for (int lane = 0; lane < 4; lane++) {
                var machine = machines.get(lane);
                var handler = helper.getLevel().getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(positions.get(lane)), faces.get(lane).getOpposite());
                helper.assertTrue(identities.add(machine) && handlers.add(handler)
                                && returns.add(state.provider.lane(lane).getReturnInv())
                                && machine.inputHandler() == handler && machine.recipeCount() == 1,
                        "Four unique adjacent physical machine, sided capability and native return owners: " + lane);
                var recipe = ScaleProcessingCatalog.recipes().get(lane);
                var input = AEItemKey.of(recipe.input());
                var output = AEItemKey.of(recipe.output());
                helper.assertTrue(inputKeys.add(input) && outputKeys.add(output),
                        "Four physical Patterns must have distinct typed inputs and outputs: " + lane);
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(lane), helper.getLevel());
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getOutputs().size() == 1
                                && decoded.getPrimaryOutput().what().equals(output)
                                && decoded.getPrimaryOutput().amount() == 1
                                && state.provider.publishedProviders(decoded).equals(
                                        java.util.List.of(state.provider.lane(lane)))
                                && state.grid.getCraftingService().getCraftingFor(output).contains(decoded),
                        "Physical Pattern must publish exclusively on its assigned native Lane: " + lane);
                helper.assertTrue(machine.inputHandler().isItemValid(0,
                                new net.minecraft.world.item.ItemStack(recipe.input())),
                        "Assigned machine must accept its physical input: " + lane);
                for (int other = 0; other < 4; other++) {
                    if (other != lane) {
                        helper.assertTrue(!machines.get(other).inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                        && !state.provider.lane(other).getAvailablePatterns().contains(decoded),
                                "Other physical machine and Lane must reject this typed Pattern: " + lane);
                    }
                }
            }
            helper.assertValueEqual(identities.size(), 4, "Four distinct BlockEntity instances");
            helper.assertValueEqual(handlers.size(), 4, "Four distinct sided handlers");
            helper.assertValueEqual(returns.size(), 4, "Four distinct native return inventories");
            helper.assertTrue(java.util.Collections.disjoint(inputKeys, outputKeys),
                    "Physical input and output types must be disjoint across four machines");
            if (!state.machinesReported) {
                LOGGER.info("AE2F_SCALE_FOUR_MACHINES sourceGrid={} sourceId={} positions={} owners={} returns={}",
                        Integer.toUnsignedString(System.identityHashCode(state.grid)), state.id.value(), positions,
                        machines.stream().map(machine -> Integer.toUnsignedString(System.identityHashCode(machine))).toList(),
                        java.util.stream.IntStream.range(0, 4).mapToObj(lane -> Integer.toUnsignedString(
                                System.identityHashCode(state.provider.lane(lane).getReturnInv()))).toList());
                state.machinesReported = true;
            }
            helper.assertTrue(state.processing.tick(), "Waiting for four serial native 16-unit machine jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), 4, "Four native physical machine jobs");
            helper.assertValueEqual(state.processing.acceptedTotal(), 64L, "Four exact typed requester callbacks");
            for (int lane = 0; lane < 4; lane++) {
                helper.assertTrue(state.provider.lane(lane).getReturnInv().isEmpty()
                                && machines.get(lane).inputCount(ScaleProcessingCatalog.recipes().get(lane).input()) == 0,
                        "Physical machine and selected native Lane must be empty after four jobs: " + lane);
            }
            helper.assertTrue(state.grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Native CPU must be idle after four serial jobs");
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(), 4, true);
            state.processing.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 23000, required = true, manualOnly = true)
    public static void scaleNativeOneGridFourMachines256ReplayDevelopment(GameTestHelper helper) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleNativeProcessingProbe processing;
            appeng.api.networking.IGrid grid;
            NetworkId id;
            int stage;
            int lastReceipt = -1;
            boolean catalogSelected;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> slot % 4 == 0, slot -> slot % 4 == 1,
                                slot -> slot % 4 == 2, slot -> slot % 4 == 3), false, ScaleProcessingCatalog.SIZE,
                        NetworkId.create(), java.util.List.of(Direction.EAST, Direction.SOUTH,
                                Direction.UP, Direction.DOWN));
                state.stage = 1;
                helper.assertTrue(false, "Waiting for four-Lane catalog Provider Grid");
                return;
            }
            helper.assertValueEqual(state.provider.composition().patternInventory().size(),
                    ScaleProcessingCatalog.SIZE, "Four-machine replay requires 256 physical Pattern slots");
            helper.assertValueEqual(state.provider.laneCount(), 4, "Four native physical Lane faces");
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                                && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for settled catalog Provider Grid");
                state.crafting = new ProcessingCraftingGrid(helper,
                        FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).orElseThrow());
                state.stage = 2;
                helper.assertTrue(false, "Waiting for source CPU and cell");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for native CPU boot");
            if (storage.getGrid() != source.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for source Provider/CPU connection");
                return;
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1, "One source CPU");
            if (state.stage == 2) {
                state.grid = source.getGrid();
                state.id = FederationDomainRegistryAccess.confirmedNetworkId(state.grid).orElseThrow();
                state.provider.register();
                state.retention = new ScaleDriveRetention(helper, state.crafting, state.id, 5,
                        new BlockPos(2, 2, 2));
                state.processing = ScaleNativeProcessingProbe.oneGridFourMachines(helper, state.provider,
                        state.crafting, true);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for four physical catalog machines and requester");
                return;
            }
            helper.assertTrue(state.grid == source.getGrid()
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.grid).filter(state.id::equals).isPresent()
                            && state.retention.ready(source)
                            && state.processing.requesterNode().getGrid() == state.grid
                            && state.crafting.cpuNode().getGrid() == state.grid,
                    "Provider, CPU, requester and five-cell Drive must retain one confirmed Grid");
            var completed = state.processing.completedJobCount();
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(),
                    completed, completed != state.lastReceipt);
            state.lastReceipt = completed;
            var faces = java.util.List.of(Direction.EAST, Direction.SOUTH, Direction.UP, Direction.DOWN);
            var positions = faces.stream().map(NativeProviderLaneFixtures.HOST_POS::relative).toList();
            var machines = positions.stream().map(position ->
                    helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(position))
                    .toList();
            var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var handlers = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var returns = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (int lane = 0; lane < 4; lane++) {
                var machine = machines.get(lane);
                var handler = helper.getLevel().getCapability(
                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                        helper.absolutePos(positions.get(lane)), faces.get(lane).getOpposite());
                helper.assertTrue(identities.add(machine) && handlers.add(handler)
                                && returns.add(state.provider.lane(lane).getReturnInv())
                                && machine.inputHandler() == handler && machine.recipeCount() == 64,
                        "Four separate 64-recipe physical machines, sided handlers and native returns: " + lane);
            }
            if (!state.catalogSelected) {
                var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
                var inputKeys = new java.util.HashSet<AEItemKey>();
                var outputKeys = new java.util.HashSet<AEItemKey>();
                var inventory = state.provider.composition().patternInventory();
                for (int slot = 0; slot < ScaleProcessingCatalog.SIZE; slot++) {
                    var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), helper.getLevel());
                    var recipe = ScaleProcessingCatalog.recipes().get(slot);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    var lane = slot % 4;
                    helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                    && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                    && decoded.getOutputs().size() == 1
                                    && decoded.getPrimaryOutput().what().equals(output)
                                    && decoded.getPrimaryOutput().amount() == 1
                                    && state.provider.publishedProviders(decoded).equals(
                                            java.util.List.of(state.provider.lane(lane)))
                                    && state.provider.lane(lane).getAvailablePatterns().contains(decoded)
                                    && state.grid.getCraftingService().getCraftingFor(output).contains(decoded)
                                    && machines.get(lane).inputHandler().isItemValid(0,
                                            new net.minecraft.world.item.ItemStack(recipe.input())),
                            "Physical slot must advertise only its modulo-four native Lane and machine: " + slot);
                    for (int other = 0; other < 4; other++) {
                        if (other != lane) {
                            helper.assertTrue(!state.provider.lane(other).getAvailablePatterns().contains(decoded)
                                            && !machines.get(other).inputHandler().isItemValid(0,
                                                    new net.minecraft.world.item.ItemStack(recipe.input())),
                                    "Unselected Lane and physical machine must reject slot: " + slot);
                        }
                    }
                    helper.assertTrue(inputKeys.add(input) && outputKeys.add(output),
                            "Physical catalog must contain unique input and output types: " + slot);
                    selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
                }
                helper.assertValueEqual(inputKeys.size(), ScaleProcessingCatalog.SIZE, "256 decoded input types");
                helper.assertValueEqual(outputKeys.size(), ScaleProcessingCatalog.SIZE, "256 decoded output types");
                helper.assertTrue(java.util.Collections.disjoint(inputKeys, outputKeys)
                                && state.grid.getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "256 disjoint physical types must be published on the native Grid");
                for (int lane = 0; lane < 4; lane++) {
                    helper.assertValueEqual(state.provider.lane(lane).getAvailablePatterns().size(), 64,
                            "Each native physical Lane must own 64 Patterns");
                }
                LOGGER.info("AE2F_SCALE_FOUR_CATALOG sourceGrid={} sourceId={} positions={} owners={} returns={}",
                        Integer.toUnsignedString(System.identityHashCode(state.grid)), state.id.value(), positions,
                        machines.stream().map(machine -> Integer.toUnsignedString(System.identityHashCode(machine))).toList(),
                        java.util.stream.IntStream.range(0, 4).mapToObj(lane -> Integer.toUnsignedString(
                                System.identityHashCode(state.provider.lane(lane).getReturnInv()))).toList());
                var high = selections.get(ScaleProcessingCatalog.SIZE - 1);
                state.processing.selectHighPattern(high.input(), high.output());
                state.processing.selectFourMachineCatalogRun(selections, state.retention);
                state.catalogSelected = true;
            }
            helper.assertTrue(state.processing.tick(), "Waiting for 256 serial modulo-four native jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), 256, "Native physical Pattern jobs");
            helper.assertValueEqual(state.processing.acceptedTotal(), 4096L, "Typed native callback units");
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(), 256, true);
            for (int lane = 0; lane < 4; lane++) {
                helper.assertTrue(state.provider.lane(lane).getReturnInv().isEmpty(),
                        "All four native return owners must be empty: " + lane);
            }
            helper.assertTrue(state.grid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Native CPU must be idle after catalog replay");
            state.processing.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }

    private static void runOneGridTwoMachines(GameTestHelper helper, boolean catalogReplay) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleNativeProcessingProbe processing;
            int stage;
            int lastGridReceipt = -1;
            boolean machinesReported;
            boolean catalogSelected;
            appeng.api.networking.IGrid sourceGrid;
            NetworkId sourceId;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> catalogReplay ? slot % 2 == 0 : slot == 0,
                                slot -> catalogReplay ? slot % 2 != 0 : slot == 1), false,
                        catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                        NetworkId.create(), java.util.List.of(Direction.EAST, Direction.SOUTH));
                state.stage = 1;
                helper.assertTrue(false, "Waiting for direct native Provider Grid");
                return;
            }
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                        && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for settled direct native Provider Grid");
                state.crafting = new ProcessingCraftingGrid(helper,
                        FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).orElseThrow());
                state.stage = 2;
                helper.assertTrue(false, "Waiting for direct native CPU and source cell");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for direct source CPU Grid boot");
            if (storage.getGrid() != source.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for direct Provider/CPU connection");
                return;
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1, "One direct native source CPU");
            if (state.stage == 2) {
                state.sourceGrid = source.getGrid();
                state.sourceId = FederationDomainRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow();
                state.provider.register();
                state.retention = new ScaleDriveRetention(helper, state.crafting,
                        FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow(), catalogReplay ? 5 : 1,
                        new BlockPos(2, 2, 2));
                state.processing = ScaleNativeProcessingProbe.oneGridTwoMachines(helper, state.provider, state.crafting,
                        catalogReplay);
                if (!catalogReplay) state.processing.selectTwoTargetRun(state.retention);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for direct native machines and requester");
                return;
            }
            helper.assertTrue(state.retention.ready(source)
                            && state.processing.requesterNode().getGrid() == source.getGrid()
                            && state.crafting.cpuNode().getGrid() == source.getGrid()
                            && source.getGrid() == state.sourceGrid
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.sourceGrid)
                                    .filter(state.sourceId::equals).isPresent(),
                    "One source Grid must own Provider, CPU, requester and mounted Drive");
            var completed = state.processing.completedJobCount();
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(),
                    completed, completed != state.lastGridReceipt);
            state.lastGridReceipt = completed;
            var east = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                    NativeProviderLaneFixtures.TARGET_POS);
            var south = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                    NativeProviderLaneFixtures.HOST_POS.south());
            helper.assertTrue(east != south
                            && east.recipeCount() == (catalogReplay ? 128 : 1)
                            && south.recipeCount() == (catalogReplay ? 128 : 1)
                            && east.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(NativeProviderLaneFixtures.TARGET_POS), Direction.WEST)
                            && south.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(NativeProviderLaneFixtures.HOST_POS.south()), Direction.NORTH)
                            && east.inputHandler().isItemValid(0, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE))
                            && !east.inputHandler().isItemValid(0, new net.minecraft.world.item.ItemStack(Items.DIRT))
                            && south.inputHandler().isItemValid(0, new net.minecraft.world.item.ItemStack(Items.DIRT))
                            && !south.inputHandler().isItemValid(0, new net.minecraft.world.item.ItemStack(Items.COBBLESTONE)),
                    "Two distinct registered adjacent machines must accept only their own typed input");
            if (!state.machinesReported) {
                LOGGER.info("AE2F_SCALE_DIRECT_MACHINES eastPos={} eastOwner={} southPos={} southOwner={} "
                                + "lane0Return={} lane1Return={} sourceGrid={} sourceId={}",
                        NativeProviderLaneFixtures.TARGET_POS,
                        Integer.toUnsignedString(System.identityHashCode(east)),
                        NativeProviderLaneFixtures.HOST_POS.south(),
                        Integer.toUnsignedString(System.identityHashCode(south)),
                        Integer.toUnsignedString(System.identityHashCode(state.provider.lane(0).getReturnInv())),
                        Integer.toUnsignedString(System.identityHashCode(state.provider.lane(1).getReturnInv())),
                        Integer.toUnsignedString(System.identityHashCode(source.getGrid())), state.sourceId.value());
                state.machinesReported = true;
            }
            var inventory = state.provider.composition().patternInventory();
            helper.assertValueEqual(inventory.size(), catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                    "One shared physical Provider Pattern inventory");
            var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
            var inputKeys = new java.util.HashSet<AEItemKey>();
            var outputKeys = new java.util.HashSet<AEItemKey>();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), helper.getLevel());
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                var input = AEItemKey.of(recipe.input());
                var output = AEItemKey.of(recipe.output());
                var lane = slot % 2;
                var selectedMachine = lane == 0 ? east : south;
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(output)
                                && decoded.getPrimaryOutput().amount() == 1 && decoded.getOutputs().size() == 1
                                && state.provider.lane(lane).getAvailablePatterns().contains(decoded)
                                && state.provider.lane(1 - lane).getAvailablePatterns().stream()
                                        .noneMatch(pattern -> pattern.equals(decoded))
                                && state.provider.publishedProviders(decoded).equals(java.util.List.of(
                                        state.provider.lane(lane)))
                                && selectedMachine.inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && !(lane == 0 ? south : east).inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && source.getGrid().getCraftingService().getCraftingFor(output).contains(decoded),
                        "Physical slot must publish exactly its assigned native AE2 Lane: " + slot);
                helper.assertTrue(inputKeys.add(input) && outputKeys.add(output),
                        "Physical catalog input/output keys must be unique: " + slot);
                selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
            }
            if (catalogReplay) {
                helper.assertValueEqual(inputKeys.size(), ScaleProcessingCatalog.SIZE, "Decoded physical input types");
                helper.assertValueEqual(outputKeys.size(), ScaleProcessingCatalog.SIZE, "Decoded physical output types");
                helper.assertValueEqual(state.provider.lane(0).getAvailablePatterns().size(), 128,
                        "East native Lane physical Pattern count");
                helper.assertValueEqual(state.provider.lane(1).getAvailablePatterns().size(), 128,
                        "South native Lane physical Pattern count");
                helper.assertTrue(source.getGrid().getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "Native crafting service must publish all 256 decoded physical outputs");
                if (!state.catalogSelected) {
                    var high = selections.get(ScaleProcessingCatalog.SIZE - 1);
                    state.processing.selectHighPattern(high.input(), high.output());
                    state.processing.selectCatalogRun(selections, state.retention);
                    state.catalogSelected = true;
                }
            }
            helper.assertTrue(state.processing.tick(), "Waiting for two direct native physical machine jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), catalogReplay ? 256 : 2,
                    "Distinct physical machine jobs");
            ScaleOneGridInspection.assertScene(helper, source, state.crafting, state.processing.requesterNode(),
                    state.processing.completedJobCount(), true);
            helper.assertValueEqual(state.processing.acceptedTotal(), catalogReplay ? 4096L : 32L,
                    "Native requester callback units");
            helper.assertTrue(state.provider.lane(0).getReturnInv() != state.provider.lane(1).getReturnInv()
                            && state.provider.lane(0).getReturnInv().isEmpty()
                            && state.provider.lane(1).getReturnInv().isEmpty()
                            && source.getGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy())
                            && east.inputCount(Items.COBBLESTONE) == 0 && east.inputCount(Items.DIRT) == 0
                            && south.inputCount(Items.COBBLESTONE) == 0 && south.inputCount(Items.DIRT) == 0,
                    "Separate native return owners, both physical machines and CPU must be idle");
            state.processing.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleFederationTwoTargetsDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 70000, required = true, manualOnly = true)
    public static void scaleFederationTwoTargets256ReplayDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 2400, required = true, manualOnly = true)
    public static void scaleFederationThreeTargetsDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 210000, required = true, manualOnly = true)
    public static void scaleFederationThreeTargets256ReplayDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 2400, required = true, manualOnly = true)
    public static void scaleFederationFourTargetsDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, false, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 240000, required = true, manualOnly = true)
    public static void scaleFederationFourTargets256ReplayDevelopment(GameTestHelper helper) {
        runFederationTwoTargets(helper, true, true, true, true);
    }

    private static void runFederationTwoTargets(GameTestHelper helper, boolean catalogReplay) {
        runFederationTwoTargets(helper, catalogReplay, false);
    }

    private static void runFederationTwoTargets(GameTestHelper helper, boolean catalogReplay, boolean threeTargets) {
        runFederationTwoTargets(helper, catalogReplay, threeTargets, false);
    }

    private static void runFederationTwoTargets(GameTestHelper helper, boolean catalogReplay, boolean threeTargets,
            boolean fourTargets) {
        runFederationTwoTargets(helper, catalogReplay, threeTargets, fourTargets, false);
    }

    private static void runFederationTwoTargets(GameTestHelper helper, boolean catalogReplay, boolean threeTargets,
            boolean fourTargets, boolean fourTargetReplay) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleFederationIdentityTarget first;
            ScaleFederationIdentityTarget second;
            ScaleFederationTwoTargetRoute route;
            ScaleFederationThirdRoute thirdRoute;
            ScaleFederationIdentityTarget third;
            ScaleFederationIdentityTarget fourth;
            ScaleFederationFourthRoute fourthRoute;
            ScaleNativeProcessingProbe processing;
            int stage;
            boolean catalogSelected;
            boolean recipesChecked;
            appeng.api.networking.IGrid sourceGrid;
            appeng.api.networking.IGrid firstGrid;
            appeng.api.networking.IGrid secondGrid;
            NetworkId sourceId;
            NetworkId firstId;
            NetworkId secondId;
            NetworkId thirdId;
            appeng.api.networking.IGrid thirdGrid;
            appeng.api.networking.IGrid fourthGrid;
            NetworkId fourthId;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                         java.util.List.of(slot -> catalogReplay ? slot % (fourTargetReplay ? 4 : threeTargets ? 3 : 2) == 0 : slot == 0,
                                 slot -> catalogReplay ? slot % (fourTargetReplay ? 4 : threeTargets ? 3 : 2) == 1 : slot == 1), true,
                        catalogReplay ? ScaleProcessingCatalog.SIZE : 2, NetworkId.create());
                state.stage = 1;
                helper.assertTrue(false, "Waiting for one physical Federation source Provider");
                return;
            }
            if (state.stage == 1) {
                if (catalogReplay && threeTargets) {
                    helper.assertValueEqual(state.provider.composition().patternInventory().size(),
                            ScaleProcessingCatalog.SIZE, "Three-target replay needs 256 physical catalog slots on H0");
                }
                helper.assertTrue(state.provider.connectEnergy()
                        && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for settled physical Federation source");
                state.crafting = new ProcessingCraftingGrid(helper,
                        FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).orElseThrow());
                state.stage = 2;
                helper.assertTrue(false, "Waiting for physical source CPU and cell");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for source CPU Grid boot");
            if (source.getGrid() != storage.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for source Provider/CPU connection");
                return;
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1, "One authentic source CPU");
            if (state.stage == 2) {
                state.provider.register();
                state.retention = new ScaleDriveRetention(helper, state.crafting,
                        FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow(),
                        catalogReplay ? 5 : 1,
                        new BlockPos(2, 2, 2));
                state.first = new ScaleFederationIdentityTarget(helper, true);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for target A anchor identity");
                return;
            }
            if (state.stage == 3) {
                helper.assertTrue(state.retention.ready(source) && state.first.anchorReady(source.getGrid()),
                        "Waiting for source Drive and settled target A anchor");
                state.first.placeEndpoint();
                state.stage = 4;
                helper.assertTrue(false, "Waiting for target A Endpoint identity");
                return;
            }
            if (state.stage == 4) {
                helper.assertTrue(state.first.endpointReady() && state.first.sameLineage()
                                && state.first.onlyAnchorClaim() && state.first.settled(),
                        "Target A Endpoint must join only its settled anchor");
                state.first.placeExportBus();
                state.stage = 5;
                helper.assertTrue(false, "Waiting for target A Export Bus identity");
                return;
            }
            if (state.stage == 5) {
                helper.assertTrue(state.first.connectExportBus() && state.first.exportBusReady()
                                && state.first.busLineage() && state.first.onlyAnchorClaim() && state.first.settled(),
                        "Target A Export Bus must join only its settled anchor");
                state.second = new ScaleFederationIdentityTarget(helper, true, new BlockPos(5, 2, 7));
                state.stage = 6;
                helper.assertTrue(false, "Waiting for target B anchor identity");
                return;
            }
            if (state.stage == 6) {
                helper.assertTrue(state.second.anchorReady(source.getGrid()),
                        "Waiting for separate settled target B anchor");
                state.second.placeEndpoint();
                state.stage = 7;
                helper.assertTrue(false, "Waiting for target B Endpoint identity");
                return;
            }
            if (state.stage == 7) {
                helper.assertTrue(state.second.endpointReady() && state.second.sameLineage()
                                && state.second.onlyAnchorClaim() && state.second.settled(),
                        "Target B Endpoint must join only its settled anchor");
                state.second.placeExportBus();
                state.stage = 8;
                helper.assertTrue(false, "Waiting for target B Export Bus identity");
                return;
            }
            if (state.stage == 8) {
                helper.assertTrue(state.second.connectExportBus() && state.second.exportBusReady()
                                && state.second.busLineage() && state.second.onlyAnchorClaim() && state.second.settled(),
                        "Target B Export Bus must join only its settled anchor");
                var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid());
                var firstId = FederationDomainRegistryAccess.confirmedNetworkId(state.first.grid());
                var secondId = FederationDomainRegistryAccess.confirmedNetworkId(state.second.grid());
                helper.assertTrue(sourceId.isPresent() && firstId.isPresent() && secondId.isPresent()
                                && source.getGrid() != state.first.grid() && source.getGrid() != state.second.grid()
                                && state.first.grid() != state.second.grid()
                                && !sourceId.orElseThrow().equals(firstId.orElseThrow())
                                && !sourceId.orElseThrow().equals(secondId.orElseThrow())
                                && !firstId.orElseThrow().equals(secondId.orElseThrow()),
                        "Source and two physical Endpoint Grids/identities must be pairwise distinct");
                state.sourceGrid = source.getGrid();
                state.firstGrid = state.first.grid();
                state.secondGrid = state.second.grid();
                state.sourceId = sourceId.orElseThrow();
                state.firstId = firstId.orElseThrow();
                state.secondId = secondId.orElseThrow();
                LOGGER.info("AE2F_SCALE_FEDERATION_TWO_IDS source={} targetA={} targetB={} sourceGrid={} targetAGrid={} targetBGrid={}",
                        sourceId.orElseThrow().value(), firstId.orElseThrow().value(), secondId.orElseThrow().value(),
                        System.identityHashCode(source.getGrid()), System.identityHashCode(state.first.grid()),
                        System.identityHashCode(state.second.grid()));
                state.route = new ScaleFederationTwoTargetRoute(helper, state.provider, storage,
                        state.first, state.second);
                state.stage = 9;
                helper.assertTrue(false, "Waiting for two physical Bridge/Federation Domain/Policy/Claim legs");
                return;
            }
            if (state.stage == 9) {
                helper.assertTrue(state.route.tick(), "Waiting for two active physical routes: " + state.route.status());
                if (threeTargets) {
                    for (var position : java.util.List.of(new BlockPos(5, 2, 11), new BlockPos(5, 2, 10),
                            new BlockPos(5, 1, 10), new BlockPos(5, 2, 12), new BlockPos(6, 2, 12),
                            new BlockPos(4, 2, 9), new BlockPos(5, 2, 9), ScaleFederationThirdRoute.HOST)) {
                        helper.assertTrue(helper.getLevel().isLoaded(helper.absolutePos(position))
                                        && (helper.getBlockState(position).isAir()
                                                || helper.getBlockState(position).is(net.minecraft.world.level.block.Blocks.BARRIER))
                                        && helper.getLevel().getBlockEntity(helper.absolutePos(position)) == null,
                                "Third physical target/Bridge/H1 cannot overwrite a device: " + position
                                        + " block=" + helper.getBlockState(position));
                    }
                    state.third = new ScaleFederationIdentityTarget(helper, true, new BlockPos(5, 2, 11));
                    state.stage = 10;
                    helper.assertTrue(false, "Waiting for independent target C anchor identity");
                    return;
                }
                state.route.assertReady();
                helper.assertTrue(state.retention.ready(source), "Drive must remain on the source Grid");
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting,
                        state.first, state.second, state.route, catalogReplay);
                if (!catalogReplay) state.processing.selectTwoTargetRun(state.retention);
                state.stage = 10;
                helper.assertTrue(false, "Waiting for two Federation native Processing jobs");
                return;
            }
            if (threeTargets && state.stage == 10) {
                helper.assertTrue(state.third.anchorReady(source.getGrid()), "Target C anchor must settle separately");
                state.third.placeEndpoint();
                state.stage = 11;
                helper.assertTrue(false, "Waiting for seeded target C Endpoint");
                return;
            }
            if (threeTargets && state.stage == 11) {
                helper.assertTrue(state.third.endpointReady() && state.third.sameLineage()
                                && state.third.onlyAnchorClaim() && state.third.settled(),
                        "Target C Endpoint must join only its anchor");
                state.third.placeExportBus();
                state.stage = 12;
                helper.assertTrue(false, "Waiting for seeded target C Export Bus");
                return;
            }
            if (threeTargets && state.stage == 12) {
                helper.assertTrue(state.third.connectExportBus() && state.third.exportBusReady()
                                && state.third.busLineage() && state.third.onlyAnchorClaim() && state.third.settled(),
                        "Target C bus must join only its anchor");
                state.thirdGrid = state.third.grid();
                state.thirdId = state.third.anchorId();
                helper.assertTrue(state.thirdGrid != state.sourceGrid && state.thirdGrid != state.firstGrid
                                && state.thirdGrid != state.secondGrid && !state.thirdId.equals(state.sourceId)
                                && !state.thirdId.equals(state.firstId) && !state.thirdId.equals(state.secondId),
                        "Four source/A/B/C Grids and confirmed IDs must be pairwise distinct");
                state.thirdRoute = new ScaleFederationThirdRoute(helper, state.sourceGrid, state.third,
                         catalogReplay, fourTargetReplay);
                state.stage = 13;
                helper.assertTrue(false, "Waiting for real H1 WEST edge and C Bridge/Federation Domain/Policy/Claim");
                return;
            }
            if (threeTargets && state.stage == 13) {
                helper.assertTrue(state.thirdRoute.tick(), "Waiting for C physical route and H1 authority");
                state.route.assertReady();
                state.thirdRoute.assertReady();
                if (fourTargets) {
                    for (var position : ScaleFederationFourthRoute.footprint()) {
                        var absolute = helper.absolutePos(position);
                        helper.assertTrue(helper.getLevel().isLoaded(absolute)
                                        && (helper.getBlockState(position).isAir()
                                                || helper.getBlockState(position).is(net.minecraft.world.level.block.Blocks.BARRIER))
                                        && helper.getLevel().getBlockEntity(absolute) == null,
                                "D candidate must be loaded, replaceable and BE-free before placement: " + position
                                        + " block=" + helper.getBlockState(position));
                    }
                    state.fourth = new ScaleFederationIdentityTarget(helper, true,
                            ScaleFederationFourthRoute.ENDPOINT);
                    state.stage = 15;
                    helper.assertTrue(false, "Waiting for independent D anchor");
                    return;
                }
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting,
                        state.first, state.second, state.route, catalogReplay, catalogReplay);
                if (!catalogReplay) state.processing.selectFederationThreeTargetRun(state.thirdRoute.remote(),
                        state.third, state.thirdRoute, state.retention);
                state.stage = 14;
                helper.assertTrue(false, "Waiting for three serial physical Federation jobs");
                return;
            }
            if (fourTargets && state.stage == 15) {
                helper.assertTrue(state.fourth.anchorReady(source.getGrid()), "D anchor must settle separately");
                state.fourth.placeEndpoint();
                state.stage = 16;
                helper.assertTrue(false, "Waiting for seeded D Endpoint");
                return;
            }
            if (fourTargets && state.stage == 16) {
                helper.assertTrue(state.fourth.endpointReady() && state.fourth.sameLineage()
                                && state.fourth.onlyAnchorClaim() && state.fourth.settled(),
                        "D Endpoint must join only its own anchor");
                state.fourth.placeExportBus();
                state.stage = 17;
                helper.assertTrue(false, "Waiting for seeded D Export Bus");
                return;
            }
            if (fourTargets && state.stage == 17) {
                helper.assertTrue(state.fourth.connectExportBus() && state.fourth.exportBusReady()
                                && state.fourth.busLineage() && state.fourth.onlyAnchorClaim() && state.fourth.settled(),
                        "D bus must join only its own anchor");
                state.fourthGrid = state.fourth.grid();
                state.fourthId = state.fourth.anchorId();
                helper.assertTrue(state.fourthGrid != state.sourceGrid && state.fourthGrid != state.firstGrid
                                && state.fourthGrid != state.secondGrid && state.fourthGrid != state.thirdGrid
                                && !java.util.List.of(state.sourceId, state.firstId, state.secondId, state.thirdId)
                                        .contains(state.fourthId),
                        "Source and A/B/C/D must be five distinct physical Grids and IDs");
                state.fourthRoute = new ScaleFederationFourthRoute(helper, state.sourceGrid, state.fourth,
                        fourTargetReplay);
                state.stage = 18;
                helper.assertTrue(false, "Fourth physical D Bridge/Federation Domain/Policy/Claim route is missing");
                return;
            }
            if (fourTargets && state.stage == 18) {
                helper.assertTrue(state.fourthRoute.tick(), "Waiting for D physical route and H2 WEST authority");
                state.route.assertReady();
                state.thirdRoute.assertReady();
                state.fourthRoute.assertReady();
                if (fourTargetReplay) helper.assertValueEqual(
                        state.fourthRoute.remote().composition().patternInventory().size(), ScaleProcessingCatalog.SIZE,
                        "Four-target replay needs 256 physical catalog slots on H2");
                LOGGER.info("AE2F_SCALE_FEDERATION_FOUR_IDS source={} A={} B={} C={} D={} grids={} H2West=true",
                        state.sourceId.value(), state.firstId.value(), state.secondId.value(), state.thirdId.value(),
                        state.fourthId.value(), java.util.List.of(state.sourceGrid, state.firstGrid, state.secondGrid,
                                state.thirdGrid, state.fourthGrid).stream().map(System::identityHashCode).toList());
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting,
                         state.first, state.second, state.route, fourTargetReplay, false, fourTargetReplay);
                state.processing.selectFederationThreeTargetRun(state.thirdRoute.remote(), state.third,
                         state.thirdRoute, state.retention, fourTargetReplay);
                state.processing.selectFederationFourthTargetRun(state.fourthRoute.remote(), state.fourth,
                        state.fourthRoute);
                state.stage = 19;
                helper.assertTrue(false, "Waiting for A/B/C authentic jobs before D job gate");
                return;
            }
            state.route.assertReady();
            if (threeTargets) {
                state.thirdRoute.assertReady();
                helper.assertTrue(state.third.grid() == state.thirdGrid
                                && FederationDomainRegistryAccess.confirmedNetworkId(state.thirdGrid)
                                        .filter(state.thirdId::equals).isPresent()
                                && state.thirdGrid != state.sourceGrid && state.thirdGrid != state.firstGrid
                                && state.thirdGrid != state.secondGrid
                                && !state.thirdId.equals(state.sourceId) && !state.thirdId.equals(state.firstId)
                                && !state.thirdId.equals(state.secondId)
                                && state.thirdRoute.remote().managedNode().getGrid() == source.getGrid(),
                        "C and H1 must retain their distinct native target/source identities");
            }
            if (fourTargets) {
                state.fourthRoute.assertReady();
                helper.assertTrue(state.fourth.grid() == state.fourthGrid
                                && FederationDomainRegistryAccess.confirmedNetworkId(state.fourthGrid)
                                        .filter(state.fourthId::equals).isPresent()
                                && state.fourthRoute.remote().managedNode().getGrid() == state.sourceGrid,
                        "D and H2 must retain separate target/source identities");
            }
            helper.assertTrue(state.retention.ready(source)
                            && source.getGrid() == state.sourceGrid
                            && state.first.grid() == state.firstGrid
                            && state.second.grid() == state.secondGrid
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.sourceGrid)
                                    .filter(state.sourceId::equals).isPresent()
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.firstGrid)
                                    .filter(state.firstId::equals).isPresent()
                            && FederationDomainRegistryAccess.confirmedNetworkId(state.secondGrid)
                                    .filter(state.secondId::equals).isPresent()
                            && state.processing.requesterNode().getGrid() == source.getGrid()
                            && state.crafting.cpuNode().getGrid() == source.getGrid(),
                    "One source Grid must own Provider, CPU, requester and Drive throughout");
            var firstMachine = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                    new BlockPos(5, 2, 4));
            var secondMachine = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                    new BlockPos(5, 2, 8));
            helper.assertTrue(firstMachine != secondMachine
                            && firstMachine.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(new BlockPos(5, 2, 4)), Direction.NORTH)
                            && secondMachine.inputHandler() == helper.getLevel().getCapability(
                                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                    helper.absolutePos(new BlockPos(5, 2, 8)), Direction.NORTH)
                            && state.provider.lane(0).getReturnInv() != state.provider.lane(1).getReturnInv(),
                    "Physical Federation machines and native Lane return owners must be distinct");
            var inventory = state.provider.composition().patternInventory();
            helper.assertValueEqual(inventory.size(), catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                    "One physical Provider inventory with occupied catalog slots");
            if (catalogReplay && threeTargets) {
                helper.assertValueEqual(state.thirdRoute.remote().composition().patternInventory().size(),
                        ScaleProcessingCatalog.SIZE, "Three-target replay needs 256 physical catalog slots on H1");
            }
            if (catalogReplay && threeTargets && !fourTargetReplay && !state.catalogSelected) {
                var remote = state.thirdRoute.remote();
                var hosts = java.util.List.of(state.provider, remote);
                var lanes = java.util.List.of(state.provider.lane(0), state.provider.lane(1), remote.lane(0));
                var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
                var inputKeys = new java.util.HashSet<AEItemKey>();
                var outputKeys = new java.util.HashSet<AEItemKey>();
                var counts = new int[3];
                for (int slot = 0; slot < ScaleProcessingCatalog.SIZE; slot++) {
                    var lane = slot % 3;
                    var host = hosts.get(lane == 2 ? 1 : 0);
                    var recipe = ScaleProcessingCatalog.recipes().get(slot);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    var decoded = PatternDetailsHelper.decodePattern(
                            host.composition().patternInventory().getStackInSlot(slot), helper.getLevel());
                    helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                    && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                    && decoded.getOutputs().size() == 1
                                    && decoded.getPrimaryOutput().what().equals(output)
                                    && decoded.getPrimaryOutput().amount() == 1
                                    && host.publishedProviders(decoded).equals(java.util.List.of(lanes.get(lane)))
                                    && source.getGrid().getCraftingService().getCraftingFor(output).contains(decoded),
                            "Physical slot must publish only its assigned H0/H1 native Lane: " + slot);
                    for (int other = 0; other < 3; other++) {
                        if (other != lane) helper.assertTrue(!lanes.get(other).getAvailablePatterns().contains(decoded),
                                "Other native Lane must not publish selected Pattern: " + slot);
                    }
                    var otherHost = hosts.get(lane == 2 ? 0 : 1);
                    helper.assertTrue(otherHost.composition().patternInventory().getStackInSlot(slot).isEmpty()
                                    && inputKeys.add(input) && outputKeys.add(output),
                            "Physical Pattern must be unique across both hosts: " + slot);
                    counts[lane]++;
                    selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
                }
                helper.assertTrue(java.util.Collections.disjoint(inputKeys, outputKeys)
                                && source.getGrid().getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "Native crafting service must expose exactly 256 disjoint physical outputs");
                for (int lane = 0; lane < 3; lane++) helper.assertValueEqual(
                        lanes.get(lane).getAvailablePatterns().size(), counts[lane],
                        "Native Lane must publish only its own modulo-three Patterns: " + lane);
                helper.assertValueEqual(counts[0], 86, "A physical Pattern count");
                helper.assertValueEqual(counts[1], 85, "B physical Pattern count");
                helper.assertValueEqual(counts[2], 85, "C physical Pattern count");
                var high = selections.get(255);
                helper.assertTrue(high.slot() % 3 == 0 && high.input().equals(AEItemKey.of(Items.COMMAND_BLOCK_MINECART))
                                && high.output().equals(AEItemKey.of(Items.GOLDEN_PICKAXE)),
                        "Highest physical slot must be the original A-owned high recipe");
                state.processing.selectFederationThreeTargetRun(remote, state.third, state.thirdRoute,
                        state.retention, true);
                state.catalogSelected = true;
            }
            if (fourTargetReplay && !state.catalogSelected) {
                var hosts = java.util.List.of(state.provider, state.provider, state.thirdRoute.remote(),
                        state.fourthRoute.remote());
                var lanes = java.util.List.of(state.provider.lane(0), state.provider.lane(1),
                        state.thirdRoute.remote().lane(0), state.fourthRoute.remote().lane(0));
                var inputKeys = new java.util.HashSet<AEItemKey>();
                var outputKeys = new java.util.HashSet<AEItemKey>();
                for (int slot = 0; slot < ScaleProcessingCatalog.SIZE; slot++) {
                    var lane = slot % 4;
                    var recipe = ScaleProcessingCatalog.recipes().get(slot);
                    var input = AEItemKey.of(recipe.input());
                    var output = AEItemKey.of(recipe.output());
                    var decoded = PatternDetailsHelper.decodePattern(
                            hosts.get(lane).composition().patternInventory().getStackInSlot(slot), helper.getLevel());
                    helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs().length == 1
                                    && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                    && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                    && decoded.getOutputs().size() == 1
                                    && decoded.getPrimaryOutput().what().equals(output)
                                    && decoded.getPrimaryOutput().amount() == 1
                                    && state.provider.publishedProviders(decoded).equals(java.util.List.of(lanes.get(lane)))
                                    && source.getGrid().getCraftingService().getCraftingFor(output).contains(decoded)
                                    && inputKeys.add(input) && outputKeys.add(output),
                            "One unique physical Pattern must publish on its modulo-four native Lane: " + slot);
                    for (int other = 0; other < 4; other++) {
                        if (other != lane) helper.assertTrue(
                                !lanes.get(other).getAvailablePatterns().contains(decoded)
                                        && (hosts.get(other) == hosts.get(lane)
                                                || hosts.get(other).composition().patternInventory()
                                                        .getStackInSlot(slot).isEmpty()),
                                "Wrong host/Lane must not publish physical Pattern: " + slot + " lane=" + other);
                    }
                }
                helper.assertTrue(java.util.Collections.disjoint(inputKeys, outputKeys)
                                && source.getGrid().getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "Native crafting service must expose exactly 256 disjoint four-host outputs");
                for (int lane = 0; lane < 4; lane++) helper.assertValueEqual(
                        lanes.get(lane).getAvailablePatterns().size(), 64,
                        "Each modulo-four Lane must exclusively publish 64 physical Patterns: " + lane);
                var high = ScaleProcessingCatalog.recipes().get(255);
                helper.assertTrue(AEItemKey.of(high.input()).equals(AEItemKey.of(Items.COMMAND_BLOCK_MINECART))
                                && AEItemKey.of(high.output()).equals(AEItemKey.of(Items.GOLDEN_PICKAXE))
                                && state.fourthRoute.remote().composition().patternInventory()
                                        .getStackInSlot(255).isEmpty() == false
                                && state.provider.composition().patternInventory().getStackInSlot(255).isEmpty()
                                && state.thirdRoute.remote().composition().patternInventory()
                                        .getStackInSlot(255).isEmpty(),
                        "High slot 255 must be physically present only on D");
                state.catalogSelected = true;
            }
            if (!state.catalogSelected) {
            var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
            var inputKeys = new java.util.HashSet<AEItemKey>();
            var outputKeys = new java.util.HashSet<AEItemKey>();
            for (int slot = 0; slot < inventory.size(); slot++) {
                var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), helper.getLevel());
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                var input = AEItemKey.of(recipe.input());
                var output = AEItemKey.of(recipe.output());
                var lane = slot % 2;
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(input)
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(output)
                                && decoded.getPrimaryOutput().amount() == 1 && decoded.getOutputs().size() == 1
                                && state.provider.lane(lane).getAvailablePatterns().contains(decoded)
                                && state.provider.lane(1 - lane).getAvailablePatterns().stream()
                                        .noneMatch(pattern -> pattern.equals(decoded))
                                && state.provider.publishedProviders(decoded).equals(java.util.List.of(
                                        state.provider.lane(lane)))
                                && source.getGrid().getCraftingService().getCraftingFor(output).contains(decoded),
                        "Physical slot must publish exactly its own native Lane: " + slot);
                helper.assertTrue(inputKeys.add(input) && outputKeys.add(output),
                        "Physical Federation catalog input/output keys must be unique: " + slot);
                selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, input, output));
            }
            if (catalogReplay) {
                helper.assertValueEqual(inputKeys.size(), 256, "Decoded physical input types");
                helper.assertValueEqual(outputKeys.size(), 256, "Decoded physical output types");
                helper.assertValueEqual(state.provider.lane(0).getAvailablePatterns().size(), 128,
                        "First native Lane Pattern count");
                helper.assertValueEqual(state.provider.lane(1).getAvailablePatterns().size(), 128,
                        "Second native Lane Pattern count");
                helper.assertTrue(source.getGrid().getCraftingService().getCraftables(key -> true).equals(outputKeys),
                        "Native crafting service must publish all 256 physical outputs");
                var high = selections.get(ScaleProcessingCatalog.SIZE - 1);
                state.processing.selectHighPattern(high.input(), high.output());
                state.processing.selectCatalogRun(selections, state.retention);
            }
            state.catalogSelected = true;
            }
            if (catalogReplay && !state.recipesChecked
                    && state.processing.completedJobCount() >= (fourTargetReplay ? 4 : threeTargets ? 3 : 2)) {
                var thirdMachine = threeTargets ? helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>
                        getBlockEntity(new BlockPos(5, 2, 12)) : null;
                var machines = fourTargetReplay ? java.util.List.of(firstMachine, secondMachine, thirdMachine,
                        helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                                ScaleFederationFourthRoute.ENDPOINT.south()))
                        : threeTargets ? java.util.List.of(firstMachine, secondMachine, thirdMachine)
                        : java.util.List.of(firstMachine, secondMachine);
                for (int lane = 0; lane < machines.size(); lane++) {
                    helper.assertValueEqual(machines.get(lane).recipeCount(), fourTargetReplay ? 64
                            : threeTargets ? lane == 0 ? 86 : 85 : 128,
                            "Selected physical machine must have only its own recipes: " + lane);
                }
                for (int slot = 0; slot < ScaleProcessingCatalog.SIZE; slot++) {
                    var input = new net.minecraft.world.item.ItemStack(ScaleProcessingCatalog.recipes().get(slot).input());
                    var lane = slot % machines.size();
                    helper.assertTrue(machines.get(lane).inputHandler().isItemValid(0, input)
                                    && java.util.stream.IntStream.range(0, machines.size())
                                            .filter(other -> other != lane)
                                            .noneMatch(other -> machines.get(other).inputHandler().isItemValid(0, input)),
                            "Each physical target machine must exclusively accept its decoded input: " + slot);
                }
                state.recipesChecked = true;
            }
            if (threeTargets && !catalogReplay) {
                var remote = state.thirdRoute.remote();
                var thirdRecipe = ScaleProcessingCatalog.recipes().get(2);
                var thirdMachine = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                        new BlockPos(5, 2, 12));
                var thirdPattern = PatternDetailsHelper.decodePattern(
                        remote.composition().patternInventory().getStackInSlot(0), helper.getLevel());
                helper.assertTrue(remote.composition().patternInventory().size() == 1
                                && state.provider.composition().patternInventory().size() == 2
                                && thirdPattern != null && thirdPattern.getInputs().length == 1
                                && thirdPattern.getInputs()[0].getPossibleInputs()[0].what()
                                        .equals(AEItemKey.of(thirdRecipe.input()))
                                && thirdPattern.getPrimaryOutput().what().equals(AEItemKey.of(thirdRecipe.output()))
                                && remote.lane(0).getAvailablePatterns().contains(thirdPattern)
                                && state.provider.publishedProviders(thirdPattern).equals(java.util.List.of(remote.lane(0)))
                                && state.provider.lane(0).getAvailablePatterns().stream().noneMatch(thirdPattern::equals)
                                && state.provider.lane(1).getAvailablePatterns().stream().noneMatch(thirdPattern::equals)
                                && thirdMachine != firstMachine && thirdMachine != secondMachine
                                && thirdMachine.inputHandler() == helper.getLevel().getCapability(
                                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                        helper.absolutePos(new BlockPos(5, 2, 12)), Direction.NORTH)
                                && !firstMachine.inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(thirdRecipe.input()))
                                && !secondMachine.inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(thirdRecipe.input()))
                                && remote.lane(0).getReturnInv() != state.provider.lane(0).getReturnInv()
                                && remote.lane(0).getReturnInv() != state.provider.lane(1).getReturnInv(),
                        "H1's single encoded Pattern must publish only on H1's native Lane");
            }
            if (fourTargets && !fourTargetReplay) {
                var remote = state.fourthRoute.remote();
                var recipe = ScaleProcessingCatalog.recipes().get(3);
                var decoded = PatternDetailsHelper.decodePattern(
                        remote.composition().patternInventory().getStackInSlot(0), helper.getLevel());
                var machine = helper.<space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity>getBlockEntity(
                        ScaleFederationFourthRoute.ENDPOINT.south());
                helper.assertTrue(remote.composition().patternInventory().size() == 1
                                && decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what()
                                        .equals(AEItemKey.of(recipe.input()))
                                && decoded.getPrimaryOutput().what().equals(AEItemKey.of(recipe.output()))
                                && remote.lane(0).getAvailablePatterns().contains(decoded)
                                && state.provider.publishedProviders(decoded).equals(java.util.List.of(remote.lane(0)))
                                && state.provider.lane(0).getAvailablePatterns().stream().noneMatch(decoded::equals)
                                && state.provider.lane(1).getAvailablePatterns().stream().noneMatch(decoded::equals)
                                && state.thirdRoute.remote().lane(0).getAvailablePatterns().stream()
                                        .noneMatch(decoded::equals)
                                && machine != firstMachine && machine != secondMachine
                                && machine.inputHandler() == helper.getLevel().getCapability(
                                        net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                                        helper.absolutePos(ScaleFederationFourthRoute.ENDPOINT.south()), Direction.NORTH)
                                && !firstMachine.inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && !secondMachine.inputHandler().isItemValid(0,
                                        new net.minecraft.world.item.ItemStack(recipe.input()))
                                && remote.lane(0).getReturnInv() != state.thirdRoute.remote().lane(0).getReturnInv()
                                && remote.lane(0).getReturnInv() != state.provider.lane(0).getReturnInv()
                                && remote.lane(0).getReturnInv() != state.provider.lane(1).getReturnInv(),
                        "H2's encoded D Pattern, machine and return must be exclusive");
            }
            helper.assertTrue(state.processing.tick(), "Waiting for serial Federation native target jobs");
            if (fourTargets && !fourTargetReplay) helper.assertValueEqual(state.processing.completedJobCount(), 4,
                    "Fourth native D 16-unit planner/CPU/requester job is missing after authentic A/B/C jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), catalogReplay ? 256 : fourTargets ? 4
                    : threeTargets ? 3 : 2,
                    "Distinct physical Federation target jobs");
            helper.assertValueEqual(state.processing.acceptedTotal(), catalogReplay ? 4096L : fourTargets ? 64L
                    : threeTargets ? 48L : 32L,
                    "Typed native requester callback units");
            helper.assertTrue(state.provider.lane(0).getReturnInv().isEmpty()
                            && state.provider.lane(1).getReturnInv().isEmpty()
                            && source.getGrid().getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Both native Lane returns and the source CPU must be idle");
            helper.assertValueEqual(state.first.inputAmount(AEItemKey.of(Items.COBBLESTONE)), 0L,
                    "Target A must consume all cobblestone");
            helper.assertValueEqual(state.second.inputAmount(AEItemKey.of(Items.DIRT)), 0L,
                    "Target B must consume all dirt");
            helper.assertValueEqual(state.first.inputAmount(AEItemKey.of(Items.DIRT)), 0L,
                    "Target A must not receive Lane B input");
            helper.assertValueEqual(state.second.inputAmount(AEItemKey.of(Items.COBBLESTONE)), 0L,
                    "Target B must not receive Lane A input");
            state.processing.close();
            if (fourTargets) {
                helper.assertTrue(state.fourthRoute.remote().lane(0).getReturnInv().isEmpty(),
                        "H2 native return inventory must retire");
                helper.assertValueEqual(state.fourth.inputAmount(
                        AEItemKey.of(ScaleProcessingCatalog.recipes().get(3).input())), 0L,
                        "D target input cell must be empty after native job");
                state.fourthRoute.close();
                state.fourth.close();
            }
            if (threeTargets) {
                helper.assertTrue(state.thirdRoute.remote().lane(0).getReturnInv().isEmpty(),
                        "H1 native return inventory must retire");
                state.thirdRoute.close();
                state.third.close();
            }
            state.route.close();
            state.second.close();
            state.first.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
              timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleNativeOneGridCatalogThreeDriveDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, false, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleFederationCatalogBatch16Development(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, false, false, false, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 46000, required = true, manualOnly = true)
    public static void scaleFederationCatalog256ReplayDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, false, false, true, true, true, true, false, false, false, false, true, true);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
               timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleSmallFederationIdentityDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, true, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
                timeoutTicks = 1200, required = true, manualOnly = true)
    public static void scaleSmallFederationRouteDevelopment(GameTestHelper helper) {
        runSmallDevelopment(helper, true, true);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly) {
        runSmallDevelopment(helper, identityOnly, routeOnly, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64,
                catalog256, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256, boolean subnetBatch) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64,
                catalog256, subnetBatch, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256, boolean subnetBatch, boolean controlBatch) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64,
                catalog256, subnetBatch, controlBatch, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256, boolean subnetBatch, boolean controlBatch, boolean federationBatch) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64,
                catalog256, subnetBatch, controlBatch, federationBatch, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256, boolean subnetBatch, boolean controlBatch, boolean federationBatch,
            boolean federationReplay) {
        runSmallDevelopment(helper, identityOnly, routeOnly, twoPatterns, oneGrid, catalog, batch16, catalog64,
                catalog256, subnetBatch, controlBatch, federationBatch, federationReplay, false);
    }

    private static void runSmallDevelopment(GameTestHelper helper, boolean identityOnly, boolean routeOnly,
            boolean twoPatterns, boolean oneGrid, boolean catalog, boolean batch16, boolean catalog64,
            boolean catalog256, boolean subnetBatch, boolean controlBatch, boolean federationBatch,
            boolean federationReplay, boolean subnetReplay) {
        var fullCatalogRun = catalog64 || catalog256 || subnetBatch || controlBatch || federationBatch;
        var profile = ScaleFactoryProfile.load("small");
        var providerPosition = new BlockPos(3, 2, 3);
        var storagePosition = providerPosition.north();
        var cpuPosition = storagePosition.north();
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleNativeProcessingProbe processing;
            ScaleDriveRetention retention;
            ScaleNativeSubnetTarget subnet;
            ScaleFederationIdentityTarget federation;
            ScaleFederationRoute route;
            ScaleNativeProcessingProbe federationProcessing;
            ScaleGridFixture fixture = oneGrid ? null : new ScaleGridFixture(helper, profile.gridCount() - 1);
            appeng.api.networking.IGrid sourceGrid;
            space.controlnet.ae2federation.identity.NetworkId sourceId;
            int stage;
            boolean observed;
            boolean catalogReported;
            boolean runSelected;
            int lastGridReceipt = -1;
        };
        helper.succeedWhen(() -> {
            if (state.stage == 12) {
                helper.assertTrue(state.route.tick(), "Waiting for physical Federation route: " + state.route.status());
                var source = state.provider.managedNode().getNode();
                state.federation.snapshot("physical-route-ready", source, state.crafting.storage());
                helper.assertTrue(source.getGrid() == state.sourceGrid
                                && FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow()
                                        .equals(state.sourceId)
                                && state.federation.onlyAnchorClaim() && state.federation.settled(),
                        "Source and target identities must remain settled after the physical Bridge");
                if (!oneGrid) helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(state.sourceGrid,
                        state.federation.grid()), profile.gridCount(),
                        "Physical route must retain 16 distinct active Grids");
                if (federationBatch) {
                    helper.assertTrue(state.retention.ready(source), "Federation Drive must remain on the source Grid");
                    state.federationProcessing = new ScaleNativeProcessingProbe(helper, state.provider,
                            state.crafting, state.federation, state.route);
                    state.processing = state.federationProcessing;
                    state.stage = 14;
                    helper.assertTrue(false, "Waiting for physical Federation catalog assertion");
                    return;
                }
                if (!routeOnly) {
                    state.federationProcessing = new ScaleNativeProcessingProbe(helper, state.provider,
                            state.crafting, state.federation);
                    state.stage = 13;
                    helper.assertTrue(false, "Waiting for planner-submitted Federation Processing job");
                    return;
                }
                state.route.close();
                state.federation.close();
                state.crafting.close();
                state.provider.close();
                state.fixture.close();
                return;
            }
            if (state.stage == 13) {
                helper.assertTrue(state.federationProcessing.tick(),
                        "Waiting for Endpoint-returned Federation Processing job");
                helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(state.sourceGrid,
                        state.federation.grid()), profile.gridCount(),
                        "Completed Federation job must retain 16 distinct active Grids");
                state.route.assertNativeJobRoute();
                state.federationProcessing.close();
                state.route.close();
                state.federation.close();
                state.crafting.close();
                state.provider.close();
                state.fixture.close();
                return;
            }
            if (state.stage == 7) {
                helper.assertValueEqual(state.crafting.storage().extract(AEItemKey.of(Items.DIAMOND), 1,
                        Actionable.MODULATE, IActionSource.empty()), 1L,
                        "Native subnet must leave one removable physical output");
                state.federation = new ScaleFederationIdentityTarget(helper);
                state.stage = 8;
                helper.assertTrue(false, "Waiting for physical Federation target anchor");
                return;
            }
            if (state.stage == 8) {
                var source = state.provider.managedNode().getNode();
                helper.assertTrue(state.federation.anchorReady(source.getGrid()),
                        "Waiting for separate settled physical target anchor");
                state.federation.anchorId();
                state.sourceGrid = source.getGrid();
                state.sourceId = FederationDomainRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow();
                state.federation.snapshot("anchor-settled", source, state.crafting.storage());
                state.federation.placeEndpoint();
                state.stage = 9;
                helper.assertTrue(false, "Waiting for pre-tick-seeded Endpoint lineage");
                return;
            }
            if (state.stage == 9) {
                var source = state.provider.managedNode().getNode();
                helper.assertTrue(state.federation.endpointReady(), "Waiting for target Endpoint Grid join");
                state.federation.snapshot("endpoint-settled", source, state.crafting.storage());
                helper.assertTrue(state.federation.sameLineage() && state.federation.onlyAnchorClaim()
                                && state.federation.settled(),
                        "Endpoint separate-lineage predicate failed; competing target claims="
                                + state.federation.targetClaims());
                helper.assertTrue(source.getGrid() == state.sourceGrid
                                && FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow()
                                        .equals(state.sourceId), "Source native Grid identity changed");
                state.federation.placeExportBus();
                state.stage = 10;
                helper.assertTrue(false, "Waiting for separately staged Export Bus");
                return;
            }
            if (state.stage == 10) {
                var source = state.provider.managedNode().getNode();
                helper.assertTrue(state.federation.connectExportBus(), "Waiting for Export Bus target connection");
                state.federation.snapshot("bus-connected", source, state.crafting.storage());
                helper.assertTrue(state.federation.exportBusReady() && state.federation.busLineage()
                                && state.federation.sameLineage() && state.federation.onlyAnchorClaim()
                                && state.federation.settled(),
                        "Export Bus separate-lineage predicate failed; competing target claims="
                                + state.federation.targetClaims());
                helper.assertTrue(source.getGrid() == state.sourceGrid
                                && FederationDomainRegistryAccess.confirmedNetworkId(source.getGrid()).orElseThrow()
                                        .equals(state.sourceId), "Source native Grid identity changed after Export Bus");
                if (!oneGrid) {
                    helper.assertTrue(state.fixture.ready(), "Waiting for 14 auxiliary Federation Grids");
                    helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(state.sourceGrid,
                            state.federation.grid()), profile.gridCount(), "Federation target must retain 16 distinct Grids");
                }
                if (routeOnly || !identityOnly) {
                    state.route = new ScaleFederationRoute(helper, state.provider, state.federation,
                            state.crafting.node(), routeOnly);
                    state.stage = 12;
                    helper.assertTrue(false, "Waiting for staged physical Bridge and Policy/Claim");
                    return;
                }
                state.federation.close();
                state.crafting.close();
                state.provider.close();
                state.fixture.close();
                state.stage = 11;
                if (!identityOnly) helper.assertTrue(false, "Small Federation Processing layout remains unqualified");
                return;
            }
            if (state.stage == 11) {
                helper.assertTrue(false, "Small Federation Processing layout remains unqualified");
                return;
            }
            if (state.stage == 4) {
                helper.assertValueEqual(state.crafting.storage().extract(AEItemKey.of(Items.DIAMOND), 1,
                        Actionable.MODULATE, IActionSource.empty()), 1L,
                        "First native job must leave one removable physical output");
                state.subnet = new ScaleNativeSubnetTarget(helper);
                state.stage = 5;
                helper.assertTrue(false, "Waiting for native Processing subnet to boot");
                return;
            }
            if (state.stage == 5) {
                helper.assertTrue(state.subnet.ready(state.provider.managedNode().getNode()),
                        "Waiting for separate native Interface and Export Bus subnet");
                helper.assertTrue(state.fixture.ready(), "Waiting for 14 native auxiliary Grids");
                helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(
                        state.provider.managedNode().getGrid(), state.subnet.grid()),
                        profile.gridCount(), "Native subnet must retain 16 distinct Grids including source and target");
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting, state.subnet);
                state.stage = 6;
                helper.assertTrue(false, "Waiting for native-subnet Processing job");
                return;
            }
            if (state.stage == 6) {
                helper.assertTrue(state.processing.tick(), "Waiting for authentic native-subnet Processing job");
                state.processing.close();
                state.subnet.close();
                state.stage = 7;
                helper.assertTrue(false, "Small Federation Processing layout remains unqualified");
                return;
            }
            if (state.stage == 0) {
                if (!oneGrid) {
                    helper.assertTrue(state.fixture.ready(), "Waiting for initial small native Grids");
                    state.fixture.recordNodes("before-provider");
                }
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> catalog || (twoPatterns ? slot <= 1 : slot == 0)),
                        !oneGrid || federationBatch,
                        catalog ? ScaleProcessingCatalog.SIZE : 3, oneGrid ? NetworkId.create() : null);
                state.stage = 1;
                if (!oneGrid) {
                    state.fixture.recordNodes("after-provider-placement");
                    state.fixture.recordManagedNode("after-provider-placement", providerPosition,
                            state.provider.managedNode().getNode());
                }
                helper.assertTrue(false, "Waiting for native Provider placement to settle");
                return;
            }
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                        && FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for confirmed Provider source identity");
                if (!oneGrid) {
                    state.fixture.recordNodes("after-provider-settlement");
                    state.fixture.recordManagedNode("after-provider-settlement", providerPosition,
                            state.provider.managedNode().getNode());
                    state.fixture.recordNode("before-cpu-placement", storagePosition);
                    state.fixture.recordNode("before-cpu-placement", cpuPosition);
                    helper.assertTrue(state.fixture.ready(), "Waiting for small native Grids after Provider placement");
                    helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(state.provider.managedNode().getGrid()),
                            profile.gridCount(), "Small scale Grids and source must survive Provider placement");
                }
                var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid())
                        .orElseThrow();
                state.crafting = new ProcessingCraftingGrid(helper, sourceId);
                state.stage = 2;
                if (!oneGrid) {
                    state.fixture.recordNodes("after-cpu-placement");
                    state.fixture.recordNode("after-cpu-placement", storagePosition);
                    state.fixture.recordNode("after-cpu-placement", cpuPosition);
                }
                helper.assertTrue(false, "Waiting for seeded native CPU placement to settle");
                return;
            }
            var sourceNode = state.provider.managedNode().getNode();
            if (!oneGrid && !state.observed) {
                state.fixture.recordNodes("before-cpu-connection");
                state.fixture.recordManagedNode("before-cpu-connection", providerPosition, sourceNode);
                state.fixture.recordNode("before-cpu-connection", storagePosition);
                state.fixture.recordNode("before-cpu-connection", cpuPosition);
                state.observed = true;
            }
            var storageNode = state.crafting.node();
            helper.assertTrue(storageNode != null && storageNode.hasGridBooted(),
                    "Waiting for native CPU storage Grid boot");
            if (storageNode.getGrid() != sourceNode.getGrid()) {
                GridHelper.createConnection(sourceNode, storageNode);
                helper.assertTrue(false, "Waiting for seeded Provider/CPU native connection");
                return;
            }
            if (!oneGrid) {
                helper.assertTrue(state.fixture.ready(), "Waiting for small native Grids after CPU connection");
                helper.assertValueEqual(state.fixture.distinctActiveGridsIncluding(sourceNode.getGrid()),
                        profile.gridCount(), "Small scale Grids and source must survive CPU placement and connection");
            }
            helper.assertValueEqual(state.crafting.cpuCount(), 1,
                    "Native source must own exactly one authentic AE2 CPU");
            if (state.processing == null && state.subnet == null) {
                if (!oneGrid) {
                    state.fixture.recordNodes("after-cpu-connection");
                    state.fixture.recordManagedNode("after-cpu-connection", providerPosition, sourceNode);
                    state.fixture.recordNode("after-cpu-connection", storagePosition);
                    state.fixture.recordNode("after-cpu-connection", cpuPosition);
                }
                state.provider.register();
                if (fullCatalogRun) {
                    state.retention = new ScaleDriveRetention(helper, state.crafting,
                            FederationDomainRegistryAccess.confirmedNetworkId(sourceNode.getGrid()).orElseThrow(),
                             catalog256 || subnetBatch || controlBatch || federationBatch ? 5 : 2,
                             subnetBatch || federationBatch ? new BlockPos(2, 2, 2) : new BlockPos(4, 2, 2));
                }
                if (federationBatch) {
                    state.federation = new ScaleFederationIdentityTarget(helper, true);
                    state.stage = 8;
                } else if (subnetBatch) {
                    state.subnet = new ScaleNativeSubnetTarget(helper, true, subnetReplay);
                } else state.processing = twoPatterns
                        ? new ScaleNativeProcessingProbe(helper, state.provider, state.crafting, true, catalog, batch16)
                        : new ScaleNativeProcessingProbe(helper, state.provider, state.crafting);
                helper.assertTrue(false, "Waiting for native Processing machine and requester");
                return;
            }
            if (subnetBatch && state.processing == null) {
                helper.assertTrue(state.subnet.ready(sourceNode), "Waiting for separate native subnet");
                helper.assertTrue(state.retention.ready(sourceNode), "Waiting for same-source-Grid Drive");
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting, state.subnet, true);
                helper.assertTrue(false, "Waiting for subnet Processing requester");
                return;
            }
            if (fullCatalogRun) {
                helper.assertTrue(state.retention.ready(sourceNode),
                        "Waiting for physical Drive and mounted cells on the source Grid");
            }
            if (federationBatch) {
                helper.assertTrue(sourceNode.getGrid() == state.sourceGrid
                                && state.processing.requesterNode().getGrid() == state.sourceGrid
                                && state.crafting.cpuNode().getGrid() == state.sourceGrid
                                && state.federation.grid() != state.sourceGrid
                                && state.federation.settled(),
                        "Federation source CPU/requester/Drive and target must remain separate and settled");
                state.route.assertNativeJobRouteIfSubmitted(state.processing.completedJobCount());
            }
            if (subnetBatch) {
                helper.assertTrue(state.subnet.ready(sourceNode)
                                && state.subnet.grid() != sourceNode.getGrid()
                                && state.processing.requesterNode().getGrid() == sourceNode.getGrid()
                                && state.crafting.cpuNode().getGrid() == sourceNode.getGrid(),
                        "One working subnet must remain distinct from the source CPU/requester/Drive Grid");
                if (subnetReplay) {
                    var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(sourceNode.getGrid());
                    var targetId = FederationDomainRegistryAccess.confirmedNetworkId(state.subnet.grid());
                    helper.assertTrue(sourceId.isPresent() && targetId.isPresent()
                                    && !sourceId.orElseThrow().equals(targetId.orElseThrow()),
                            "One working subnet target and source must have distinct settled identities");
                }
            }
            if (twoPatterns) {
                if (oneGrid && !subnetBatch && !federationBatch) {
                    var completedJobs = state.processing.completedJobCount();
                    var record = state.lastGridReceipt != completedJobs;
                    state.lastGridReceipt = completedJobs;
                    ScaleOneGridInspection.assertScene(helper, sourceNode, state.crafting,
                            state.processing.requesterNode(), completedJobs, record);
                }
                var inventory = state.provider.composition().patternInventory();
                if (catalog) {
                    var occupied = 0;
                    var inputKeys = new java.util.HashSet<AEItemKey>();
                    var outputKeys = new java.util.HashSet<AEItemKey>();
                    var advertised = state.provider.lane(0).getAvailablePatterns();
                    var craftables = sourceNode.getGrid().getCraftingService().getCraftables(key -> true);
                    helper.assertValueEqual(inventory.size(), ScaleProcessingCatalog.SIZE,
                            "Single Provider physical Pattern inventory size");
                    for (int slot = 0; slot < inventory.size(); slot++) {
                        var encoded = inventory.getStackInSlot(slot);
                        if (encoded.isEmpty()) continue;
                        occupied++;
                        var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
                        var recipe = ScaleProcessingCatalog.recipes().get(slot);
                        var inputKey = AEItemKey.of(recipe.input());
                        var outputKey = AEItemKey.of(recipe.output());
                        helper.assertTrue(details != null && details.getInputs().length == 1
                                        && details.getInputs()[0].getPossibleInputs().length == 1
                                        && details.getInputs()[0].getPossibleInputs()[0].what().equals(inputKey)
                                        && details.getPrimaryOutput().what().equals(outputKey)
                                        && details.getOutputs().size() == 1
                                        && (federationBatch && state.processing.machineRecipeCount() == 0
                                                || state.processing.acceptsMachineInput(recipe.input()))
                                        && advertised.stream().anyMatch(pattern -> pattern.getPrimaryOutput().what()
                                                .equals(outputKey) && pattern.getInputs()[0].getPossibleInputs()[0]
                                                .what().equals(inputKey))
                                        && sourceNode.getGrid().getCraftingService().getCraftingFor(outputKey).stream()
                                                .anyMatch(pattern -> pattern.getPrimaryOutput().what().equals(outputKey)),
                                "Encoded physical slot, registered machine and native advertisement differ: " + slot);
                        inputKeys.add(inputKey);
                        outputKeys.add(outputKey);
                    }
                    helper.assertValueEqual(occupied, ScaleProcessingCatalog.SIZE,
                            "Occupied physical Processing Pattern slots");
                    helper.assertValueEqual(inputKeys.size(), ScaleProcessingCatalog.SIZE,
                            "Distinct physical Processing inputs");
                    helper.assertValueEqual(outputKeys.size(), ScaleProcessingCatalog.SIZE,
                            "Distinct physical Processing outputs");
                    helper.assertValueEqual(advertised.size(), ScaleProcessingCatalog.SIZE,
                            "Native Lane advertised Processing details");
                    if (!federationBatch || state.processing.machineRecipeCount() != 0) {
                        helper.assertValueEqual(state.processing.machineRecipeCount(), ScaleProcessingCatalog.SIZE,
                                "Registered machine Processing recipe count");
                    }
                    helper.assertTrue(craftables.equals(outputKeys), "Native crafting service output catalog differs");
                    var high = PatternDetailsHelper.decodePattern(
                            inventory.getStackInSlot(ScaleProcessingCatalog.SIZE - 1), helper.getLevel());
                    helper.assertTrue(high != null && high.getInputs().length == 1
                                    && high.getInputs()[0].getPossibleInputs().length == 1
                                    && high.getOutputs().size() == 1,
                            "High physical Processing slot is not one typed recipe");
                    var highInput = high.getInputs()[0].getPossibleInputs()[0].what();
                    var highOutput = high.getPrimaryOutput().what();
                    helper.assertTrue(highInput instanceof AEItemKey && highOutput instanceof AEItemKey
                                    && (!batch16 || highInput.equals(AEItemKey.of(Items.COMMAND_BLOCK_MINECART))
                                            && highOutput.equals(AEItemKey.of(Items.GOLDEN_PICKAXE)))
                                    && state.provider.lane(0).getAvailablePatterns().contains(high)
                                    && sourceNode.getGrid().getCraftingService().getCraftingFor(highOutput)
                                            .stream().anyMatch(pattern -> pattern.getPrimaryOutput().what()
                                                    .equals(highOutput) && pattern.getInputs().length == 1
                                                    && pattern.getInputs()[0].getPossibleInputs()[0].what()
                                                            .equals(highInput)),
                            "High physical Processing slot is not natively advertised with its exact input");
                    state.processing.selectHighPattern((AEItemKey) highInput, (AEItemKey) highOutput);
                    if (fullCatalogRun && !state.runSelected) {
                        var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
                        var selectedInputs = new java.util.HashSet<AEItemKey>();
                        var selectedOutputs = new java.util.HashSet<AEItemKey>();
                         for (int index = 0; index < (catalog256 || federationReplay || subnetReplay
                                 ? ScaleProcessingCatalog.SIZE
                                  : subnetBatch || controlBatch || federationBatch ? 3 : 64); index++) {
                             int slot = catalog256 || federationReplay || subnetReplay ? index
                                    : subnetBatch || controlBatch || federationBatch ? index == 2 ? 255 : index
                                    : index == 63 ? ScaleProcessingCatalog.SIZE - 1 : index;
                            var decoded = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot), helper.getLevel());
                            helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                            && decoded.getInputs()[0].getPossibleInputs().length == 1
                                            && decoded.getPrimaryOutput().amount() == 1
                                            && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1,
                                    "Selected physical catalog recipe must encode one input and one output");
                            var selectedInput = (AEItemKey) decoded.getInputs()[0].getPossibleInputs()[0].what();
                            var selectedOutput = (AEItemKey) decoded.getPrimaryOutput().what();
                            helper.assertTrue(selectedInputs.add(selectedInput) && selectedOutputs.add(selectedOutput),
                                    "Selected physical catalog keys must be distinct");
                            selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, selectedInput, selectedOutput));
                        }
                        state.processing.selectCatalogRun(selections, state.retention);
                        state.runSelected = true;
                    }
                    if (!state.catalogReported && (!federationBatch
                            || state.processing.machineRecipeCount() == ScaleProcessingCatalog.SIZE)) {
                        LOGGER.info("AE2F_SCALE_CATALOG physicalSlots={} occupied={} distinctInputs={} "
                                        + "distinctOutputs={} laneDetails={} nativeCraftables={} machineRecipes={}",
                                inventory.size(), occupied, inputKeys.size(), outputKeys.size(), advertised.size(),
                                craftables.size(), state.processing.machineRecipeCount());
                        state.catalogReported = true;
                    }
                }
                helper.assertTrue(!inventory.getStackInSlot(0).isEmpty(), "First physical Pattern slot is empty");
                helper.assertTrue(!inventory.getStackInSlot(1).isEmpty(), "Second physical Pattern slot is empty");
                var first = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(0), helper.getLevel());
                var second = PatternDetailsHelper.decodePattern(inventory.getStackInSlot(1), helper.getLevel());
                helper.assertTrue(first != null && first.getPrimaryOutput().what().equals(AEItemKey.of(Items.DIAMOND))
                                && first.getInputs()[0].getPossibleInputs()[0].what().equals(AEItemKey.of(Items.COBBLESTONE))
                                && second != null && second.getPrimaryOutput().what().equals(AEItemKey.of(Items.GOLD_INGOT))
                                && second.getInputs()[0].getPossibleInputs()[0].what().equals(AEItemKey.of(Items.DIRT))
                                && state.provider.lane(0).getAvailablePatterns().size()
                                        == (catalog ? ScaleProcessingCatalog.SIZE : 2)
                                && state.provider.lane(0).getAvailablePatterns().contains(first)
                                && state.provider.lane(0).getAvailablePatterns().contains(second),
                        "Two distinct decoded Processing Patterns are not natively advertised");
            }
            helper.assertTrue(state.processing.tick(), "Waiting for authentic native Processing job");
            if (catalog) {
                helper.assertValueEqual(state.processing.completedJobCount(),
                        catalog256 || federationReplay || subnetReplay ? 256 : catalog64 ? 64 : 3,
                        "Completed physical catalog Processing jobs including slot 255");
            }
            if (oneGrid && !subnetBatch && !federationBatch) {
                ScaleOneGridInspection.assertScene(helper, sourceNode, state.crafting,
                        state.processing.requesterNode(), state.processing.completedJobCount(), true);
            }
            state.processing.close();
            if (subnetBatch) state.subnet.close();
            if (federationBatch) {
                state.route.assertNativeJobRoute();
                state.route.close();
                state.federation.close();
            }
            if (state.retention != null) state.retention.close();
            if (twoPatterns) {
                state.crafting.close();
                state.provider.close();
                if (!oneGrid) state.fixture.close();
                return;
            }
            state.fixture.close();
            state.fixture = new ScaleGridFixture(helper, profile.gridCount() - 2);
            state.stage = 4;
            helper.assertTrue(false, "Waiting to replace the native Processing target with a subnet");
        });
    }

    private static void verifyGridTopology(GameTestHelper helper, String tier) {
        var profile = ScaleFactoryProfile.load(tier);
        var fixture = new ScaleGridFixture(helper, profile);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for powered native scale Grids");
            helper.assertValueEqual(fixture.distinctActiveGrids(), profile.gridCount(),
                    "Scale tier must construct distinct live native AE2 Grids");
            fixture.close();
        });
    }
}
