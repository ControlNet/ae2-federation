package space.controlnet.ae2federation.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import appeng.api.networking.GridServices;
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
        modBus.addListener(this::registerGameTests);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        var selection = System.getProperty("ae2federation.testSelection", "positive");
        var testId = System.getProperty("ae2federation.testId", "harnessnativesmoke");
        var testClasses = new java.util.ArrayList<>(Arrays.asList(FederationGameTests.class, IdentityBaselineGameTests.class, IdentityGameTests.class,
					 PortGameTests.class, ProviderLaneGameTests.class, EndpointGameTests.class, EndpointModeGameTests.class,
					 EndpointReturnGameTests.class, EndpointAuthorizationGameTests.class,
					 StorageProofGameTests.class, StorageNativeCharacterizationGameTests.class, StorageMountGameTests.class,
					 StorageProvenanceGameTests.class, StorageChainGameTests.class,
					 StorageSubscriptionGameTests.class, StorageSubscriptionDiamondGameTest.class,
					 StorageSubscriptionBoundaryGameTest.class, StorageSubscriptionMaskingGameTest.class,
						 ResourceQualificationGameTests.class,
						 CraftingBindingGameTests.class, CraftingBindingFailureGameTests.class,
						 TerminalCraftingGameTests.class, TerminalCraftingFailureGameTests.class,
						 NativeAutomationGameTests.class, NativeAutomationDemandGameTests.class,
						 CraftingLifecycleGameTests.class,
					 NativeCraftingGameTests.class, NativeCraftingFailureGameTests.class, NativeEnergyGameTests.class,
					 DirectionalEnergyGameTests.class,
					 ObservabilityGameTests.class,
					   MultipartBridgeGameTests.class, HubGameTests.class, FabricGameTests.class,
					   FabricBridgeGameTests.class, PolicyLifecycleGameTests.class, PolicyRevisionGameTests.class,
					   ProviderLifecycleGameTests.class, ProviderClaimGameTests.class,
						   ProcessingRegressionGameTests.class, ProcessingLockGameTests.class,
							   ProcessingRestartGameTests.class, ProcessingOwnershipGameTests.class,
							   ProcessingBenchmarkGameTests.class, UiGraphBenchmarkGameTests.class,
							   MixedFactoryGameTests.class, ScaleFactoryGameTests.class, TaskThirtyFivePacketGameTests.class));
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
        testClasses.stream()
                .flatMap(testClass -> Arrays.stream(testClass.getDeclaredMethods()))
                .filter(method -> selected(selection, testId, method))
                .forEach(event::register);
    }

    private static boolean selected(String selection, String testId, Method method) {
        return switch (selection) {
            case "positive", "benchmark" -> method.getName().equalsIgnoreCase(testId);
            case "required-failure" -> method.getName().equals("harnessRequiredFailure");
            case "timeout" -> method.getName().equals("harnessTimeout");
            case "none" -> false;
            default -> throw new IllegalArgumentException("Unknown GameTest selection: " + selection);
        };
    }
}
