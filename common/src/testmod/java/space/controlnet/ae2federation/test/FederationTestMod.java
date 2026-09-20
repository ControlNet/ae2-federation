package space.controlnet.ae2federation.test;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import appeng.api.networking.GridServices;
import space.controlnet.ae2federation.test.identity.NativeNodeDataProbe;
import space.controlnet.ae2federation.test.identity.NativeNodeDataProbeService;

import java.lang.reflect.Method;
import java.util.Arrays;

@Mod(FederationTestMod.MOD_ID)
public final class FederationTestMod {
    public static final String MOD_ID = "ae2federation_test";

    public FederationTestMod(IEventBus modBus) {
        GridServices.register(NativeNodeDataProbe.class, NativeNodeDataProbeService.class);
        modBus.addListener(this::registerGameTests);
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        var selection = System.getProperty("ae2federation.testSelection", "positive");
        var testId = System.getProperty("ae2federation.testId", "harnessnativesmoke");
        Arrays.stream(new Class<?>[] { FederationGameTests.class, IdentityBaselineGameTests.class, IdentityGameTests.class,
					 PortGameTests.class, ProviderLaneGameTests.class, EndpointGameTests.class, EndpointModeGameTests.class,
					 EndpointReturnGameTests.class, EndpointAuthorizationGameTests.class,
					 StorageProofGameTests.class, StorageNativeCharacterizationGameTests.class, StorageMountGameTests.class,
					 StorageProvenanceGameTests.class, StorageChainGameTests.class,
					 StorageSubscriptionGameTests.class, StorageSubscriptionDiamondGameTest.class,
					 StorageSubscriptionBoundaryGameTest.class, StorageSubscriptionMaskingGameTest.class,
					 NativeCraftingGameTests.class, NativeCraftingFailureGameTests.class, NativeEnergyGameTests.class,
					   MultipartBridgeGameTests.class, HubGameTests.class, FabricGameTests.class,
					   FabricBridgeGameTests.class, PolicyLifecycleGameTests.class, PolicyRevisionGameTests.class,
					   ProviderLifecycleGameTests.class, ProviderClaimGameTests.class,
					   ProcessingRegressionGameTests.class, ProcessingLockGameTests.class,
						   ProcessingRestartGameTests.class, ProcessingOwnershipGameTests.class,
						   ProcessingBenchmarkGameTests.class })
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
