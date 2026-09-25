package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;

public final class ScaleSourceHosts {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleSourceHosts.class);
    private static final BlockPos POWER = new BlockPos(2, 2, 2);
    private static final BlockPos CONTROLLER = new BlockPos(2, 3, 2);
    private static final BlockPos DRIVE = new BlockPos(4, 2, 2);
    private static final int PLACED_HOSTS = 16;

    private ScaleSourceHosts() {
    }

    public static void run(GameTestHelper helper) {
        run(helper, scene -> true);
    }

    public static void run(GameTestHelper helper, Predicate<Scene> target) {
        run(helper, target, 1, 1);
    }

    public static void run(GameTestHelper helper, Predicate<Scene> target, int patternSlots, int driveCells) {
        run(helper, target, patternSlots, driveCells, true);
    }

    public static void run(GameTestHelper helper, Predicate<Scene> target, int patternSlots, int driveCells,
            boolean inspectEachTick) {
        var state = new State();
        helper.succeedWhen(() -> {
            if (state.stage == 0) {
                preflight(helper);
                state.crafting = new ProcessingCraftingGrid(helper, NetworkId.create());
                state.stage = 1;
                helper.assertTrue(false, "Waiting for source CPU and storage boot");
                return;
            }
            var storage = state.crafting.node();
            if (state.stage == 1) {
                helper.assertTrue(storage != null && storage.hasGridBooted(), "Waiting for source storage node");
                var sourceId = FabricRegistryAccess.confirmedNetworkId(storage.getGrid());
                helper.assertTrue(sourceId.isPresent(), "Waiting for source identity");
                helper.setBlock(POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.<CreativeEnergyCellBlockEntity>getBlockEntity(POWER).getMainNode()
                        .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", sourceId.orElseThrow()));
                state.stage = 2;
                helper.assertTrue(false, "Waiting for source power");
                return;
            }
            if (state.stage == 2) {
                helper.assertTrue(storage.isActive() && state.crafting.cpuNode().getGrid() == storage.getGrid()
                        && FabricRegistryAccess.confirmedNetworkId(storage.getGrid()).isPresent(),
                        "Waiting for one powered source CPU Grid");
                state.grid = storage.getGrid();
                state.id = FabricRegistryAccess.confirmedNetworkId(state.grid).orElseThrow();
                state.retention = new ScaleDriveRetention(helper, state.crafting, state.id, driveCells, DRIVE);
                state.requester = new NativeCraftingRequester(helper.getLevel(),
                        helper.absolutePos(ProcessingCraftingGrid.REQUESTER_POS), state.crafting.storage(), state.id,
                        null, false);
                helper.setBlock(CONTROLLER, AEBlocks.CONTROLLER.block());
                helper.<ControllerBlockEntity>getBlockEntity(CONTROLLER).getMainNode()
                        .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", state.id));
                state.stage = 3;
                helper.assertTrue(false, "Waiting for source controller");
                return;
            }
            var controller = node(helper, CONTROLLER);
            if (state.stage == 3) {
                helper.assertTrue(controller != null && controller.hasGridBooted()
                        && controller.getGrid() == state.grid && state.retention.ready(storage),
                        "Waiting for controller and physical Drive on source Grid");
                state.requester.connect(storage);
                state.cables = cablePlan();
                state.stage = 4;
                helper.assertTrue(false, "Waiting for dense source spine");
                return;
            }
            if (state.cableIndex < state.cables.size()) {
                var cable = state.cables.get(state.cableIndex);
                var predecessor = node(helper, cable.parent());
                helper.assertTrue(predecessor != null && predecessor.hasGridBooted()
                        && predecessor.getGrid() == state.grid,
                        "Cable predecessor must settle on source Grid: " + cable.parent());
                var upstream = state.cables.stream().filter(candidate -> candidate.position().equals(cable.parent()))
                        .findFirst().map(Cable::parent).orElse(POWER);
                helper.assertTrue(predecessor.getInWorldConnections()
                                .containsKey(direction(cable.parent(), upstream)),
                        "Cable predecessor must retain its earlier physical edge: " + cable.parent());
                helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable.position()), null, null,
                        (cable.dense() ? AEParts.SMART_DENSE_CABLE : AEParts.GLASS_CABLE)
                                .item(AEColor.TRANSPARENT)) != null,
                        "Cannot place physical source cable: " + cable.position());
                state.cableIndex++;
                helper.assertTrue(false, "Waiting for staged source cable " + cable.position());
                return;
            }
            if (state.hosts.size() > state.registeredHosts) {
                var position = hostPosition(state.registeredHosts);
                var hostNode = node(helper, position);
                helper.assertTrue(hostNode != null && hostNode.hasGridBooted()
                        && hostNode.getGrid() == state.grid
                        && hostNode.getInWorldConnections().containsKey(Direction.WEST),
                        "Waiting for physical Provider WEST node before Lane publication: " + position);
                state.hosts.get(state.registeredHosts).register();
                state.registeredHosts++;
                helper.assertTrue(false, "Waiting for registered Provider Lane " + position);
                return;
            }
            if (state.hosts.size() < PLACED_HOSTS) {
                var position = hostPosition(state.hosts.size());
                var west = node(helper, position.west());
                helper.assertTrue(west != null && west.hasGridBooted() && west.getGrid() == state.grid
                        && west.getInWorldConnections().containsKey(Direction.WEST),
                        "Provider WEST cable must have its upstream physical edge: " + position);
                var host = new NativeProviderLaneFixtures(helper, List.of(slot -> slot < patternSlots), false, patternSlots,
                        state.id, List.of(Direction.EAST), position);
                state.hosts.add(host);
                state.stage = 5;
                helper.assertTrue(false, "Waiting for physical Provider host " + position);
                return;
            }
            if (!state.inspected || inspectEachTick) inspect(helper, state, storage, controller);
            state.inspected = true;
            if (!target.test(new Scene(state.grid, state.id, state.crafting, state.retention,
                    state.requester, List.copyOf(state.hosts)))) {
                helper.assertTrue(false, "Waiting for host 0 direct Processing destination");
                return;
            }
            state.requester.close();
            state.hosts.forEach(NativeProviderLaneFixtures::close);
            for (int index = 0; index < 16; index++) helper.setBlock(hostPosition(index), Blocks.AIR);
            for (var cable : state.cables) helper.setBlock(cable.position(), Blocks.AIR);
            helper.setBlock(CONTROLLER, Blocks.AIR);
            state.retention.close();
            state.crafting.close();
            helper.setBlock(POWER, Blocks.AIR);
            helper.succeed();
        });
    }

    private static void inspect(GameTestHelper helper, State state, IGridNode storage, IGridNode controller) {
        helper.assertValueEqual(state.hosts.size(), 16, "Sixteen distinct physical Provider hosts required");
        helper.assertTrue(state.requester.isReady(storage) && state.retention.ready(storage)
                && state.crafting.cpuNode().getGrid() == state.grid && state.crafting.cpuCount() == 1
                && controller.getGrid() == state.grid && controller.isActive()
                && state.grid.getPathingService().getChannelMode() == ChannelMode.DEFAULT
                && state.grid.getPathingService().getControllerState() == ControllerState.CONTROLLER_ONLINE,
                "One source requester, Drive, CPU and native controller must remain active");
        var identities = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var nodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        var lanes = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        var details = new ArrayList<String>();
        for (int index = 0; index < 16; index++) {
            var position = hostPosition(index);
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            helper.assertTrue(entity instanceof PatternProviderBlockEntity,
                    "Physical Provider BE missing: " + position);
            var node = ((PatternProviderBlockEntity) entity).getMainNode().getNode();
            var host = state.hosts.get(index);
            helper.assertTrue(identities.add(entity) && nodes.add(node) && lanes.add(host.lane(0))
                    && host.laneCount() == 1 && host.composition().isActive() && node.hasGridBooted()
                    && node.getGrid() == state.grid && node.isActive() && node.meetsChannelRequirements()
                    && node.getUsedChannels() > 0 && node.getInWorldConnections().containsKey(Direction.WEST)
                    && node.getInWorldConnections().get(Direction.WEST).getUsedChannels() > 0
                    && node.getInWorldConnections().get(Direction.WEST).getOtherSide(node).getGrid() == state.grid
                    && FabricRegistryAccess.confirmedNetworkId(node.getGrid()).filter(state.id::equals).isPresent(),
                    "Provider requires independent BE, WEST edge, source ID and assigned active channel: " + position
                            + " node=" + node);
            details.add("(" + position.getX() + ";" + position.getY() + ";" + position.getZ() + "):"
                    + node.getUsedChannels());
        }
        for (var cable : state.cables) {
            var node = node(helper, cable.position());
            var face = direction(cable.position(), cable.parent());
            helper.assertTrue(node != null && node.hasGridBooted() && node.getGrid() == state.grid
                    && node.getInWorldConnections().containsKey(face) && node.meetsChannelRequirements()
                    && node.getInWorldConnections().get(face).getUsedChannels() > 0
                    && node.getInWorldConnections().get(face).getUsedChannels() <= (cable.dense() ? 32 : 8),
                    "Dense/ordinary cable must retain its physical upstream source edge: " + cable.position());
        }
        var root = node(helper, new BlockPos(2, 4, 2));
        int rootChannels = root.getInWorldConnections().get(Direction.DOWN).getUsedChannels();
        helper.assertTrue(rootChannels >= 16 && rootChannels <= 32
                && state.grid.getPathingService().getUsedChannels() >= 16,
                "Native controller/dense root must carry at least sixteen assigned channels: root=" + rootChannels
                        + " grid=" + state.grid.getPathingService().getUsedChannels());
        if (!state.inspected) {
            LOGGER.info("AE2F_SCALE_SOURCE_HOSTS hosts={} providerNodes={} lanes={} grid={} networkId={} controllerChannels={} "
                            + "rootChannels={} gridChannels={} powerChannels={} chestChannels={} cpuChannels={} "
                            + "driveChannels={} providerChannels={}",
                    identities.size(), nodes.size(), lanes.size(), System.identityHashCode(state.grid), state.id.value(),
                    controller.getUsedChannels(), rootChannels, state.grid.getPathingService().getUsedChannels(),
                    node(helper, POWER).getUsedChannels(), storage.getUsedChannels(),
                    state.crafting.cpuNode().getUsedChannels(), node(helper, DRIVE).getUsedChannels(), details);
        }
    }

    private static void preflight(GameTestHelper helper) {
        var planned = new ArrayList<BlockPos>();
        planned.add(POWER);
        planned.add(CONTROLLER);
        planned.add(DRIVE);
        planned.add(new BlockPos(3, 2, 2));
        planned.add(new BlockPos(3, 2, 1));
        planned.add(ProcessingCraftingGrid.REQUESTER_POS);
        cablePlan().forEach(cable -> planned.add(cable.position()));
        for (int index = 0; index < 16; index++) planned.add(hostPosition(index));
        helper.assertValueEqual(planned.stream().distinct().count(), (long) planned.size(),
                "Source placement plan must not overlap itself");
        for (var position : planned) {
            var world = helper.absolutePos(position);
            helper.assertTrue(helper.getBounds().contains(world.getX() + 0.5, world.getY() + 0.5,
                            world.getZ() + 0.5) && helper.getLevel().isLoaded(world)
                            && helper.getLevel().getBlockEntity(world) == null
                            && (helper.getBlockState(position).isAir()
                                    || helper.getBlockState(position).is(Blocks.BARRIER)),
                    "Source placement must be in bounds, loaded and BE-free: " + position);
        }
    }

    private static List<Cable> cablePlan() {
        var cables = new ArrayList<Cable>();
        var parent = CONTROLLER;
        for (int y = 4; y <= 6; y++) {
            var position = new BlockPos(2, y, 2);
            cables.add(new Cable(position, parent, true));
            parent = position;
        }
        for (int x = 3; x <= 29; x++) {
            var position = new BlockPos(x, 6, 2);
            cables.add(new Cable(position, parent, true));
            parent = position;
        }
        for (int column = 0; column < 4; column++) {
            int x = 5 + column * 8;
            parent = new BlockPos(x, 6, 2);
            for (int z = 3; z <= 29; z++) {
                var position = new BlockPos(x, 6, z);
                cables.add(new Cable(position, parent, true));
                parent = position;
            }
            for (int row = 0; row < 4; row++) {
                var position = new BlockPos(x + 1, 6, 5 + row * 8);
                cables.add(new Cable(position, position.west(), false));
            }
        }
        return List.copyOf(cables);
    }

    private static BlockPos hostPosition(int index) {
        return new BlockPos(7 + 8 * (index % 4), 6, 5 + 8 * (index / 4));
    }

    private static Direction direction(BlockPos from, BlockPos to) {
        return Direction.fromDelta(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    private static IGridNode node(GameTestHelper helper, BlockPos position) {
        return GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), Direction.UP);
    }

    private record Cable(BlockPos position, BlockPos parent, boolean dense) {
    }

    public record Scene(IGrid grid, NetworkId id, ProcessingCraftingGrid crafting,
            ScaleDriveRetention retention, NativeCraftingRequester requester,
            List<NativeProviderLaneFixtures> hosts) {
    }

    private static final class State {
        private int stage;
        private int cableIndex;
        private int registeredHosts;
        private boolean inspected;
        private IGrid grid;
        private NetworkId id;
        private ProcessingCraftingGrid crafting;
        private ScaleDriveRetention retention;
        private NativeCraftingRequester requester;
        private List<Cable> cables;
        private final List<NativeProviderLaneFixtures> hosts = new ArrayList<>();
    }
}
