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
import net.minecraft.world.level.block.state.BlockState;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * TEST-ONLY bus scene with every network in one Federation domain. The subnets and their bus pairs are those of
 * {@link BusFarmScene}: a block of columns each, a column stacking {@link BusFarmScene#LAYERS} chests between cable
 * layers, an Export Bus under each chest and an Import Bus above it, and one all-cable riser per block. The main
 * network is one more riser, with the creative energy cell and the storage every subnet reaches through its storage
 * and ME power rules.
 *
 * <p>The domain: a row of 3x3x1 Router bodies along X, each joined to the next by a 3x3 bundled cable two long,
 * whose capacity has no limit. A Switch sits on every riser; risers side by side make one Switch body, which attaches
 * up to {@link #NETWORKS_EACH} networks, one plain cable's capacity. From the middle of each Router's north and south
 * faces a plain cable runs over the farm and down onto one Switch body: a subdomain. Narrow subnets share a body eight
 * at a time; a subnet as wide as a Router's next three has a body of its own.
 *
 * <p>Each Router's subdomains lie in one of four rows on its side, so neighbouring subdomains stay apart; neighbouring
 * subnets of one subdomain differ in cable colour. Every network runs in AE2's infinite channel mode, as in
 * {@link BusFarmScene}.
 */
public final class DomainFarmScene implements BusScene {
    /** Networks on each Switch body at most: one plain cable's capacity. */
    public static final int NETWORKS_EACH = 8;
    private static final int LAYERS = BusFarmScene.LAYERS;
    private static final int PLACEMENTS_PER_TICK = 2000;
    /** Routers' X spacing: one Router block and a bundle two long. */
    private static final int PITCH = 3;
    /** Rows of subdomains on each side of the Routers: a subdomain may be as wide as this many Routers' spacing, less one. */
    private static final int ROWS = 4;
    private static final AEColor MAIN_COLOR = AEColor.YELLOW;
    private static final AEColor[] SUBNET_COLORS = {AEColor.RED, AEColor.BLUE};
    /** Relative height of the top cable layer, the risers' top. */
    private static final int TOP_Y = 2 * LAYERS + 1;
    /** Relative height of the Switches, on the risers. */
    private static final int SWITCH_Y = TOP_Y + 1;
    /** Relative height of the Routers' middle when not lifted: two above the Switches, the plain cables one over them. */
    private static final int ROUTER_Y = SWITCH_Y + 2;

    private final GameTestHelper helper;
    private final int subnets;
    private final BulkItemStorage bulk;
    private final BlockPos origin;
    private final int blockWidth;
    private final int blockDepth;
    private final int routers;
    private final int routerZ;
    /** Relative height of the Routers' middle. */
    private final int routerY;
    private final List<Runnable> placements = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    /** Each network's riser top, in network order; the first is the main network's. */
    private final List<BlockPos> risers = new ArrayList<>();
    /** Each subdomain's networks: the index of its first network and of the one after its last. */
    private final List<int[]> subdomains = new ArrayList<>();
    /** Each subdomain's plain cable next to its Router, in subdomain order. */
    private final List<BlockPos> subdomainCables = new ArrayList<>();
    private final List<BlockPos> chests = new ArrayList<>();
    private final List<AEKey> pairKeys = new ArrayList<>();
    private final Set<ChunkPos> chunks = new HashSet<>();
    private final List<PolicyKey> rules = new ArrayList<>();
    /** Router, Switch and Federation Cable blocks: the domain's nodes. */
    private int federationBlocks;
    private int next;
    private boolean ruled;
    private IGrid mountedOn;
    private long ruleEditNanos;

    /**
     * @param subnets   how many subnets reach the main network
     * @param busesEach buses on each subnet, an even number: half export, half import
     * @param types     distinct item types the main network stores
     */
    public DomainFarmScene(GameTestHelper helper, int subnets, int busesEach, int types) {
        this(helper, subnets, busesEach, types, 0);
    }

    /**
     * @param lift blocks the Routers are raised by: each subdomain's plain cable runs that much longer down to its
     *             Switch, which makes the domain larger with the same networks and buses
     */
    public DomainFarmScene(GameTestHelper helper, int subnets, int busesEach, int types, int lift) {
        if (busesEach % 2 != 0) throw new IllegalArgumentException("Buses come in pairs");
        if (lift < 0) throw new IllegalArgumentException("The Routers cannot sit lower");
        this.helper = helper;
        routerY = ROUTER_Y + lift;
        this.subnets = subnets;
        bulk = new BulkItemStorage(types, 1_000_000L);
        var pairs = busesEach / 2;
        var columns = (pairs + LAYERS - 1) / LAYERS + 1;
        // Narrow blocks stand side by side under one Switch body, so they are one column wide.
        blockWidth = columns <= 2 ? 1 : (int) Math.ceil(Math.sqrt(columns));
        blockDepth = (columns + blockWidth - 1) / blockWidth;
        var each = Math.min(NETWORKS_EACH, (ROWS * PITCH - 1) / blockWidth);
        if (each < 1) throw new IllegalArgumentException("A subnet wider than a subdomain's row");
        var networks = subnets + 1;
        var subdomainCount = (networks + each - 1) / each;
        routers = (subdomainCount + 1) / 2;
        // Beside the test's own structure; the rows reach ROWS blocks deep on each side of the Routers.
        var reach = ROWS * (blockDepth + 1) + 2;
        origin = new BlockPos(0, 1, (int) helper.getBounds().getZsize() + 4);
        routerZ = reach + 1;
        var keys = bulk.keys();
        var totalPairs = subnets * pairs;
        for (var subdomain = 0; subdomain < subdomainCount; subdomain++) {
            var first = subdomain * each;
            var end = Math.min(networks, first + each);
            subdomains.add(new int[] {first, end});
            planSubdomain(subdomain, first, end, pairs, keys, totalPairs);
        }
        planRouters();
        for (var x = -2; x <= routers * PITCH + ROWS * PITCH; x++) {
            for (var z = 0; z <= 2 * routerZ; z++) chunks.add(new ChunkPos(helper.absolutePos(origin.offset(x, 0, z))));
        }
        for (var chunk : chunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
    }

    @Override
    public boolean build() {
        var end = Math.min(placements.size(), next + PLACEMENTS_PER_TICK);
        for (; next < end; next++) placements.get(next).run();
        return next == placements.size();
    }

    /**
     * True once every subnet is powered and its own storage reaches its pairs' item on the main network. Turns on every
     * subnet's storage and ME power rules in one edit, once each shares the main network's domain.
     */
    @Override
    public boolean ready() {
        var main = mainGrid();
        if (main == null) return false;
        for (var riser : risers) {
            var grid = gridAt(riser);
            if (grid != null) infiniteChannels(grid);
        }
        var mainId = FederationDomainRegistryAccess.confirmedNetworkId(main);
        if (mainId.isEmpty()) return false;
        if (mountedOn != main) {
            if (mountedOn != null) mountedOn.getStorageService().removeGlobalStorageProvider(bulk);
            main.getStorageService().addGlobalStorageProvider(bulk);
            mountedOn = main;
            return false;
        }
        if (!ruled) {
            if (!inMainDomain(1, risers.size())) return false;
            var policies = PolicyService.get(helper.getLevel());
            var edits = new ArrayList<PolicyEdit>();
            for (var network = 1; network < risers.size(); network++) {
                var id = networkId(network);
                var storage = new PolicyKey(id, mainId.get(), PolicyCapability.STORAGE);
                var power = new PolicyKey(id, mainId.get(), PolicyCapability.ME_POWER);
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
                throw new IllegalStateException("Rules refused: " + result);
            }
            ruled = true;
            return false;
        }
        var grids = new HashSet<IGrid>();
        for (var network = 1; network < risers.size(); network++) {
            if (!reaches(network)) return false;
            grids.add(gridAt(risers.get(network)));
        }
        return grids.size() == subnets && !grids.contains(main);
    }

    /** Whether networks {@code from} (inclusive) to {@code to} (exclusive) share a domain with the main network. */
    public boolean inMainDomain(int from, int to) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var mainId = FederationDomainRegistryAccess.confirmedNetworkId(mainGrid());
        if (mainId.isEmpty()) return false;
        var mainDomains = registry.federationdomainsFor(mainId.get());
        if (mainDomains.isEmpty()) return false;
        for (var network = from; network < to; network++) {
            var grid = gridAt(risers.get(network));
            var id = grid == null ? java.util.Optional.<NetworkId>empty()
                    : FederationDomainRegistryAccess.confirmedNetworkId(grid);
            if (id.isEmpty() || registry.federationdomainsFor(id.get()).stream().noneMatch(mainDomains::contains)) {
                return false;
            }
        }
        return true;
    }

    /** Whether subnets {@code from} to {@code to} (exclusive) are powered and each reaches its item on main. */
    public boolean reachMain(int from, int to) {
        for (var network = from; network < to; network++) {
            if (!reaches(network)) return false;
        }
        return true;
    }

    /** Whether no subnet from {@code from} to {@code to} (exclusive) reaches its item on main any more. */
    public boolean cutOff(int from, int to) {
        for (var network = from; network < to; network++) {
            if (reaches(network)) return false;
        }
        return true;
    }

    /** The networks of the last subdomain, the farthest from the main network: first and end (exclusive). */
    public int[] lastSubdomain() {
        return subdomains.getLast().clone();
    }

    /** How many networks join the domain, the main network among them. */
    public int networks() {
        return risers.size();
    }

    /** Breaks the plain cable joining the last subdomain to its Router, as a player does. */
    public void breakLastSubdomainCable() {
        helper.setBlock(subdomainCables.getLast(), Blocks.AIR);
    }

    /** Places the last subdomain's plain cable back. */
    public void restoreLastSubdomainCable() {
        helper.setBlock(subdomainCables.getLast(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
    }

    @Override
    public long operations() {
        return bulk.operations();
    }

    @Override
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

    @Override
    public int placements() {
        return placements.size();
    }

    public int routers() {
        return routers;
    }

    /** The domain's Router, Switch and Federation Cable blocks: its nodes. */
    public int federationBlocks() {
        return federationBlocks;
    }

    @Override
    public long ruleEditNanos() {
        return ruleEditNanos;
    }

    @Override
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

    @Override
    public long clear() {
        var started = System.nanoTime();
        if (mountedOn != null) mountedOn.getStorageService().removeGlobalStorageProvider(bulk);
        mountedOn = null;
        // Removing a bundle's cables removes the bundle, which the level would keep otherwise.
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

    /** Removes the scene; its rules stay, as each benchmark runs in a GameTest server of its own. */
    @Override
    public void close() {
        clear();
    }

    /**
     * Subdomain {@code subdomain}: networks {@code first} to {@code end} (exclusive) side by side along X from its
     * Router's X, in row {@code router % ROWS} south of the Routers for even subdomains, north for odd ones.
     */
    private void planSubdomain(int subdomain, int first, int end, int pairs, List<AEKey> keys, int totalPairs) {
        var router = subdomain / 2;
        var south = subdomain % 2 == 0;
        var step = south ? 1 : -1;
        var row = router % ROWS;
        var x = router * PITCH;
        // The riser row nearest the Routers; the blocks reach away from them.
        var z = routerZ + step * (2 + row * (blockDepth + 1));
        for (var network = first; network < end; network++) {
            var corner = origin.offset(x + (network - first) * blockWidth, 0, z);
            var riser = corner.above(TOP_Y);
            risers.add(riser);
            if (network == 0) {
                for (var y = 1; y <= TOP_Y; y++) cable(corner.above(y), MAIN_COLOR);
                // Beside the main riser only: no subnet block reaches X below the first Router's.
                block(corner.offset(-1, LAYERS, 0), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            } else {
                planSubnet(network - 1, corner, step, SUBNET_COLORS[(network - first) % 2], pairs, keys, totalPairs);
            }
            block(riser.above(), RouterRegistration.SWITCH.get().defaultBlockState());
        }
        // Over the farm from the Router's face, then down onto the subdomain's first Switch.
        var face = origin.offset(x, routerY, routerZ + step);
        subdomainCables.add(face.offset(0, 0, step));
        for (var dz = face.getZ() + step; dz != origin.getZ() + z + step; dz += step) {
            block(new BlockPos(face.getX(), face.getY(), dz), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        }
        for (var y = SWITCH_Y + 1; y < routerY; y++) {
            block(origin.offset(x, y, z), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        }
    }

    /** A subnet's block, as in {@link BusFarmScene}: its riser at {@code corner}, its columns reaching {@code step} in Z. */
    private void planSubnet(int subnet, BlockPos corner, int step, AEColor color, int pairs, List<AEKey> keys,
            int totalPairs) {
        for (var y = 1; y <= TOP_Y; y++) cable(corner.above(y), color);
        var pair = 0;
        for (var dz = 0; dz < blockDepth && pair < pairs; dz++) {
            for (var dx = 0; dx < blockWidth && pair < pairs; dx++) {
                if (dx == 0 && dz == 0) continue;
                var column = corner.offset(dx, 0, step * dz);
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

    /** The Routers and the bundles between them; the bundles form once their cables are placed. */
    private void planRouters() {
        var bundles = new ArrayList<BundleSegment>();
        for (var router = 0; router < routers; router++) {
            var x = router * PITCH;
            for (var dy = -1; dy <= 1; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    block(origin.offset(x, routerY + dy, routerZ + dz), RouterRegistration.ROUTER.get().defaultBlockState());
                }
            }
            if (router + 1 == routers) break;
            var min = origin.offset(x + 1, routerY - 1, routerZ - 1);
            var max = min.offset(PITCH - 2, 2, 2);
            for (var position : BlockPos.betweenClosed(min, max)) {
                block(position.immutable(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            }
            bundles.add(new BundleSegment(helper.absolutePos(min), helper.absolutePos(max), Direction.Axis.X));
        }
        placements.add(() -> {
            var record = BundledCables.get(helper.getLevel());
            for (var segment : bundles) record.form(helper.getLevel(), segment);
        });
    }

    private boolean reaches(int network) {
        var grid = gridAt(risers.get(network));
        if (grid == null || !grid.getEnergyService().isNetworkPowered()) return false;
        return grid.getStorageService().getInventory().extract(pairKeys.get(network - 1), 1, Actionable.SIMULATE,
                IActionSource.empty()) == 1;
    }

    private NetworkId networkId(int network) {
        return FederationDomainRegistryAccess.confirmedNetworkId(gridAt(risers.get(network)))
                .orElseThrow(() -> new IllegalStateException("Network " + network + " has no confirmed id"));
    }

    /** Gives {@code grid} AE2's infinite channel mode, as AE2's own test plots do; repaths only when it changes. */
    private static void infiniteChannels(IGrid grid) {
        ((PathingService) grid.getPathingService()).setForcedChannelMode(ChannelMode.INFINITE);
    }

    private IGrid mainGrid() {
        return gridAt(risers.getFirst());
    }

    private IGrid gridAt(BlockPos position) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof IPartHost host)) return null;
        var cable = host.getPart(null);
        if (cable == null || cable.getGridNode() == null) return null;
        return cable.getGridNode().getGrid();
    }

    private void cable(BlockPos position, AEColor color) {
        placed.add(position);
        placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(color)));
    }

    private void block(BlockPos position, BlockState state) {
        if (state.getBlock() == RouterRegistration.ROUTER.get() || state.getBlock() == RouterRegistration.SWITCH.get()
                || state.getBlock() == RouterRegistration.FEDERATION_CABLE.get()) {
            federationBlocks++;
        }
        placed.add(position);
        placements.add(() -> helper.setBlock(position, state));
    }
}
