package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.parts.PartHelper;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEParts;
import appeng.api.util.AEColor;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.api.networking.IGridNode;
import net.minecraft.nbt.CompoundTag;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;

public final class ScaleNativeSubnetThreeTargets {
    private static final BlockPos H1 = new BlockPos(9, 2, 3);
    private static final BlockPos H2 = new BlockPos(9, 2, 7);
    private static final List<BlockPos> TRUNK = List.of(new BlockPos(3, 3, 2), new BlockPos(3, 4, 2),
            new BlockPos(4, 4, 2), new BlockPos(5, 4, 2), new BlockPos(6, 4, 2),
            new BlockPos(7, 4, 2), new BlockPos(8, 4, 2), new BlockPos(8, 3, 2),
            new BlockPos(8, 2, 2), new BlockPos(8, 2, 3));
    private static final List<BlockPos> FOUR_TRUNK = java.util.stream.Stream.concat(TRUNK.stream(),
            List.of(new BlockPos(8, 2, 4), new BlockPos(8, 2, 5), new BlockPos(8, 2, 6),
                    new BlockPos(8, 2, 7)).stream()).toList();

    private ScaleNativeSubnetThreeTargets() {
    }

    public static void run(GameTestHelper helper) {
        run(helper, false);
    }

    public static void run(GameTestHelper helper, boolean fourthTarget) {
        run(helper, fourthTarget, false);
    }

    public static void run(GameTestHelper helper, boolean fourthTarget, boolean catalogReplay) {
        var state = new Object() {
            NativeProviderLaneFixtures provider;
            ProcessingCraftingGrid crafting;
            ScaleDriveRetention retention;
            ScaleNativeSubnetTarget east;
            ScaleNativeSubnetTarget south;
            ScaleNativeSubnetTarget third;
            ScaleNativeSubnetTarget fourth;
            NativeProviderLaneFixtures remote;
            NativeProviderLaneFixtures fourthHost;
            ScaleNativeProcessingProbe processing;
            IGrid sourceGrid;
            List<IGrid> grids;
            List<NetworkId> ids;
            CreativeEnergyCellBlockEntity sourcePower;
            IGridNode sourcePowerNode;
            CompoundTag sourcePowerState;
            int stage;
            int cableCount;
            boolean preflight;
            boolean catalogSelected;
        };
        var path = fourthTarget ? FOUR_TRUNK : TRUNK;
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                state.provider = new NativeProviderLaneFixtures(helper,
                        java.util.List.of(slot -> catalogReplay ? slot % 4 == 0 : slot == 0,
                                slot -> catalogReplay ? slot % 4 == 1 : slot == 1), false,
                        catalogReplay ? ScaleProcessingCatalog.SIZE : 2,
                        NetworkId.create(), java.util.List.of(Direction.EAST, Direction.SOUTH));
                state.stage = 1;
                helper.assertTrue(false, "Waiting for source Provider Grid");
                return;
            }
            if (state.stage == 1) {
                helper.assertTrue(state.provider.connectEnergy()
                        && FabricRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).isPresent(),
                        "Waiting for settled source Provider Grid");
                state.crafting = new ProcessingCraftingGrid(helper,
                        FabricRegistryAccess.confirmedNetworkId(state.provider.managedNode().getGrid()).orElseThrow());
                state.stage = 2;
                helper.assertTrue(false, "Waiting for source CPU Grid");
                return;
            }
            var source = state.provider.managedNode().getNode();
            var storage = state.crafting.node();
            helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for source Grid boot");
            if (storage.getGrid() != source.getGrid()) {
                GridHelper.createConnection(source, storage);
                helper.assertTrue(false, "Waiting for source CPU connection");
                return;
            }
            if (state.stage == 2) {
                state.sourceGrid = source.getGrid();
                state.provider.register();
                var sourceId = FabricRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow();
                state.retention = new ScaleDriveRetention(helper, state.crafting, sourceId, catalogReplay ? 5 : 1,
                        new BlockPos(2, 2, 2));
                state.east = new ScaleNativeSubnetTarget(helper, true, true, Direction.EAST);
                state.south = new ScaleNativeSubnetTarget(helper, true, true, Direction.SOUTH);
                state.stage = 3;
                helper.assertTrue(false, "Waiting for source Drive and target pods A/B");
                return;
            }
            helper.assertTrue(source.getGrid() == state.sourceGrid && state.retention.ready(source)
                    && state.east.ready(source) && state.south.ready(source),
                    "Source Drive and target pods A/B must stay on settled Grids");
            if (fourthTarget && !state.preflight) {
                var candidates = java.util.stream.Stream.concat(FOUR_TRUNK.subList(TRUNK.size(), FOUR_TRUNK.size())
                        .stream(), List.of(H2, H2.east(), H2.east(2), H2.east(2).below(),
                                H2.east(2).north(), H2.east(3).north()).stream()).toList();
                for (var position : candidates) {
                    var world = helper.absolutePos(position);
                    helper.assertTrue(helper.getLevel().isLoaded(world)
                                    && (helper.getBlockState(position).isAir()
                                            || helper.getBlockState(position).is(Blocks.BARRIER))
                                    && helper.getLevel().getBlockEntity(world) == null,
                            "D candidate must be loaded and BE-free air/barrier before placement: " + position
                                    + " block=" + helper.getBlockState(position));
                }
                state.preflight = true;
            }
            if (state.cableCount > 0) {
                var previous = path.get(state.cableCount - 1);
                var previousNode = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(previous), Direction.UP);
                var previousFace = state.cableCount == 1 ? Direction.DOWN
                        : Direction.fromDelta(path.get(state.cableCount - 2).getX() - previous.getX(),
                                path.get(state.cableCount - 2).getY() - previous.getY(),
                                path.get(state.cableCount - 2).getZ() - previous.getZ());
                helper.assertTrue(previousNode != null && previousNode.hasGridBooted()
                                && previousNode.getGrid() == state.sourceGrid
                                && previousNode.getInWorldConnections().containsKey(previousFace),
                        "Each staged cable must settle an in-world predecessor edge on the source Grid: " + previous);
            }
            if (state.cableCount < path.size()) {
                var position = path.get(state.cableCount);
                var worldPosition = helper.absolutePos(position);
                helper.assertTrue(helper.getLevel().isLoaded(worldPosition)
                        && (helper.getBlockState(position).isAir()
                                || helper.getBlockState(position).is(Blocks.BARRIER))
                        && helper.getLevel().getBlockEntity(worldPosition) == null,
                        "Candidate trunk must be loaded and cannot overwrite CPU, power, Drive or pods: "
                                + position + " block=" + helper.getBlockState(position));
                helper.assertTrue(PartHelper.setPart(helper.getLevel(), worldPosition, null, null,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null,
                        "Candidate physical source cable must be placeable: " + position);
                state.cableCount++;
                helper.assertTrue(false, "Waiting for staged physical source cable " + position);
                return;
            }
            for (int index = 0; index < path.size(); index++) {
                var position = path.get(index);
                var cable = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
                helper.assertTrue(cable != null && cable.hasGridBooted() && cable.getGrid() == state.sourceGrid,
                        "Every candidate cable segment must join the exact source Grid: " + position);
            }
            if (state.stage == 3) {
                var sourceId = FabricRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow();
                state.sourcePower = helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west());
                state.sourcePowerNode = state.sourcePower.getMainNode().getNode();
                state.sourcePowerState = new CompoundTag();
                state.sourcePower.getMainNode().saveToNBT(state.sourcePowerState);
                state.remote = new NativeProviderLaneFixtures(helper,
                        List.of(slot -> catalogReplay ? slot % 4 == 2 : slot == 0), false,
                        catalogReplay ? ScaleProcessingCatalog.SIZE : 1, sourceId, List.of(Direction.EAST), H1);
                if (catalogReplay) ScaleProcessingCatalog.install(state.remote, slot -> slot % 4 == 2);
                else {
                    var recipe = ScaleProcessingCatalog.recipes().get(2);
                    state.remote.installPattern(0, List.of(ProcessingRegressionFixtures.item(recipe.input(), 1)),
                            List.of(ProcessingRegressionFixtures.item(recipe.output(), 1)));
                }
                state.stage = 4;
                helper.assertTrue(false, "Waiting for physical H1 WEST cable connection");
                return;
            }
            var currentPowerState = new CompoundTag();
            state.sourcePower.getMainNode().saveToNBT(currentPowerState);
            boolean sameBlock = state.sourcePower == helper.getBlockEntity(NativeProviderLaneFixtures.HOST_POS.west());
            boolean sameNode = state.sourcePowerNode == state.sourcePower.getMainNode().getNode();
            boolean sameState = state.sourcePowerState.equals(currentPowerState);
            helper.assertTrue(sameBlock && sameNode && sameState,
                    "Remote H1 must not reload NBT into H0's already-live creative energy node: blockSame="
                            + sameBlock + " nodeSame=" + sameNode + " stateSame=" + sameState);
            var h1Node = helper.<PatternProviderBlockEntity>getBlockEntity(H1).getMainNode().getNode();
            helper.assertTrue(h1Node != null && h1Node.hasGridBooted()
                            && h1Node.getInWorldConnections().containsKey(Direction.WEST)
                            && h1Node.getInWorldConnections().get(Direction.WEST).getOtherSide(h1Node).getGrid()
                                    == state.sourceGrid
                            && h1Node.getGrid() == state.sourceGrid,
                    "H1 requires a live physical WEST cable edge on the exact source Grid");
            helper.assertTrue(FabricRegistryAccess.confirmedNetworkId(h1Node.getGrid())
                            .filter(FabricRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow()::equals)
                            .isPresent(),
                    "H1 physical Provider node must retain the source identity");
            if (state.stage == 4) {
                state.remote.register();
                state.third = new ScaleNativeSubnetTarget(helper, true, true, H1.east(), Direction.EAST);
                state.stage = 5;
                helper.assertTrue(false, "Waiting for separate target C Grid");
                return;
            }
            helper.assertTrue(state.third.ready(source), "Third physical subnet must boot and remain active");
            if (fourthTarget) {
                if (state.stage == 5) {
                    var sourceId = FabricRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow();
                    state.fourthHost = new NativeProviderLaneFixtures(helper,
                            List.of(slot -> catalogReplay ? slot % 4 == 3 : slot == 0), false,
                            catalogReplay ? ScaleProcessingCatalog.SIZE : 1, sourceId, List.of(Direction.EAST), H2);
                    if (catalogReplay) ScaleProcessingCatalog.install(state.fourthHost, slot -> slot % 4 == 3);
                    else {
                        var recipe = ScaleProcessingCatalog.recipes().get(3);
                        state.fourthHost.installPattern(0, List.of(ProcessingRegressionFixtures.item(recipe.input(), 1)),
                                List.of(ProcessingRegressionFixtures.item(recipe.output(), 1)));
                    }
                    state.stage = 7;
                    helper.assertTrue(false, "Waiting for physical D WEST source edge");
                    return;
                }
                var world = helper.absolutePos(H2);
                var host = helper.getLevel().getBlockEntity(world);
                var node = host instanceof PatternProviderBlockEntity providerBlock
                        ? providerBlock.getMainNode().getNode() : null;
                helper.assertTrue(node != null && node.hasGridBooted()
                                && node.getInWorldConnections().containsKey(Direction.WEST)
                                && node.getInWorldConnections().get(Direction.WEST).getOtherSide(node).getGrid()
                                        == state.sourceGrid && node.getGrid() == state.sourceGrid,
                        "D requires a physical Provider BE and WEST edge on the exact source Grid");
                helper.assertTrue(FabricRegistryAccess.confirmedNetworkId(node.getGrid())
                                .filter(FabricRegistryAccess.confirmedNetworkId(state.sourceGrid).orElseThrow()::equals)
                                .isPresent(), "D must have the confirmed source identity");
                if (state.stage == 7) {
                    state.fourthHost.register();
                    state.fourth = new ScaleNativeSubnetTarget(helper, true, true, H2.east(), Direction.EAST);
                    state.stage = 8;
                    helper.assertTrue(false, "Waiting for separate target D Grid");
                    return;
                }
                helper.assertTrue(state.fourth.ready(source), "Fourth physical subnet must boot and remain active");
                if (catalogReplay) helper.assertValueEqual(state.fourthHost.composition().patternInventory().size(),
                        ScaleProcessingCatalog.SIZE, "Four-subnet replay needs 256 physical catalog slots on D");
            }
            var targets = fourthTarget ? List.of(state.east, state.south, state.third, state.fourth)
                    : List.of(state.east, state.south, state.third);
            var observedGrids = targets.stream().map(ScaleNativeSubnetTarget::grid)
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            observedGrids.add(0, state.sourceGrid);
            var observedIds = observedGrids.stream().map(grid -> FabricRegistryAccess.confirmedNetworkId(grid)
                    .orElseThrow()).toList();
            var uniqueGrids = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<IGrid, Boolean>());
            uniqueGrids.addAll(observedGrids);
            helper.assertTrue(uniqueGrids.size() == observedGrids.size()
                            && new HashSet<>(observedIds).size() == observedGrids.size(),
                    "All targets and source must have pairwise separate native Grids and confirmed IDs");
            if (state.stage == (fourthTarget ? 8 : 5)) {
                if (fourthTarget) {
                    org.slf4j.LoggerFactory.getLogger(ScaleNativeSubnetThreeTargets.class).info(
                            "AE2F_SCALE_FOUR_TARGET_IDS source={} A={} B={} C={} D={} grids={} H1={} H2={} westEdges=true",
                            observedIds.get(0).value(), observedIds.get(1).value(), observedIds.get(2).value(),
                            observedIds.get(3).value(), observedIds.get(4).value(),
                            observedGrids.stream().map(System::identityHashCode).toList(),
                            System.identityHashCode(h1Node),
                            System.identityHashCode(helper.<PatternProviderBlockEntity>getBlockEntity(H2)
                                    .getMainNode().getNode()));
                } else {
                    org.slf4j.LoggerFactory.getLogger(ScaleNativeSubnetThreeTargets.class).info(
                            "AE2F_SCALE_THREE_TARGET_IDS source={} A={} B={} C={} sourceGrid={} AGrid={} BGrid={} CGrid={} H1={} westEdge={}",
                            observedIds.get(0).value(), observedIds.get(1).value(), observedIds.get(2).value(),
                            observedIds.get(3).value(), System.identityHashCode(observedGrids.get(0)),
                            System.identityHashCode(observedGrids.get(1)), System.identityHashCode(observedGrids.get(2)),
                            System.identityHashCode(observedGrids.get(3)), System.identityHashCode(h1Node),
                            h1Node.getInWorldConnections().containsKey(Direction.WEST));
                }
                state.grids = observedGrids;
                state.ids = observedIds;
                state.processing = new ScaleNativeProcessingProbe(helper, state.provider, state.crafting,
                        state.east, state.south, catalogReplay, catalogReplay);
                if (catalogReplay) state.processing.selectFourSubnetCatalogRun(state.remote, state.third,
                        state.fourthHost, state.fourth, state.retention);
                else {
                    state.processing.selectThreeTargetRun(state.remote, state.third, state.retention);
                    if (fourthTarget) state.processing.selectFourTargetRun(state.fourthHost, state.fourth);
                }
                state.stage = 6;
                helper.assertTrue(false, "Waiting for typed native jobs");
                return;
            }
            helper.assertTrue(java.util.stream.IntStream.range(0, observedGrids.size())
                            .allMatch(index -> state.grids.get(index) == observedGrids.get(index))
                            && state.ids.equals(observedIds)
                            && state.processing.requesterNode().getGrid() == state.sourceGrid
                            && state.crafting.cpuNode().getGrid() == state.sourceGrid,
                    "Source and three target identities must stay settled through all jobs");
            var lanes = fourthTarget
                    ? List.of(state.provider.lane(0), state.provider.lane(1), state.remote.lane(0),
                            state.fourthHost.lane(0))
                    : List.of(state.provider.lane(0), state.provider.lane(1), state.remote.lane(0));
            var inventory = state.provider.composition().patternInventory();
            helper.assertValueEqual(inventory.size(), catalogReplay ? 256 : 2, "H0 physical Pattern slots");
            helper.assertValueEqual(state.remote.composition().patternInventory().size(), catalogReplay ? 256 : 1,
                    "H1 physical Pattern slots");
            if (fourthTarget) helper.assertValueEqual(state.fourthHost.composition().patternInventory().size(),
                    catalogReplay ? 256 : 1, "D host physical Pattern slots");
            if (!catalogReplay || !state.catalogSelected) {
            var selections = new java.util.ArrayList<ScaleNativeProcessingProbe.CatalogSelection>();
            var inputs = new HashSet<AEItemKey>();
            var outputs = new HashSet<AEItemKey>();
            for (int slot = 0; slot < (catalogReplay ? ScaleProcessingCatalog.SIZE : targets.size()); slot++) {
                int lane = catalogReplay ? slot % 4 : slot;
                var encoded = lane == 3 ? state.fourthHost.composition().patternInventory()
                        .getStackInSlot(catalogReplay ? slot : 0)
                        : lane == 2 ? state.remote.composition().patternInventory()
                                .getStackInSlot(catalogReplay ? slot : 0)
                        : inventory.getStackInSlot(catalogReplay ? slot : lane);
                var decoded = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                var key = AEItemKey.of(recipe.input());
                var output = AEItemKey.of(recipe.output());
                var machine = targets.get(lane).machine();
                helper.assertTrue(decoded != null && decoded.getInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs().length == 1
                                && decoded.getInputs()[0].getPossibleInputs()[0].what().equals(key)
                                && decoded.getInputs()[0].getPossibleInputs()[0].amount() == 1
                                && decoded.getPrimaryOutput().what().equals(output)
                                && decoded.getPrimaryOutput().amount() == 1 && decoded.getOutputs().size() == 1
                                && lanes.get(lane).getAvailablePatterns().contains(decoded)
                                && state.provider.publishedProviders(decoded).equals(List.of(lanes.get(lane)))
                                && state.sourceGrid.getCraftingService().getCraftingFor(output).contains(decoded)
                                && machine.inputHandler() == helper.getLevel().getCapability(
                                          Capabilities.ItemHandler.BLOCK,
                                          helper.absolutePos(lane == 0 ? new BlockPos(6, 2, 2)
                                                  : lane == 1 ? new BlockPos(2, 2, 6)
                                                          : lane == 2 ? new BlockPos(12, 2, 2)
                                                          : new BlockPos(12, 2, 6)),
                                         lane == 1 ? Direction.NORTH : Direction.WEST)
                                && machine.inputHandler().isItemValid(0, new ItemStack(recipe.input())),
                        "Physical host slot must exclusively publish its typed native Lane: " + slot);
                for (int other = 0; other < targets.size(); other++) {
                    if (other != lane) helper.assertTrue(!lanes.get(other).getAvailablePatterns().contains(decoded)
                                    && !targets.get(other).machine().inputHandler()
                                            .isItemValid(0, new ItemStack(recipe.input())),
                            "Unselected Lane and machine must reject selected recipe: " + slot + "/" + other);
                }
                helper.assertTrue(inputs.add(key) && outputs.add(output), "Physical catalog keys must be unique: " + slot);
                if (catalogReplay) selections.add(new ScaleNativeProcessingProbe.CatalogSelection(slot, key, output));
            }
            if (catalogReplay) {
                helper.assertTrue(java.util.Collections.disjoint(inputs, outputs)
                                && state.sourceGrid.getCraftingService().getCraftables(key -> true).equals(outputs),
                        "All 256 disjoint physical types must be natively craftable");
                for (int lane = 0; lane < 4; lane++) {
                    helper.assertValueEqual(lanes.get(lane).getAvailablePatterns().size(), 64,
                            "Each physical subnet Lane owns 64 Patterns");
                    helper.assertValueEqual(targets.get(lane).machine().recipeCount(), 64,
                            "Each physical subnet machine owns 64 recipes");
                }
                state.processing.selectHighPattern(selections.get(255).input(), selections.get(255).output());
                state.processing.selectFourSubnetCatalog(selections);
                state.catalogSelected = true;
            }
            }
            var returns = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            var machines = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            for (int slot = 0; slot < targets.size(); slot++) {
                helper.assertTrue(returns.add(lanes.get(slot).getReturnInv())
                                && machines.add(targets.get(slot).machine()),
                        "Physical machines and native Lane return owners must be distinct: " + slot);
            }
            helper.assertTrue(state.processing.tick(), "Waiting for serial physical subnet jobs");
            helper.assertValueEqual(state.processing.completedJobCount(), catalogReplay ? 256 : fourthTarget ? 4 : 3,
                    "Four native target jobs must include a real D 16-output job");
            helper.assertValueEqual(state.processing.acceptedTotal(), catalogReplay ? 4096L : fourthTarget ? 64L : 48L,
                    "Typed callback total");
            helper.assertTrue(lanes.stream().allMatch(lane -> lane.getReturnInv().isEmpty())
                            && state.sourceGrid.getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "All returns and source CPU must retire");
            for (int slot = 0; slot < targets.size(); slot++) {
                for (int other = 0; other < targets.size(); other++) {
                    var input = AEItemKey.of(ScaleProcessingCatalog.recipes().get(slot).input());
                    helper.assertValueEqual(targets.get(other).inputAmount(input), 0L,
                            "All native target input cells must be empty after serial jobs");
                }
            }
            state.processing.close();
            if (fourthTarget) {
                state.fourth.close();
                state.fourthHost.close();
                helper.setBlock(H2, Blocks.AIR);
            }
            state.third.close();
            state.remote.close();
            helper.setBlock(H1, Blocks.AIR);
            state.south.close();
            state.east.close();
            state.retention.close();
            state.crafting.close();
            state.provider.close();
        });
    }
}
