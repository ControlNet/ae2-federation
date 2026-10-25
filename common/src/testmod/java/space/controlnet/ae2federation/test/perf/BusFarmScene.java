package space.controlnet.ae2federation.test.perf;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.service.PathingService;
import appeng.parts.automation.ExportBusPart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

/**
 * TEST-ONLY scene for the bus benchmarks: a main network whose storage holds many item types, and subnets that each
 * reach it and run pairs of buses. In a pair an Export Bus puts one item of the pair's type into a chest and an Import
 * Bus takes it back, so both keep working at AE2's fastest bus rate and every item crosses to the main network: the
 * subnets have no storage and no energy of their own. {@link Layout#DIRECT} puts every bus on the main network itself,
 * one AE2 network. {@link Layout#NATIVE} links each subnet as AE2 does: a Storage Bus on an ME Interface of the main
 * network for items, a Quartz Fiber for energy. {@link Layout#FEDERATION} links it with a Bridge, and the subnet's
 * storage and ME power rules on the main network.
 *
 * <p>Each subnet is a block of columns beside the others, under one cable plane of the main network. A column stacks
 * {@link #LAYERS} chests between cable layers: the cable below each chest holds its Export Bus, the cable above its
 * Import Bus. One column of each block is all cable, joining its layers, with the link to the main network above it;
 * a Quartz Fiber joins the main network to the column beside it. The main network's creative energy cell powers all. Neighbouring blocks differ in cable colour, so they stay separate networks.
 * Every network runs in AE2's infinite channel mode, as AE2's own test plots force it: channels are assigned only when a network changes,
 * so a static scene ticks the same as one built from controllers and dense cable.
 */
public final class BusFarmScene implements BusScene {
    public enum Layout {
        DIRECT, NATIVE, FEDERATION
    }

    /** Chests stacked in one column: one bus pair each. */
    public static final int LAYERS = 5;
    /** Block changes made per tick while building, so no single tick builds the whole scene. */
    private static final int PLACEMENTS_PER_TICK = 2000;
    private static final AEColor MAIN_COLOR = AEColor.YELLOW;
    private static final AEColor[] SUBNET_COLORS = {AEColor.RED, AEColor.BLUE};
    /** Relative height of the main network's cable plane, above the top cable layer of every column. */
    private static final int MAIN_Y = 2 * LAYERS + 2;

    private final GameTestHelper helper;
    private final Layout layout;
    private final int subnets;
    private final BulkItemStorage bulk;
    private final BlockPos origin;
    private final int blockWidth;
    private final int blockDepth;
    private final int blocksPerRow;
    private final int columnsPerSubnet;
    private final List<Runnable> placements = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    private final Set<BlockPos> cables = new HashSet<>();
    private final List<BlockPos> risers = new ArrayList<>();
    private final List<BlockPos> chests = new ArrayList<>();
    private final List<AEKey> pairKeys = new ArrayList<>();
    private final Set<ChunkPos> chunks = new HashSet<>();
    private final List<PolicyKey> rules = new ArrayList<>();
    private int next;
    private boolean ruled;
    /** The Grid the bulk storage is mounted on: the main network's, which can change while the scene is built. */
    private IGrid mountedOn;
    private long ruleEditNanos;

    /**
     * @param subnets       how many subnets reach the main network
     * @param busesEach     buses on each subnet, an even number: half export, half import
     * @param types         distinct item types the main network stores
     */
    public BusFarmScene(GameTestHelper helper, Layout layout, int subnets, int busesEach, int types) {
        if (busesEach % 2 != 0) throw new IllegalArgumentException("Buses come in pairs");
        this.helper = helper;
        this.layout = layout;
        this.subnets = subnets;
        bulk = new BulkItemStorage(types, 1_000_000L);
        var pairs = busesEach / 2;
        columnsPerSubnet = (pairs + LAYERS - 1) / LAYERS;
        var columns = columnsPerSubnet + 1;
        blockWidth = (int) Math.ceil(Math.sqrt(columns));
        blockDepth = (columns + blockWidth - 1) / blockWidth;
        blocksPerRow = Math.max(1, (int) Math.ceil(Math.sqrt((double) subnets * blockWidth * blockDepth) / blockWidth));
        // Beside the test's own structure, on its ground level.
        origin = new BlockPos(0, 1, (int) helper.getBounds().getZsize() + 4);
        var keys = bulk.keys();
        var totalPairs = subnets * pairs;
        for (var subnet = 0; subnet < subnets; subnet++) {
            planSubnet(subnet, pairs, keys, totalPairs);
        }
        planMain();
        for (var x = 0; x < width(); x++) {
            for (var z = 0; z < depth(); z++) {
                chunks.add(new ChunkPos(helper.absolutePos(origin.offset(x, 0, z))));
            }
        }
        for (var chunk : chunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
    }

    /** Runs the next batch of placements; true once the whole scene is placed. */
    public boolean build() {
        var end = Math.min(placements.size(), next + PLACEMENTS_PER_TICK);
        for (; next < end; next++) placements.get(next).run();
        return next == placements.size();
    }

    /**
     * Advances the networks towards their working state; true once every subnet is powered and its own storage
     * reaches its pairs' item on the main network. In the Federation layout it turns on every subnet's storage and ME
     * power rules in one edit, once each Bridge links its subnet.
     */
    public boolean ready() {
        var main = mainGrid();
        if (main == null) return false;
        // Every network at once: a subnet left waiting for its channels idles its buses down to AE2's slowest rate.
        infiniteChannels(main);
        for (var riser : risers) {
            var grid = gridAt(riser.above(MAIN_Y - 1));
            if (grid != null) infiniteChannels(grid);
        }
        if (FederationDomainRegistryAccess.confirmedNetworkId(main).isEmpty()) return false;
        if (mountedOn != main) {
            if (mountedOn != null) mountedOn.getStorageService().removeGlobalStorageProvider(bulk);
            main.getStorageService().addGlobalStorageProvider(bulk);
            mountedOn = main;
            return false;
        }
        if (layout == Layout.FEDERATION && !ruled) {
            if (!bridgesLinked()) return false;
            var mainId = FederationDomainRegistryAccess.confirmedNetworkId(main).orElseThrow();
            var policies = PolicyService.get(helper.getLevel());
            var edits = new ArrayList<PolicyEdit>();
            for (var riser : risers) {
                var storage = new PolicyKey(networkAt(riser), mainId, PolicyCapability.STORAGE);
                var power = new PolicyKey(networkAt(riser), mainId, PolicyCapability.ME_POWER);
                rules.add(storage);
                rules.add(power);
                edits.add(new PolicyEdit(storage, policies.revision(storage), PolicyRule.storageDefaults()));
                edits.add(new PolicyEdit(power, policies.revision(power),
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
            }
            var started = System.nanoTime();
            var result = policies.editAll(edits);
            ruleEditNanos = System.nanoTime() - started;
            if (!(result instanceof PolicyMutationResult.Accepted)) {
                throw new IllegalStateException("Storage rules refused: " + result);
            }
            ruled = true;
            return false;
        }
        var grids = new HashSet<IGrid>();
        for (var subnet = 0; subnet < subnets; subnet++) {
            var grid = gridAt(risers.get(subnet).above(MAIN_Y - 1));
            if (grid == null) return false;
            if (!grid.getEnergyService().isNetworkPowered()) return false;
            grids.add(grid);
            var key = pairKeys.get(subnet);
            if (grid.getStorageService().getInventory().extract(key, 1, Actionable.SIMULATE,
                    IActionSource.empty()) != 1) {
                return false;
            }
        }
        return grids.size() == (layout == Layout.DIRECT ? 1 : subnets) && (layout == Layout.DIRECT
                ? grids.contains(main) : !grids.contains(main));
    }

    /** Inserts and extracts on the main network's bulk storage so far. */
    public long operations() {
        return bulk.operations();
    }

    /** Items waiting in the pairs' chests: what the Export Buses put in and the Import Buses did not take back yet. */
    public long itemsInChests() {
        long items = 0;
        for (var position : chests) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(position))
                    instanceof net.minecraft.world.Container container) {
                for (var slot = 0; slot < container.getContainerSize(); slot++) {
                    items += container.getItem(slot).getCount();
                }
            }
        }
        return items;
    }

    public int placements() {
        return placements.size();
    }

    /** How long the one edit that turned on every subnet's rules took, in the Federation layout. */
    public long ruleEditNanos() {
        return ruleEditNanos;
    }

    /**
     * Turns the first subnet's storage rule off and on again, as a player does in the Federation screen, while every
     * other rule stays on; returns the nanoseconds of each edit.
     */
    public long[] toggleOneRule() {
        var policies = PolicyService.get(helper.getLevel());
        var key = rules.getFirst();
        var times = new long[2];
        for (var round = 0; round < 2; round++) {
            var rule = policies.configured(key).orElseThrow().rule();
            var started = System.nanoTime();
            var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule.withEnabled(round == 1)));
            times[round] = System.nanoTime() - started;
            if (!(result instanceof PolicyMutationResult.Accepted)) {
                throw new IllegalStateException("Rule edit refused: " + result);
            }
        }
        return times;
    }

    /** The main network's Grid, then each subnet's; null for a network that is not formed. */
    public List<IGrid> grids() {
        var grids = new ArrayList<IGrid>();
        grids.add(mainGrid());
        for (var riser : risers) grids.add(gridAt(riser.above(MAIN_Y - 1)));
        return grids;
    }

    /** Deletes the rules the scene turned on, for a test that shares its level with others. */
    public void deleteRules() {
        var policies = PolicyService.get(helper.getLevel());
        for (var key : rules) {
            var result = policies.delete(new PolicyDelete(key, policies.revision(key)));
            if (!(result instanceof PolicyMutationResult.Accepted)) {
                throw new IllegalStateException("Rule delete refused: " + result);
            }
        }
        rules.clear();
    }

    /** Removes every block the scene placed and releases its chunks; returns the nanoseconds it took. */
    public long clear() {
        var started = System.nanoTime();
        if (mountedOn != null) mountedOn.getStorageService().removeGlobalStorageProvider(bulk);
        mountedOn = null;
        for (var position : placed) {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(position))
                    instanceof net.minecraft.world.Container container) {
                container.clearContent();
            }
            helper.setBlock(position, Blocks.AIR);
        }
        placed.clear();
        for (var chunk : chunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        return System.nanoTime() - started;
    }

    /**
     * Removes the scene. Its rules stay: each benchmark runs in a GameTest server of its own, whose world is deleted
     * afterwards, and turning a thousand rules off one by one would take minutes (see {@link #toggleOneRule()}).
     */
    @Override
    public void close() {
        clear();
    }

    private void planSubnet(int subnet, int pairs, List<AEKey> keys, int totalPairs) {
        var blockX = subnet % blocksPerRow;
        var blockZ = subnet / blocksPerRow;
        var color = layout == Layout.DIRECT ? MAIN_COLOR : SUBNET_COLORS[(blockX + blockZ) % 2];
        var corner = origin.offset(blockX * blockWidth, 0, blockZ * blockDepth);
        // The riser sits at either end of the block, so the Quartz Fiber beside it lands on a full column of this block.
        var riser = corner.offset(blockZ % 2 == 0 ? 0 : blockWidth - 1, 0, 0);
        risers.add(riser);
        for (var y = 1; y <= 2 * LAYERS + 1; y++) cable(riser.above(y), color);
        var pair = 0;
        for (var dz = 0; dz < blockDepth && pair < pairs; dz++) {
            for (var dx = 0; dx < blockWidth && pair < pairs; dx++) {
                var column = corner.offset(dx, 0, dz);
                if (column.equals(riser)) continue;
                for (var layer = 0; layer < LAYERS && pair < pairs; layer++, pair++) {
                    var key = keys.get((int) ((long) (subnet * pairs + pair) * keys.size() / totalPairs));
                    if (pair == 0) pairKeys.add(key);
                    var below = column.above(2 * layer + 1);
                    var chest = below.above();
                    var top = chest.above();
                    if (layer == 0) cable(below, color);
                    cable(top, color);
                    block(chest, Blocks.CHEST.defaultBlockState());
                    chests.add(chest);
                    placements.add(() -> {
                        ExportBusPart export = PartHelper.setPart(helper.getLevel(), helper.absolutePos(below),
                                Direction.UP, null, AEParts.EXPORT_BUS.asItem());
                        export.getConfig().setStack(0, new GenericStack(key, 1));
                    });
                    placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(top), Direction.DOWN,
                            null, AEParts.IMPORT_BUS.asItem()));
                }
            }
        }
    }

    private void planMain() {
        var corner = origin.above(MAIN_Y);
        for (var x = 0; x < width(); x++) {
            for (var z = 0; z < depth(); z++) cable(corner.offset(x, 0, z), MAIN_COLOR);
        }
        block(corner.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (layout == Layout.DIRECT) return;
        for (var subnet = 0; subnet < risers.size(); subnet++) {
            var riser = risers.get(subnet);
            var link = riser.above(MAIN_Y);
            var top = riser.above(MAIN_Y - 1);
            if (layout == Layout.FEDERATION) {
                placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(link), Direction.DOWN,
                        null, BridgeRegistration.BRIDGE.get()));
            } else {
                placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(link), Direction.DOWN,
                        null, AEParts.INTERFACE.asItem()));
                placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(top), Direction.UP,
                        null, AEParts.STORAGE_BUS.asItem()));
                var fiber = riser.offset((subnet / blocksPerRow) % 2 == 0 ? 1 : -1, MAIN_Y, 0);
                if (!cables.contains(fiber.below())) {
                    throw new IllegalStateException("No subnet cable under the Quartz Fiber at " + fiber);
                }
                placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(fiber), Direction.DOWN,
                        null, AEParts.QUARTZ_FIBER.asItem()));
            }
        }
    }

    /** Whether every Bridge shares a Federation domain with its subnet; nudges the ones that do not yet. */
    private boolean bridgesLinked() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var mainId = FederationDomainRegistryAccess.confirmedNetworkId(mainGrid()).orElseThrow();
        var mainDomains = registry.federationdomainsFor(mainId);
        var linked = true;
        for (var riser : risers) {
            var grid = gridAt(riser.above(MAIN_Y - 1));
            var subnet = grid == null ? java.util.Optional.<NetworkId>empty()
                    : FederationDomainRegistryAccess.confirmedNetworkId(grid);
            if (subnet.isPresent() && registry.federationdomainsFor(subnet.get()).stream()
                    .anyMatch(mainDomains::contains)) {
                continue;
            }
            linked = false;
            var link = helper.absolutePos(riser.above(MAIN_Y));
            if (helper.getLevel().getBlockEntity(link) instanceof IPartHost host
                    && host.getPart(Direction.DOWN) instanceof MultipartBridgePart bridge
                    && helper.getLevel().getGameTime() % 20 == 0) {
                bridge.onNeighborChanged(helper.getLevel(), link, link.below());
            }
        }
        return linked;
    }

    /** Gives {@code grid} AE2's infinite channel mode, as AE2's own test plots do; repaths only when it changes. */
    private static void infiniteChannels(IGrid grid) {
        ((PathingService) grid.getPathingService()).setForcedChannelMode(ChannelMode.INFINITE);
    }

    private NetworkId networkAt(BlockPos position) {
        return FederationDomainRegistryAccess.confirmedNetworkId(gridAt(position.above(MAIN_Y - 1)))
                .orElseThrow(() -> new IllegalStateException("Subnet at " + position + " has no confirmed id"));
    }

    private IGrid mainGrid() {
        return gridAt(origin.above(MAIN_Y));
    }

    private IGrid gridAt(BlockPos position) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof IPartHost host)) return null;
        var cable = host.getPart(null);
        if (cable == null || cable.getGridNode() == null) return null;
        return cable.getGridNode().getGrid();
    }

    private void cable(BlockPos position, AEColor color) {
        placed.add(position);
        cables.add(position);
        placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(color)));
    }

    private void block(BlockPos position, net.minecraft.world.level.block.state.BlockState state) {
        placed.add(position);
        placements.add(() -> helper.setBlock(position, state));
    }

    private int width() {
        return blocksPerRow * blockWidth;
    }

    private int depth() {
        return ((subnets + blocksPerRow - 1) / blocksPerRow) * blockDepth;
    }
}
