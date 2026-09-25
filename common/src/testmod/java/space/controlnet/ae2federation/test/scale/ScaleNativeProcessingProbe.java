package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;
import space.controlnet.ae2federation.test.mixed.MixedFactoryObservation;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;
import space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures;

public final class ScaleNativeProcessingProbe implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleNativeProcessingProbe.class);
    private static final AEItemKey INPUT = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey SECOND_INPUT = AEItemKey.of(Items.DIRT);
    private static final AEItemKey SECOND_OUTPUT = AEItemKey.of(Items.GOLD_INGOT);
    private final GameTestHelper helper;
    private final PatternProviderLogic provider;
    private final ProcessingCraftingGrid crafting;
    private final MixedMachineBlockEntity machine;
    private final NativeCraftingRequester requester;
    private final String layout;
    private final ScaleFederationIdentityTarget federationTarget;
    private final ScaleNativeSubnetTarget subnetTarget;
    private final ScaleNativeSubnetTarget secondSubnetTarget;
    private ScaleNativeSubnetTarget thirdSubnetTarget;
    private ScaleNativeSubnetTarget fourthSubnetTarget;
    private PatternProviderLogic thirdProvider;
    private PatternProviderLogic fourthProvider;
    private MixedMachineBlockEntity thirdMachine;
    private MixedMachineBlockEntity fourthMachine;
    private PatternProviderLogic secondProvider;
    private final MixedMachineBlockEntity secondMachine;
    private List<MixedMachineBlockEntity> directMachines;
    private List<PatternProviderLogic> directProviders;
    private ScaleFederationRoute federationRoute;
    private ScaleFederationIdentityTarget secondFederationTarget;
    private ScaleFederationTwoTargetRoute twoTargetRoute;
    private ScaleFederationThirdRoute thirdFederationRoute;
    private ScaleFederationIdentityTarget thirdFederationTarget;
    private ScaleFederationIdentityTarget fourthFederationTarget;
    private ScaleFederationFourthRoute fourthFederationRoute;
    private MixedMachineBlockEntity secondFederationMachine;
    private net.neoforged.neoforge.items.IItemHandler federationReturnHandler;
    private final net.neoforged.neoforge.items.IItemHandler[] twoTargetReturnHandlers = new net.neoforged.neoforge.items.IItemHandler[4];
    private final boolean twoPatterns;
    private final boolean catalog;
    private final int quantity;
    private AEItemKey highInput;
    private AEItemKey highOutput;
    private MixedMachineBlockEntity highAuthorizationMachine;
    private PatternProviderLogic highAuthorizationProvider;
    private List<CatalogSelection> catalogRun;
    private boolean threeTargetCatalog;
    private boolean fourTargetCatalog;
    private ScaleDriveRetention retention;
    private Future<ICraftingPlan> plan;
    private int stage;
    private int jobIndex;
    private int directDestinationWait;
    private String firstJobId;
    private String secondJobIds;
    private boolean targetInputObserved;
    private space.controlnet.ae2federation.processing.provider.AuthorizedLaneIdentity activeFederationLane;

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting) {
        this(helper, provider.lane(0), crafting, placeMachine(helper), "native-big-grid", null,
                () -> installPattern(provider), false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, boolean twoPatterns) {
        this(helper, provider, crafting, twoPatterns, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, boolean twoPatterns, boolean catalog) {
        this(helper, provider, crafting, twoPatterns, catalog, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, boolean twoPatterns, boolean catalog, boolean batch16) {
        this(helper, provider.lane(0), crafting, placeMachine(helper), "native-big-grid", null,
                () -> {
                    if (catalog) {
                        ScaleProcessingCatalog.install(provider);
                    } else {
                        installPattern(provider);
                        provider.installPattern(1, List.of(ProcessingRegressionFixtures.item(Items.DIRT, 1)),
                                List.of(ProcessingRegressionFixtures.item(Items.GOLD_INGOT, 1)));
                    }
                }, twoPatterns, catalog, batch16);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleNativeSubnetTarget target) {
        this(helper, provider.lane(0), crafting, target.machine(), "native-subnet", null,
                () -> installPattern(provider), false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleNativeSubnetTarget target, boolean batch16) {
        this(helper, provider.lane(0), crafting, target.machine(), "native-subnet", null,
                () -> ScaleProcessingCatalog.install(provider), true, true, batch16, target);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleNativeSubnetTarget first, ScaleNativeSubnetTarget second) {
        this(helper, provider, crafting, first, second, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleNativeSubnetTarget first, ScaleNativeSubnetTarget second,
            boolean catalogReplay) {
        this(helper, provider, crafting, first, second, catalogReplay, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleNativeSubnetTarget first, ScaleNativeSubnetTarget second,
            boolean catalogReplay, boolean fourSubnetCatalog) {
        this(helper, provider.lane(0), crafting, first.machine(),
                fourSubnetCatalog ? "native-subnet-four-targets" : "native-subnet", null,
                  () -> {
                      if (catalogReplay) ScaleProcessingCatalog.install(provider,
                              slot -> !fourSubnetCatalog || slot % 4 < 2);
                     else {
                         installPattern(provider);
                         provider.installPattern(1, List.of(ProcessingRegressionFixtures.item(Items.DIRT, 1)),
                                 List.of(ProcessingRegressionFixtures.item(Items.GOLD_INGOT, 1)));
                     }
                  }, true, catalogReplay, true, first, provider.lane(1), second);
    }

    public void selectFourSubnetCatalogRun(NativeProviderLaneFixtures remote, ScaleNativeSubnetTarget third,
            NativeProviderLaneFixtures fourthHost, ScaleNativeSubnetTarget fourth,
            ScaleDriveRetention driveRetention) {
        thirdSubnetTarget = third;
        thirdProvider = remote.lane(0);
        thirdMachine = third.machine();
        fourthSubnetTarget = fourth;
        fourthProvider = fourthHost.lane(0);
        fourthMachine = fourth.machine();
        fourTargetCatalog = true;
        retention = driveRetention;
        var machines = List.of(machine, secondMachine, thirdMachine, fourthMachine);
        var providers = List.of(provider, secondProvider, thirdProvider, fourthProvider);
        for (int lane = 2; lane < 4; lane++) {
            var selectedMachine = machines.get(lane);
            var returns = providers.get(lane).getReturnInv();
            var handler = new GenericStackItemStorage(returns);
            selectedMachine.processSingleItemPerTick();
            var recipes = new java.util.ArrayList<MixedMachineBlockEntity.MachineRecipe>();
            for (int slot = lane; slot < ScaleProcessingCatalog.SIZE; slot += 4) {
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                recipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                        false, returns, handler));
            }
            selectedMachine.configure(recipes);
        }
    }

    public void selectFourSubnetCatalog(List<CatalogSelection> selections) {
        if (!fourTargetCatalog || catalogRun != null || selections.size() != ScaleProcessingCatalog.SIZE) {
            throw new IllegalStateException("Expected four physical subnet catalog Lanes");
        }
        catalogRun = List.copyOf(selections);
        var machines = List.of(machine, secondMachine, thirdMachine, fourthMachine);
        var providers = List.of(provider, secondProvider, thirdProvider, fourthProvider);
        for (var selection : catalogRun) {
            var selectedMachine = machines.get(selection.slot() % 4);
            var returns = providers.get(selection.slot() % 4).getReturnInv();
            MixedFactoryObservation.authorizeMachine(selectedMachine, selectedMachine.inputHandler(),
                    selectedMachine.outputInventory(), returns, selection.input(), selection.output());
        }
    }

    public static ScaleNativeProcessingProbe oneGridTwoMachines(GameTestHelper helper,
            NativeProviderLaneFixtures provider, ProcessingCraftingGrid crafting) {
        return oneGridTwoMachines(helper, provider, crafting, false);
    }

    public static ScaleNativeProcessingProbe oneGridFourMachines(GameTestHelper helper,
            NativeProviderLaneFixtures provider, ProcessingCraftingGrid crafting) {
        return oneGridFourMachines(helper, provider, crafting, false);
    }

    public static ScaleNativeProcessingProbe oneGridFourMachines(GameTestHelper helper,
            NativeProviderLaneFixtures provider, ProcessingCraftingGrid crafting, boolean catalogReplay) {
        var probe = new ScaleNativeProcessingProbe(helper, provider.lane(0), crafting,
                placeMachine(helper, NativeProviderLaneFixtures.TARGET_POS), "native-one-grid-four-machines", null,
                () -> {
                    if (catalogReplay) ScaleProcessingCatalog.install(provider);
                    else {
                        installPattern(provider);
                        provider.installPattern(1, List.of(ProcessingRegressionFixtures.item(Items.DIRT, 1)),
                                List.of(ProcessingRegressionFixtures.item(Items.GOLD_INGOT, 1)));
                    }
                }, true, catalogReplay, true, null, provider.lane(1), null,
                placeMachine(helper, NativeProviderLaneFixtures.HOST_POS.south()));
        var machines = new java.util.ArrayList<MixedMachineBlockEntity>();
        machines.add(probe.machine);
        machines.add(probe.secondMachine);
        for (int lane = 2; lane < 4; lane++) {
            var machine = placeMachine(helper, NativeProviderLaneFixtures.HOST_POS.relative(
                    lane == 2 ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN));
            machine.processSingleItemPerTick();
            var returns = provider.lane(lane).getReturnInv();
            var handler = new GenericStackItemStorage(returns);
            var recipes = new java.util.ArrayList<MixedMachineBlockEntity.MachineRecipe>();
            for (int slot = lane; slot < (catalogReplay ? ScaleProcessingCatalog.SIZE : 4); slot += 4) {
                var recipe = ScaleProcessingCatalog.recipes().get(slot);
                recipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                        false, returns, handler));
                if (!catalogReplay) provider.installPattern(slot,
                        List.of(ProcessingRegressionFixtures.item(recipe.input(), 1)),
                        List.of(ProcessingRegressionFixtures.item(recipe.output(), 1)));
            }
            machine.configure(recipes);
            machines.add(machine);
        }
        probe.directMachines = List.copyOf(machines);
        probe.directProviders = java.util.stream.IntStream.range(0, 4)
                .mapToObj(lane -> (PatternProviderLogic) provider.lane(lane)).toList();
        return probe;
    }

    public void selectFourMachineRun(ScaleDriveRetention driveRetention) {
        var selections = new java.util.ArrayList<CatalogSelection>();
        for (int slot = 0; slot < 4; slot++) {
            var recipe = ScaleProcessingCatalog.recipes().get(slot);
            selections.add(new CatalogSelection(slot, AEItemKey.of(recipe.input()), AEItemKey.of(recipe.output())));
            MixedFactoryObservation.authorizeMachine(directMachines.get(slot), directMachines.get(slot).inputHandler(),
                    directMachines.get(slot).outputInventory(), directProviders.get(slot).getReturnInv(),
                    AEItemKey.of(recipe.input()), AEItemKey.of(recipe.output()));
        }
        catalogRun = List.copyOf(selections);
        retention = driveRetention;
    }

    public void selectFourMachineCatalogRun(List<CatalogSelection> selections, ScaleDriveRetention driveRetention) {
        if (directMachines == null || !catalog || selections.size() != ScaleProcessingCatalog.SIZE) {
            throw new IllegalStateException("Expected four physical machine catalog Lanes");
        }
        selectCatalogRun(selections, driveRetention);
    }

    public static ScaleNativeProcessingProbe oneGridTwoMachines(GameTestHelper helper,
            NativeProviderLaneFixtures provider, ProcessingCraftingGrid crafting, boolean catalog) {
        return new ScaleNativeProcessingProbe(helper, provider.lane(0), crafting,
                 placeMachine(helper, NativeProviderLaneFixtures.TARGET_POS), "native-one-grid-two-machines", null,
                 () -> {
                     if (catalog) ScaleProcessingCatalog.install(provider);
                     else {
                         installPattern(provider);
                         provider.installPattern(1, List.of(ProcessingRegressionFixtures.item(Items.DIRT, 1)),
                                 List.of(ProcessingRegressionFixtures.item(Items.GOLD_INGOT, 1)));
                     }
                 }, true, catalog, true, null, provider.lane(1), null,
                 placeMachine(helper, NativeProviderLaneFixtures.HOST_POS.south()));
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget target) {
        this(helper, provider.lane(0), crafting, target.placeProcessingMachine(), "federation", target,
                () -> installPattern(provider), false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget target, ScaleFederationRoute route) {
        this(helper, provider.lane(0), crafting, target.placeProcessingMachine(), "federation", target,
                () -> ScaleProcessingCatalog.install(provider), true, true, true);
        federationRoute = route;
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget first,
            ScaleFederationIdentityTarget second, ScaleFederationTwoTargetRoute route) {
        this(helper, provider, crafting, first, second, route, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget first,
            ScaleFederationIdentityTarget second, ScaleFederationTwoTargetRoute route, boolean catalogReplay) {
        this(helper, provider, crafting, first, second, route, catalogReplay, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget first,
            ScaleFederationIdentityTarget second, ScaleFederationTwoTargetRoute route, boolean catalogReplay,
            boolean threeTargetCatalog) {
        this(helper, provider, crafting, first, second, route, catalogReplay, threeTargetCatalog, false);
    }

    public ScaleNativeProcessingProbe(GameTestHelper helper, NativeProviderLaneFixtures provider,
            ProcessingCraftingGrid crafting, ScaleFederationIdentityTarget first,
            ScaleFederationIdentityTarget second, ScaleFederationTwoTargetRoute route, boolean catalogReplay,
            boolean threeTargetCatalog, boolean fourTargetCatalog) {
        this(helper, provider.lane(0), crafting, first.placeProcessingMachine(), "federation-two-targets", first,
                   () -> {
                    if (catalogReplay) ScaleProcessingCatalog.install(provider,
                            slot -> fourTargetCatalog ? slot % 4 < 2 : !threeTargetCatalog || slot % 3 != 2);
                    else {
                        installPattern(provider);
                        provider.installPattern(1, List.of(ProcessingRegressionFixtures.item(Items.DIRT, 1)),
                                List.of(ProcessingRegressionFixtures.item(Items.GOLD_INGOT, 1)));
                    }
                }, true, catalogReplay, true);
        secondFederationTarget = second;
        secondProvider = provider.lane(1);
        secondFederationMachine = second.placeProcessingMachine();
        secondFederationMachine.processSingleItemPerTick();
        twoTargetRoute = route;
        this.threeTargetCatalog = threeTargetCatalog;
        this.fourTargetCatalog = fourTargetCatalog;
    }

    private static void installPattern(NativeProviderLaneFixtures provider) {
        provider.installPattern(0, List.of(ProcessingRegressionFixtures.item(Items.COBBLESTONE, 1)),
                List.of(ProcessingRegressionFixtures.item(Items.DIAMOND, 1)));
    }

    private static MixedMachineBlockEntity placeMachine(GameTestHelper helper) {
        return placeMachine(helper, NativeProviderLaneFixtures.TARGET_POS);
    }

    private static MixedMachineBlockEntity placeMachine(GameTestHelper helper, net.minecraft.core.BlockPos position) {
        helper.setBlock(position, MixedMachineRegistration.BLOCK.get());
        return helper.getBlockEntity(position);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns) {
        this(helper, provider, crafting, machine, layout, federationTarget, installPattern, twoPatterns, false);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns, boolean catalog) {
        this(helper, provider, crafting, machine, layout, federationTarget, installPattern, twoPatterns, catalog, false);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns, boolean catalog,
            boolean batch16) {
        this(helper, provider, crafting, machine, layout, federationTarget, installPattern, twoPatterns, catalog,
                batch16, null);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns, boolean catalog,
            boolean batch16, ScaleNativeSubnetTarget subnetTarget) {
        this(helper, provider, crafting, machine, layout, federationTarget, installPattern, twoPatterns, catalog,
                batch16, subnetTarget, null, null);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns, boolean catalog,
            boolean batch16, ScaleNativeSubnetTarget subnetTarget, PatternProviderLogic secondProvider,
            ScaleNativeSubnetTarget secondSubnetTarget) {
        this(helper, provider, crafting, machine, layout, federationTarget, installPattern, twoPatterns, catalog,
                batch16, subnetTarget, secondProvider, secondSubnetTarget, null);
    }

    private ScaleNativeProcessingProbe(GameTestHelper helper, PatternProviderLogic provider,
            ProcessingCraftingGrid crafting, MixedMachineBlockEntity machine, String layout,
            ScaleFederationIdentityTarget federationTarget, Runnable installPattern, boolean twoPatterns, boolean catalog,
            boolean batch16, ScaleNativeSubnetTarget subnetTarget, PatternProviderLogic secondProvider,
            ScaleNativeSubnetTarget secondSubnetTarget, MixedMachineBlockEntity directSecondMachine) {
        this.helper = helper;
        this.provider = provider;
        this.crafting = crafting;
        this.layout = layout;
        this.federationTarget = federationTarget;
        this.subnetTarget = subnetTarget;
        this.secondSubnetTarget = secondSubnetTarget;
        this.secondProvider = secondProvider;
        this.secondMachine = directSecondMachine != null ? directSecondMachine
                : secondSubnetTarget == null ? null : secondSubnetTarget.machine();
        this.twoPatterns = twoPatterns;
        this.catalog = catalog;
        this.quantity = batch16 ? 16 : 1;
        MixedFactoryObservation.begin(0, false);
        this.machine = machine;
        if (batch16) machine.processSingleItemPerTick();
        var returns = provider.getReturnInv();
        if (federationTarget == null) {
            var returnHandler = new GenericStackItemStorage(returns);
            var recipes = new java.util.ArrayList<MixedMachineBlockEntity.MachineRecipe>();
            recipes.add(new MixedMachineBlockEntity.MachineRecipe(Items.COBBLESTONE, Items.DIAMOND,
                    false, returns, returnHandler));
            if (catalog) {
                 for (int slot = 1; slot < ScaleProcessingCatalog.SIZE; slot++) {
                      if (secondMachine != null && ((layout.equals("native-one-grid-four-machines")
                              || layout.equals("native-subnet-four-targets"))
                              ? slot % 4 != 0 : slot % 2 != 0)) continue;
                     var recipe = ScaleProcessingCatalog.recipes().get(slot);
                     recipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                             false, returns, returnHandler));
                }
            } else if (twoPatterns && secondSubnetTarget == null && directSecondMachine == null) {
                recipes.add(new MixedMachineBlockEntity.MachineRecipe(Items.DIRT, Items.GOLD_INGOT,
                        false, returns, returnHandler));
            }
            machine.configure(recipes);
            MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                    returns, INPUT, OUTPUT);
            if (twoPatterns) {
                if (secondMachine == null) {
                    MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                            returns, SECOND_INPUT, SECOND_OUTPUT);
                } else {
                    var secondReturns = secondProvider.getReturnInv();
                    secondMachine.processSingleItemPerTick();
                     var secondReturnHandler = new GenericStackItemStorage(secondReturns);
                     var secondRecipes = new java.util.ArrayList<MixedMachineBlockEntity.MachineRecipe>();
                     if (catalog) {
                          for (int slot = 1; slot < ScaleProcessingCatalog.SIZE;
                                   slot += layout.equals("native-one-grid-four-machines")
                                           || layout.equals("native-subnet-four-targets") ? 4 : 2) {
                             var recipe = ScaleProcessingCatalog.recipes().get(slot);
                             secondRecipes.add(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                                     false, secondReturns, secondReturnHandler));
                         }
                     } else secondRecipes.add(new MixedMachineBlockEntity.MachineRecipe(Items.DIRT,
                             Items.GOLD_INGOT, false, secondReturns, secondReturnHandler));
                     secondMachine.configure(secondRecipes);
                    MixedFactoryObservation.authorizeMachine(secondMachine, secondMachine.inputHandler(),
                            secondMachine.outputInventory(), secondReturns, SECOND_INPUT, SECOND_OUTPUT);
                }
            }
        }
        installPattern.run();
        var sourceId = FabricRegistryAccess.confirmedNetworkId(crafting.node().getGrid()).orElseThrow();
        requester = new NativeCraftingRequester(helper.getLevel(),
                helper.absolutePos(ProcessingCraftingGrid.REQUESTER_POS), crafting.storage(), sourceId, null);
        requester.connect(crafting.node());
    }

    public void selectHighPattern(AEItemKey input, AEItemKey output) {
        if (!catalog || highInput != null && (!highInput.equals(input) || !highOutput.equals(output))) {
            throw new IllegalStateException("Physical high-slot Processing identity changed");
        }
        highInput = input;
        highOutput = output;
        var selectedMachine = directMachines != null ? directMachines.get(ScaleProcessingCatalog.SIZE - 1 & 3)
                : fourTargetCatalog ? fourthMachine : threeTargetCatalog ? machine
                : secondMachine != null ? secondMachine : machine;
        var selectedProvider = directProviders != null ? directProviders.get(ScaleProcessingCatalog.SIZE - 1 & 3)
                : fourTargetCatalog ? fourthProvider : threeTargetCatalog ? provider
                : secondMachine != null ? secondProvider : provider;
        if (threeTargetCatalog || fourTargetCatalog) {
            highAuthorizationMachine = selectedMachine;
            highAuthorizationProvider = selectedProvider;
        }
        MixedFactoryObservation.authorizeMachine(selectedMachine, selectedMachine.inputHandler(),
                selectedMachine.outputInventory(), selectedProvider.getReturnInv(), input, output);
    }

    public void selectCatalogRun(List<CatalogSelection> selections, ScaleDriveRetention driveRetention) {
        if (!catalog || quantity != 16 || catalogRun != null
                || selections.size() != 3 && selections.size() != 64
                        && selections.size() != ScaleProcessingCatalog.SIZE) {
            throw new IllegalStateException("Expected one physical catalog replay");
        }
        catalogRun = List.copyOf(selections);
        retention = driveRetention;
        for (var selection : catalogRun) {
            var secondLane = selection.slot() % 2 != 0;
            var selectedMachine = directMachines != null ? directMachines.get(selection.slot() % 4)
                    : secondLane && secondFederationMachine != null ? secondFederationMachine
                    : secondLane && secondMachine != null ? secondMachine : machine;
            var selectedProvider = directProviders != null ? directProviders.get(selection.slot() % 4)
                    : secondLane && (secondMachine != null || secondFederationMachine != null)
                    ? secondProvider : provider;
            MixedFactoryObservation.authorizeMachine(selectedMachine, selectedMachine.inputHandler(),
                    selectedMachine.outputInventory(), selectedProvider.getReturnInv(),
                    selection.input(), selection.output());
        }
    }

    public record CatalogSelection(int slot, AEItemKey input, AEItemKey output) {
    }

    public void selectTwoTargetRun(ScaleDriveRetention driveRetention) {
        if (secondMachine == null && secondFederationTarget == null || catalogRun != null) {
            throw new IllegalStateException("Expected two physical targets");
        }
        catalogRun = List.of(new CatalogSelection(0, INPUT, OUTPUT),
                new CatalogSelection(1, SECOND_INPUT, SECOND_OUTPUT));
        retention = driveRetention;
    }

    public void selectThreeTargetRun(NativeProviderLaneFixtures remote, ScaleNativeSubnetTarget third,
            ScaleDriveRetention driveRetention) {
        if (secondSubnetTarget == null || catalogRun != null) {
            throw new IllegalStateException("Expected two existing physical subnet targets");
        }
        thirdSubnetTarget = third;
        thirdProvider = remote.lane(0);
        thirdMachine = third.machine();
        thirdMachine.processSingleItemPerTick();
        var recipe = ScaleProcessingCatalog.recipes().get(2);
        var returns = thirdProvider.getReturnInv();
        thirdMachine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                false, returns, new GenericStackItemStorage(returns))));
        var thirdInput = AEItemKey.of(recipe.input());
        var thirdOutput = AEItemKey.of(recipe.output());
        MixedFactoryObservation.authorizeMachine(thirdMachine, thirdMachine.inputHandler(),
                thirdMachine.outputInventory(), returns, thirdInput, thirdOutput);
        catalogRun = List.of(new CatalogSelection(0, INPUT, OUTPUT),
                new CatalogSelection(1, SECOND_INPUT, SECOND_OUTPUT), new CatalogSelection(2, thirdInput, thirdOutput));
        retention = driveRetention;
    }

    public void selectFourTargetRun(NativeProviderLaneFixtures remote, ScaleNativeSubnetTarget fourth) {
        if (thirdSubnetTarget == null || catalogRun == null || catalogRun.size() != 3) {
            throw new IllegalStateException("Expected three existing physical subnet jobs");
        }
        fourthSubnetTarget = fourth;
        fourthProvider = remote.lane(0);
        fourthMachine = fourth.machine();
        fourthMachine.processSingleItemPerTick();
        var recipe = ScaleProcessingCatalog.recipes().get(3);
        var returns = fourthProvider.getReturnInv();
        fourthMachine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                false, returns, new GenericStackItemStorage(returns))));
        var input = AEItemKey.of(recipe.input());
        var output = AEItemKey.of(recipe.output());
        MixedFactoryObservation.authorizeMachine(fourthMachine, fourthMachine.inputHandler(),
                fourthMachine.outputInventory(), returns, input, output);
        catalogRun = List.of(catalogRun.get(0), catalogRun.get(1), catalogRun.get(2),
                new CatalogSelection(3, input, output));
    }

    public void selectFederationThreeTargetRun(NativeProviderLaneFixtures remote,
            ScaleFederationIdentityTarget third, ScaleFederationThirdRoute route,
            ScaleDriveRetention driveRetention) {
        selectFederationThreeTargetRun(remote, third, route, driveRetention, false);
    }

    public void selectFederationThreeTargetRun(NativeProviderLaneFixtures remote,
            ScaleFederationIdentityTarget third, ScaleFederationThirdRoute route,
            ScaleDriveRetention driveRetention, boolean catalogReplay) {
        if (twoTargetRoute == null || catalogRun != null) {
            throw new IllegalStateException("Expected two established physical Federation routes");
        }
        thirdFederationRoute = route;
        thirdFederationTarget = third;
        thirdProvider = remote.lane(0);
        thirdMachine = third.placeProcessingMachine();
        thirdMachine.processSingleItemPerTick();
        var recipe = ScaleProcessingCatalog.recipes().get(2);
        if (catalogReplay) {
            var selections = new java.util.ArrayList<CatalogSelection>();
            for (int slot = 0; slot < ScaleProcessingCatalog.SIZE; slot++) {
                var selected = ScaleProcessingCatalog.recipes().get(slot);
                selections.add(new CatalogSelection(slot, AEItemKey.of(selected.input()), AEItemKey.of(selected.output())));
            }
            catalogRun = List.copyOf(selections);
        } else catalogRun = List.of(new CatalogSelection(0, INPUT, OUTPUT),
                new CatalogSelection(1, SECOND_INPUT, SECOND_OUTPUT),
                new CatalogSelection(2, AEItemKey.of(recipe.input()), AEItemKey.of(recipe.output())));
        retention = driveRetention;
        if (!fourTargetCatalog) {
            for (var selection : catalogRun) {
                var targetLane = threeTargetCatalog ? selection.slot() % 3 : selection.slot();
                var selectedMachine = targetLane == 2 ? thirdMachine
                        : targetLane == 1 ? secondFederationMachine : machine;
                var selectedProvider = targetLane == 2 ? thirdProvider
                        : targetLane == 1 ? secondProvider : provider;
                MixedFactoryObservation.authorizeMachine(selectedMachine, selectedMachine.inputHandler(),
                        selectedMachine.outputInventory(), selectedProvider.getReturnInv(),
                        selection.input(), selection.output());
            }
            if (catalogReplay) selectHighPattern(catalogRun.get(255).input(), catalogRun.get(255).output());
        }
    }

    public void selectFederationFourthTargetRun(NativeProviderLaneFixtures remote,
            ScaleFederationIdentityTarget fourth, ScaleFederationFourthRoute route) {
        if (thirdFederationTarget == null || catalogRun == null
                || catalogRun.size() != (fourTargetCatalog ? ScaleProcessingCatalog.SIZE : 3)) {
            throw new IllegalStateException("Expected three physical Federation jobs before D selection");
        }
        fourthFederationTarget = fourth;
        fourthFederationRoute = route;
        fourthProvider = remote.lane(0);
        fourthMachine = fourth.placeProcessingMachine();
        fourthMachine.processSingleItemPerTick();
        if (fourTargetCatalog) {
            for (var selection : catalogRun) {
                var lane = selection.slot() % 4;
                var selectedMachine = lane == 3 ? fourthMachine : lane == 2 ? thirdMachine
                        : lane == 1 ? secondFederationMachine : machine;
                var selectedProvider = lane == 3 ? fourthProvider : lane == 2 ? thirdProvider
                        : lane == 1 ? secondProvider : provider;
                MixedFactoryObservation.authorizeMachine(selectedMachine, selectedMachine.inputHandler(),
                        selectedMachine.outputInventory(), selectedProvider.getReturnInv(),
                        selection.input(), selection.output());
            }
            var high = catalogRun.get(255);
            selectHighPattern(high.input(), high.output());
        } else {
            var recipe = ScaleProcessingCatalog.recipes().get(3);
            var input = AEItemKey.of(recipe.input());
            var output = AEItemKey.of(recipe.output());
            MixedFactoryObservation.authorizeMachine(fourthMachine, fourthMachine.inputHandler(),
                    fourthMachine.outputInventory(), fourthProvider.getReturnInv(), input, output);
            catalogRun = List.of(catalogRun.get(0), catalogRun.get(1), catalogRun.get(2),
                    new CatalogSelection(3, input, output));
        }
    }

    public boolean tick() {
        if (threeTargetCatalog) helper.assertTrue((ScaleProcessingCatalog.SIZE - 1) % 3 == 0
                        && highAuthorizationMachine == machine && highAuthorizationProvider == provider,
                "Global high slot 255 must authorize only its modulo-three A machine and Provider");
        if (fourTargetCatalog) helper.assertTrue((ScaleProcessingCatalog.SIZE - 1) % 4 == 3
                        && highAuthorizationMachine == fourthMachine && highAuthorizationProvider == fourthProvider,
                "Global high slot 255 must authorize only its modulo-four D machine and Provider");
        var selection = catalogRun == null ? null : catalogRun.get(jobIndex);
        var secondLane = (secondMachine != null || secondFederationTarget != null)
                && (selection == null ? jobIndex == 1
                        : selection.slot() % (fourTargetCatalog ? 4 : threeTargetCatalog ? 3 : 2) == 1);
        var thirdLane = (thirdSubnetTarget != null || thirdFederationTarget != null)
                && selection != null && (fourTargetCatalog ? selection.slot() % 4 == 2
                        : threeTargetCatalog ? selection.slot() % 3 == 2 : selection.slot() == 2);
        var fourthLane = (fourthSubnetTarget != null || fourthFederationTarget != null)
                && selection != null && (fourTargetCatalog ? selection.slot() % 4 == 3 : selection.slot() == 3);
        var subnetTarget = fourthLane ? fourthSubnetTarget : thirdLane ? thirdSubnetTarget
                : secondSubnetTarget != null && secondLane ? secondSubnetTarget : this.subnetTarget;
        var otherTargets = thirdSubnetTarget == null
                ? secondSubnetTarget == null ? List.<ScaleNativeSubnetTarget>of()
                        : List.of(secondLane ? this.subnetTarget : secondSubnetTarget)
                : (fourthSubnetTarget == null ? List.of(this.subnetTarget, secondSubnetTarget, thirdSubnetTarget)
                        : List.of(this.subnetTarget, secondSubnetTarget, thirdSubnetTarget, fourthSubnetTarget)).stream()
                        .filter(target -> target != subnetTarget).toList();
        var federationTarget = fourthFederationTarget != null && fourthLane ? fourthFederationTarget
                : thirdFederationTarget != null && thirdLane ? thirdFederationTarget
                : secondFederationTarget != null && secondLane ? secondFederationTarget : this.federationTarget;
        var otherFederationTarget = secondFederationTarget == null ? null
                : secondLane ? this.federationTarget : secondFederationTarget;
        var otherFederationTargets = thirdFederationTarget == null ? List.<ScaleFederationIdentityTarget>of()
                : (fourthFederationTarget == null
                        ? List.of(this.federationTarget, secondFederationTarget, thirdFederationTarget)
                        : List.of(this.federationTarget, secondFederationTarget, thirdFederationTarget,
                                fourthFederationTarget)).stream()
                        .filter(target -> target != federationTarget).toList();
        var provider = fourthLane ? fourthProvider : thirdLane ? thirdProvider
                : directMachines != null ? directProviders.get(selection.slot() % 4)
                : secondLane ? secondProvider : this.provider;
        var machine = directMachines != null ? directMachines.get(selection.slot() % 4)
                : fourthLane ? fourthMachine : thirdLane ? thirdMachine
                : secondFederationTarget != null && secondLane ? secondFederationMachine
                : secondMachine != null && secondLane ? secondMachine : this.machine;
        var inputKey = selection != null ? selection.input() : jobIndex == 0 ? INPUT : jobIndex == 1 ? SECOND_INPUT : highInput;
        var outputKey = selection != null ? selection.output() : jobIndex == 0 ? OUTPUT : jobIndex == 1 ? SECOND_OUTPUT : highOutput;
        if (catalog && (highInput == null || highOutput == null)) {
            throw new IllegalStateException("High physical Processing slot was not decoded");
        }
        if (!requester.isReady(crafting.node())
                || !crafting.node().getGrid().getCraftingService().isCraftable(outputKey)) {
            return false;
        }
        if (stage == 0) {
            if (twoTargetRoute != null) twoTargetRoute.assertReady();
            if (thirdFederationRoute != null) thirdFederationRoute.assertReady();
            if (fourthFederationRoute != null) fourthFederationRoute.assertReady();
            if ((federationRoute != null || twoTargetRoute != null) && (federationTarget.grid() == crafting.node().getGrid()
                    || federationTarget.inputAmount(inputKey) != 0
                     || otherFederationTarget != null && otherFederationTarget.inputAmount(inputKey) != 0
                     || otherFederationTargets.stream().anyMatch(target -> target.inputAmount(inputKey) != 0))) {
                throw new IllegalStateException("Federation target must be separate and empty before each job");
            }
            if (subnetTarget != null && (subnetTarget.grid() == crafting.node().getGrid()
                    || subnetTarget.inputAmount(inputKey) != 0
                     || otherTargets.stream().anyMatch(target -> target.inputAmount(inputKey) != 0))) {
                throw new IllegalStateException("Native target must be separate and empty before each job");
            }
            if (catalog && (catalogRun != null || jobIndex == 2) && (federationRoute == null && twoTargetRoute == null
                    && !machine.inputHandler().isItemValid(0,
                     new net.minecraft.world.item.ItemStack(inputKey.getItem()))
                    || crafting.node().getGrid().getCraftingService().getCraftingFor(outputKey).stream()
                            .noneMatch(pattern -> pattern.getPrimaryOutput().what().equals(outputKey)
                                    && pattern.getInputs().length == 1
                                    && pattern.getInputs()[0].getPossibleInputs().length == 1
                                    && pattern.getInputs()[0].getPossibleInputs()[0].what().equals(inputKey)))) {
                throw new IllegalStateException("High physical Processing recipe is not natively executable");
            }
            if (crafting.storage().insert(inputKey, quantity, Actionable.MODULATE, requester.actionSource()) != quantity) {
                throw new IllegalStateException("Physical source cell rejected the Processing input");
            }
            if (twoPatterns && (physical(inputKey) != quantity || physical(jobIndex == 0 ? SECOND_INPUT : INPUT) != 0)) {
                throw new IllegalStateException("Physical source input ownership is not per-key");
            }
            if (catalog && catalogRun == null && jobIndex == 2 && (physical(INPUT) != 0 || physical(SECOND_INPUT) != 0
                    || physical(highOutput) != 0 || physical(OUTPUT) != quantity
                    || physical(SECOND_OUTPUT) != quantity)) {
                throw new IllegalStateException("Earlier physical catalog outputs were not retained");
            }
            var sourceNode = crafting.node();
            plan = sourceNode.getGrid().getCraftingService().beginCraftingCalculation(helper.getLevel(),
                    new ICraftingSimulationRequester() {
                        @Override
                        public IActionSource getActionSource() { return requester.actionSource(); }

                        @Override
                        public appeng.api.networking.IGridNode getGridNode() { return sourceNode; }
                    }, outputKey, quantity, CalculationStrategy.REPORT_MISSING_ITEMS);
            stage = 1;
            return false;
        }
        if (stage == 1) {
            if (!plan.isDone()) return false;
            try {
                var result = plan.get();
                if (result.simulation() || !result.finalOutput().what().equals(outputKey)
                        || result.finalOutput().amount() != quantity) {
                    throw new IllegalStateException("Native Processing plan does not produce requested quantity");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Native Processing plan was interrupted", exception);
            } catch (ExecutionException exception) {
                throw new IllegalStateException("Native Processing planner failed", exception);
            }
            stage = 2;
        }
        if (stage == 2) {
            if (!requester.handleCrafting(outputKey, quantity, helper.getLevel(),
                    crafting.node().getGrid().getCraftingService())) return false;
            stage = 3;
        }
        if (stage == 3 && subnetTarget != null) {
            var wrongTarget = otherTargets.stream().filter(target -> target.inputAmount(inputKey) != 0)
                    .findFirst();
            if (wrongTarget.isPresent()) {
                throw new IllegalStateException("Native target " + (fourthLane ? "D" : thirdLane ? "C" : secondLane ? "B" : "A")
                        + " missing work: opposite physical target received " + wrongTarget.orElseThrow().inputAmount(inputKey)
                        + " units of " + inputKey.getId());
            }
            if (subnetTarget.inputAmount(inputKey) != quantity) return false;
            if (secondSubnetTarget != null) {
                helper.assertValueEqual(physical(inputKey), 0L,
                        "Source cell must release its 16 inputs before the selected target Export Bus is enabled");
                for (var otherTarget : otherTargets) helper.assertValueEqual(otherTarget.inputAmount(inputKey), 0L,
                        "Unselected native target must not receive selected Lane input");
            }
            LOGGER.info("AE2F_SCALE_SUBNET physicalSlot={} inputKey={} targetInputBefore={} oppositeInput=0 lane={} sourceGrid={} targetGrid={} separate={}",
                    selection.slot(), inputKey.getId(), subnetTarget.inputAmount(inputKey),
                      fourthLane ? 3 : thirdLane ? 2 : secondLane ? 1 : 0,
                    System.identityHashCode(crafting.node().getGrid()), System.identityHashCode(subnetTarget.grid()),
                    crafting.node().getGrid() != subnetTarget.grid());
            subnetTarget.export(inputKey);
            targetInputObserved = true;
            stage = 4;
        }
        if (stage == 3 && federationTarget != null) {
            if (twoTargetRoute != null && !thirdLane && !fourthLane)
                twoTargetRoute.assertSelectedLaneDestination(secondLane ? 1 : 0, inputKey);
            if ((thirdLane || fourthLane) && thirdFederationRoute != null
                    && federationTarget.inputAmount(inputKey) == 0
                    && otherFederationTargets.stream().anyMatch(target -> target.inputAmount(inputKey) != 0)) {
                throw new IllegalStateException("Federation target " + (fourthLane ? "D" : "C")
                        + " missing work: remote host resolved another Endpoint");
            }
            for (var other : otherFederationTargets) helper.assertValueEqual(other.inputAmount(inputKey), 0L,
                    "Unselected Federation target must not receive selected input");
            if (otherFederationTarget != null && otherFederationTarget.inputAmount(inputKey) != 0) {
                throw new IllegalStateException("Federation target " + (secondLane ? "B" : "A")
                        + " missing work: opposite physical target received "
                        + otherFederationTarget.inputAmount(inputKey) + " units of " + inputKey.getId());
            }
            var context = federationTarget.endpoint().binding().runtime().itemReturnContext();
            if (context.isEmpty() || federationTarget.inputAmount(inputKey) != quantity) return false;
            var owner = context.orElseThrow().owner();
            var handler = federationTarget.returnHandler();
            if (owner.logic() != provider || owner.inventory() != provider.getReturnInv()
                    || owner.lane().isEmpty() || handler != context.orElseThrow().capability()) {
                throw new IllegalStateException("Federation Endpoint return is not owned by the selected native Lane");
            }
            activeFederationLane = owner.lane().orElseThrow();
            if (federationRoute != null || twoTargetRoute != null) {
                if (federationRoute != null) federationRoute.assertNativeJobLane(owner.lane().orElseThrow());
                else if (fourthLane) fourthFederationRoute.assertNativeJobLane(owner.lane().orElseThrow());
                else if (thirdLane) thirdFederationRoute.assertNativeJobLane(owner.lane().orElseThrow());
                else twoTargetRoute.assertNativeJobLane(secondLane ? 1 : 0, owner.lane().orElseThrow());
                helper.assertValueEqual(physical(inputKey), 0L,
                        "Source physical input must be routed before target Export Bus activation");
                helper.assertValueEqual(federationTarget.inputAmount(inputKey), 16L,
                        "Target physical cell must hold 16 native-routed inputs before Export Bus activation");
                var targetLane = fourthLane ? 3 : thirdLane ? 2 : secondLane ? 1 : 0;
                if (twoTargetRoute == null ? jobIndex == 0
                        : twoTargetReturnHandlers[targetLane] == null) {
                    if (twoTargetRoute != null && !thirdLane) {
                        helper.assertValueEqual(otherFederationTarget.inputAmount(inputKey), 0L,
                                "Opposite Federation target cell must not receive this Lane's input");
                    }
                    var recipes = ScaleProcessingCatalog.recipes().stream()
                            .map(recipe -> new MixedMachineBlockEntity.MachineRecipe(recipe.input(), recipe.output(),
                                    false, owner.inventory(), handler)).toList();
                    if (twoTargetRoute != null) {
                        if (catalogRun != null && catalogRun.size() == ScaleProcessingCatalog.SIZE) {
                            recipes = java.util.stream.IntStream.range(0, ScaleProcessingCatalog.SIZE)
                                     .filter(slot -> slot % (fourTargetCatalog ? 4 : threeTargetCatalog ? 3 : 2)
                                             == targetLane)
                                    .mapToObj(slot -> ScaleProcessingCatalog.recipes().get(slot))
                                    .map(recipe -> new MixedMachineBlockEntity.MachineRecipe(recipe.input(),
                                            recipe.output(), false, owner.inventory(), handler)).toList();
                        } else recipes = List.of(new MixedMachineBlockEntity.MachineRecipe(
                                inputKey.getItem(), outputKey.getItem(), false, owner.inventory(), handler));
                        twoTargetReturnHandlers[targetLane] = handler;
                    }
                    machine.configure(recipes);
                } else if (handler != (twoTargetRoute == null ? federationReturnHandler
                        : twoTargetReturnHandlers[targetLane])) {
                    throw new IllegalStateException("Federation Endpoint return capability changed between selected jobs");
                }
                federationReturnHandler = handler;
                LOGGER.info("AE2F_SCALE_FEDERATION physicalSlot={} inputKey={} sourceInputBefore={} targetInputBefore={} sourceGrid={} targetGrid={} route=ACTIVE returnOwner=exact lane={}",
                        selection.slot(), inputKey.getId(), physical(inputKey), federationTarget.inputAmount(inputKey),
                        System.identityHashCode(crafting.node().getGrid()), System.identityHashCode(federationTarget.grid()),
                          targetLane);
                federationTarget.export(inputKey);
            } else machine.configure(List.of(new MixedMachineBlockEntity.MachineRecipe(Items.COBBLESTONE, Items.DIAMOND,
                    false, owner.inventory(), handler)));
            MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                    owner.inventory(), inputKey, outputKey);
            targetInputObserved = true;
            stage = 4;
        }
        if (stage == 3 && directMachines != null && requester.acceptedAmount(outputKey) == 0
                && ++directDestinationWait > 80) {
            helper.fail("Native machine missing work after submitted Lane " + selection.slot()
                    + " job: selectedInput=" + machine.inputCount(inputKey.getItem())
                    + " sourceInput=" + physical(inputKey));
        }
        if (stage == 3 && directMachines == null && secondMachine != null && secondSubnetTarget == null && secondLane
                 && requester.acceptedAmount(outputKey) == 0 && ++directDestinationWait > 80) {
            helper.fail("Native machine B missing work after submitted Lane 1 job for physical slot "
                    + (selection == null ? 1 : selection.slot()) + ": eastInput=" + this.machine.inputCount(inputKey.getItem())
                    + " southInput=" + secondMachine.inputCount(inputKey.getItem())
                    + " sourceInput=" + physical(inputKey));
        }
        if (stage == 3 && directMachines != null) {
            for (int lane = 0; lane < directMachines.size(); lane++) {
                if (lane != selection.slot() % 4) {
                    helper.assertValueEqual(directMachines.get(lane).inputCount(inputKey.getItem()), 0L,
                            "Unselected physical machine must not receive selected input: " + lane);
                }
            }
        }
        if ((stage == 3 && federationTarget == null && subnetTarget == null
                || stage == 4 && (federationTarget != null || subnetTarget != null))
                && requester.observedDone() && requester.acceptedAmount(outputKey) == quantity
                && requester.activeLink() == null) {
            var input = physical(inputKey);
            var physicalOutput = physical(outputKey);
            var transitions = MixedFactoryObservation.machineTransitions().stream()
                    .filter(receipt -> receipt.input().equals(inputKey.getId().toString())
                            && receipt.output().equals(outputKey.getId().toString())
                            && receipt.machineOwner().equals(Integer.toUnsignedString(System.identityHashCode(machine)))
                            && receipt.returnOwner().equals(Integer.toUnsignedString(
                                    System.identityHashCode(provider.getReturnInv())))).toList();
            if (thirdFederationTarget != null) {
                var selectedOwner = Integer.toUnsignedString(System.identityHashCode(machine));
                helper.assertTrue(MixedFactoryObservation.machineTransitions().stream()
                                .filter(receipt -> receipt.input().equals(inputKey.getId().toString()))
                                .allMatch(receipt -> receipt.machineOwner().equals(selectedOwner)),
                         "Only the selected Federation machine may transition for this typed input");
                if (threeTargetCatalog) {
                    var machines = List.of(this.machine, secondFederationMachine, thirdMachine);
                    for (int lane = 0; lane < machines.size(); lane++) {
                        var machineOwner = Integer.toUnsignedString(System.identityHashCode(machines.get(lane)));
                        var actual = MixedFactoryObservation.machineTransitions().stream()
                                .filter(receipt -> receipt.machineOwner().equals(machineOwner)).count();
                        var completed = jobIndex / 3 + (lane <= jobIndex % 3 ? 1 : 0);
                        helper.assertValueEqual(actual, completed * 16L,
                                "Only selected Federation machine may grow for job " + jobIndex + " lane " + lane);
                    }
                }
            }
            if (fourthFederationTarget != null) {
                var machines = List.of(this.machine, secondFederationMachine, thirdMachine, fourthMachine);
                for (int lane = 0; lane < machines.size(); lane++) {
                    var owner = Integer.toUnsignedString(System.identityHashCode(machines.get(lane)));
                    var actual = MixedFactoryObservation.machineTransitions().stream()
                            .filter(receipt -> receipt.machineOwner().equals(owner)).count();
                    var completed = fourTargetCatalog ? jobIndex / 4 + (lane <= jobIndex % 4 ? 1 : 0)
                            : lane <= jobIndex ? 1 : 0;
                    helper.assertValueEqual(actual, completed * 16L,
                            "Only selected Federation machine may transition at job " + jobIndex + " lane " + lane);
                }
            }
            if (fourthSubnetTarget != null) {
                var machines = List.of(this.machine, secondMachine, thirdMachine, fourthMachine);
                for (int lane = 0; lane < machines.size(); lane++) {
                    var owner = Integer.toUnsignedString(System.identityHashCode(machines.get(lane)));
                    var actual = MixedFactoryObservation.machineTransitions().stream()
                            .filter(receipt -> receipt.machineOwner().equals(owner)).count();
                    var completed = fourTargetCatalog ? jobIndex / 4 + (lane <= jobIndex % 4 ? 1 : 0)
                            : lane <= jobIndex ? 1 : 0;
                    helper.assertValueEqual(actual, completed * 16L,
                            "Only selected native subnet machine may transition at job " + jobIndex + " lane " + lane);
                }
            }
            var handlerAccepted = transitions.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::accepted).sum();
            var handlerConsumed = transitions.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::consumed).sum();
            var handlerOutput = transitions.stream().mapToLong(MixedFactoryObservation.HandlerReceipt::produced).sum();
            var expectedJobs = jobIndex + 1;
            if (directMachines != null) {
                for (int lane = 0; lane < directMachines.size(); lane++) {
                    var owner = Integer.toUnsignedString(System.identityHashCode(directMachines.get(lane)));
                    var actual = MixedFactoryObservation.machineTransitions().stream()
                            .filter(receipt -> receipt.machineOwner().equals(owner)).count();
                    var expected = catalog ? (jobIndex / 4 + (lane <= jobIndex % 4 ? 1 : 0)) * 16L
                            : lane <= jobIndex ? 16L : 0L;
                    helper.assertValueEqual(actual, expected,
                            "Only selected physical machine may change during native job " + jobIndex
                                    + "; machine lane " + lane);
                }
            }
            if (quantity == 16 && (transitions.size() != quantity || transitions.stream().anyMatch(receipt ->
                    receipt.accepted() != 1 || receipt.consumed() != 1 || receipt.produced() != 1))) {
                throw new IllegalStateException("Batch Processing did not make 16 one-item machine transitions");
            }
            if (input != 0 || physicalOutput != handlerOutput || handlerAccepted != quantity
                    || handlerConsumed != quantity || handlerOutput != quantity
                    || requester.acceptedAmount(outputKey) != handlerOutput
                    || requester.uniqueNativeJobCount() != expectedJobs
                    || machine.inputCount(inputKey.getItem()) != 0
                    || !provider.getReturnInv().isEmpty()
                    || catalogRun == null && twoPatterns && jobIndex < 2
                            && (physical(jobIndex == 0 ? SECOND_OUTPUT : OUTPUT)
                            != (retention == null ? jobIndex * quantity : 0)
                            || physical(jobIndex == 0 ? SECOND_INPUT : INPUT) != 0)
                    || catalog && jobIndex < 2 && (physical(highInput) != 0 || physical(highOutput) != 0)
                    || catalog && catalogRun == null && jobIndex == 2 && (physical(INPUT) != 0 || physical(SECOND_INPUT) != 0
                             || physical(OUTPUT) != quantity || physical(SECOND_OUTPUT) != quantity)
                    || federationTarget != null && (!targetInputObserved || federationTarget.inputAmount(inputKey) != 0)
                    || subnetTarget != null && (!targetInputObserved || subnetTarget.inputAmount(inputKey) != 0
                             || otherTargets.stream().anyMatch(target -> target.inputAmount(inputKey) != 0)
                            || subnetTarget.grid() == crafting.node().getGrid())
                    || crafting.node().getGrid().getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy())) {
                throw new IllegalStateException("Native Processing per-key physical conservation failed: input="
                         + input + ",accepted=" + handlerAccepted + ",consumed=" + handlerConsumed
                         + ",produced=" + handlerOutput + ",output=" + physicalOutput
                        + ",accepted=" + requester.acceptedAmount(outputKey));
            }
            if (federationRoute != null) federationRoute.assertNativeJobRoute();
            if (twoTargetRoute != null) {
                if (fourthLane) fourthFederationRoute.assertNativeJobLane(activeFederationLane);
                else if (thirdLane) thirdFederationRoute.assertNativeJobLane(activeFederationLane);
                else twoTargetRoute.assertNativeJobLane(secondLane ? 1 : 0, activeFederationLane);
                for (var other : otherFederationTargets) helper.assertValueEqual(other.inputAmount(inputKey), 0L,
                        "Other Federation targets must remain empty for completed Lane input");
                if (thirdFederationRoute == null) helper.assertValueEqual(otherFederationTarget.inputAmount(inputKey), 0L,
                        "Opposite Federation target must remain empty for completed Lane input");
            }
            if (twoPatterns) {
                if (jobIndex == 0) {
                    firstJobId = requester.nativeJobIds();
                } else if (requester.nativeJobIds().equals(firstJobId)
                        || !requester.nativeJobIds().contains(firstJobId)) {
                    throw new IllegalStateException("Second native Processing job did not have a distinct link UUID");
                }
                if (catalog && jobIndex == 1) secondJobIds = requester.nativeJobIds();
                if (catalog && jobIndex == 2 && !Set.of(requester.nativeJobIds().split(","))
                        .containsAll(Set.of(secondJobIds.split(",")))) {
                    throw new IllegalStateException("Third native Processing job lost an earlier link UUID");
                }
            }
            var receipt = "AE2F_SCALE_PROCESSING layout="
                    + (fourthFederationTarget != null ? "federation-four-targets"
                            : thirdFederationTarget == null ? layout : "federation-three-targets")
                    + " plannerComplete=true jobs="
                    + requester.uniqueNativeJobCount() + " jobIds=" + requester.nativeJobIds()
                     + (catalog || selection != null ? " physicalSlot=" + (selection != null ? selection.slot()
                             : jobIndex == 2 ? ScaleProcessingCatalog.SIZE - 1 : jobIndex) : "")
                    + " inputKey=" + inputKey.getId() + " outputKey=" + outputKey.getId()
                     + " input=" + quantity + " finalInput=" + input
                    + " targetInputObserved=" + (subnetTarget != null ? targetInputObserved
                            : federationTarget == null ? "native" : targetInputObserved)
                    + " targetFinalInput=" + (subnetTarget != null ? subnetTarget.inputAmount(inputKey)
                             : federationTarget == null ? "native" : federationTarget.inputAmount(inputKey))
                    + " endpointReturn=" + (subnetTarget != null ? "provider-inventory-exact"
                            : federationTarget == null ? "native" : "owner-exact")
                     + " machineTransitions=" + transitions.size()
                      + (secondMachine == null && secondFederationTarget == null ? "" : " targetLane="
                                 + (directMachines != null ? selection.slot() % 4
                                          : fourthLane ? 3 : thirdLane ? 2 : secondLane ? 1 : 0)
                              + " machineOwner=" + Integer.toUnsignedString(System.identityHashCode(machine))
                             + " returnOwner=" + Integer.toUnsignedString(
                                     System.identityHashCode(provider.getReturnInv())))
                     + " machineAccepted=" + handlerAccepted + " machineConsumed=" + handlerConsumed
                     + " machineOutput=" + handlerOutput + " callback=" + requester.acceptedAmount(outputKey)
                    + " physicalOutput=" + physicalOutput;
            LOGGER.info("{}", receipt);
            var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
            if (!configured.isBlank()) {
                var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-small-processing.log");
                try {
                    Files.createDirectories(path.getParent());
                    Files.writeString(path, receipt + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    throw new IllegalStateException("Cannot persist scale Processing receipt", exception);
                }
            }
            if (catalogRun != null && jobIndex < catalogRun.size() - 1
                    || catalogRun == null && twoPatterns && (jobIndex == 0 || catalog && jobIndex == 1)) {
                if (retention != null) retention.retain(catalogRun, jobIndex);
                jobIndex++;
                directDestinationWait = 0;
                targetInputObserved = false;
                activeFederationLane = null;
                stage = 0;
                plan = null;
                return false;
            }
            if (retention != null) retention.retain(catalogRun, jobIndex);
            stage = 5;
        }
        return stage == 5;
    }

    public IGridNode requesterNode() {
        return requester.getActionableNode();
    }

    public boolean acceptsMachineInput(net.minecraft.world.item.Item item) {
        return machine.inputHandler().isItemValid(0, new net.minecraft.world.item.ItemStack(item));
    }

    public int machineRecipeCount() {
        return machine.recipeCount();
    }

    public int completedJobCount() {
        return stage == 5 ? jobIndex + 1 : jobIndex;
    }

    public long acceptedTotal() {
        return requester.acceptedAmount();
    }

    private long physical(AEItemKey key) {
        return crafting.storage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    @Override
    public void close() {
        requester.close();
        MixedFactoryObservation.close();
    }
}
