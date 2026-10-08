package space.controlnet.ae2federation.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import appeng.api.networking.GridServices;
import space.controlnet.ae2federation.test.art.ModLogoRenderer;
import space.controlnet.ae2federation.test.energy.LargeEnergyCellRegistration;
import space.controlnet.ae2federation.test.identity.NativeNodeDataProbe;
import space.controlnet.ae2federation.test.identity.NativeNodeDataProbeService;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.multiclient.MultiClientClientHarness;
import space.controlnet.ae2federation.test.multiclient.MultiClientServerHarness;

import java.lang.reflect.Method;
import java.util.Arrays;

@Mod(FederationTestMod.MOD_ID)
public final class FederationTestMod {
    public static final String MOD_ID = "ae2federation_test";

    public FederationTestMod(IEventBus modBus) {
        GridServices.register(NativeNodeDataProbe.class, NativeNodeDataProbeService.class);
        LargeEnergyCellRegistration.register(modBus);
        MixedMachineRegistration.register(modBus);
        ObservationRuntimeEvidence.register();
        if (Boolean.getBoolean("ae2federation.multiclient.enabled")) {
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                MultiClientClientHarness.register();
            } else {
                MultiClientServerHarness.register();
            }
        }
        if (System.getProperty(ModLogoRenderer.OUTPUT) != null && net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            ModLogoRenderer.register();
        }
        modBus.addListener(this::registerGameTests);
        modBus.addListener(space.controlnet.ae2federation.test.world.RestartChunkTickets::register);
        modBus.addListener(space.controlnet.ae2federation.test.world.OtherDimensionSite::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                space.controlnet.ae2federation.test.world.OtherDimensionSite::onServerStopped);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        var selection = System.getProperty("ae2federation.testSelection", "positive");
        var testId = System.getProperty("ae2federation.testId", "harnessnativesmoke");
        var testClasses = new java.util.ArrayList<>(Arrays.asList(FederationGameTests.class, IdentityBaselineGameTests.class, IdentityGameTests.class, BoundaryIdentityGameTests.class,
					 IdentityQueryCostGameTests.class, ProductionProviderGameTests.class, ProductionProviderRetentionGameTests.class, StorageAliasSemanticsGameTests.class, StorageStatusGameTests.class,
					 LegacySaveImportGameTests.class,
					 PortGameTests.class, ProviderLaneGameTests.class, EndpointGameTests.class, EndpointFederationFaceGameTests.class, ProviderFederationFaceGameTests.class, EndpointModeGameTests.class,
					 EndpointReturnGameTests.class, EndpointAuthorizationGameTests.class,
					 StorageProofGameTests.class, StorageNativeCharacterizationGameTests.class, StorageMountGameTests.class,
					 StorageProvenanceGameTests.class, StorageChainGameTests.class,
					 StorageSubscriptionGameTests.class, StorageSubscriptionDiamondGameTest.class,
					 StorageSubscriptionBoundaryGameTest.class, StorageSubscriptionMaskingGameTest.class,
					 StorageSourceIndexGameTests.class,
					 StorageNativeStateGameTests.class,
						 ResourceQualificationGameTests.class,
						 NativeAutomationGameTests.class, NativeAutomationDemandGameTests.class,
					 NativeCraftingGameTests.class, NativeCraftingFailureGameTests.class, NativeEnergyGameTests.class,
					 SharedEnergyGameTests.class,
					 ObservabilityGameTests.class,
					   MultipartBridgeGameTests.class, RouterGameTests.class, FederationDomainGameTests.class, TopologyContinuityGameTests.class,
					   FederationDomainBridgeGameTests.class, PolicyLifecycleGameTests.class, PolicyRevisionGameTests.class, RuleLinkGameTests.class, PatternProjectionGameTests.class, SurvivalRecipeGameTests.class,
					   ProviderLifecycleGameTests.class, ProviderClaimGameTests.class,
						   ProcessingRegressionGameTests.class, ProcessingLockGameTests.class,
							   ProcessingRestartGameTests.class, ProcessingOwnershipGameTests.class,
							   ProcessingBenchmarkGameTests.class, UiGraphBenchmarkGameTests.class, PerformanceBenchmarkGameTests.class,
							   MixedFactoryGameTests.class, ScaleFactoryGameTests.class, TaskThirtyFivePacketGameTests.class,
							   CrossDimensionGameTests.class, FederationP2PGameTests.class, ProjectionContainerGameTests.class));
        try {
            testClasses.add(Class.forName("space.controlnet.ae2federation.test.AppliedFluxResourceGameTests"));
        } catch (ClassNotFoundException ignored) {
        }
        try {
            testClasses.add(Class.forName("space.controlnet.ae2federation.test.FunctionalStorageCompatibilityGameTests"));
        } catch (ClassNotFoundException ignored) {
        }
        try {
            testClasses.add(Class.forName("space.controlnet.ae2federation.test.PrettyPipesCompatibilityGameTests"));
        } catch (ClassNotFoundException ignored) {
        }
        try {
            testClasses.add(Class.forName("space.controlnet.ae2federation.test.PrettyPipesFluidsCompatibilityGameTests"));
        } catch (ClassNotFoundException ignored) {
        }
        var registered = new java.util.HashSet<String>();
        testClasses.stream()
                .flatMap(testClass -> Arrays.stream(testClass.getDeclaredMethods()))
                .filter(method -> selected(selection, testId, method))
                .forEach(method -> {
                    registered.add(method.getName().toLowerCase(java.util.Locale.ROOT));
                    event.register(method);
                });
        if (selection.equals("batch")) {
            var missing = new java.util.ArrayList<>(batchIds(testId));
            missing.removeAll(registered);
            if (!missing.isEmpty()) throw new IllegalArgumentException("Unknown GameTest ids in batch: " + missing);
        }
    }

    /**
     * Development-only: the lower-cased ids of a comma-separated batch, in the requested order. One server runs them one
     * after another; CI and evidence runs keep one server per test ({@code positive}).
     */
    public static java.util.List<String> batchIds(String testIds) {
        return Arrays.stream(testIds.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                .map(id -> id.toLowerCase(java.util.Locale.ROOT)).distinct().toList();
    }

    private static boolean selected(String selection, String testId, Method method) {
        return switch (selection) {
            case "positive", "benchmark" -> method.getName().equalsIgnoreCase(testId);
            case "batch" -> batchIds(testId).contains(method.getName().toLowerCase(java.util.Locale.ROOT));
            case "required-failure" -> method.getName().equals("harnessRequiredFailure");
            case "timeout" -> method.getName().equals("harnessTimeout");
            case "none" -> false;
            default -> throw new IllegalArgumentException("Unknown GameTest selection: " + selection);
        };
    }
}
