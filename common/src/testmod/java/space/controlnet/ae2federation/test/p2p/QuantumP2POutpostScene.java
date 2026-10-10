package space.controlnet.ae2federation.test.p2p;

import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.crafting.BridgeChainFixture;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * TEST-ONLY: the guide's "An Outpost in the Nether", built in the order a player builds it. A base network in the
 * overworld (a drive holding the stock, and the only energy cell) and an outpost network in the nether (a terminal, no
 * power) each touch a Switch. A third network, the carrier, is the two halves of a Quantum Network Bridge with a
 * Federation P2P tunnel on each; it takes the base's power through a quartz fiber and has no energy of its own. Each
 * tunnel's front meets its side's Switch through a Federation cable. The outpost uses the base's storage and runs on
 * its power.
 * <p>
 * Each side is laid out as the guide scene, the bridge standing upright (x and y). Overworld, scene origin (1, 1, 3):
 * bridge at x 0-2, a cable at x 3, the tunnel's bus at x 4 with the tunnel on its top, under the front cable, and the
 * fiber on its east side, then the base's drive at x 5 with its Switch on top and its energy cell at x 6. Nether, scene
 * origin (0, 0, 1): the outpost's terminal on a cable at x 0 under its Switch, the front cable at x 1, the tunnel's bus
 * at x 2 with the tunnel on its west side, and the bridge at x 3-5. The nether half is built after the base has settled
 * and gets its singularity later still, so the Quantum link joins two halves that already exist.
 * <p>
 * {@link #advanceFactory()} then adds the page's factory east of the base: a cable of the base's at x 7 carries a
 * Bridge whose outer side meets the factory's cable at x 8, and the factory's pattern provider at x 9 has a Molecular
 * Assembler on top. The outpost gets a crafting CPU at nether x 1, beside its terminal's cable.
 */
public final class QuantumP2POutpostScene implements AutoCloseable {
    public static final BlockPos SITE_SIZE = new BlockPos(7, 4, 3);
    public static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final BlockPos OVERWORLD = new BlockPos(1, 1, 3);
    private static final BlockPos BASE_LINK = OVERWORLD.offset(1, 1, 0);
    private static final BlockPos BASE_CABLE = OVERWORLD.offset(3, 1, 0);
    private static final BlockPos BASE_BUS = OVERWORLD.offset(4, 1, 0);
    private static final BlockPos BASE_FRONT = BASE_BUS.above();
    private static final BlockPos BASE_DRIVE = OVERWORLD.offset(5, 1, 0);
    private static final BlockPos BASE_SWITCH = BASE_DRIVE.above();
    private static final BlockPos BASE_POWER = OVERWORLD.offset(6, 1, 0);
    private static final BlockPos NETHER = new BlockPos(0, 0, 1);
    private static final BlockPos OUTPOST_TERMINAL = NETHER;
    private static final BlockPos OUTPOST_SWITCH = OUTPOST_TERMINAL.above();
    private static final BlockPos OUTPOST_FRONT = NETHER.offset(1, 1, 0);
    private static final BlockPos OUTPOST_BUS = NETHER.offset(2, 1, 0);
    private static final BlockPos OUTPOST_LINK = NETHER.offset(4, 1, 0);
    // The factory of "Order from the base's factory": a cable of the base's east of its energy cell carries the Bridge,
    // whose outer side meets the factory's own cable; the factory is a pattern provider with a Molecular Assembler.
    private static final BlockPos BASE_BRIDGE_CABLE = OVERWORLD.offset(7, 1, 0);
    private static final BlockPos FACTORY_CABLE = OVERWORLD.offset(8, 1, 0);
    private static final BlockPos FACTORY_PROVIDER = OVERWORLD.offset(9, 1, 0);
    private static final BlockPos FACTORY_ASSEMBLER = FACTORY_PROVIDER.above();
    /** The outpost's crafting CPU, beside its terminal's cable and under the Nether Federation cable. */
    private static final BlockPos OUTPOST_CPU = NETHER.offset(1, 0, 0);
    /** Ticks a half waits before the next step, so that its nodes are established when the link joins them. */
    private static final int SETTLE = 20;

    private final GameTestHelper helper;
    private final OtherDimensionSite site;
    private final P2PTunnels tunnels;
    private final long frequency;
    private int step;
    private long steppedAt;
    private int factoryStep;
    private long factorySteppedAt;
    private MultipartBridgePart factoryBridge;
    private NetworkId factoryNetwork;
    private final Set<PolicyKey> configured = new LinkedHashSet<>();
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public QuantumP2POutpostScene(GameTestHelper helper) {
        this.helper = helper;
        site = OtherDimensionSite.nether(helper, SITE_SIZE);
        tunnels = new P2PTunnels(helper.makeMockPlayer(GameType.CREATIVE));
        frequency = QuantumBridges.randomFrequency(helper);
    }

    public OtherDimensionSite site() {
        return site;
    }

    /**
     * Builds the scene one step per call and fails until the outpost shares the base's domain through the tunnels, the
     * outpost's Storage and ME power rules with the base are on and the base holds nine iron.
     */
    public void advance() {
        if (step == 0) {
            helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
            helper.setBlock(BASE_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(BASE_DRIVE, AEBlocks.DRIVE.block());
            helper.assertTrue(helper.<DriveBlockEntity>getBlockEntity(BASE_DRIVE).getInternalInventory()
                    .insertItem(0, AEItems.ITEM_CELL_1K.stack(), false).isEmpty(), "The base's drive takes a cell");
            helper.assertTrue(P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(BASE_CABLE))
                    && P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(BASE_BUS)),
                    "The carrier's cables must be placed");
            // The fiber passes the base's power to the carrier without joining the two networks.
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(BASE_BUS), Direction.EAST, null,
                    AEParts.QUARTZ_FIBER.get()) != null, "The quartz fiber must be placed toward the base's drive");
            buildBridge(BASE_LINK, (position, state) -> {
                helper.setBlock(position, state);
                return helper.getLevel().getBlockEntity(helper.absolutePos(position));
            });
            insertSingularity(baseLink());
            helper.assertTrue(tunnels.attune(helper.getLevel(), helper.absolutePos(BASE_BUS), Direction.UP) != null,
                    "A Federation cable must attune the base's tunnel");
            helper.setBlock(BASE_FRONT, RouterRegistration.FEDERATION_CABLE.get());
            helper.setBlock(BASE_SWITCH, RouterRegistration.SWITCH.get());
            next();
        }
        if (step == 1) {
            helper.assertTrue(settled() && network(baseGrid()) != null, "Waiting for the base to settle");
            helper.assertTrue(P2PTunnels.placeCable(site.level(), site.absolute(OUTPOST_TERMINAL))
                    && PartHelper.setPart(site.level(), site.absolute(OUTPOST_TERMINAL), Direction.NORTH, null,
                            AEParts.TERMINAL.get()) != null, "The outpost's terminal must be placed");
            site.setBlock(OUTPOST_SWITCH, RouterRegistration.SWITCH.get().defaultBlockState());
            site.setBlock(OUTPOST_FRONT, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            helper.assertTrue(P2PTunnels.placeCable(site.level(), site.absolute(OUTPOST_BUS)),
                    "The nether bus must be placed");
            buildBridge(OUTPOST_LINK, (position, state) -> {
                site.setBlock(position, state);
                return site.getBlockEntity(position);
            });
            helper.assertTrue(tunnels.attune(site.level(), site.absolute(OUTPOST_BUS), Direction.WEST) != null,
                    "A Federation cable must attune the nether tunnel");
            next();
        }
        if (step == 2) {
            helper.assertTrue(settled() && outpostNode() != null && network(outpostGrid()) != null,
                    "Waiting for the nether half and the outpost to settle");
            // The player puts the second singularity in last, which links the bridge.
            insertSingularity(site.getBlockEntity(OUTPOST_LINK));
            next();
        }
        if (step == 3) {
            helper.assertTrue(linked(), "Waiting for the Quantum Bridge to join the two halves");
            helper.assertTrue(tunnels.pair(baseTunnel(), List.of(outpostTunnel())),
                    "The memory card must pair the tunnels across the bridge");
            next();
        }
        if (step == 4) {
            helper.assertTrue(connected(), "Waiting for the tunnels to join the base and the outpost into one domain: "
                    + "base " + baseIdentity() + " " + network(baseGrid()) + ", outpost "
                    + outpostGrid().getService(NetworkIdentityService.class).settlement().status() + " "
                    + network(outpostGrid()) + ", linked " + linked());
            var policies = PolicyService.get(helper.getLevel());
            var result = policies.editAll(List.of(
                    new PolicyEdit(key(PolicyCapability.STORAGE), policies.revision(key(PolicyCapability.STORAGE)),
                            PolicyRule.storageDefaults()),
                    new PolicyEdit(key(PolicyCapability.ME_POWER), policies.revision(key(PolicyCapability.ME_POWER)),
                            PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)))));
            helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rules must be accepted: " + result);
            helper.assertValueEqual(baseGrid().getStorageService().getInventory()
                    .insert(IRON, 9, Actionable.MODULATE, IActionSource.empty()), 9L, "The base takes the iron");
            next();
        }
    }

    /**
     * Once {@link #advance()} has joined the outpost to the base, builds the factory one step per call: the outpost's
     * crafting CPU and the factory's network, then the Bridge between the base and the factory, then the rules (the
     * factory runs on the base's power; the base uses the factory's Crafting with re-export and no Storage, as the
     * guide builds it; the outpost uses the base's Crafting), then the planks pattern. Fails until the base can order the factory's planks.
     */
    public void advanceFactory() {
        if (factoryStep == 0) {
            helper.assertTrue(step == 5, "The outpost must share the base's domain before the factory is built");
            site.setBlock(OUTPOST_CPU, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            QuantumBridges.seed(site.getBlockEntity(OUTPOST_CPU), network(outpostGrid()));
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(BASE_BRIDGE_CABLE), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.PURPLE)) != null, "The base's cable must be placed");
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(FACTORY_CABLE), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.YELLOW)) != null, "The factory's cable must be placed");
            helper.setBlock(FACTORY_PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(FACTORY_ASSEMBLER, AEBlocks.MOLECULAR_ASSEMBLER.block());
            nextFactory();
        }
        if (factoryStep == 1) {
            helper.assertTrue(factorySettled() && network(factoryGrid()) != null,
                    "Waiting for the factory to settle as a network of its own");
            helper.assertTrue(factoryGrid() != baseGrid(), "The factory must not join the base");
            factoryNetwork = network(factoryGrid());
            factoryBridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BASE_BRIDGE_CABLE), Direction.EAST,
                    null, BridgeRegistration.BRIDGE.get());
            helper.assertTrue(factoryBridge != null, "The Bridge must be placed on the base's cable, facing the factory");
            nextFactory();
        }
        if (factoryStep == 2) {
            factoryBridge.onUpdateShape(factoryBridge.getSide());
            helper.assertTrue(factoryBridge.membershipCandidate().isPresent(),
                    "Waiting for the Bridge to join the base and the factory: " + factoryBridge.operationalReason());
            helper.assertTrue(twoDomainsMeetAtBase(), "Waiting for two domains that meet only at the base");
            rule(new PolicyKey(factoryNetwork, network(baseGrid()), PolicyCapability.ME_POWER),
                    PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
            factoryCrafting(RuleMode.REEXPORT);
            rule(key(PolicyCapability.CRAFTING), PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
            nextFactory();
        }
        if (factoryStep == 3) {
            helper.assertTrue(factoryGrid() != null && factoryGrid().getEnergyService().isNetworkPowered(),
                    "Waiting for the factory to run on the base's power");
            helper.assertValueEqual(outpostCpus(), 1, "Waiting for the outpost's crafting CPU");
            var provider = helper.<PatternProviderBlockEntity>getBlockEntity(FACTORY_PROVIDER);
            provider.getLogic().getPatternInv().addItems(BridgeChainFixture.planksPattern(helper.getLevel()));
            provider.getLogic().updatePatterns();
            nextFactory();
        }
        if (factoryStep == 4) {
            helper.assertTrue(baseCanCraft(BridgeChainFixture.planks()), "Waiting for the factory's planks on the base");
        }
    }

    /** Steps "Base uses Factory's" Crafting to {@code mode}; its Storage stays as it is. */
    public void factoryCrafting(RuleMode mode) {
        rule(new PolicyKey(network(baseGrid()), factoryNetwork, PolicyCapability.CRAFTING),
                PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withMode(mode));
    }

    public boolean outpostCanCraft(AEItemKey key) {
        return outpostGrid().getCraftingService().isCraftable(key);
    }

    public boolean baseCanCraft(AEItemKey key) {
        return baseGrid().getCraftingService().isCraftable(key);
    }

    /** The factory's pattern providers projected onto the outpost's Grid, through the base's re-export. */
    public int outpostProjections() {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(network(outpostGrid()), factoryNetwork);
    }

    public int outpostCpus() {
        return outpostGrid().getCraftingService().getCpus().size();
    }

    public int baseCpus() {
        return baseGrid().getCraftingService().getCpus().size();
    }

    public long busyOutpostCpus() {
        return outpostGrid().getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    /** What the outpost's terminal lists of {@code key}. */
    public long outpostSees(AEItemKey key) {
        return outpostGrid().getStorageService().getInventory().getAvailableStacks().get(key);
    }

    /** What the base's terminal would list of {@code key}: its drive, as the factory stores nothing. */
    public long baseSees(AEItemKey key) {
        return baseGrid().getStorageService().getInventory().getAvailableStacks().get(key);
    }

    /** Puts {@code amount} of {@code key} into the base's storage. */
    public void stockBase(AEItemKey key, long amount) {
        helper.assertValueEqual(baseGrid().getStorageService().getInventory().insert(key, amount, Actionable.MODULATE,
                IActionSource.empty()), amount, "The base takes the items");
    }

    /** Starts a calculation on the outpost's own crafting service, as its crafting terminal does. */
    public void beginOnOutpost(AEItemKey what, long amount) {
        plan = null;
        var node = outpostNode();
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
        planFuture = outpostGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(), requester, what,
                amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** The outpost's plan once calculated, or null while it is still being worked out. */
    public ICraftingPlan plan() {
        if (plan == null && planFuture != null && planFuture.isDone()) {
            try {
                plan = planFuture.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Calculation interrupted", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Calculation failed", exception);
            }
        }
        return plan;
    }

    /** Submits the outpost's plan, which the outpost's own CPU then runs. */
    public boolean submitOnOutpost() {
        return outpostGrid().getCraftingService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    /** The outpost and the base in one domain, the base and the factory in another, the outpost not with the factory. */
    private boolean twoDomainsMeetAtBase() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var outpost = registry.federationdomainsFor(network(outpostGrid()));
        var base = registry.federationdomainsFor(network(baseGrid()));
        var factory = registry.federationdomainsFor(factoryNetwork);
        return outpost.size() == 1 && base.size() == 2 && factory.size() == 1 && base.containsAll(outpost)
                && base.containsAll(factory) && outpost.stream().noneMatch(factory::contains);
    }

    private void rule(PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(helper.getLevel());
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The rule must be accepted: " + key + " "
                + result);
        configured.add(key);
    }

    private void nextFactory() {
        factoryStep++;
        factorySteppedAt = helper.getTick();
    }

    private boolean factorySettled() {
        return helper.getTick() - factorySteppedAt >= SETTLE;
    }

    private IGrid factoryGrid() {
        var node = helper.<PatternProviderBlockEntity>getBlockEntity(FACTORY_PROVIDER).getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private void next() {
        step++;
        steppedAt = helper.getTick();
    }

    private boolean settled() {
        return helper.getTick() - steppedAt >= SETTLE;
    }

    /** Whether the bridge has joined both halves into one network on which both tunnels have power and a channel. */
    public boolean linked() {
        var base = baseTunnel();
        var outpost = outpostTunnel();
        return P2PTunnels.online(base, outpost) && base.getGridNode().getGrid() == outpost.getGridNode().getGrid();
    }

    /** Whether the base's and the outpost's networks share a domain. */
    public boolean connected() {
        var base = network(baseGrid());
        var outpost = network(outpostGrid());
        if (base == null || outpost == null) return false;
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var domains = registry.federationdomainsFor(base);
        return registry.federationdomainsFor(outpost).stream().anyMatch(domains::contains);
    }

    /** How much iron the outpost's network sees. */
    public long outpostIron() {
        return outpostGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
    }

    /** Whether the outpost, which has no energy of its own, runs. */
    public boolean outpostPowered() {
        return outpostNode().isPowered();
    }

    /** The base network's identity settlement, which a Quantum link merging an established half would leave pending. */
    public IdentityStatus baseIdentity() {
        return baseGrid().getService(NetworkIdentityService.class).settlement().status();
    }

    /**
     * Takes the singularity out of the base's link chamber, as a player does, which breaks the Quantum link; returns
     * it.
     */
    public ItemStack breakLink() {
        var singularity = baseLink().getInternalInventory().extractItem(0, 1, false);
        helper.assertFalse(singularity.isEmpty(), "The base's link chamber must hold its singularity");
        return singularity;
    }

    /** Puts {@code singularity} back into the base's link chamber, which links the bridge again. */
    public void restoreLink(ItemStack singularity) {
        helper.assertTrue(baseLink().getInternalInventory().insertItem(0, singularity, false).isEmpty(),
                "The base's link chamber must take its singularity back");
    }

    public FederationP2PTunnelPart baseTunnel() {
        return P2PTunnels.tunnel(helper.getLevel(), helper.absolutePos(BASE_BUS), Direction.UP);
    }

    public FederationP2PTunnelPart outpostTunnel() {
        return P2PTunnels.tunnel(site.level(), site.absolute(OUTPOST_BUS), Direction.WEST);
    }

    /** An upright bridge around {@code link}: the link chamber in the middle and eight rings around it in x and y. */
    private static void buildBridge(BlockPos link, BiFunction<BlockPos, BlockState, BlockEntity> place) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                var block = x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK : AEBlocks.QUANTUM_RING;
                place.apply(link.offset(x, y, 0), block.block().defaultBlockState());
            }
        }
    }

    private void insertSingularity(QuantumBridgeBlockEntity link) {
        var singularity = new ItemStack(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem());
        singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
        helper.assertTrue(link.getInternalInventory().insertItem(0, singularity, false).isEmpty(),
                "The link chamber must take the singularity");
    }

    private QuantumBridgeBlockEntity baseLink() {
        return (QuantumBridgeBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(BASE_LINK));
    }

    private PolicyKey key(PolicyCapability capability) {
        return new PolicyKey(network(outpostGrid()), network(baseGrid()), capability);
    }

    private static NetworkId network(IGrid grid) {
        return grid == null ? null : FederationDomainRegistryAccess.confirmedNetworkId(grid).orElse(null);
    }

    public IGrid baseGrid() {
        DriveBlockEntity drive = helper.getBlockEntity(BASE_DRIVE);
        var node = drive.getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    private IGrid outpostGrid() {
        var node = outpostNode();
        return node == null ? null : node.getGrid();
    }

    private IGridNode outpostNode() {
        var terminal = PartHelper.getPart(site.level(), site.absolute(OUTPOST_TERMINAL), Direction.NORTH);
        return terminal == null ? null : terminal.getGridNode();
    }

    /**
     * Switches off the rules the factory steps set and takes the factory away again: these networks reach past the
     * test structure, and live rules and a Bridge would keep their mounts and projections for later tests.
     */
    @Override
    public void close() {
        try {
            var policies = PolicyService.get(helper.getLevel());
            for (var key : configured) {
                policies.configured(key).filter(record -> record.rule().enabled()).ifPresent(record -> policies.edit(
                        new PolicyEdit(key, record.revision(), record.rule().withEnabled(false))));
            }
            if (factoryStep > 0) {
                for (var position : List.of(FACTORY_ASSEMBLER, FACTORY_PROVIDER, FACTORY_CABLE, BASE_BRIDGE_CABLE)) {
                    helper.setBlock(position, Blocks.AIR);
                }
            }
        } finally {
            site.close();
        }
    }
}
