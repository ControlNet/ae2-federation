package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.p2p.P2PTunnels;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/** Disposable plain, fixed federated, or seeded factories using the same patterns and accounting. */
final class PlaythroughFactory implements AutoCloseable {
    record Result(boolean pass, int ticks, String invariant, String reason, Map<String, Long> usedItems,
                  Map<String, Long> extracted, long arrived, Map<String, String> invariants,
                  Map<String, Long> storedByNetwork, long expectedAmount, long planBytes, long totalCrafts,
                  int assemblerParallelism, int tickBudget, long cpuBytes,
                  Map<String, Map<String, Long>> expectedInventories, Map<String, Map<String, Long>> inventories,
                  Map<String, PlaythroughAccounting.Signature> signatures, Map<String, List<String>> ledger,
                  Map<String, Object> cleanup) {
        Result failure(String failedInvariant, String detail) {
            var checks = new java.util.LinkedHashMap<>(invariants);
            checks.put(failedInvariant, detail);
            return new Result(false, ticks, failedInvariant, detail, usedItems, extracted, arrived, checks, storedByNetwork,
                    expectedAmount, planBytes, totalCrafts, assemblerParallelism, tickBudget, cpuBytes,
                    expectedInventories, inventories, signatures, ledger, cleanup);
        }
        static Result unexecuted(String invariant, String reason) {
            return new Result(false, 0, invariant, reason, Map.of(), Map.of(), 0, Map.of("execution", "NOT_CHECKED"),
                    Map.of(), 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
    private static final BlockPos CENTER = new BlockPos(6, 5, 10);
    private static final List<Direction> FACES = List.of(Direction.WEST, Direction.EAST, Direction.NORTH);
    private final GameTestHelper helper;
    private PlaythroughTopology.Layout topology;
    private String perturbation = "none";
    private int perturbedAt;
    private boolean restored;
    private P2PTunnels tunnels;
    private FederationP2PTunnelPart tunnelInput;
    private FederationP2PTunnelPart tunnelOutput;
    private boolean paired;
    private final Map<String, Object> perturbationEvidence = new TreeMap<>();
    private final Map<BlockPos, Direction> providerNetworks = new HashMap<>();
    private final Map<PolicyKey, PolicyRule> interruptedRules = new HashMap<>();

    private BlockPos center() { return topology == null ? CENTER : new BlockPos(12, 12, 14); }
    private int distance() { return topology == null ? 1 : 5; }
    private BlockPos chestPos(Direction face) { return center().relative(face, distance()); }
    private List<Direction> faces() {
        return topology == null ? FACES : List.of(Direction.WEST, Direction.EAST, Direction.NORTH,
                Direction.SOUTH, Direction.UP, Direction.DOWN).subList(0, Math.max(3, topology.networks()));
    }
    private Direction powerFace() { return topology == null ? Direction.WEST : Direction.valueOf(topology.power()); }

    private final boolean federated;
    private final PlaythroughRecipes.Proof proof;
    private final AEItemKey target;
    private final long amount;
    private int budget;
    private final int baseBudget;
    private final int ticksPerCraft = Integer.getInteger("ae2federation.playthrough.ticksPerCraft", 20);
    private int copies;
    private int patternGroups;
    private long totalCrafts;
    private long expectedAmount;
    private long cpuBytes;
    private int cpuChangedTick;
    private final RouterFixtures routers;
    private final Set<BlockPos> placed = new LinkedHashSet<>();
    private final List<BlockPos> providers = new ArrayList<>();
    private final List<BlockPos> assemblers = new ArrayList<>();
    private final Set<PolicyKey> policies = new LinkedHashSet<>();
    private final PlaythroughMachines machines;
    private PlaythroughSource source;
    private Future<ICraftingPlan> future;
    private ICraftingPlan plan;
    private int ticks;
    private int stage;
    private int readyTick;
    private int idleSince;
    private Result result;
    private final Set<NetworkId> networks = new LinkedHashSet<>();
    private final Map<String, PlaythroughAccounting.Signature> signatures = new TreeMap<>();
    private final Map<String, Object> cleanup = new TreeMap<>();
    private Map<String, Long> expectedStock = Map.of();
    private boolean closed;

    PlaythroughFactory(GameTestHelper helper, boolean federated, PlaythroughRecipes.Proof proof,
                       AEItemKey target, long amount, int budget) {
        this.helper = helper; this.federated = federated; this.proof = proof;
        this.target = target; this.amount = amount; this.budget = budget; this.baseBudget = budget;
        int requestedCopies = Integer.getInteger("ae2federation.playthrough.assemblerCopies", 28);
        int groups = Math.max(1, (proof.craftingPatterns().size() + 8) / 9);
        patternGroups = groups;
        if (groups < 1 || groups > 28) throw new IllegalArgumentException("Factory requires 1 to 252 patterns: " + proof.patterns().size());
        copies = Math.min(requestedCopies, 28 / groups);
        if (ticksPerCraft < 1 || copies < 1 || requestedCopies > 28) throw new IllegalArgumentException("Invalid craft budget or assembler copies");
        routers = new RouterFixtures(helper);
        machines = new PlaythroughMachines(helper, placed, federated);
    }

    PlaythroughFactory(GameTestHelper helper, PlaythroughTopology.Layout layout, String perturbation,
                       PlaythroughRecipes.Proof proof, AEItemKey target, long amount, int budget) {
        this(helper, true, proof, target, amount, budget);
        this.topology = layout;
        this.perturbation = perturbation;
        if (layout.splitPatterns() && proof.patterns().size() > 1) {
            patternGroups = 2 * ((proof.patterns().size() + 17) / 18);
            copies = Math.min(Integer.getInteger("ae2federation.playthrough.assemblerCopies", 28), 28 / patternGroups);
            if (copies < 1) throw new IllegalArgumentException("Split factory exceeds 28 providers");
        }
    }

    private void initializeTopology() {
        place(center(), RouterRegistration.ROUTER.get());
        for (var face : faces()) {
            for (int i = 1; i <= 3; i++) place(center().relative(face, i),
                    i == 2 && topology.join().equals("multi-hop")
                            ? RouterRegistration.ROUTER.get()
                            : RouterRegistration.FEDERATION_CABLE.get());
            place(center().relative(face, 4), RouterRegistration.SWITCH.get());
            if (topology.extraCpus() && !FACES.contains(face))
                place(face == Direction.SOUTH ? chestPos(face).west() : chestPos(face).relative(face), AEBlocks.CRAFTING_STORAGE_256K.block());
        }
        if (topology.join().equals("beam")) {
            helper.setBlock(center().west(2), Blocks.AIR);
            for (var face : List.of(Direction.WEST, Direction.EAST)) {
                var pos = center().west(face == Direction.WEST ? 1 : 3);
                helper.setBlock(pos, BeamRegistration.BEAM_FORMER.get()
                        .defaultBlockState().setValue(BlockStateProperties.FACING, face));
            }
        }
        if (topology.join().equals("p2p")) {
            var bus = center().west(2);
            helper.setBlock(bus, Blocks.AIR); nativeCable(bus);
            nativeCable(bus.above()); place(bus.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
            tunnels = new P2PTunnels(helper.makeMockPlayer(GameType.CREATIVE));
            tunnelInput = tunnels.attune(helper.getLevel(), helper.absolutePos(bus), Direction.EAST);
            tunnelOutput = tunnels.attune(helper.getLevel(), helper.absolutePos(bus), Direction.WEST);
            if (tunnelInput == null || tunnelOutput == null) throw new IllegalStateException("Federation P2P attunement failed");
        }
        if (topology.join().startsWith("bundle-")) {
            int width = topology.join().equals("bundle-2x2") ? 2 : 3;
            for (int y = 0; y < width; y++) for (int z = 0; z < width; z++) {
                for (int x = 2; x <= 4; x++) {
                    var pos = center().west(x).offset(0, y, z);
                    placed.add(pos);
                    helper.setBlock(pos, x == 2 ? RouterRegistration.ROUTER.get()
                            : x == 4 ? RouterRegistration.SWITCH.get()
                            : RouterRegistration.FEDERATION_CABLE.get());
                }
            }
            var start = helper.absolutePos(center().west(3));
            BundledCables.get(helper.getLevel()).form(helper.getLevel(),
                    new BundleSegment(start, start.offset(0, width - 1, width - 1), Direction.Axis.X));
        }
        if (!topology.capacity().equals("normal")) {
            int limit = CableCapacityLimit.current().orElseThrow();
            if (limit != 8) throw new IllegalStateException("Boundary fixture requires the branch default cable capacity of 8, actual " + limit);
            if (topology.capacity().equals("over-limit")) for (var face : faces())
                place(chestPos(face).below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            int endpoints = limit - topology.networks() + (topology.capacity().equals("over-limit") ? 1 : 0);
            // Each real Endpoint contributes one unit. Attach to the west subdomain and the global trunk.
            for (int i = 0; i < endpoints; i++) {
                var side = i % 2 == 0 ? Direction.NORTH : Direction.SOUTH;
                var pos = center().west(1 + i / 2).relative(side);
                placed.add(pos);
                helper.setBlock(pos, ProcessingRegistration.ENDPOINT.get()
                        .defaultBlockState().setValue(BlockStateProperties.FACING, side.getOpposite()));
            }
        }
        if (topology.networks() == 2) {
            // A physical native cable joins materials to requester around the outside of the Federation arms.
            for (int z = 1; z <= 5; z++) nativeCable(chestPos(Direction.WEST).north(z));
            for (int x = 1; x < 5; x++) nativeCable(chestPos(Direction.WEST).north(5).east(x));
        }
    }

    private void nativeCable(BlockPos pos) { placed.add(pos); routers.nativePorts().placeCable(pos); }

    private void perturb() {
        if (perturbation.equals("none")) return;
        if (perturbedAt == 0) {
            var ledger = PlaythroughAccounting.ledger(helper.getLevel(), networks);
            if (ledger.get("owed").isEmpty() || grid(Direction.NORTH).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy())) return;
            perturbedAt = ticks;
            perturbationEvidence.put("firstPushTick", ticks);
            perturbationEvidence.put("before", ledger);
            if (perturbation.equals("cable") || perturbation.equals("router")) {
                helper.setBlock(perturbation.equals("router") ? center() : center().east(), Blocks.AIR);
            } else {
                var capability = PolicyCapability.valueOf(perturbation);
                var service = PolicyService.get(helper.getLevel());
                for (var key : policies) if (key.capability() == capability) {
                    var saved = service.configured(key).orElseThrow().rule();
                    interruptedRules.put(key, saved);
                    editRule(key, saved.withEnabled(false));
                }
                if (interruptedRules.isEmpty()) throw new IllegalStateException("No rule selected for perturbation");
            }
        } else if (!restored && ticks - perturbedAt >= 40) {
            perturbationEvidence.put("during", PlaythroughAccounting.ledger(helper.getLevel(), networks));
            if (perturbation.equals("router")) helper.setBlock(center(), RouterRegistration.ROUTER.get());
            else if (perturbation.equals("cable")) helper.setBlock(center().east(), RouterRegistration.FEDERATION_CABLE.get());
            else interruptedRules.forEach(this::editRule);
            restored = true;
            perturbationEvidence.put("restoredTick", ticks);
        }
    }

    private void editRule(PolicyKey key, PolicyRule rule) {
        var service = PolicyService.get(helper.getLevel());
        if (!(service.edit(new PolicyEdit(key, service.revision(key), rule)) instanceof PolicyMutationResult.Accepted))
            throw new IllegalStateException("Perturbation rule refused: " + key);
    }

    void initialize() {
        for (var face : faces()) {
            placed.add(chestPos(face));
            routers.nativePorts().placeChest(chestPos(face));
            chest(face).setCell(AEItems.ITEM_CELL_256K.stack());
        }
        chest(Direction.NORTH).setPriority(1000);
        place(chestPos(powerFace()).relative(powerFace() == Direction.DOWN ? Direction.WEST : Direction.DOWN), AEBlocks.CREATIVE_ENERGY_CELL.block());
        place(center().north(distance() + 1), AEBlocks.CRAFTING_STORAGE_256K.block());
        int count = patternGroups * copies;
        if (count > 28) throw new IllegalStateException("Factory template supports at most 252 patterns, needed " + proof.patterns().size());
        var sides = List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
        var providerCounts = new java.util.EnumMap<Direction, Integer>(Direction.class);
        for (int i = 0; i < count; i++) {
            var factoryFace = topology != null && topology.splitPatterns() && i % 2 == 1 ? Direction.SOUTH : Direction.EAST;
            int localIndex = providerCounts.merge(factoryFace, 1, Integer::sum) - 1;
            var controller = center().relative(factoryFace, distance() + 1 + localIndex / 4);
            var assemblySides = factoryFace == Direction.EAST ? sides : List.of(Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST);
            int sideIndex = localIndex % 4;
            place(controller, AEBlocks.CONTROLLER.block());
            var pos = controller.relative(assemblySides.get(sideIndex));
            placed.add(pos); placed.add(pos.relative(assemblySides.get(sideIndex)));
            RouterCraftingScene.placeAssemblyProvider(helper, pos, assemblySides.get(sideIndex));
            var assembler = helper.<MolecularAssemblerBlockEntity>getBlockEntity(pos.relative(assemblySides.get(sideIndex)));
            if (!assembler.getUpgrades().addItems(AEItems.SPEED_CARD.stack(5)).isEmpty()) {
                throw new IllegalStateException("Assembler refused its five acceleration cards");
            }
            providerNetworks.put(pos, factoryFace);
            providers.add(pos); assemblers.add(pos.relative(assemblySides.get(sideIndex)));
            placed.add(pos); placed.add(pos.relative(assemblySides.get(sideIndex)));
        }
        machines.initialize(proof, chestPos(Direction.EAST), chestPos(Direction.NORTH));
        if (topology != null) initializeTopology();
        if (!federated) {
            placed.add(CENTER);
            routers.nativePorts().placeCable(CENTER);
        }
    }

    private void place(BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (placed.add(pos)) helper.setBlock(pos, block);
    }

    Result tick() throws Exception {
        if (result != null) return result;
        ticks++;
        for (var face : faces()) if (grid(face) != null) {
            FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).ifPresent(networks::add);
        }
        if (stage < 3 && ticks > 400) {
            if (topology == null) throw new IllegalStateException("Factory did not build: " + diagnostic());
            return result = Result.unexecuted("layout-ready", "Seed " + topology.seed() + ", stage " + stage + ", dimensions " + topology.dimensions());
        }
        if (stage >= 3 && ticks - readyTick > budget) {
            return finish(false, stage == 3 ? "pattern-visible" : stage == 4 ? "plan-ready" : "job-complete", diagnostic());
        }
        switch (stage) {
            case 0 -> {
                if (tunnels != null && !paired) {
                    if (!P2PTunnels.online(tunnelInput, tunnelOutput)) return null;
                    paired = tunnels.pair(tunnelInput, List.of(tunnelOutput));
                    if (!paired) throw new IllegalStateException("Federation P2P memory-card pairing failed");
                    FederationDomainRegistryAccess.confirmedNetworkId(tunnelInput.getGridNode().getGrid()).ifPresent(networks::add);
                }
                if (faces().stream().anyMatch(face -> grid(face) == null
                        || FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).isEmpty())) return null;
                faces().forEach(face -> networks.add(network(face)));
                if (federated) {
                    if (faces().stream().map(this::grid).distinct().count() != (topology == null ? 3 : topology.networks())) throw new IllegalStateException("Federated grids merged");
                    if (topology == null) { placed.add(CENTER); routers.placeSwitch(CENTER); }
                } else if (faces().stream().map(this::grid).distinct().count() != 1) return null;
                stage = 1;
            }
            case 1 -> {
                if (federated) {
                    if (topology == null && faces().stream().anyMatch(face -> !(routers.router(CENTER).binding(face) instanceof RouterPortBinding.Native))) return null;
                    var registry = FederationDomainRegistryAccess.get(helper.getLevel());
                    var domains = new java.util.HashSet<>(registry.federationdomainsFor(network(Direction.WEST)));
                    for (var face : faces()) domains.retainAll(registry.federationdomainsFor(network(face)));
                    if (domains.isEmpty()) return null;
                    for (var face : faces()) if (!network(face).equals(network(powerFace()))) rule(face, powerFace(),
                            PolicyCapability.ME_POWER, PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                    rule(Direction.NORTH, Direction.EAST, PolicyCapability.CRAFTING, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                    rule(Direction.NORTH, Direction.EAST, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    if (!network(Direction.NORTH).equals(network(Direction.WEST)))
                        rule(Direction.NORTH, Direction.WEST, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    for (var face : faces()) if (!FACES.contains(face)) {
                        rule(Direction.NORTH, face, PolicyCapability.CRAFTING, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                        rule(Direction.NORTH, face, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    }
                }
                source = new PlaythroughSource(helper.getLevel(), helper.absolutePos(chestPos(Direction.WEST)),
                        chest(Direction.WEST).getMainNode().getNode(), proof.supplies());
                stage = 2;
            }
            case 2 -> {
                if (topology != null && !topology.capacity().equals("normal")) {
                    var demand = CableCapacity.demand(helper.getLevel(), helper.absolutePos(center().west()));
                    if (demand.isEmpty()) return null;
                    cleanup.put("capacityDemand", demand.get());
                    int expected = topology.capacity().equals("over-limit") ? 9 : 8;
                    if (demand.get().units() != expected) return null;
                    if (ticks < 80) return null;
                }
                if (faces().stream().anyMatch(face -> !grid(face).getEnergyService().isNetworkPowered())
                        || grid(Direction.NORTH).getCraftingService().getCpus().isEmpty()) return null;
                for (var pos : providers) {
                    var host = (PatternProviderLogicHost) helper.getBlockEntity(pos);
                    if (host.getLogic().getGrid() != grid(providerNetworks.get(pos))) return null;
                }
                if (!machines.install(proof, networks)) return null;
                for (int copy = 0; copy < copies; copy++) {
                    int index = 0;
                    for (var pattern : proof.craftingPatterns().values()) {
                        boolean split = topology != null && topology.splitPatterns() && proof.patterns().size() > 1;
                        int group = split ? index / 18 * 2 + index % 2 : index / 9;
                        int slot = split ? index / 2 % 9 : index % 9;
                        var host = (PatternProviderLogicHost) helper.getBlockEntity(providers.get(copy * patternGroups + group));
                        host.getLogic().getPatternInv().setItemDirect(slot, pattern.copy());
                        index++;
                    }
                }
                for (var pos : providers) ((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic().updatePatterns();
                String mismatch = checkSignatures();
                if (!mismatch.isEmpty()) return finish(false, "recipe-signature", mismatch);
                stage = 3; readyTick = ticks;
            }
            case 3 -> {
                if (topology != null && topology.capacity().equals("over-limit")) {
                    if (ticks - readyTick < 40) return null;
                    return refusal();
                }
                var service = grid(Direction.NORTH).getCraftingService();
                if (!service.isCraftable(target)) return null;
                for (var pattern : proof.patterns().values()) {
                    var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
                    if (details.getOutputs().stream().anyMatch(output -> !service.isCraftable(output.what()))) return null;
                }
                var node = chest(Direction.NORTH).getMainNode().getNode();
                future = service.beginCraftingCalculation(helper.getLevel(), new ICraftingSimulationRequester() {
                    @Override public IActionSource getActionSource() { return IActionSource.empty(); }
                    @Override public appeng.api.networking.IGridNode getGridNode() { return node; }
                }, target, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
                stage = 4;
            }
            case 4 -> {
                if (!future.isDone()) return null;
                plan = future.get();
                if (plan.simulation()) return finish(false, "plan-inputs", "Missing: " + plan.missingItems());
                if (!craftsTarget()) return finish(false, "target-crafted-plan", "The plan does not execute a pattern producing the target");
                sizePlan();
                stage = 6;
            }
            case 6 -> {
                if (ticks - cpuChangedTick < 20) return null;
                var cpu = grid(Direction.NORTH).getCraftingService().getCpus().stream()
                        .filter(candidate -> candidate.getAvailableStorage() >= plan.bytes() && candidate.getCoProcessors() >= copies)
                        .findFirst();
                if (cpu.isEmpty()) {
                    if (ticks - cpuChangedTick > 400) throw new IllegalStateException("Plan-sized CPU did not form: " + diagnostic());
                    return null;
                }
                cpuBytes = cpu.get().getAvailableStorage();
                var submitted = grid(Direction.NORTH).getCraftingService().submitJob(plan, null, cpu.get(), true, IActionSource.empty());
                if (!submitted.successful()) return finish(false, "job-submit", submitted.errorCode() + " " + submitted.errorDetail());
                stage = 5;
            }
            case 5 -> {
                perturb();
                if (perturbedAt != 0 && (!restored || ticks - perturbedAt < 100
                        || faces().stream().anyMatch(face -> !grid(face).getEnergyService().isNetworkPowered())
                        || grid(Direction.NORTH).getCraftingService().getCpus().isEmpty())) { idleSince = 0; return null; }
                if (grid(Direction.NORTH).getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy())) {
                    idleSince = 0; return null;
                }
                if (idleSince == 0) idleSince = ticks;
                // Allow a complete 20-tick return-ledger sweep after the CPU becomes idle.
                if (ticks - idleSince < 25) return null;
                return finish(true, "", "");
            }
            default -> throw new IllegalStateException("Unknown factory stage");
        }
        return null;
    }

    private Result refusal() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var checks = new TreeMap<String, String>();
        checks.put("domain-over-capacity", registry.shareOverCapacityFederationDomain(network(Direction.NORTH), network(Direction.EAST)) ? "PASS" : "Domain is not over capacity");
        checks.put("domain-stopped", !registry.shareRunningFederationDomain(network(Direction.NORTH), network(Direction.EAST)) ? "PASS" : "Domain still runs");
        checks.put("pattern-refused", grid(Direction.EAST).getCraftingService().isCraftable(target)
                && !grid(Direction.NORTH).getCraftingService().isCraftable(target) ? "PASS" : "Local pattern unavailable or leaked through stopped domain");
        checks.put("recipe-signature", checkSignatures().isEmpty() ? "PASS" : checkSignatures());
        checks.put("clean-refusal", source.extracted.isEmpty() && faces().stream().allMatch(face ->
                grid(face).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy())) ? "PASS" : "Unexpected extraction or busy CPU");
        var ledger = PlaythroughAccounting.ledger(helper.getLevel(), networks);
        checks.put("return-ledger", ledger.values().stream().allMatch(List::isEmpty) ? "PASS" : ledger.toString());
        var inventories = new TreeMap<String, Map<String, Long>>();
        for (var face : faces()) inventories.put(face.name(), PlaythroughAccounting.contents(storage(face), helper.getLevel()));
        checks.put("cell-inventories", inventories.values().stream().allMatch(Map::isEmpty) ? "PASS" : inventories.toString());
        String failed = checks.entrySet().stream().filter(entry -> !entry.getValue().equals("PASS")).map(Map.Entry::getKey).findFirst().orElse("");
        result = new Result(failed.isEmpty(), ticks, failed, failed.isEmpty() ? "Clean capacity refusal" : checks.get(failed),
                Map.of(), Map.of(), 0, checks, Map.of(), 0, 0, 0, copies, budget, 0, inventories, inventories, new TreeMap<>(signatures), ledger, cleanup);
        return result;
    }

    private Result finish(boolean pass, String invariant, String reason) {
        var used = new TreeMap<String, Long>();
        if (plan != null) for (var entry : plan.usedItems()) used.put(keyName(entry.getKey()), entry.getLongValue());
        var extracted = new TreeMap<String, Long>();
        if (source != null) source.extracted.forEach((key, value) -> extracted.put(keyName(key), value));
        var checks = new java.util.LinkedHashMap<String, String>();
        if (topology != null) {
            cleanup.put("perturbation", perturbationEvidence);
            checks.put("perturbation", perturbation.equals("none") || perturbedAt > 0 && restored
                    ? "PASS" : "No observed first push while CPU busy, or restoration incomplete");
        }
        long arrived = held(storage(Direction.NORTH), target);
        checks.put("target-crafted-plan", plan == null ? "NOT_CHECKED: plan unavailable" : craftsTarget()
                ? "PASS" : "The plan does not execute a pattern producing the target");
        checks.put("ordered-amount", plan == null ? "NOT_CHECKED: plan unavailable" : arrived == expectedAmount ? "PASS" : "Expected planned net output " + expectedAmount + ", requester holds " + arrived);
        long busy = faces().stream().map(this::grid).distinct().flatMap(grid -> grid.getCraftingService().getCpus().stream())
                .filter(cpu -> cpu.isBusy()).count();
        var cpuCounts = new TreeMap<String, Integer>();
        for (var face : faces()) cpuCounts.put(face.name(), grid(face).getCraftingService().getCpus().size());
        cleanup.put("cpuCounts", cpuCounts);
        var allocation = new TreeMap<String, List<String>>();
        for (var pos : providers) allocation.computeIfAbsent(providerNetworks.get(pos).name(), ignored -> new ArrayList<>())
                .addAll(((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic().getAvailablePatterns().stream()
                        .map(pattern -> pattern.getDefinition().toString()).toList());
        cleanup.put("installedPatternsByNetwork", allocation);
        checks.put("cpu-idle", busy == 0 ? "PASS" : busy + " busy CPUs");
        var providerBuffers = new ArrayList<String>();
        for (var pos : providers) {
            var logic = ((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic();
            if (logic.isBusy() || !logic.getReturnInv().isEmpty()) providerBuffers.add(pos.toString());
        }
        checks.put("provider-buffer", providerBuffers.isEmpty() ? "PASS" : "Leftovers at " + providerBuffers);
        var assemblerBuffers = new ArrayList<String>();
        for (var pos : assemblers) {
            var inv = helper.<MolecularAssemblerBlockEntity>getBlockEntity(pos).getInternalInventory();
            for (int slot = 0; slot < inv.size(); slot++) if (!inv.getStackInSlot(slot).isEmpty()) {
                assemblerBuffers.add(pos + " slot " + slot + ": " + inv.getStackInSlot(slot));
            }
        }
        checks.put("assembler-buffer", assemblerBuffers.isEmpty() ? "PASS" : "Leftovers at " + assemblerBuffers);
        checks.putAll(machines.checks());
        var expected = new HashMap<AEKey, Long>();
        if (plan != null) for (var entry : plan.usedItems()) if (entry.getLongValue() != 0) expected.put(entry.getKey(), entry.getLongValue());
        checks.put("source-extractions", plan == null ? "NOT_CHECKED: plan unavailable" : expected.equals(source.extracted)
                ? "PASS" : "Extraction counts differ from ICraftingPlan.usedItems()");
        var ledger = PlaythroughAccounting.ledger(helper.getLevel(), networks);
        checks.put("return-ledger", ledger.get("owed").isEmpty() ? "PASS" : ledger.get("owed").toString());
        checks.put("return-transit", ledger.get("transit").isEmpty() ? "PASS" : ledger.get("transit").toString());
        var inventories = new TreeMap<String, Map<String, Long>>();
        inventories.put("materials", PlaythroughAccounting.contents(storage(Direction.WEST), helper.getLevel()));
        inventories.put("factory", PlaythroughAccounting.contents(storage(Direction.EAST), helper.getLevel()));
        inventories.put("requester", PlaythroughAccounting.contents(storage(Direction.NORTH), helper.getLevel()));
        var expectedInventories = new TreeMap<String, Map<String, Long>>();
        expectedInventories.put("materials", Map.of()); expectedInventories.put("factory", Map.of()); expectedInventories.put("requester", expectedStock);
        for (var face : faces()) if (!FACES.contains(face)) {
            inventories.put(face.name(), PlaythroughAccounting.contents(storage(face), helper.getLevel()));
            expectedInventories.put(face.name(), Map.of());
        }
        checks.put("cell-inventories", plan == null ? "NOT_CHECKED: plan unavailable" : expectedInventories.equals(inventories)
                ? "PASS" : "Expected " + expectedInventories + ", actual " + inventories);
        String mismatch = checkSignatures();
        checks.put("recipe-signature", mismatch.isEmpty() ? "PASS" : mismatch);
        if (!mismatch.isEmpty()) { pass = false; invariant = "recipe-signature"; reason = mismatch; }
        if (pass) for (var check : checks.entrySet()) if (!check.getValue().equals("PASS")) {
            pass = false; invariant = check.getKey(); reason = check.getValue(); break;
        }
        var stored = new TreeMap<String, Long>();
        stored.put("materials", held(storage(Direction.WEST), target));
        stored.put("factory", held(storage(Direction.EAST), target));
        stored.put("requester", arrived);
        result = new Result(pass, ticks, invariant, reason, used, extracted, arrived, checks, stored, expectedAmount, plan == null ? 0 : plan.bytes(),
                totalCrafts, copies, budget, cpuBytes, expectedInventories, inventories, new TreeMap<>(signatures), ledger, cleanup);
        storage(Direction.NORTH).extract(target, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        return result;
    }

    private void sizePlan() {
        expectedStock = PlaythroughAccounting.expected(plan, helper.getLevel());
        for (var entry : plan.patternTimes().entrySet()) {
            long times = entry.getValue();
            totalCrafts = Math.addExact(totalCrafts, times);
        }
        expectedAmount = expectedStock.getOrDefault(keyName(target), 0L);
        budget = Math.toIntExact(Math.addExact(baseBudget, Math.multiplyExact(ticksPerCraft, (totalCrafts + copies - 1) / copies)));
        if (!proof.machines().isEmpty()) budget = Math.toIntExact(Math.max(budget,
                Math.addExact(baseBudget, PlaythroughMachines.executionTicks(proof, totalCrafts))));
        // A solid rectangular native CPU; storage grows with the plan and the rest are co-processors.
        long storageBlocks = Math.max(1, (plan.bytes() + 262143) / 262144);
        int height = Math.toIntExact((storageBlocks + 32 + 15) / 16);
        if (height > 17) throw new IllegalStateException("Plan exceeds CPU template capacity: " + plan.bytes());
        int index = 0;
        for (int y = 0; y < height; y++) for (int z = 0; z < 8; z++) for (int x = 0; x < 2; x++) {
            var pos = center().north(distance() + 1 + z).west(x).above(y);
            var block = index++ < storageBlocks ? AEBlocks.CRAFTING_STORAGE_256K.block() : AEBlocks.CRAFTING_ACCELERATOR.block();
            placed.add(pos); helper.setBlock(pos, block);
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "AE2F_PLAYTHROUGH_PLAN item={} layout={} bytes={} crafts={} parallelism={} budget={} expected={}",
                target, federated ? "federated" : "baseline", plan.bytes(), totalCrafts, copies, budget, expectedAmount);
        cpuChangedTick = ticks;
        readyTick = ticks;
    }

    private boolean craftsTarget() {
        return plan != null && plan.patternTimes().entrySet().stream().anyMatch(entry -> entry.getValue() > 0
                && entry.getKey().getOutputs().stream().anyMatch(output -> output.what().equals(target)));
    }

    private String keyName(AEKey key) { return PlaythroughAccounting.name(key, helper.getLevel()); }

    private String checkSignatures() {
        var identities = new HashMap<AEItemKey, String>();
        proof.patterns().forEach((id, stack) -> identities.put(AEItemKey.of(stack), id));
        for (var pos : providers) {
            var logic = ((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic();
            for (var details : logic.getAvailablePatterns()) {
                String id = identities.get(details.getDefinition());
                if (id == null) throw new IllegalStateException("Unexpected installed pattern");
                var signature = PlaythroughAccounting.signature(details, helper.getLevel());
                signatures.put(id, signature);
                if (!signature.equals(proof.signatures().get(id))) return id + ": expected " + proof.signatures().get(id) + ", installed " + signature;
                var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(proof.patterns().get(id), helper.getLevel());
                if (decoded == null || !signature.equals(PlaythroughAccounting.signature(decoded, helper.getLevel()))) {
                    return id + ": fresh decoding differs from installed signature " + signature;
                }
            }
        }
        String machineMismatch = machines.signatures(proof, signatures);
        if (!machineMismatch.isEmpty()) return machineMismatch;
        return signatures.keySet().equals(proof.signatures().keySet()) ? "" : "Installed signature set incomplete";
    }

    private String diagnostic() {
        return "stage=" + stage + ", ticks=" + ticks + ", target=" + target + ", requested=" + amount
                + ", planBytes=" + (plan == null ? 0 : plan.bytes()) + ", crafts=" + totalCrafts + ", parallelism=" + copies + ", budget=" + budget
                + ", arrived=" + held(storage(Direction.NORTH), target)
                + ", busy=" + grid(Direction.NORTH).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    private MEChestBlockEntity chest(Direction face) { return helper.getBlockEntity(chestPos(face)); }
    private IGrid grid(Direction face) { return chest(face).getMainNode().getGrid(); }
    private MEStorage storage(Direction face) {
        var storage = java.util.Objects.requireNonNull(chest(face).getOriginalCellInventory(0));
        return face == Direction.NORTH ? machines.storage(storage) : storage;
    }
    private NetworkId network(Direction face) { return FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).orElseThrow(); }
    private static long held(MEStorage storage, AEKey key) { return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()); }

    private void rule(Direction user, Direction source, PolicyCapability capability, PolicyRule rule) {
        var key = new PolicyKey(network(user), network(source), capability);
        var service = PolicyService.get(helper.getLevel());
        if (!(service.edit(new PolicyEdit(key, service.revision(key), rule)) instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("Factory rule refused: " + key);
        }
        policies.add(key);
    }

    Map<String, Object> interruption() {
        var state = new TreeMap<String, Object>();
        state.put("stage", stage); state.put("ticks", ticks);
        state.put("ledger", PlaythroughAccounting.ledger(helper.getLevel(), networks));
        state.put("cleanup", cleanup);
        state.put("inventories", Map.of("materials", PlaythroughAccounting.contents(storage(Direction.WEST), helper.getLevel()),
                "factory", PlaythroughAccounting.contents(storage(Direction.EAST), helper.getLevel()),
                "requester", PlaythroughAccounting.contents(storage(Direction.NORTH), helper.getLevel())));
        return state;
    }

    void verifyClosed() {
        if (networks.stream().anyMatch(CraftingReturnLedger.get(helper.getLevel())::references)) {
            cleanup.put("afterDelay", PlaythroughAccounting.ledger(helper.getLevel(), networks));
            throw new IllegalStateException("Fixture ledger state reappeared after teardown");
        }
        cleanup.put("afterDelay", PlaythroughAccounting.ledger(helper.getLevel(), networks));
        cleanup.put("verifiedAfterDelay", true);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        var failures = new ArrayList<Exception>();
        cleanup.put("networkIds", networks.stream().map(network -> network.value().toString()).sorted().toList());
        attempt(failures, () -> cleanup.put("before", PlaythroughAccounting.ledger(helper.getLevel(), networks)));
        attempt(failures, () -> { if (future != null && !future.isDone()) future.cancel(true); });
        for (var face : faces()) attempt(failures, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(chestPos(face))) instanceof MEChestBlockEntity chest && chest.getMainNode().getGrid() != null) chest.getMainNode().getGrid().getCraftingService().getCpus().forEach(cpu -> { if (cpu.isBusy()) cpu.cancelJob(); });
        });
        attempt(failures, () -> { if (source != null) source.close(); });
        var service = PolicyService.get(helper.getLevel());
        for (var key : policies) attempt(failures, () -> service.configured(key).ifPresent(record -> service.edit(
                new PolicyEdit(key, service.revision(key), record.rule().withEnabled(false)))));
        attempt(failures, routers::close);
        for (var pos : placed) attempt(failures, () -> helper.setBlock(pos, Blocks.AIR));
        attempt(failures, () -> helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(topology == null ? new BlockPos(2, 1, 0) : BlockPos.ZERO),
                        helper.absolutePos(new BlockPos(35, 35, 35)))).forEach(net.minecraft.world.entity.Entity::discard));
        attempt(failures, () -> PlaythroughAccounting.clearLedger(helper.getLevel(), networks));
        attempt(failures, () -> cleanup.put("after", PlaythroughAccounting.ledger(helper.getLevel(), networks)));
        if (!failures.isEmpty()) {
            var failure = new IllegalStateException("Factory teardown failed", failures.getFirst());
            failures.stream().skip(1).forEach(failure::addSuppressed);
            throw failure;
        }
    }

    @FunctionalInterface private interface Cleanup { void run() throws Exception; }
    private static void attempt(List<Exception> failures, Cleanup action) {
        try { action.run(); } catch (Exception exception) { failures.add(exception); }
    }
}
