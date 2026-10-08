package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.world.BlockEntityReload;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;
import space.controlnet.ae2federation.test.world.QuantumBridges;

/**
 * Two networks joined by a Bridge: the consumer (the Bridge's main side) has a CPU and an ME chest; the provider (its
 * outer side) has an ME chest and no CPU, with two pattern providers. One holds a sticks crafting pattern beside a
 * Molecular Assembler. The other holds a processing pattern (cobblestone to stone) and faces a plain chest, a machine
 * the test runs by hand: it takes the inputs from the chest and puts the outputs into the provider network itself, as
 * an import bus or a second provider would, so late, partial and surplus returns can be tested.
 */
public final class PatternProjectionFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final CraftingNativeSourceFixture assembler;
    /** The consumer's overworld CPU: beside the Bridge, or beside the cable to the Quantum Bridge when there is one. */
    private final BlockPos consumerCpuPos;
    /** The overworld Quantum Bridge's link chamber; its ring's east edge touches the consumer's cable. */
    private static final BlockPos OVERWORLD_CHAMBER = BASE.offset(-4, 0, 2);
    private static final BlockPos NETHER_CHAMBER = new BlockPos(1, 0, 1);
    private static final BlockPos NETHER_CELL = NETHER_CHAMBER.east(2);
    private static final BlockPos NETHER_CPU = NETHER_CHAMBER.south(2);
    public static final BlockPos SITE_SIZE = new BlockPos(5, 2, 5);
    private final ConsumerCpus cpus;
    private final @Nullable OtherDimensionSite site;
    private final long frequency;
    private @Nullable NetworkId consumerId;
    private @Nullable NetworkId providerId;
    private final BlockPos processingProviderPos = BASE.east().north(2);
    private final BlockPos machinePos = BASE.east().north(3);
    private boolean bridgePlaced;
    private boolean consumerCpuPlaced;
    private boolean processingPlaced;
    private boolean processingPatternInstalled;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    /**
     * Where the consumer's CPUs are. {@code NETHER}: its only CPU is in a nether site, on the consumer network over a
     * real Quantum Network Bridge, so its chunk can unload while the rest of the consumer network stays loaded.
     * {@code BOTH}: one in the overworld and one in the nether.
     */
    public enum ConsumerCpus {
        HERE, NETHER, BOTH
    }

    public PatternProjectionFixture(GameTestHelper helper) {
        this(helper, ConsumerCpus.HERE);
    }

    public PatternProjectionFixture(GameTestHelper helper, ConsumerCpus cpus) {
        this.helper = helper;
        this.cpus = cpus;
        consumerCpuPos = cpus == ConsumerCpus.HERE ? BASE.west() : BASE.offset(-2, 0, -1);
        site = cpus == ConsumerCpus.HERE ? null : OtherDimensionSite.nether(helper, SITE_SIZE);
        frequency = QuantumBridges.randomFrequency(helper);
        assembler = new CraftingNativeSourceFixture(helper, BASE, false, false);
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells();
    }

    /** Advances the placement one step at a time; true once both networks and every provider are ready. */
    public boolean ready() {
        if (!bridge.networksSettled() || !assembler.advanceInitialPlacement(providerNetwork())) return false;
        if (!processingPlaced) {
            // It pushes only north, into the machine, and joins the provider network through its other sides.
            helper.setBlock(processingProviderPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState().setValue(
                    appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION, appeng.block.crafting.PushDirection.NORTH));
            helper.setBlock(machinePos, Blocks.CHEST);
            processingProvider().getMainNode().loadFromNBT(
                    NetworkIdentityNodeSeed.managedNode("proxy", providerNetwork()));
            processingPlaced = true;
            return false;
        }
        if (!consumerCpuPlaced) {
            if (site != null && !site.ready()) return false;
            if (cpus != ConsumerCpus.NETHER) {
                helper.setBlock(consumerCpuPos, AEBlocks.CRAFTING_STORAGE_1K.block());
                helper.<CraftingBlockEntity>getBlockEntity(consumerCpuPos).getMainNode()
                        .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", consumerNetwork()));
            }
            if (site != null) placeNetherCpu();
            consumerCpuPlaced = true;
            return false;
        }
        if (!bridgePlaced) {
            bridge.placeFirstBridge();
            bridgePlaced = true;
            return false;
        }
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        if (processingProvider().getMainNode().getGrid() != providerGrid()) return false;
        if (!processingPatternInstalled) {
            processingProvider().getLogic().getPatternInv().addItems(stonePattern());
            processingProvider().getLogic().updatePatterns();
            processingPatternInstalled = true;
        }
        return assembler.initialReady(providerGrid()) && providerGrid().getCraftingService().isCraftable(stone())
                && consumerService().getCpus().size() == (cpus == ConsumerCpus.BOTH ? 2 : 1)
                && FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                && FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).isPresent();
    }

    /**
     * The consumer network's id, read once it is confirmed and kept: while part of a Grid is unloaded its identity
     * may not be confirmed, but the network is the same.
     */
    public NetworkId consumerNetwork() {
        if (consumerId == null) consumerId = bridge.mainNetwork();
        return consumerId;
    }

    public NetworkId providerNetwork() {
        if (providerId == null) providerId = bridge.outerNetwork();
        return providerId;
    }

    public PolicyKey crafting() {
        return new PolicyKey(consumerNetwork(), providerNetwork(), PolicyCapability.CRAFTING);
    }

    public PolicyKey storage() {
        return new PolicyKey(consumerNetwork(), providerNetwork(), PolicyCapability.STORAGE);
    }

    /** The provider network using the consumer's storage, the reverse of {@link #storage()}. */
    public PolicyKey reverseStorage() {
        return new PolicyKey(providerNetwork(), consumerNetwork(), PolicyCapability.STORAGE);
    }

    /** The consumer uses the provider's crafting, with the storage rule crafting needs, in one edit. */
    public void enableRules() {
        var policies = PolicyService.get(helper.getLevel());
        accepted(policies.editAll(List.of(
                new PolicyEdit(storage(), policies.revision(storage()), PolicyRule.storageDefaults()),
                new PolicyEdit(crafting(), policies.revision(crafting()),
                        PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))))));
    }

    /** Sets one rule's mode directly through the policy service, as an API caller can without the pair editor. */
    public void setRule(PolicyKey key, RuleMode mode) {
        var policies = PolicyService.get(helper.getLevel());
        var rule = policies.configured(key).map(record -> record.rule()).orElseGet(() -> key.capability()
                == PolicyCapability.STORAGE ? PolicyRule.storageDefaults()
                : PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
        accepted(policies.edit(new PolicyEdit(key, policies.revision(key), rule.withMode(mode))));
    }

    /** Removes the Bridge, so the two networks no longer share a Federation Domain; both stay loaded. */
    public void disconnect() {
        bridge.removeFirstBridge();
    }

    /** Places the Bridge again, so the two networks share a Federation Domain once more. */
    public void reconnect() {
        bridge.placeFirstBridge();
    }

    public boolean connected() {
        return bridge.firstBridgeReady();
    }

    public int projections() {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(crafting());
    }

    public java.util.Optional<CraftingProjectionService.Status> status() {
        return CraftingProjectionService.status(helper.getLevel(), crafting());
    }

    public long ledgerSweeps() {
        return CraftingProjectionService.get(helper.getLevel()).ledgerSweeps();
    }

    public boolean providerRouted() {
        return CraftingProjectionService.get(helper.getLevel()).routes(providerGrid());
    }

    public long owed(AEKey key) {
        return CraftingReturnLedger.get(helper.getLevel()).owed(providerNetwork(), consumerNetwork(), key);
    }

    /** What the ledger says of {@code key}: owed, held, transit stock, and the consumer's watched jobs. */
    public String returns(AEKey key) {
        var ledger = CraftingReturnLedger.get(helper.getLevel());
        return "owed " + owed(key) + ", held " + heldForConsumer(key) + ", transit "
                + ledger.transit(providerNetwork(), key) + ", jobs " + ledger.jobs(consumerNetwork())
                + ", on the provider network " + onProviderNetwork(key) + " (its chest " + held(providerChest(), key)
                + "), in the consumer's chest " + held(consumerChest(), key) + ", consumer CPUs " + visibleConsumerCpus()
                + " (busy " + busyConsumerCpus() + ")";
    }

    /** The consumer's jobs the ledger watches. */
    public int watchedJobs() {
        return CraftingReturnLedger.get(helper.getLevel()).jobs(consumerNetwork()).size();
    }

    /** What the provider network's transit stock holds of {@code key}. */
    public long transit(AEKey key) {
        return CraftingReturnLedger.get(helper.getLevel()).transit(providerNetwork(), key);
    }

    public long heldForConsumer(AEKey key) {
        return CraftingReturnLedger.get(helper.getLevel()).held(providerNetwork(), consumerNetwork(), key);
    }

    /** Plans {@code amount} of {@code what} on the consumer's own crafting service, as its ME Terminal does. */
    public void begin(AEKey what, long amount) {
        var node = bridge.consumerChest().getMainNode().getNode();
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public appeng.api.networking.IGridNode getGridNode() {
                return node;
            }
        };
        plan = null;
        planFuture = consumerService().beginCraftingCalculation(helper.getLevel(), requester, what, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public boolean planReady() {
        if (plan != null) return true;
        if (planFuture == null || !planFuture.isDone()) return false;
        try {
            plan = planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Crafting calculation failed", exception);
        }
    }

    public ICraftingPlan plan() {
        return plan;
    }

    public boolean submit() {
        return consumerService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public long busyConsumerCpus() {
        return consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    public void cancelConsumerJob() {
        consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).map(CraftingCPUCluster.class::cast)
                .forEach(CraftingCPUCluster::cancelJob);
    }

    /** What the hand-run machine holds: the inputs the processing provider pushed into it. */
    public long inMachine(net.minecraft.world.item.Item item) {
        var chest = helper.<ChestBlockEntity>getBlockEntity(machinePos);
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    /** Runs the machine: empties it, and puts {@code outputs} stone into the provider network as its output. */
    public long runMachine(long outputs) {
        var chest = helper.<ChestBlockEntity>getBlockEntity(machinePos);
        chest.clearContent();
        return providerGrid().getStorageService().getInventory().insert(stone(), outputs, Actionable.MODULATE,
                IActionSource.empty());
    }

    public void putInConsumer(AEKey what, long amount) {
        helper.assertValueEqual(consumerChest().insert(what, amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "The consumer's chest must take the materials");
    }

    public void putInProvider(AEKey what, long amount) {
        helper.assertValueEqual(providerChest().insert(what, amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "The provider's chest must take the materials");
    }

    /** What the consumer's own ME chest holds. */
    public MEStorage consumerChest() {
        return Objects.requireNonNull(bridge.consumerChest().getOriginalCellInventory(0));
    }

    /** What the provider's own ME chest holds. */
    public MEStorage providerChest() {
        return Objects.requireNonNull(bridge.providerChest().getOriginalCellInventory(0));
    }

    /**
     * Mounts {@code storage} on the provider network as an own inventory at {@code priority}, as a storage bus would.
     *
     * @return what unmounts it again
     */
    public Runnable mountOnProvider(MEStorage storage, int priority) {
        var service = providerGrid().getStorageService();
        IStorageProvider provider = mounts -> mounts.mount(storage, priority);
        service.addGlobalStorageProvider(provider);
        return () -> service.removeGlobalStorageProvider(provider);
    }

    /** Takes the cell out of the provider's ME chest, which then stores nothing; the cell is returned. */
    public ItemStack takeProviderCell() {
        var chest = bridge.providerChest();
        var cell = chest.getCell().copy();
        chest.setCell(ItemStack.EMPTY);
        return cell;
    }

    public void restoreProviderCell(ItemStack cell) {
        bridge.providerChest().setCell(cell);
    }

    public long held(MEStorage storage, AEKey what) {
        return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public ICraftingService consumerService() {
        return consumerGrid().getCraftingService();
    }

    public IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    /**
     * Unloads and reloads every block of both networks in one tick, as a server restart does, so AE2 builds both Grids
     * anew from the saved data, the consumer's running job included.
     */
    public void reloadAll() {
        BlockEntityReload.reload(helper, BASE.offset(site == null ? -1 : -5, -1, -3), BASE.offset(3, 1, 3));
        bridge.refreshFirstBridgePart();
    }

    /**
     * True once both networks stand again after {@link #reloadAll} and the Bridge links them, whether or not the
     * consumer's CPU is loaded.
     */
    public boolean reloadedWithoutConsumerCpu() {
        if (!bridge.networksSettled()) return false;
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        return processingProvider().getMainNode().getGrid() == providerGrid();
    }

    /** True once both networks stand again after {@link #reloadAll} and the Bridge links them. */
    public boolean reloaded() {
        if (!bridge.networksSettled()) return false;
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        return processingProvider().getMainNode().getGrid() == providerGrid()
                && consumerService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    public static AEItemKey planks() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey sticks() {
        return AEItemKey.of(Items.STICK);
    }

    public static AEItemKey cobblestone() {
        return AEItemKey.of(Items.COBBLESTONE);
    }

    public static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    private PatternProviderBlockEntity processingProvider() {
        return helper.getBlockEntity(processingProviderPos);
    }

    /**
     * Puts the container-item patterns on the provider network: sugar from a honey bottle (which leaves its glass
     * bottle) and fluix glass cable from a white one and {@code #ae2:can_remove_color} (a water bucket, which leaves
     * its bucket, its water as a fluid, or a snowball) on the assembler's provider, and filling a glass bottle with
     * honey on the hand-run machine.
     */
    public void installContainerPatterns() {
        var crafting = assembler.provider().getLogic();
        crafting.getPatternInv().addItems(sugarPattern());
        crafting.getPatternInv().addItems(cleanCablePattern());
        crafting.updatePatterns();
        var processing = processingProvider().getLogic();
        processing.getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(glassBottle(), 1)), List.of(new GenericStack(honeyBottle(), 1))));
        processing.updatePatterns();
    }

    public ItemStack sugarPattern() {
        return craftingPattern(false, false, new ItemStack(Items.HONEY_BOTTLE));
    }

    public ItemStack cleanCablePattern() {
        return craftingPattern(true, true, AEParts.GLASS_CABLE.stack(AEColor.WHITE), new ItemStack(Items.WATER_BUCKET));
    }

    private ItemStack craftingPattern(boolean substitutes, boolean fluidSubstitutes, ItemStack... grid) {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int slot = 0; slot < grid.length; slot++) items.set(slot, grid[slot]);
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), substitutes, fluidSubstitutes);
    }

    /** The crafting service's providers for {@code details} on the consumer: its own and the projected ones. */
    public List<ICraftingProvider> consumerProviders(IPatternDetails details) {
        var providers = new java.util.ArrayList<ICraftingProvider>();
        ((CraftingService) consumerService()).getProviders(details).forEach(providers::add);
        return providers;
    }

    /** Runs the bottling machine: every glass bottle in it comes back to the provider network as a honey bottle. */
    public long runBottleMachine() {
        var chest = helper.<ChestBlockEntity>getBlockEntity(machinePos);
        long bottles = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(Items.GLASS_BOTTLE)) {
                bottles += chest.getItem(slot).getCount();
                chest.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (bottles == 0) return 0;
        return providerGrid().getStorageService().getInventory().insert(honeyBottle(), bottles, Actionable.MODULATE,
                IActionSource.empty());
    }

    /** The consumer's only CPU. */
    public CraftingCPUCluster consumerCpu() {
        return consumerService().getCpus().stream().map(CraftingCPUCluster.class::cast).findFirst().orElseThrow();
    }

    /** Whether the consumer's CPU has no job and holds nothing. */
    public boolean consumerCpuEmpty() {
        var cpu = consumerCpu();
        return !cpu.isBusy() && cpu.craftingLogic.getInventory().list.isEmpty();
    }

    /** What the provider network's storage holds of {@code what}, its own and what it sees. */
    public long onProviderNetwork(AEKey what) {
        return held(providerGrid().getStorageService().getInventory(), what);
    }

    public static AEItemKey honeyBottle() {
        return AEItemKey.of(Items.HONEY_BOTTLE);
    }

    public static AEItemKey glassBottle() {
        return AEItemKey.of(Items.GLASS_BOTTLE);
    }

    public static AEItemKey sugar() {
        return AEItemKey.of(Items.SUGAR);
    }

    public static AEItemKey bucket() {
        return AEItemKey.of(Items.BUCKET);
    }

    public static AEItemKey waterBucket() {
        return AEItemKey.of(Items.WATER_BUCKET);
    }

    public static AEItemKey snowball() {
        return AEItemKey.of(Items.SNOWBALL);
    }

    public static AEItemKey whiteGlassCable() {
        return AEItemKey.of(AEParts.GLASS_CABLE.stack(AEColor.WHITE));
    }

    public static AEItemKey fluixGlassCable() {
        return AEItemKey.of(AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT));
    }

    private static ItemStack stonePattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(cobblestone(), 1)),
                List.of(new GenericStack(stone(), 1)));
    }

    private static void accepted(PolicyMutationResult result) {
        if (!(result instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("Rule edit refused: " + result);
        }
    }

    /**
     * Runs the consumer network's red cable west and south from the Bridge to a Quantum Bridge, whose twin in the
     * nether site has its own energy cell and the consumer's CPU. Every node carries the consumer's identity, so the
     * link merges no second one
     * (cables join as AE2 places them, within their own cable bus).
     */
    private void placeNetherCpu() {
        var main = consumerNetwork();
        for (var cable : new BlockPos[] {BASE.west(), BASE.west(2), BASE.offset(-2, 0, 1), BASE.offset(-2, 0, 2)}) {
            appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.RED));
        }
        QuantumBridges.build(OVERWORLD_CHAMBER, frequency, main, (position, state) -> {
            helper.setBlock(position, state);
            return helper.getLevel().getBlockEntity(helper.absolutePos(position));
        });
        QuantumBridges.build(NETHER_CHAMBER, frequency, main, (position, state) -> {
            site.setBlock(position, state);
            return site.getBlockEntity(position);
        });
        site.setBlock(NETHER_CELL, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        QuantumBridges.seed(site.getBlockEntity(NETHER_CELL), main);
        site.setBlock(NETHER_CPU, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
        QuantumBridges.seed(site.getBlockEntity(NETHER_CPU), main);
    }

    public OtherDimensionSite site() {
        return Objects.requireNonNull(site, "This fixture has no nether site");
    }

    /** The CPU in the nether site, while its chunk is loaded and it has formed; null otherwise. */
    public @Nullable CraftingCPUCluster netherCpu() {
        var level = site().level();
        var position = site().absolute(NETHER_CPU);
        if (!level.isLoaded(position) || !(level.getBlockEntity(position) instanceof CraftingBlockEntity cpu)) return null;
        return cpu.getCluster();
    }

    /** The consumer's CPU in the overworld. */
    public CraftingCPUCluster overworldCpu() {
        return Objects.requireNonNull(helper.<CraftingBlockEntity>getBlockEntity(consumerCpuPos).getCluster());
    }

    /** Submits the plan to {@code cpu} of the consumer, as a player choosing it in the terminal does. */
    public boolean submitOn(appeng.api.networking.crafting.ICraftingCPU cpu) {
        return consumerService().submitJob(plan, null, cpu, true, IActionSource.empty()).successful();
    }

    /** The CPUs the consumer's crafting service lists now. */
    public int visibleConsumerCpus() {
        return consumerService().getCpus().size();
    }

    @Override
    public void close() {
        if (site != null) site.close();
        bridge.close();
    }
}
