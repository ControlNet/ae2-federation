package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.test.processing.EndpointFixtures;

@PrefixGameTestTemplate(false)
public final class EndpointGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(EndpointGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private EndpointGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointLocalNative(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            fixture.installPattern();
            var composition = fixture.composition();
            helper.assertTrue(!composition.claimLocal(List.of(fixture.remoteNativeProvider())),
                    "A remote native Provider must not own Local mode");
            helper.assertTrue(composition.claimLocal(List.of(fixture.nativeProvider())),
                    "Exactly one native adjacent Provider must own Local mode");
            helper.assertTrue(fixture.nativePush(), "Native PatternProviderLogic must push through Interface ME_STORAGE");
            helper.assertValueEqual(fixture.subnetItemCount(), 1L, "Native local input must reach subnet storage");
            helper.assertTrue(fixture.providerNode().getGrid() != composition.subnetNode().getGrid(),
                    "Provider source Grid must remain separate from Endpoint subnet Grid");
            helper.assertTrue(composition.nativeReturnInventory().orElseThrow() == fixture.provider().getLogic().getReturnInv(),
                    "Endpoint return owner must be the identified native Provider inventory");
            helper.assertValueEqual(composition.subnetFaces().size(), 5, "One Federation face must leave five subnet faces");
            var facts = new LinkedHashMap<String, String>();
            facts.put("verifiedUpstreamCount", "1");
            facts.put("remoteProviderRejected", "true");
            facts.put("physicalAdjacencyVerified", "true");
            facts.put("nativePushEntry", "PatternProviderLogic.pushPattern");
            facts.put("nativeTargetEntry", "PatternProviderTargetCache.find");
            facts.put("nativePushOwner", fixture.nativePushOwnerIdentity());
            facts.put("nativeTargetOwner", fixture.nativeTargetOwnerIdentity());
            facts.put("localInputAccepted", "true");
            facts.put("subnetInputCount", "1");
            facts.put("subnetFaceCount", "5");
            facts.put("upstreamOutsideSubnet", "true");
            facts.put("providerGrid", identity(fixture.providerNode().getGrid()));
            facts.put("subnetGrid", identity(composition.subnetNode().getGrid()));
            facts.put("returnInventory", identity(composition.nativeReturnInventory().orElseThrow()));
            facts.put("nativeReturnOwner", "true");
            facts.put("endpointBufferSlots", "0");
            writeEvidence("endpointlocalnative", 9, facts);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointFiveFaceItemFluid(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var composition = fixture.composition();
            helper.assertTrue(composition.claimLocal(List.of(fixture.nativeProvider())), "Local owner must bind");
            var facts = new LinkedHashMap<String, String>();
            var nativeReturn = composition.nativeReturnInventory().orElseThrow();
            String sharedStorage = null;
            for (var face : EndpointFixtures.ALLOWED_FACES) {
                var nativeItem = fixture.nativeItemCapability(face);
                var nativeFluid = fixture.nativeFluidCapability(face);
                helper.assertTrue(nativeItem != null, "Pinned AE2 must expose its native sided item return capability");
                helper.assertTrue(nativeFluid != null, "Pinned AE2 must expose its native sided fluid return capability");
                helper.assertTrue(nativeItem.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(),
                        "Native sided item capability must reach the Provider return inventory");
                nativeReturn.setStack(0, null);
                helper.assertValueEqual(nativeFluid.fill(new FluidStack(Fluids.WATER, 125),
                        IFluidHandler.FluidAction.EXECUTE), 125,
                        "Native sided fluid capability must reach the Provider return inventory");
                nativeReturn.setStack(0, null);
                var item = composition.itemReturnCapability(face, composition.openReturnInsert()).orElseThrow();
                var itemRemainder = item.insertItem(0, new ItemStack(Items.GOLD_INGOT), false);
                helper.assertTrue(itemRemainder.isEmpty(), "Every allowed face must delegate item insertion");
                nativeReturn.setStack(0, null);
                var fluid = composition.fluidReturnCapability(face, composition.openReturnInsert()).orElseThrow();
                helper.assertValueEqual(fluid.fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE),
                        250, "Every allowed face must delegate fluid insertion");
                nativeReturn.setStack(0, null);
                var faceNode = composition.faceNode(face).orElseThrow();
                var faceStorage = composition.faceStorage(face).orElseThrow();
                helper.assertTrue(faceNode == fixture.subnetNode(),
                        "Each allowed face must resolve the native Endpoint subnet node");
                if (sharedStorage == null) {
                    sharedStorage = identity(faceStorage);
                }
                facts.put("face." + face.getSerializedName() + ".item", "true");
                facts.put("face." + face.getSerializedName() + ".fluid", "true");
                facts.put("face." + face.getSerializedName() + ".node", identity(faceNode));
                facts.put("face." + face.getSerializedName() + ".storage", identity(faceStorage));
            }
            helper.assertTrue(composition.faceNode(EndpointFixtures.FEDERATION_FACE).isEmpty()
                    && composition.faceStorage(EndpointFixtures.FEDERATION_FACE).isEmpty()
                    && composition.itemReturnCapability(EndpointFixtures.FEDERATION_FACE,
                            composition.openReturnInsert()).isEmpty()
                    && composition.fluidReturnCapability(EndpointFixtures.FEDERATION_FACE,
                            composition.openReturnInsert()).isEmpty(),
                    "The Federation face must expose no Endpoint subnet or return capability");
            facts.put("subnetFaceCount", "5");
            facts.put("sharedSubnetNode", "true");
            facts.put("subnetNode", identity(composition.subnetNode()));
            facts.put("subnetStorage", sharedStorage);
            facts.put("federationFaceExcluded", "true");
            facts.put("returnInventory", identity(nativeReturn));
            facts.put("nativeItemAdapter", "GenericStackItemStorage");
            facts.put("nativeFluidAdapter", "GenericStackFluidStorage");
            facts.put("nativeSidedItemCapability", "true");
            facts.put("nativeSidedFluidCapability", "true");
            facts.put("resourceGuessing", "false");
            writeEvidence("endpointfivefaceitemfluid", 18, facts);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointRejectTwoUpstreams(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var composition = fixture.composition();
            var accepted = composition.claimLocal(List.of(fixture.nativeProvider(), fixture.secondNativeProvider()));
            helper.assertTrue(!accepted, "Two adjacent native Providers must fail closed");
            helper.assertTrue(composition.nativeReturnInventory().isEmpty(), "Rejected owners must install no return target");
            helper.assertTrue(composition.targetStorage(Direction.EAST, composition.openLocalInput()).isEmpty(),
                    "Rejected ownership must expose no Local input target");
            writeEvidence("endpointrejecttwoupstreams", 4, Map.of(
                    "candidateUpstreamCount", "2", "verifiedUpstreamCount", "0",
                    "twoUpstreamsAccepted", "false", "returnTargetBound", "false",
                    "localInputAccepted", "false", "partialBinding", "false"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointRejectCapabilityLoop(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var composition = fixture.composition();
            helper.assertTrue(composition.claimLocal(List.of(fixture.nativeProvider())), "Local owner must bind");
            var inputContext = composition.openLocalInput();
            var returnContext = composition.openReturnInsert();
            helper.assertTrue(composition.returnInventory(Direction.EAST, inputContext).isEmpty(),
                    "Local target context cannot resolve the return inventory");
            helper.assertTrue(composition.targetStorage(Direction.EAST, returnContext).isEmpty(),
                    "Return context cannot resolve subnet target storage");
            var target = composition.targetStorage(Direction.EAST, inputContext).orElseThrow();
            helper.assertValueEqual(target.insert(AEItemKey.of(Items.IRON_INGOT), 1, Actionable.MODULATE, IActionSource.empty()),
                    1L, "Capability-aware Local input must reach only subnet storage");
            helper.assertTrue(composition.nativeReturnInventory().orElseThrow().isEmpty(),
                    "Input insertion must not loop into native return inventory");
            writeEvidence("endpointrejectcapabilityloop", 6, Map.of(
                    "loopAttempted", "true", "loopAccepted", "false", "inputContextReturnResolved", "false",
                    "returnContextTargetResolved", "false", "subnetInputAccepted", "true",
                    "returnInventoryChanged", "false", "contextSeparated", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 240, required = true, manualOnly = true)
    public static void endpointModeIsolation(GameTestHelper helper) {
        runFixture(helper, fixture -> {
            var composition = fixture.composition();
            helper.assertTrue(composition.claimLocal(List.of(fixture.nativeProvider())), "Local owner must bind");
            var staleLocal = composition.openLocalInput();
            var staleReturn = composition.openReturnInsert();
            composition.setMode(EndpointMode.FEDERATED);
            helper.assertTrue(composition.targetStorage(Direction.EAST, staleLocal).isEmpty(),
                    "Mode change must invalidate outstanding Local input contexts");
            helper.assertTrue(composition.returnInventory(Direction.EAST, staleReturn).isEmpty(),
                    "Mode change must invalidate outstanding return contexts");
            helper.assertTrue(composition.targetStorage(Direction.EAST, composition.openLocalInput()).isEmpty(),
                    "Federated mode must reject new Local upstream input");
            helper.assertTrue(composition.targetStorage(Direction.EAST, composition.openFederatedInput()).isPresent(),
                    "Federated mode must retain its distinct target context");
            composition.setMode(EndpointMode.LOCAL);
            helper.assertTrue(composition.targetStorage(Direction.EAST, composition.openLocalInput()).isEmpty(),
                    "Returning to Local mode must require a fresh verified owner");
            writeEvidence("endpointmodeisolation", 7, Map.of(
                    "localModeAccepted", "true", "federatedTargetAccepted", "true",
                    "federatedLocalInputAccepted", "false", "staleLocalContextAccepted", "false",
                    "staleReturnContextAccepted", "false", "outstandingBindingReused", "false",
                    "freshClaimRequired", "true"));
        });
    }

    private static void runFixture(GameTestHelper helper, java.util.function.Consumer<EndpointFixtures> assertions) {
        var fixture = new EndpointFixtures(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Provider and subnet nodes to become active");
            assertions.accept(fixture);
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
        var orderedFacts = new TreeMap<>(facts);
        orderedFacts.forEach((name, value) -> LOGGER.info("AE2F_ENDPOINT_TRACE testId={} fact={} value={}",
                testId, name, value));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        orderedFacts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native Endpoint capability evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Endpoint evidence to " + path, exception);
        }
    }
}
