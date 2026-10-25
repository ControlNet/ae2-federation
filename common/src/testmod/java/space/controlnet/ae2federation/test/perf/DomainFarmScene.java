package space.controlnet.ae2federation.test.perf;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.service.PathingService;
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
 * TEST-ONLY scene with every network in one Federation domain: a row of 3x3x1 Router bodies along X, each joined to
 * the next by a 3x3 bundled cable, whose capacity has no limit. From the middle of each Router's north and south faces
 * a plain cable two blocks long leads to a subdomain: a 1x1x2 Switch body with {@link #NETWORKS_EACH} networks on its
 * other faces, each one coloured glass cable, as many as a plain cable carries. One of them is the main network, with a
 * creative energy cell and the storage every other network reaches through its storage and ME power rules.
 *
 * <p>Subdomains of neighbouring Routers stay a block apart, so neither their plain cables nor their networks touch;
 * the networks of one Switch differ in colour.
 */
public final class DomainFarmScene implements AutoCloseable {
    /** Networks on each Switch: one plain cable's capacity. */
    public static final int NETWORKS_EACH = 8;
    private static final int PLACEMENTS_PER_TICK = 2000;
    /** Routers' X spacing: one Router block and a bundle three long. */
    private static final int PITCH = 4;
    private static final AEColor[] COLORS = {AEColor.RED, AEColor.BLUE, AEColor.GREEN, AEColor.YELLOW,
            AEColor.ORANGE, AEColor.PURPLE, AEColor.CYAN, AEColor.LIGHT_GRAY};
    /** The Switch faces that carry a network, as offsets from the Switch block nearer the Router. */
    private static final Direction[] NETWORK_FACES = {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};

    private final GameTestHelper helper;
    private final int networks;
    private final BulkItemStorage bulk;
    private final BlockPos origin;
    private final int routers;
    private final List<Runnable> placements = new ArrayList<>();
    private final List<BlockPos> placed = new ArrayList<>();
    /** Each subdomain's plain cable next to its Router, in subdomain order. */
    private final List<BlockPos> subdomainCables = new ArrayList<>();
    /** Each network's glass cable, in network order; the first is the main network's. */
    private final List<BlockPos> networkCables = new ArrayList<>();
    private final List<AEKey> keys;
    private final Set<ChunkPos> chunks = new HashSet<>();
    private final List<PolicyKey> rules = new ArrayList<>();
    private int next;
    private boolean ruled;
    private IGrid mountedOn;
    private long ruleEditNanos;

    /** @param networks how many networks join the domain, the main network among them; a multiple of eight */
    public DomainFarmScene(GameTestHelper helper, int networks) {
        if (networks % NETWORKS_EACH != 0) throw new IllegalArgumentException("Whole subdomains only");
        this.helper = helper;
        this.networks = networks;
        var subdomains = networks / NETWORKS_EACH;
        routers = (subdomains + 1) / 2;
        bulk = new BulkItemStorage(networks, 1_000_000L);
        keys = bulk.keys();
        // Beside the test's own structure; the subdomains reach five blocks north and south of the Routers.
        origin = new BlockPos(0, 2, (int) helper.getBounds().getZsize() + 10);
        for (var router = 0; router < routers; router++) {
            var x = router * PITCH;
            for (var dy = 0; dy < 3; dy++) {
                for (var dz = 0; dz < 3; dz++) block(origin.offset(x, dy, dz), RouterRegistration.ROUTER.get().defaultBlockState());
            }
        }
        var bundles = new ArrayList<BundleSegment>();
        for (var router = 0; router + 1 < routers; router++) {
            var min = origin.offset(router * PITCH + 1, 0, 0);
            var max = min.offset(PITCH - 2, 2, 2);
            for (var position : BlockPos.betweenClosed(min, max)) {
                block(position.immutable(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            }
            bundles.add(new BundleSegment(helper.absolutePos(min), helper.absolutePos(max), Direction.Axis.X));
        }
        for (var subdomain = 0; subdomain < subdomains; subdomain++) planSubdomain(subdomain);
        var mainCable = networkCables.getFirst();
        block(mainCable.above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        // Bundles form from cables already placed.
        placements.add(() -> {
            var record = BundledCables.get(helper.getLevel());
            for (var segment : bundles) record.form(helper.getLevel(), segment);
        });
        for (var x = -1; x <= routers * PITCH; x++) {
            for (var z = -6; z <= 8; z++) chunks.add(new ChunkPos(helper.absolutePos(origin.offset(x, 0, z))));
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
     * Advances the networks towards their working state; true once every network is powered and reaches its item on
     * the main network. Turns on every other network's storage and ME power rules in one edit, once each shares the
     * main network's domain.
     */
    public boolean ready() {
        var main = mainGrid();
        if (main == null) return false;
        for (var cable : networkCables) {
            var grid = gridAt(cable);
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
            if (!inMainDomain(1, networks)) return false;
            var policies = PolicyService.get(helper.getLevel());
            var edits = new ArrayList<PolicyEdit>();
            for (var network = 1; network < networks; network++) {
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
        return reachMain(1, networks);
    }

    /** Whether networks {@code from} (inclusive) to {@code to} (exclusive) share a domain with the main network. */
    public boolean inMainDomain(int from, int to) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var mainId = FederationDomainRegistryAccess.confirmedNetworkId(mainGrid());
        if (mainId.isEmpty()) return false;
        var mainDomains = registry.federationdomainsFor(mainId.get());
        if (mainDomains.isEmpty()) return false;
        for (var network = from; network < to; network++) {
            var grid = gridAt(networkCables.get(network));
            var id = grid == null ? java.util.Optional.<NetworkId>empty()
                    : FederationDomainRegistryAccess.confirmedNetworkId(grid);
            if (id.isEmpty() || registry.federationdomainsFor(id.get()).stream().noneMatch(mainDomains::contains)) {
                return false;
            }
        }
        return true;
    }

    /** Whether networks {@code from} to {@code to} (exclusive) are powered and each reaches its item on main. */
    public boolean reachMain(int from, int to) {
        for (var network = from; network < to; network++) {
            if (!reaches(network)) return false;
        }
        return true;
    }

    /** Whether no network from {@code from} to {@code to} (exclusive) reaches its item on main any more. */
    public boolean cutOff(int from, int to) {
        for (var network = from; network < to; network++) {
            if (reaches(network)) return false;
        }
        return true;
    }

    /** The networks of the last subdomain, the farthest from the main network: first and end (exclusive). */
    public int[] lastSubdomain() {
        return new int[] {networks - NETWORKS_EACH, networks};
    }

    /** Breaks the plain cable joining the last subdomain to its Router, as a player does. */
    public void breakLastSubdomainCable() {
        helper.setBlock(subdomainCables.getLast(), Blocks.AIR);
    }

    /** Places the last subdomain's plain cable back. */
    public void restoreLastSubdomainCable() {
        helper.setBlock(subdomainCables.getLast(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
    }

    public int placements() {
        return placements.size();
    }

    public int routers() {
        return routers;
    }

    /** How long the one edit that turned on every network's rules took. */
    public long ruleEditNanos() {
        return ruleEditNanos;
    }

    /** Turns the first network's storage rule off and on again; returns the nanoseconds of each edit. */
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

    /** Removes every block the scene placed and releases its chunks; returns the nanoseconds it took. */
    public long clear() {
        var started = System.nanoTime();
        if (mountedOn != null) mountedOn.getStorageService().removeGlobalStorageProvider(bulk);
        mountedOn = null;
        // Removing a bundle's cables removes the bundle, which the level would keep otherwise.
        for (var position : placed) helper.setBlock(position, Blocks.AIR);
        placed.clear();
        for (var chunk : chunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        return System.nanoTime() - started;
    }

    /** Removes the scene; its rules stay, as each benchmark runs in a GameTest server of its own. */
    @Override
    public void close() {
        clear();
    }

    /** Subdomain {@code subdomain}: even ones south of their Router, odd ones north. */
    private void planSubdomain(int subdomain) {
        var x = (subdomain / 2) * PITCH;
        var south = subdomain % 2 == 0;
        var step = south ? 1 : -1;
        // The middle of the Router's face, then the plain cable, then the Switch body.
        var face = origin.offset(x, 1, south ? 2 : 0);
        var cable = face.relative(south ? Direction.SOUTH : Direction.NORTH);
        subdomainCables.add(cable);
        block(cable, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        block(cable.offset(0, 0, step), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        var near = cable.offset(0, 0, 2 * step);
        var far = near.offset(0, 0, step);
        block(near, RouterRegistration.SWITCH.get().defaultBlockState());
        block(far, RouterRegistration.SWITCH.get().defaultBlockState());
        var color = 0;
        for (var switchBlock : List.of(near, far)) {
            for (var direction : NETWORK_FACES) glassCable(switchBlock.relative(direction), COLORS[color++]);
        }
    }

    private boolean reaches(int network) {
        var grid = gridAt(networkCables.get(network));
        if (grid == null || !grid.getEnergyService().isNetworkPowered()) return false;
        return grid.getStorageService().getInventory().extract(keys.get(network), 1, Actionable.SIMULATE,
                IActionSource.empty()) == 1;
    }

    private NetworkId networkId(int network) {
        return FederationDomainRegistryAccess.confirmedNetworkId(gridAt(networkCables.get(network)))
                .orElseThrow(() -> new IllegalStateException("Network " + network + " has no confirmed id"));
    }

    /** Gives {@code grid} AE2's infinite channel mode, as AE2's own test plots do; repaths only when it changes. */
    private static void infiniteChannels(IGrid grid) {
        ((PathingService) grid.getPathingService()).setForcedChannelMode(ChannelMode.INFINITE);
    }

    private IGrid mainGrid() {
        return gridAt(networkCables.getFirst());
    }

    private IGrid gridAt(BlockPos position) {
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(position)) instanceof IPartHost host)) return null;
        var cable = host.getPart(null);
        if (cable == null || cable.getGridNode() == null) return null;
        return cable.getGridNode().getGrid();
    }

    private void glassCable(BlockPos position, AEColor color) {
        placed.add(position);
        networkCables.add(position);
        placements.add(() -> PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(color)));
    }

    private void block(BlockPos position, BlockState state) {
        placed.add(position);
        placements.add(() -> helper.setBlock(position, state));
    }
}
