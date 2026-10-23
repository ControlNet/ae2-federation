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
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/** A disposable plain or three-network factory, driven serially with the same patterns and supplies. */
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
    private final boolean federated;
    private final PlaythroughRecipes.Proof proof;
    private final AEItemKey target;
    private final long amount;
    private int budget;
    private final int baseBudget;
    private final int ticksPerCraft = Integer.getInteger("ae2federation.playthrough.ticksPerCraft", 20);
    private final int copies;
    private long totalCrafts;
    private long expectedAmount;
    private long cpuBytes;
    private int cpuChangedTick;
    private final RouterFixtures routers;
    private final Set<BlockPos> placed = new LinkedHashSet<>();
    private final List<BlockPos> providers = new ArrayList<>();
    private final List<BlockPos> assemblers = new ArrayList<>();
    private final Set<PolicyKey> policies = new LinkedHashSet<>();
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
        int groups = (proof.patterns().size() + 8) / 9;
        if (groups < 1 || groups > 28) throw new IllegalArgumentException("Factory requires 1 to 252 patterns: " + proof.patterns().size());
        copies = Math.min(requestedCopies, 28 / groups);
        if (ticksPerCraft < 1 || copies < 1 || requestedCopies > 28) throw new IllegalArgumentException("Invalid craft budget or assembler copies");
        routers = new RouterFixtures(helper);
    }

    void initialize() {
        for (var face : FACES) {
            placed.add(CENTER.relative(face));
            routers.placeNativeDevice(CENTER, face);
            chest(face).setCell(AEItems.ITEM_CELL_256K.stack());
        }
        chest(Direction.NORTH).setPriority(1000);
        place(CENTER.west().below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        place(CENTER.north(2), AEBlocks.CRAFTING_STORAGE_256K.block());
        int count = (proof.patterns().size() + 8) / 9 * copies;
        if (count > 28) throw new IllegalStateException("Factory template supports at most 252 patterns, needed " + proof.patterns().size());
        var sides = List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
        for (int i = 0; i < count; i++) {
            var controller = CENTER.east(2 + i / 4);
            place(controller, AEBlocks.CONTROLLER.block());
            var pos = controller.relative(sides.get(i % 4));
            placed.add(pos); placed.add(pos.relative(sides.get(i % 4)));
            RouterCraftingScene.placeAssemblyProvider(helper, pos, sides.get(i % 4));
            var assembler = helper.<MolecularAssemblerBlockEntity>getBlockEntity(pos.relative(sides.get(i % 4)));
            if (!assembler.getUpgrades().addItems(AEItems.SPEED_CARD.stack(5)).isEmpty()) {
                throw new IllegalStateException("Assembler refused its five acceleration cards");
            }
            providers.add(pos); assemblers.add(pos.relative(sides.get(i % 4)));
            placed.add(pos); placed.add(pos.relative(sides.get(i % 4)));
        }
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
        for (var face : FACES) if (grid(face) != null) {
            FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).ifPresent(networks::add);
        }
        if (stage < 3 && ticks > 400) throw new IllegalStateException("Factory did not build: " + diagnostic());
        if (stage >= 3 && ticks - readyTick > budget) {
            return finish(false, stage == 3 ? "pattern-visible" : stage == 4 ? "plan-ready" : "job-complete", diagnostic());
        }
        switch (stage) {
            case 0 -> {
                if (FACES.stream().anyMatch(face -> grid(face) == null
                        || FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).isEmpty())) return null;
                FACES.forEach(face -> networks.add(network(face)));
                if (federated) {
                    if (FACES.stream().map(this::grid).distinct().count() != 3) throw new IllegalStateException("Federated grids merged");
                    placed.add(CENTER); routers.placeSwitch(CENTER);
                } else if (FACES.stream().map(this::grid).distinct().count() != 1) return null;
                stage = 1;
            }
            case 1 -> {
                if (federated) {
                    if (FACES.stream().anyMatch(face -> !(routers.router(CENTER).binding(face) instanceof RouterPortBinding.Native))) return null;
                    var registry = FederationDomainRegistryAccess.get(helper.getLevel());
                    var domains = new java.util.HashSet<>(registry.federationdomainsFor(network(Direction.WEST)));
                    for (var face : FACES) domains.retainAll(registry.federationdomainsFor(network(face)));
                    if (domains.isEmpty()) return null;
                    for (var face : List.of(Direction.EAST, Direction.NORTH)) rule(face, Direction.WEST,
                            PolicyCapability.ME_POWER, PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                    rule(Direction.NORTH, Direction.EAST, PolicyCapability.CRAFTING, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                    rule(Direction.NORTH, Direction.EAST, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    rule(Direction.NORTH, Direction.WEST, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                }
                source = new PlaythroughSource(helper.getLevel(), helper.absolutePos(CENTER.west()),
                        chest(Direction.WEST).getMainNode().getNode(), proof.supplies());
                stage = 2;
            }
            case 2 -> {
                if (FACES.stream().anyMatch(face -> !grid(face).getEnergyService().isNetworkPowered())
                        || grid(Direction.NORTH).getCraftingService().getCpus().isEmpty()) return null;
                for (var pos : providers) {
                    var host = (PatternProviderLogicHost) helper.getBlockEntity(pos);
                    if (host.getLogic().getGrid() != grid(Direction.EAST)) return null;
                }
                for (int copy = 0; copy < copies; copy++) {
                    int index = 0;
                    for (var pattern : proof.patterns().values()) {
                        var host = (PatternProviderLogicHost) helper.getBlockEntity(providers.get(copy * (providers.size() / copies) + index / 9));
                        host.getLogic().getPatternInv().setItemDirect(index % 9, pattern.copy());
                        index++;
                    }
                }
                for (var pos : providers) ((PatternProviderLogicHost) helper.getBlockEntity(pos)).getLogic().updatePatterns();
                String mismatch = checkSignatures();
                if (!mismatch.isEmpty()) return finish(false, "recipe-signature", mismatch);
                stage = 3; readyTick = ticks;
            }
            case 3 -> {
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

    private Result finish(boolean pass, String invariant, String reason) {
        var used = new TreeMap<String, Long>();
        if (plan != null) for (var entry : plan.usedItems()) used.put(keyName(entry.getKey()), entry.getLongValue());
        var extracted = new TreeMap<String, Long>();
        if (source != null) source.extracted.forEach((key, value) -> extracted.put(keyName(key), value));
        var checks = new java.util.LinkedHashMap<String, String>();
        long arrived = held(storage(Direction.NORTH), target);
        checks.put("target-crafted-plan", plan == null ? "NOT_CHECKED: plan unavailable" : craftsTarget()
                ? "PASS" : "The plan does not execute a pattern producing the target");
        checks.put("ordered-amount", plan == null ? "NOT_CHECKED: plan unavailable" : arrived == expectedAmount ? "PASS" : "Expected planned net output " + expectedAmount + ", requester holds " + arrived);
        long busy = grid(Direction.NORTH).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
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
        var expectedInventories = Map.of("materials", Map.<String, Long>of(), "factory", Map.<String, Long>of(), "requester", expectedStock);
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
        // A solid rectangular native CPU; storage grows with the plan and the rest are co-processors.
        long storageBlocks = Math.max(1, (plan.bytes() + 262143) / 262144);
        int height = Math.toIntExact((storageBlocks + 32 + 15) / 16);
        if (height > 17) throw new IllegalStateException("Plan exceeds CPU template capacity: " + plan.bytes());
        int index = 0;
        for (int y = 0; y < height; y++) for (int z = 0; z < 8; z++) for (int x = 0; x < 2; x++) {
            var pos = CENTER.north(2 + z).west(x).above(y);
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
        return signatures.keySet().equals(proof.signatures().keySet()) ? "" : "Installed signature set incomplete";
    }

    private String diagnostic() {
        return "stage=" + stage + ", ticks=" + ticks + ", target=" + target + ", requested=" + amount
                + ", planBytes=" + (plan == null ? 0 : plan.bytes()) + ", crafts=" + totalCrafts + ", parallelism=" + copies + ", budget=" + budget
                + ", arrived=" + held(storage(Direction.NORTH), target)
                + ", busy=" + grid(Direction.NORTH).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    private MEChestBlockEntity chest(Direction face) { return helper.getBlockEntity(CENTER.relative(face)); }
    private IGrid grid(Direction face) { return chest(face).getMainNode().getGrid(); }
    private MEStorage storage(Direction face) { return java.util.Objects.requireNonNull(chest(face).getOriginalCellInventory(0)); }
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
        for (var face : FACES) attempt(failures, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(CENTER.relative(face))) instanceof MEChestBlockEntity chest && chest.getMainNode().getGrid() != null) chest.getMainNode().getGrid().getCraftingService().getCpus().forEach(cpu -> { if (cpu.isBusy()) cpu.cancelJob(); });
        });
        attempt(failures, () -> { if (source != null) source.close(); });
        var service = PolicyService.get(helper.getLevel());
        for (var key : policies) attempt(failures, () -> service.configured(key).ifPresent(record -> service.edit(
                new PolicyEdit(key, service.revision(key), record.rule().withEnabled(false)))));
        attempt(failures, routers::close);
        for (var pos : placed) attempt(failures, () -> helper.setBlock(pos, Blocks.AIR));
        attempt(failures, () -> helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(2, 1, 0)),
                        helper.absolutePos(new BlockPos(18, 30, 15)))).forEach(net.minecraft.world.entity.Entity::discard));
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
