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
    private BlockPos originOffset = BlockPos.ZERO;
    private boolean restartPending;
    private RestartState resumed;
    private static final java.nio.file.Path RESTART = java.nio.file.Path.of("playthrough-restart.json").toAbsolutePath();
    private static final com.google.gson.Gson RESTART_JSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
    private record RestartState(long pid, long origin, PlaythroughTopology.Layout layout, String target, long amount,
            int budget, int copies, long crafts, long expectedAmount, long cpuBytes, long planBytes,
            Map<String, Long> expectedStock, Map<String, Long> used, Map<String, Long> extracted,
            Map<String, String> patterns, Map<String, PlaythroughAccounting.Signature> signatures,
            List<Long> placed, List<Long> providers, List<Long> assemblers, Map<Long, Direction> providerNetworks,
            List<String> networks, List<PolicyKey> policies, Map<String, Object> evidence, Result baseline,
            String craftingId, boolean targetCrafted, List<String> supplies) { }
    private int resumeWait;
    private boolean resumeVerified;

    private int unloadedAt;
    private long unloadStarted;
    private final int unloadSeconds = Integer.getInteger(PlaythroughTopology.PREFIX + "unloadSeconds", 120);
    private String unloadFailure = "";
    private List<net.minecraft.world.level.block.entity.BlockEntity> unloadingFactory = List.of();
    private boolean reloading;
    private boolean sprintPausedForUnload;
    private PlaythroughChunkDiagnostics chunkDiagnostics;
    /** A disturbance that did not happen to a running job: not a result, and not coverage. */
    static final String NOT_EXECUTED = "perturbation-not-executed";
    private P2PTunnels tunnels;
    private FederationP2PTunnelPart tunnelInput;
    private FederationP2PTunnelPart tunnelOutput;
    private boolean paired;
    private space.controlnet.ae2federation.bridge.MultipartBridgePart factoryBridge;
    private final Map<String, Object> perturbationEvidence = new TreeMap<>();
    private final Map<BlockPos, Direction> providerNetworks = new HashMap<>();
    private final Map<PolicyKey, PolicyRule> interruptedRules = new HashMap<>();
    private BlockPos cut;
    private net.minecraft.world.level.block.state.BlockState cutState;

    private BlockPos center() { return topology == null ? CENTER : new BlockPos(12, 12, 14); }
    private int distance() { return topology == null ? 1 : 5; }
    private BlockPos chestPos(Direction face) { return center().relative(face, distance()); }
    private List<Direction> faces() {
        return topology == null ? FACES : List.of(Direction.WEST, Direction.EAST, Direction.NORTH,
                Direction.SOUTH, Direction.UP, Direction.DOWN).subList(0, Math.max(3, topology.networks()));
    }
    private Direction materialFace() { return topology == null ? Direction.WEST : topology.role(0); }
    private Direction factoryFace() { return topology == null ? Direction.EAST : topology.role(1); }
    private Direction requesterFace() { return topology == null ? Direction.NORTH : topology.role(2); }
    private Direction powerFace() { return topology == null ? Direction.WEST : Direction.valueOf(topology.power()); }

    private final boolean federated;
    private PlaythroughRecipes.Proof proof;
    private final PlaythroughRecipes.Proof originalProof;
    private final Map<BlockPos, BlockPos> endpoints = new HashMap<>();
    private boolean patternsInstalled;
    private PlaythroughRemote remote;
    private boolean initialized;
    private BlockPos absolute(BlockPos pos) { return helper.absolutePos(pos.offset(originOffset)); }
    private boolean endpointPath() { return topology != null && !topology.path().equals("projection"); }
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
    private int drainedSince;
    private Result result;
    private final Set<NetworkId> networks = new LinkedHashSet<>();
    private final Map<String, PlaythroughAccounting.Signature> signatures = new TreeMap<>();
    private final Map<String, Object> cleanup = new TreeMap<>();
    private Map<String, Long> expectedStock = Map.of();
    private boolean closed;

    PlaythroughFactory(GameTestHelper helper, boolean federated, PlaythroughRecipes.Proof proof,
                       AEItemKey target, long amount, int budget) {
        this.helper = helper; this.federated = federated; this.proof = proof;
        this.originalProof = proof;
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
        if (unloadSeconds < 1 || unloadSeconds > 300) throw new IllegalArgumentException("unloadSeconds must be 1..300");
        if (perturbation.equals("chunk") && !layout.join().equals("quantum"))
            throw new IllegalArgumentException("Factory chunk disturbance requires a Quantum remote factory");
        if (layout.join().equals("quantum") && (!layout.path().equals("projection") || layout.networks() == 2 || factoryFace() != Direction.EAST))
            throw new IllegalArgumentException("Quantum factory requires projection and at least three separate networks");
        if (endpointPath()) {
            if (!proof.shelf().isEmpty())
                throw new IllegalArgumentException("Topology Endpoint paths run crafting recipes only; machine Endpoints use machinePath");
            this.proof = PlaythroughEndpoints.processing(helper, proof);
            patternGroups = proof.patterns().size(); copies = 1;
            if (patternGroups > 4) throw new IllegalArgumentException("Endpoint fixture currently supports at most four recipe-specific assemblers");
        }
        if (!endpointPath() && layout.splitPatterns() && proof.craftingPatterns().size() > 1) {
            patternGroups = 2 * ((proof.craftingPatterns().size() + 17) / 18);
            copies = Math.min(Integer.getInteger("ae2federation.playthrough.assemblerCopies", 28), 28 / patternGroups);
            if (copies < 1) throw new IllegalArgumentException("Split factory exceeds 28 providers");
        }
        // Last, so a refused layout leaves no forced nether site behind.
        if (layout.join().equals("quantum")) remote = new PlaythroughRemote(helper);
    }

    /** Why a disturbance cannot run on this layout and baseline plan, or empty. Checked before each case is built. */
    static String precondition(PlaythroughTopology.Layout layout, String perturbation, long baselineCrafts) {
        if (!layout.capacity().equals("normal")) return "";
        if (layout.join().equals("quantum") && (!layout.path().equals("projection") || layout.networks() == 2 || layout.role(1) != Direction.EAST))
            return "Quantum factory requires projection, at least three networks and an east factory";
        if (layout.join().equals("bridge") && layout.networks() < 3) return "Bridge star requires at least three networks";
        return switch (perturbation) {
            case "chunk" -> layout.join().equals("quantum") ? "" : "Factory chunk disturbance requires a Quantum remote factory";
            case "bridge", "bridge-part" -> layout.join().equals("bridge") ? "" : "Bridge disturbance requires the bridge join";
            case "cancel" -> baselineCrafts == 1 ? "" : "Cancellation proof requires a single-craft target; baseline plan crafts " + baselineCrafts;
            case "restart" -> "prepare".equals(System.getProperty("ae2federation.compat.transitRestartPhase"))
                    && layout.join().equals("router-switch") && layout.path().equals("projection") && baselineCrafts == 1 ? ""
                    : "Restart requires the prepare phase, router-switch projection and a single craft; baseline plan crafts " + baselineCrafts;
            default -> "";
        };
    }

    private void initializeTopology() {
        if (topology.join().equals("bridge")) {
            if (topology.networks() < 3) throw new IllegalArgumentException("Bridge star requires at least three networks");
            nativeCable(center());
            var bridges = new space.controlnet.ae2federation.test.bridge.BridgeFixtures(helper);
            for (var face : faces()) {
                for (int i = 1; i <= 4; i++) nativeCable(center().relative(face, i));
                if (face != requesterFace()) {
                    var bridge = bridges.placeBridge(center().relative(face, 4), face);
                    if (face == factoryFace()) factoryBridge = bridge;
                }
                placeExtraCpu(face);
            }
            return;
        }
        place(center(), RouterRegistration.ROUTER.get());
        for (var face : faces()) {
            for (int i = 1; i <= 3; i++) place(center().relative(face, i),
                    i == 2 && topology.join().equals("multi-hop")
                            ? RouterRegistration.ROUTER.get()
                            : RouterRegistration.FEDERATION_CABLE.get());
            if (topology.join().equals("router")) {
                var port = center().relative(face, 4);
                placed.add(port);
                setBlock(port, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, face.getOpposite()));
            } else place(center().relative(face, 4), RouterRegistration.SWITCH.get());
            placeExtraCpu(face);
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
            tunnelInput = tunnels.attune(helper.getLevel(), absolute(bus), Direction.EAST);
            tunnelOutput = tunnels.attune(helper.getLevel(), absolute(bus), Direction.WEST);
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
            var start = absolute(center().west(3));
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

    private void placeExtraCpu(Direction face) {
        if (topology.extraCpus() && !FACES.contains(face))
            place(face == Direction.SOUTH ? chestPos(face).west() : chestPos(face).relative(face), AEBlocks.CRAFTING_STORAGE_256K.block());
    }

    private void perturb() {
        if (perturbation.equals("none")) return;
        if (perturbedAt == 0) {
            var ledger = PlaythroughAccounting.ledger(helper.getLevel(), networks);
            if (ledger.get("owed").isEmpty() || grid(requesterFace()).getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy())) return;
            perturbedAt = ticks;
            perturbationEvidence.put("firstPushTick", ticks);
            perturbationEvidence.put("before", ledger);
            if (perturbation.equals("restart")) {
                if (!"prepare".equals(System.getProperty("ae2federation.compat.transitRestartPhase"))
                        || !topology.join().equals("router-switch") || endpointPath() || totalCrafts != 1)
                    throw new IllegalArgumentException("Restart preparation requires the existing prepare phase, router-switch projection and a single craft");
                setBlock(center(), Blocks.AIR.defaultBlockState());
                restartPending = true;
            } else if (perturbation.equals("chunk")) {
                // The site's tickets are released by the chunk branch of tick(), once every input is pushed.
                perturbationEvidence.put("jobAtFirstPush", jobStatus());
            } else if (perturbation.equals("bridge") || perturbation.equals("bridge-part")) {
                if (factoryBridge == null) throw new IllegalArgumentException("Bridge disturbance requires the bridge join");
                factoryBridge.getHost().removePart(factoryBridge);
                if (perturbation.equals("bridge")) helper.setBlock(center().relative(factoryFace(), 4), Blocks.AIR);
                perturbationEvidence.put("bridgeHostRemoved", perturbation.equals("bridge"));
                perturbationEvidence.put("bridgeRemoved", true);
            } else if (perturbation.equals("cancel")) {
                if (totalCrafts != 1) throw new IllegalArgumentException("Cancellation proof requires a single-craft target");
                grid(requesterFace()).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).forEach(cpu -> cpu.cancelJob());
                perturbationEvidence.put("cancelled", true);
                restored = true;
            } else if (perturbation.equals("cable") || perturbation.equals("router")) {
                // The factory's own link, or the hub; restored as exactly the block that was cut.
                cut = perturbation.equals("router") ? center() : center().relative(factoryFace());
                cutState = helper.getBlockState(cut);
                perturbationEvidence.put("cut", cut + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(cutState.getBlock()));
                helper.setBlock(cut, Blocks.AIR);
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
            if (perturbation.equals("bridge") || perturbation.equals("bridge-part")) {
                if (perturbation.equals("bridge")) routers.nativePorts().placeCable(center().relative(factoryFace(), 4));
                factoryBridge = new space.controlnet.ae2federation.test.bridge.BridgeFixtures(helper).placeBridge(center().relative(factoryFace(), 4), factoryFace());
            } else if (perturbation.equals("restart")) setBlock(center(), RouterRegistration.ROUTER.get().defaultBlockState());
            else if (perturbation.equals("cable") || perturbation.equals("router")) {
                // Native cable is a multipart host; place it the way the fixture built it.
                if (cutState.is(AEBlocks.CABLE_BUS.block())) routers.nativePorts().placeCable(cut);
                else helper.setBlock(cut, cutState);
                perturbationEvidence.put("restoredBlock", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(helper.getBlockState(cut).getBlock()).toString());
            } else interruptedRules.forEach(this::editRule);
            restored = true;
            perturbationEvidence.put("restoredTick", ticks);
        }
    }

    /**
     * As the existing unload scenes do, release the site only once the CPU holds no input it has not pushed. Until
     * then every push attempt reads the factory's blocks on the main thread, which reloads their chunk at once. Tick
     * sprint leaves vanilla no spare time for its unload queue, which then drains only above 2000 entries, so sprint
     * pauses for the unload window unless {@code pauseSprintForUnload=false}.
     */
    private boolean releaseFactory() {
        var cpu = requesterCpu();
        if (cpu != null && cpu.isBusy()) for (var held : cpu.craftingLogic.getInventory().list) if (held.getLongValue() > 0) return false;
        unloadingFactory = placed.stream().filter(remote::contains).map(this::entity).filter(java.util.Objects::nonNull).toList();
        if (unloadingFactory.isEmpty()) throw new IllegalStateException("No factory block entities to unload");
        perturbationEvidence.put("allInputsPushedTick", ticks);
        perturbationEvidence.put("jobAtRelease", jobStatus());
        if (cpu == null || !cpu.isBusy()) return true;
        var tickManager = helper.getLevel().getServer().tickRateManager();
        sprintPausedForUnload = Boolean.parseBoolean(System.getProperty(PlaythroughTopology.PREFIX + "pauseSprintForUnload", "true")) && tickManager.isSprinting();
        if (sprintPausedForUnload) tickManager.stopSprinting();
        perturbationEvidence.put("sprintPausedForUnload", sprintPausedForUnload);
        chunkDiagnostics = new PlaythroughChunkDiagnostics(remote.site.level(), unloadingFactory.stream().map(entity -> entity.getBlockPos()).toList());
        remote.site.releaseTickets();
        unloadStarted = System.nanoTime();
        perturbationEvidence.put("ticketsReleased", true);
        return true;
    }

    private void resumeSprintAfterUnload() {
        if (sprintPausedForUnload) {
            helper.getLevel().getServer().tickRateManager().requestGameToSprint(1000000);
            sprintPausedForUnload = false;
        }
    }

    private void editRule(PolicyKey key, PolicyRule rule) {
        var service = PolicyService.get(helper.getLevel());
        if (!(service.edit(new PolicyEdit(key, service.revision(key), rule)) instanceof PolicyMutationResult.Accepted))
            throw new IllegalStateException("Perturbation rule refused: " + key);
    }

    void initialize() {
        if (remote != null && !remote.site.ready()) return;
        initialized = true;
        for (var face : faces()) {
            placed.add(chestPos(face));
            setBlock(chestPos(face), AEBlocks.ME_CHEST.block().defaultBlockState());
            chest(face).setCell(AEItems.ITEM_CELL_256K.stack());
        }
        chest(requesterFace()).setPriority(1000);
        place(chestPos(powerFace()).relative(powerFace() == Direction.DOWN ? Direction.WEST : Direction.DOWN), AEBlocks.CREATIVE_ENERGY_CELL.block());
        place(center().relative(requesterFace(), distance() + 1), AEBlocks.CRAFTING_STORAGE_256K.block());
        int count = patternGroups * copies;
        if (count > 28) throw new IllegalStateException("Factory template supports at most 252 patterns, needed " + proof.patterns().size());
        var sides = List.of(Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH);
        var providerCounts = new java.util.EnumMap<Direction, Integer>(Direction.class);
        for (int i = 0; i < count; i++) {
            var factoryFace = topology != null && topology.splitPatterns() && i % 2 == 1 ? Direction.SOUTH : factoryFace();
            int localIndex = providerCounts.merge(factoryFace, 1, Integer::sum) - 1;
            var controller = center().relative(factoryFace, distance() + 1 + localIndex / 4);
            var assemblySides = factoryFace.getAxis() == Direction.Axis.X ? sides : List.of(Direction.UP, Direction.DOWN, Direction.WEST, Direction.EAST);
            int sideIndex = localIndex % 4;
            place(controller, AEBlocks.CONTROLLER.block());
            var pos = controller.relative(assemblySides.get(sideIndex));
            placed.add(pos); placed.add(pos.relative(assemblySides.get(sideIndex)));
            var outward = assemblySides.get(sideIndex);
            var assemblerPos = pos.relative(outward, endpointPath() ? 2 : 1);
            if (endpointPath()) {
                PlaythroughEndpoints.place(helper, pos, outward, factoryFace, topology.path().equals("local-endpoint"), placed::add);
                endpoints.put(pos, pos.relative(outward));
            } else {
                setBlock(pos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                setBlock(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            }
            var assembler = (MolecularAssemblerBlockEntity) entity(assemblerPos);
            if (!perturbation.equals("chunk") && !assembler.getUpgrades().addItems(AEItems.SPEED_CARD.stack(5)).isEmpty()) {
                throw new IllegalStateException("Assembler refused its five acceleration cards");
            }
            providerNetworks.put(pos, factoryFace);
            providers.add(pos); assemblers.add(assemblerPos);
            placed.add(pos); placed.add(pos.relative(assemblySides.get(sideIndex)));
        }
        if (topology != null) initializeTopology();
        if (remote != null) remote.initialize();
        machines.initialize(proof, chestPos(Direction.EAST), chestPos(Direction.NORTH));
        if (!federated) {
            placed.add(CENTER);
            routers.nativePorts().placeCable(CENTER);
        }
    }

    private void place(BlockPos pos, net.minecraft.world.level.block.Block block) {
        if (placed.add(pos)) setBlock(pos, block.defaultBlockState());
    }

    private void setBlock(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (remote != null && remote.contains(pos)) { watchUnload(pos); remote.site.setBlock(pos, state); }
        else helper.setBlock(pos.offset(originOffset), state);
    }

    private net.minecraft.world.level.block.entity.BlockEntity entity(BlockPos pos) {
        if (remote != null && remote.contains(pos)) { watchUnload(pos); return remote.site.getBlockEntity(pos); }
        return helper.getBlockEntity(pos.offset(originOffset));
    }

    /** A harness access to the remote factory between ticket release and reload would load its chunk again. */
    private void watchUnload(BlockPos pos) {
        if (chunkDiagnostics != null && !reloading) chunkDiagnostics.touched(pos);
    }

    /** The requester job's AE2 progress, read without touching the factory's chunks. */
    private String jobStatus() {
        return grid(requesterFace()).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy())
                .map(cpu -> String.valueOf(cpu.getJobStatus())).toList().toString();
    }

    Result tick() throws Exception {
        if (!initialized) { initialize(); return null; }
        if (remote != null && !remote.ready()) return null;
        if (result != null) return result;
        ticks++;
        if (perturbation.equals("chunk") && perturbedAt > 0 && !restored) {
            // The ordinary job budget bounds the wait for every push; only the unload window pauses it.
            if (chunkDiagnostics == null) {
                if (ticks - readyTick > budget) return finish(false, "job-complete", diagnostic());
                if (!releaseFactory()) return null;
                if (chunkDiagnostics == null) {
                    restored = true;
                    return finish(false, NOT_EXECUTED, "No busy requester CPU when the factory site was to be released");
                }
            }
            readyTick++;
            if (!reloading) chunkDiagnostics.sample();
            if (!reloading && System.nanoTime() - unloadStarted > unloadSeconds * 1_000_000_000L) {
                unloadFailure = "Factory chunks did not unload: " + remote.site.tickDiagnostics() + " " + remote.site.unloadDiagnostics()
                        + ", factoryLive=" + unloadingFactory.stream().filter(entity -> !entity.isRemoved()).map(entity -> entity.getClass().getSimpleName() + "@" + entity.getBlockPos()).toList();
                perturbationEvidence.put("unloadTimeout", chunkDiagnostics.report());
                perturbationEvidence.put("jobAtTimeout", jobStatus());
                remote.site.forceAgain(); reloading = true;
            }
            if (!reloading) {
                if (unloadingFactory.stream().anyMatch(entity -> !entity.isRemoved()
                        || remote.site.level().hasChunk(entity.getBlockPos().getX() >> 4, entity.getBlockPos().getZ() >> 4))) return null;
                if (unloadedAt == 0) {
                    unloadedAt = ticks;
                    perturbationEvidence.put("unloadedTick", ticks);
                    perturbationEvidence.put("removedFactoryEntities", unloadingFactory.size());
                    perturbationEvidence.put("unloadSeconds", (System.nanoTime() - unloadStarted) / 1_000_000_000.0);
                    perturbationEvidence.put("transportUnload", remote.site.unloadDiagnostics());
                    perturbationEvidence.put("unloadChunks", chunkDiagnostics.report());
                    perturbationEvidence.put("jobAtUnload", jobStatus());
                    boolean busy = grid(requesterFace()).getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
                    perturbationEvidence.put("busyAtUnload", busy);
                    if (!busy) {
                        // The disturbance did not happen to a running job; reload and record that, not a result.
                        unloadFailure = "Job completed before actual factory chunk unload";
                        remote.site.forceAgain(); reloading = true;
                        return null;
                    }
                }
                if (ticks - unloadedAt < 40) return null;
                remote.site.forceAgain(); reloading = true;
                return null;
            }
            if (!remote.site.ticking() || faces().stream().anyMatch(face -> grid(face) == null)) return null;
            restored = true;
            resumeSprintAfterUnload();
            perturbationEvidence.put("restoredTick", ticks);
            if (!unloadFailure.isEmpty()) return finish(false, NOT_EXECUTED, unloadFailure);
        }
        for (var face : faces()) if (grid(face) != null) {
            FederationDomainRegistryAccess.confirmedNetworkId(grid(face)).ifPresent(networks::add);
        }
        for (var pos : endpoints.values()) {
            var binding = space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding.findEndpoint(helper.getLevel(), absolute(pos));
            if (binding != null) FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid()).ifPresent(networks::add);
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
                    var domains = new java.util.HashSet<>(registry.federationdomainsFor(network(materialFace())));
                    for (var face : faces()) domains.retainAll(registry.federationdomainsFor(network(face)));
                    if (topology != null && topology.join().equals("bridge")) {
                        if (faces().stream().anyMatch(face -> face != requesterFace() && !registry.shareFederationDomain(network(requesterFace()), network(face)))) return null;
                    } else if (domains.isEmpty()) return null;
                    for (var face : faces()) if (!network(face).equals(network(powerFace()))) rule(face, topology != null && topology.join().equals("bridge") && face != requesterFace() ? requesterFace() : powerFace(),
                            PolicyCapability.ME_POWER, PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY)));
                    rule(requesterFace(), factoryFace(), PolicyCapability.CRAFTING, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                    rule(requesterFace(), factoryFace(), PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    if (!network(requesterFace()).equals(network(materialFace())))
                        rule(requesterFace(), materialFace(), PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    for (var face : faces()) if (!FACES.contains(face)) {
                        rule(requesterFace(), face, PolicyCapability.CRAFTING, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
                        rule(requesterFace(), face, PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    }
                }
                source = new PlaythroughSource(helper.getLevel(), absolute(chestPos(materialFace())),
                        chest(materialFace()).getMainNode().getNode(), proof.supplies());
                stage = 2;
            }
            case 2 -> {
                if (topology != null && !topology.capacity().equals("normal")) {
                    var demand = CableCapacity.demand(helper.getLevel(), absolute(center().west()));
                    if (demand.isEmpty()) return null;
                    cleanup.put("capacityDemand", demand.get());
                    int expected = topology.capacity().equals("over-limit") ? 9 : 8;
                    if (demand.get().units() != expected) return null;
                    if (ticks < 80) return null;
                }
                if (faces().stream().anyMatch(face -> !grid(face).getEnergyService().isNetworkPowered())
                        || grid(requesterFace()).getCraftingService().getCpus().isEmpty()) return null;
                for (var pos : providers) {
                    var host = (PatternProviderLogicHost) entity(pos);
                    if (host.getLogic().getGrid() != grid(providerNetworks.get(pos))) return null;
                }
                if (!machines.install(proof, networks)) return null;
                if (!patternsInstalled) for (int copy = 0; copy < copies; copy++) {
                    int index = 0;
                    for (var pattern : proof.craftingPatterns().values()) {
                        boolean split = topology != null && topology.splitPatterns() && proof.craftingPatterns().size() > 1;
                        int group = endpointPath() ? index : split ? index / 18 * 2 + index % 2 : index / 9;
                        int slot = endpointPath() ? 0 : split ? index / 2 % 9 : index % 9;
                        var host = (PatternProviderLogicHost) entity(providers.get(copy * patternGroups + group));
                        host.getLogic().getPatternInv().setItemDirect(slot, pattern.copy());
                        if (endpointPath()) PlaythroughEndpoints.program(helper, assemblers.get(index), new ArrayList<>(originalProof.patterns().values()).get(index));
                        index++;
                    }
                }
                if (!patternsInstalled) for (var pos : providers) ((PatternProviderLogicHost) entity(pos)).getLogic().updatePatterns();
                patternsInstalled = true;
                if (endpointPath()) {
                    var installed = new ArrayList<>(proof.patterns().values());
                    for (int i = 0; i < providers.size(); i++) if (!PlaythroughEndpoints.ready(helper, providers.get(i), endpoints.get(providers.get(i)),
                            topology.path().equals("local-endpoint"), installed.get(i))) return null;
                    String isolation = PlaythroughEndpoints.isolation(helper, endpoints.values(), faces().stream().map(this::grid).toList());
                    if (!isolation.isEmpty()) throw new IllegalStateException("Endpoint fixture did not build: " + isolation);
                }
                String mismatch = checkSignatures();
                if (!mismatch.isEmpty()) return finish(false, "recipe-signature", mismatch);
                stage = 3; readyTick = ticks;
            }
            case 3 -> {
                if (topology != null && topology.capacity().equals("over-limit")) {
                    if (ticks - readyTick < 40) return null;
                    return refusal();
                }
                var service = grid(requesterFace()).getCraftingService();
                if (!service.isCraftable(target)) return null;
                for (var pattern : proof.patterns().values()) {
                    var details = appeng.api.crafting.PatternDetailsHelper.decodePattern(pattern, helper.getLevel());
                    if (details.getOutputs().stream().anyMatch(output -> !service.isCraftable(output.what()))) return null;
                }
                var node = chest(requesterFace()).getMainNode().getNode();
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
                var cpu = grid(requesterFace()).getCraftingService().getCpus().stream()
                        .filter(candidate -> candidate.getAvailableStorage() >= plan.bytes() && candidate.getCoProcessors() >= copies)
                        .findFirst();
                if (cpu.isEmpty()) {
                    if (ticks - cpuChangedTick > 400) throw new IllegalStateException("Plan-sized CPU did not form: " + diagnostic());
                    return null;
                }
                cpuBytes = cpu.get().getAvailableStorage();
                var submitted = grid(requesterFace()).getCraftingService().submitJob(plan, null, cpu.get(), true, IActionSource.empty());
                if (!submitted.successful()) return finish(false, "job-submit", submitted.errorCode() + " " + submitted.errorDetail());
                stage = 5;
            }
            case 5 -> {
                perturb();
                if (perturbedAt != 0 && (!restored || ticks - perturbedAt < 40
                        || faces().stream().anyMatch(face -> !grid(face).getEnergyService().isNetworkPowered())
                        || grid(requesterFace()).getCraftingService().getCpus().isEmpty())) { idleSince = 0; drainedSince = 0; return null; }
                if (grid(requesterFace()).getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy())) {
                    idleSince = 0; drainedSince = 0; return null;
                }
                if (idleSince == 0) idleSince = ticks;
                // Surplus machine output can still leave through its 8-tick hopper after the CPU is idle.
                if (!machines.drained() && ticks - idleSince < machines.drainTicks()) { drainedSince = 0; return null; }
                if (drainedSince == 0) drainedSince = ticks;
                // Allow a complete 20-tick return-ledger sweep after the CPU becomes idle.
                if (ticks - drainedSince < 25) return null;
                return finish(true, "", "");
            }
            default -> throw new IllegalStateException("Unknown factory stage");
        }
        return null;
    }

    private Result refusal() {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var checks = new TreeMap<String, String>();
        checks.put("domain-over-capacity", registry.shareOverCapacityFederationDomain(network(requesterFace()), network(factoryFace())) ? "PASS" : "Domain is not over capacity");
        checks.put("domain-stopped", !registry.shareRunningFederationDomain(network(requesterFace()), network(factoryFace())) ? "PASS" : "Domain still runs");
        checks.put("pattern-refused", grid(factoryFace()).getCraftingService().isCraftable(target)
                && !grid(requesterFace()).getCraftingService().isCraftable(target) ? "PASS" : "Local pattern unavailable or leaked through stopped domain");
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
        if (resumed != null) used.putAll(resumed.used());
        if (plan != null) for (var entry : plan.usedItems()) used.put(keyName(entry.getKey()), entry.getLongValue());
        var extracted = new TreeMap<String, Long>();
        if (resumed != null) extracted.putAll(resumed.extracted());
        if (source != null) source.extracted.forEach((key, value) -> extracted.merge(keyName(key), value, Math::addExact));
        var checks = new java.util.LinkedHashMap<String, String>();
        if (topology != null) {
            cleanup.put("perturbation", perturbationEvidence);
            checks.put("perturbation", perturbation.equals("none") || perturbedAt > 0 && restored
                    ? "PASS" : "No observed first push while CPU busy, or restoration incomplete");
        }
        long arrived = held(storage(requesterFace()), target);
        checks.put("target-crafted-plan", plan == null && resumed == null ? "NOT_CHECKED: plan unavailable" : craftsTarget()
                ? "PASS" : "The plan does not execute a pattern producing the target");
        checks.put("ordered-amount", plan == null && resumed == null ? "NOT_CHECKED: plan unavailable" : arrived == expectedAmount ? "PASS" : "Expected planned net output " + expectedAmount + ", requester holds " + arrived);
        long busy = faces().stream().map(this::grid).distinct().flatMap(grid -> grid.getCraftingService().getCpus().stream())
                .filter(cpu -> cpu.isBusy()).count();
        var cpuCounts = new TreeMap<String, Integer>();
        for (var face : faces()) cpuCounts.put(face.name(), grid(face).getCraftingService().getCpus().size());
        cleanup.put("cpuCounts", cpuCounts);
        var allocation = new TreeMap<String, List<String>>();
        for (var pos : providers) allocation.computeIfAbsent(providerNetworks.get(pos).name(), ignored -> new ArrayList<>())
                .addAll(logic(pos).getAvailablePatterns().stream()
                        .map(pattern -> pattern.getDefinition().toString()).toList());
        machines.installedPatterns().forEach((grid, patterns) -> allocation.computeIfAbsent(grid == grid(Direction.EAST)
                ? Direction.EAST.name() : "detached", ignored -> new ArrayList<>()).addAll(patterns));
        cleanup.put("installedPatternsByNetwork", allocation);
        checks.put("cpu-idle", busy == 0 ? "PASS" : busy + " busy CPUs");
        var providerBuffers = new ArrayList<String>();
        for (var pos : providers) {
            var logic = logic(pos);
            if (logic.isBusy() || !logic.getReturnInv().isEmpty()) providerBuffers.add(pos.toString());
        }
        checks.put("provider-buffer", providerBuffers.isEmpty() ? "PASS" : "Leftovers at " + providerBuffers);
        var assemblerBuffers = new ArrayList<String>();
        for (var pos : assemblers) {
            var inv = ((MolecularAssemblerBlockEntity) entity(pos)).getInternalInventory();
            for (int slot = 0; slot < inv.size(); slot++) if (!(endpointPath() && slot == 10) && !inv.getStackInSlot(slot).isEmpty()) {
                assemblerBuffers.add(pos + " slot " + slot + ": " + inv.getStackInSlot(slot));
            }
        }
        checks.put("assembler-buffer", assemblerBuffers.isEmpty() ? "PASS" : "Leftovers at " + assemblerBuffers);
        checks.putAll(machines.checks());
        var expected = new HashMap<AEKey, Long>();
        if (plan != null) for (var entry : plan.usedItems()) if (entry.getLongValue() != 0) expected.put(entry.getKey(), entry.getLongValue());
        checks.put("source-extractions", plan == null && resumed == null ? "NOT_CHECKED: plan unavailable" : (resumed != null ? used.equals(extracted) : expected.equals(source.extracted))
                ? "PASS" : "Extraction counts differ from ICraftingPlan.usedItems()");
        var ledger = PlaythroughAccounting.ledger(helper.getLevel(), networks);
        checks.put("return-ledger", ledger.get("owed").isEmpty() ? "PASS" : ledger.get("owed").toString());
        checks.put("return-transit", ledger.get("transit").isEmpty() ? "PASS" : ledger.get("transit").toString());
        var inventories = new TreeMap<String, Map<String, Long>>();
        inventories.put("materials", PlaythroughAccounting.contents(storage(materialFace()), helper.getLevel()));
        inventories.put("factory", PlaythroughAccounting.contents(storage(factoryFace()), helper.getLevel()));
        inventories.put("requester", PlaythroughAccounting.contents(storage(requesterFace()), helper.getLevel()));
        var expectedInventories = new TreeMap<String, Map<String, Long>>();
        expectedInventories.put("materials", Map.of()); expectedInventories.put("factory", Map.of()); expectedInventories.put("requester", expectedStock);
        for (var face : faces()) if (!FACES.contains(face)) {
            inventories.put(face.name(), PlaythroughAccounting.contents(storage(face), helper.getLevel()));
            expectedInventories.put(face.name(), Map.of());
        }
        checks.put("cell-inventories", plan == null && resumed == null ? "NOT_CHECKED: plan unavailable" : expectedInventories.equals(inventories)
                ? "PASS" : "Expected " + expectedInventories + ", actual " + inventories);
        String mismatch = checkSignatures();
        checks.put("recipe-signature", mismatch.isEmpty() ? "PASS" : mismatch);
        if (!mismatch.isEmpty()) { pass = false; invariant = "recipe-signature"; reason = mismatch; }
        if (pass) for (var check : checks.entrySet()) if (!check.getValue().equals("PASS")) {
            pass = false; invariant = check.getKey(); reason = check.getValue(); break;
        }
        var stored = new TreeMap<String, Long>();
        stored.put("materials", held(storage(materialFace()), target));
        stored.put("factory", held(storage(factoryFace()), target));
        stored.put("requester", arrived);
        result = new Result(pass, ticks, invariant, reason, used, extracted, arrived, checks, stored, expectedAmount, plan == null ? resumed == null ? 0 : resumed.planBytes() : plan.bytes(),
                totalCrafts, copies, budget, cpuBytes, expectedInventories, inventories, new TreeMap<>(signatures), ledger, cleanup);
        storage(requesterFace()).extract(target, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
        return result;
    }

    private void sizePlan() {
        expectedStock = PlaythroughAccounting.expected(plan, helper.getLevel());
        for (var entry : plan.patternTimes().entrySet()) {
            long times = entry.getValue();
            totalCrafts = Math.addExact(totalCrafts, times);
        }
        expectedAmount = expectedStock.getOrDefault(keyName(target), 0L);
        var machineRuns = PlaythroughMachines.runs(plan, proof);
        cleanup.put("machineRuns", machineRuns);
        // Assembler-kind machine recipes (smithing, stonecutting) run as assembler crafts.
        var shelfRuns = new TreeMap<>(machineRuns); shelfRuns.keySet().retainAll(proof.shelf().keySet());
        long assemblerCrafts = totalCrafts - shelfRuns.values().stream().mapToLong(Long::longValue).sum();
        budget = Math.toIntExact(Math.addExact(Math.addExact(baseBudget, Math.multiplyExact(ticksPerCraft,
                (assemblerCrafts + copies - 1) / copies)), machines.executionTicks(shelfRuns)));
        // A solid rectangular native CPU; storage grows with the plan and the rest are co-processors.
        long storageBlocks = Math.max(1, (plan.bytes() + 262143) / 262144);
        int height = Math.toIntExact((storageBlocks + 32 + 15) / 16);
        if (height > 17) throw new IllegalStateException("Plan exceeds CPU template capacity: " + plan.bytes());
        int index = 0;
        for (int y = 0; y < height; y++) for (int z = 0; z < 8; z++) for (int x = 0; x < 2; x++) {
            var pos = center().relative(requesterFace(), distance() + 1 + z).relative(requesterFace().getCounterClockWise(), x).above(y);
            var block = index++ < storageBlocks ? AEBlocks.CRAFTING_STORAGE_256K.block() : AEBlocks.CRAFTING_ACCELERATOR.block();
            placed.add(pos); helper.setBlock(pos, block);
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "AE2F_PLAYTHROUGH_PLAN item={} layout={} bytes={} crafts={} parallelism={} budget={} expected={}",
                target, federated ? "federated" : "baseline", plan.bytes(), totalCrafts, copies, budget, expectedAmount);
        cpuChangedTick = ticks;
        readyTick = ticks;
    }

    /** For a resumed job, the answer prepare measured on the original plan. */
    private boolean craftsTarget() {
        if (resumed != null) return resumed.targetCrafted();
        return plan != null && plan.patternTimes().entrySet().stream().anyMatch(entry -> entry.getValue() > 0
                && entry.getKey().getOutputs().stream().anyMatch(output -> output.what().equals(target)));
    }

    private String keyName(AEKey key) { return PlaythroughAccounting.name(key, helper.getLevel()); }

    private appeng.helpers.patternprovider.PatternProviderLogic logic(BlockPos pos) {
        var host = (PatternProviderLogicHost) entity(pos);
        if (host instanceof space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity provider
                && provider.laneCount() > 0) return provider.lane(0);
        return host.getLogic();
    }

    private String checkSignatures() {
        if (endpointPath()) {
            var configured = new ArrayList<>(originalProof.patterns().values());
            for (int i = 0; i < assemblers.size(); i++) {
                var assembler = (MolecularAssemblerBlockEntity) entity(assemblers.get(i));
                if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(assembler.getInternalInventory().getStackInSlot(10), configured.get(i)))
                    return "Assembler recipe configuration changed at " + assemblers.get(i);
            }
        }
        var identities = new HashMap<AEItemKey, String>();
        proof.patterns().forEach((id, stack) -> identities.put(AEItemKey.of(stack), id));
        for (var pos : providers) {
            var logic = logic(pos);
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
                + ", planBytes=" + (plan == null ? resumed == null ? 0 : resumed.planBytes() : plan.bytes()) + ", crafts=" + totalCrafts + ", parallelism=" + copies + ", budget=" + budget
                + ", arrived=" + held(storage(requesterFace()), target)
                + ", busy=" + grid(requesterFace()).getCraftingService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    private MEChestBlockEntity chest(Direction face) { return (MEChestBlockEntity) entity(chestPos(face)); }
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
        state.put("inventories", Map.of("materials", PlaythroughAccounting.contents(storage(materialFace()), helper.getLevel()),
                "factory", PlaythroughAccounting.contents(storage(factoryFace()), helper.getLevel()),
                "requester", PlaythroughAccounting.contents(storage(requesterFace()), helper.getLevel())));
        return state;
    }


    boolean restartPending() { return restartPending; }

    void prepareRestart(Result baseline) throws Exception {
        if (!restartPending || !baseline.pass()) throw new IllegalStateException("Restart requires a passing baseline and a live pushed job");
        var used = new TreeMap<String, Long>();
        plan.usedItems().forEach(entry -> used.put(keyName(entry.getKey()), entry.getLongValue()));
        var extracted = new TreeMap<String, Long>();
        source.extracted.forEach((key, value) -> extracted.put(keyName(key), value));
        if (!used.equals(extracted)) throw new IllegalStateException("All planned inputs must be in the saved CPU before restart");
        var cpu = requesterCpu();
        var link = cpu == null || !cpu.isBusy() ? null : cpu.craftingLogic.getLastLink();
        if (link == null) throw new IllegalStateException("Restart requires the requester CPU's running crafting link");
        var supplies = new ArrayList<String>();
        for (var key : proof.supplies()) {
            if (!(key instanceof AEItemKey item)) throw new IllegalArgumentException("Restart saves item supplies only: " + key);
            supplies.add(item.toStack().save(helper.getLevel().registryAccess()).toString());
        }
        var encoded = new TreeMap<String, String>();
        proof.patterns().forEach((id, stack) -> encoded.put(id, stack.save(helper.getLevel().registryAccess()).toString()));
        var allocation = new TreeMap<Long, Direction>();
        providerNetworks.forEach((pos, face) -> allocation.put(pos.asLong(), face));
        var state = new RestartState(ProcessHandle.current().pid(), absolute(BlockPos.ZERO).asLong(), topology,
                keyName(target), amount, budget, copies, totalCrafts, expectedAmount, cpuBytes, plan.bytes(), expectedStock,
                used, extracted, encoded, proof.signatures(), placed.stream().map(BlockPos::asLong).toList(),
                providers.stream().map(BlockPos::asLong).toList(), assemblers.stream().map(BlockPos::asLong).toList(),
                allocation, networks.stream().map(id -> id.value().toString()).toList(), List.copyOf(policies),
                perturbationEvidence, baseline, link.getCraftingID().toString(), craftsTarget(), supplies);
        PlaythroughCheckpoint.write(RESTART, RESTART_JSON.toJson(state));
        restartTickets(helper, BlockPos.of(state.origin()), true);
    }

    private appeng.me.cluster.implementations.CraftingCPUCluster requesterCpu() {
        return entity(center().relative(requesterFace(), distance() + 1)) instanceof appeng.blockentity.crafting.CraftingBlockEntity crafting
                ? crafting.getCluster() : null;
    }

    /**
     * Before the Router returns, the restarted world must hold the saved job on the requester's own CPU: busy with the
     * saved crafting id. Read from the CPU blocks, as an unpowered requester lists no CPUs. Starts the post-restart
     * extraction count. False while the CPU may still be forming.
     */
    private boolean observeResume() {
        var cpu = requesterCpu();
        var link = cpu == null ? null : cpu.craftingLogic.getLastLink();
        boolean running = cpu != null && cpu.isBusy() && link != null && link.getCraftingID().toString().equals(resumed.craftingId());
        if (!running && ++resumeWait < 200) return false;
        resumeVerified = running;
        perturbationEvidence.put("savedCraftingId", resumed.craftingId());
        perturbationEvidence.put("cpuBeforeRestore", cpu == null ? "no formed requester CPU" : "busy=" + cpu.isBusy() + ", craftingId=" + (link == null ? null : link.getCraftingID()));
        perturbationEvidence.put("resumedBeforeRestore", running);
        perturbationEvidence.put("visibleCpusBeforeRestore", grid(requesterFace()).getCraftingService().getCpus().size());
        perturbationEvidence.put("busyBeforeRestore", grid(requesterFace()).getCraftingService().getCpus().stream().anyMatch(visible -> visible.isBusy()));
        perturbationEvidence.put("afterRestart", PlaythroughAccounting.ledger(helper.getLevel(), networks));
        var supplies = new java.util.HashSet<appeng.api.stacks.AEKey>();
        for (var stack : resumed.supplies()) {
            try {
                supplies.add(AEItemKey.of(net.minecraft.world.item.ItemStack.parseOptional(helper.getLevel().registryAccess(), net.minecraft.nbt.TagParser.parseTag(stack))));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalStateException("Saved supply is unreadable: " + stack, exception);
            }
        }
        // Any extraction after the restart is counted against the saved plan, as one before it would be.
        source = new PlaythroughSource(helper.getLevel(), absolute(chestPos(materialFace())), chest(materialFace()).getMainNode().getNode(), supplies);
        return true;
    }

    static void resumeRestart(GameTestHelper helper) throws Exception {
        var state = RESTART_JSON.fromJson(java.nio.file.Files.readString(RESTART), RestartState.class);
        if (state.pid() == ProcessHandle.current().pid()) throw new IllegalStateException("Restart recovery requires a different JVM");
        // The prepared world keeps its restart tickets until recovery ends, also when it fails.
        final PlaythroughFactory factory;
        try {
            var patterns = new java.util.LinkedHashMap<String, net.minecraft.world.item.ItemStack>();
            for (var entry : state.patterns().entrySet()) patterns.put(entry.getKey(), net.minecraft.world.item.ItemStack.parseOptional(
                    helper.getLevel().registryAccess(), net.minecraft.nbt.TagParser.parseTag(entry.getValue())));
            var proof = new PlaythroughRecipes.Proof(patterns, Set.of(), Map.of(), state.signatures());
            factory = new PlaythroughFactory(helper, state.layout(), "restart", proof, PlaythroughRecipes.key(state.target()), state.amount(), state.budget());
            factory.originOffset = BlockPos.of(state.origin()).subtract(helper.absolutePos(BlockPos.ZERO));
            factory.resumed = state; factory.initialized = true; factory.stage = 5; factory.perturbedAt = 1;
            factory.copies = state.copies(); factory.totalCrafts = state.crafts(); factory.expectedAmount = state.expectedAmount();
            factory.cpuBytes = state.cpuBytes(); factory.expectedStock = state.expectedStock();
            state.placed().forEach(pos -> factory.placed.add(BlockPos.of(pos)));
            state.providers().forEach(pos -> factory.providers.add(BlockPos.of(pos)));
            state.assemblers().forEach(pos -> factory.assemblers.add(BlockPos.of(pos)));
            state.providerNetworks().forEach((pos, face) -> factory.providerNetworks.put(BlockPos.of(pos), face));
            state.networks().forEach(id -> factory.networks.add(new NetworkId(java.util.UUID.fromString(id))));
            factory.policies.addAll(state.policies()); factory.perturbationEvidence.putAll(state.evidence());
            factory.perturbationEvidence.put("differentJvm", true);
        } catch (Exception exception) {
            restartTickets(helper, BlockPos.of(state.origin()), false);
            throw exception;
        }
        var directory = java.nio.file.Path.of(System.getProperty("ae2federation.compat.report")).toAbsolutePath().getParent();
        helper.onEachTick(() -> {
            try {
                if (factory.closed) return;
                if (factory.faces().stream().anyMatch(face -> factory.grid(face) == null)) return;
                if (factory.ticks == 0 && factory.source == null && !factory.observeResume()) return;
                var result = factory.tick();
                if (result == null) return;
                factory.close();
                restartTickets(helper, BlockPos.of(state.origin()), false);
                var row = new java.util.LinkedHashMap<String, Object>();
                row.put("layout", state.layout()); row.put("item", state.target()); row.put("perturbation", "restart");
                var comparison = PlaythroughTopology.compare(state.baseline(), result, false);
                row.put("baseline", state.baseline()); row.put("federated", comparison.federated());
                // A job lost on reload is its own outcome, however cleanly its debts settle.
                row.put("classification", factory.resumeVerified ? comparison.classification() : "job-lost-on-reload");
                row.put("inventoryComparison", comparison.inventoryComparison());
                row.put("expectedOutcome", "original-job-resumes-and-completes-after-process-restart");
                helper.runAfterDelay(25, () -> {
                    try {
                        factory.verifyClosed();
                        PlaythroughTopology.write(directory, List.of(state.layout()), List.of(row), true, "", Map.of());
                        helper.succeed();
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                });
            } catch (Exception exception) {
                try { factory.close(); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
                try { restartTickets(helper, BlockPos.of(state.origin()), false); } catch (Exception cleanup) { exception.addSuppressed(cleanup); }
                throw new IllegalStateException("Restart recovery failed", exception);
            }
        });
    }

    private static void restartTickets(GameTestHelper helper, BlockPos origin, boolean add) {
        var low = new net.minecraft.world.level.ChunkPos(origin.offset(-16, 0, -16));
        var high = new net.minecraft.world.level.ChunkPos(origin.offset(48, 0, 48));
        var chunks = new ArrayList<net.minecraft.world.level.ChunkPos>();
        for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) chunks.add(new net.minecraft.world.level.ChunkPos(x, z));
        space.controlnet.ae2federation.test.world.RestartChunkTickets.force(helper.getLevel(), origin, chunks, add);
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
        resumeSprintAfterUnload();
        var failures = new ArrayList<Exception>();
        cleanup.put("networkIds", networks.stream().map(network -> network.value().toString()).sorted().toList());
        attempt(failures, () -> cleanup.put("before", PlaythroughAccounting.ledger(helper.getLevel(), networks)));
        attempt(failures, () -> { if (future != null && !future.isDone()) future.cancel(true); });
        for (var face : faces()) attempt(failures, () -> {
            if (entity(chestPos(face)) instanceof MEChestBlockEntity chest && chest.getMainNode().getGrid() != null) chest.getMainNode().getGrid().getCraftingService().getCpus().forEach(cpu -> { if (cpu.isBusy()) cpu.cancelJob(); });
        });
        attempt(failures, () -> { if (source != null) source.close(); });
        var service = PolicyService.get(helper.getLevel());
        for (var key : policies) attempt(failures, () -> service.configured(key).ifPresent(record -> service.edit(
                new PolicyEdit(key, service.revision(key), record.rule().withEnabled(false)))));
        attempt(failures, routers::close);
        for (var pos : placed) attempt(failures, () -> setBlock(pos, Blocks.AIR.defaultBlockState()));
        if (remote != null) attempt(failures, remote::close);
        attempt(failures, () -> helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                net.minecraft.world.phys.AABB.encapsulatingFullBlocks(absolute(topology == null ? new BlockPos(2, 1, 0) : BlockPos.ZERO),
                        absolute(new BlockPos(35, 35, 35)))).forEach(net.minecraft.world.entity.Entity::discard));
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
