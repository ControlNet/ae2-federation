package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.storage.StorageHelper;
import appeng.me.helpers.PlayerSource;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.NativeCraftingFixtures;
import space.controlnet.ae2federation.test.crafting.NativeCraftingRequester;

@PrefixGameTestTemplate(false)
public final class NativeCraftingGameTests {
    private NativeCraftingGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void craftProofNativeTerminal(GameTestHelper helper) {
        var fixture = new NativeCraftingFixtures(helper, true);
        var terminalSource = new PlayerSource(helper.makeMockPlayer(GameType.CREATIVE));
        var materialsInserted = new boolean[] { false };
        var submitted = new boolean[] { false };
        helper.succeedWhen(() -> {
            awaitReady(helper, fixture);
            if (!materialsInserted[0]) {
                fixture.insertMaterials(2);
                materialsInserted[0] = true;
                helper.assertTrue(false, "Waiting for native storage cache after material insertion");
            }
            if (!submitted[0]) {
                fixture.begin(CalculationStrategy.REPORT_MISSING_ITEMS, 4, terminalSource);
                if (!fixture.planReady()) {
                    helper.assertTrue(false, "Waiting for terminal-equivalent native plan");
                }
                var result = fixture.submit(null);
                helper.assertTrue(result.successful(), "Terminal-equivalent native plan must submit");
                helper.assertTrue(fixture.submittedLink() == null, "Native terminal submission must be standalone");
                helper.assertValueEqual(fixture.uniqueNativeJobCount(), 1,
                        "Native CPU observation must expose one terminal job UUID");
                submitted[0] = true;
            }
            helper.assertValueEqual(fixture.materialAmount(), 0L, "Native CPU must extract initial materials");
            helper.assertTrue(fixture.assemblerReceivedInput() || fixture.outputAmount() == 4,
                    "Native provider must dispatch the pattern to the assembler");
            helper.assertValueEqual(fixture.outputAmount(), 4L,
                    "Standalone native final output must return through Grid storage");
            NativeCraftingEvidence.write("craftproofnativeterminal", 10, Map.ofEntries(
                    Map.entry("requestMode", "terminal-equivalent"), Map.entry("planSimulation", "false"),
                    Map.entry("submitted", "true"), Map.entry("standaloneLink", "true"),
                    Map.entry("initialMaterial", "2"), Map.entry("materialAfterSubmit", "0"),
                    Map.entry("providerDispatch", "true"), Map.entry("resultInserted", "4"),
                    Map.entry("uniqueNativeTasks", Integer.toString(fixture.uniqueNativeJobCount())),
                    Map.entry("nativeJobIds", fixture.nativeJobIds()),
                    Map.entry("craftingService", identity(fixture.service())),
                    Map.entry("cpuCount", Integer.toString(fixture.cpuCount())),
                    Map.entry("provider", identity(fixture.provider())),
                    Map.entry("assembler", identity(fixture.assembler())),
                    Map.entry("storage", identity(fixture.chest().getInventory()))));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftProofNativeStocking(GameTestHelper helper) {
        var fixture = new NativeCraftingFixtures(helper, true);
        var requester = new NativeCraftingRequester[1];
        var linkId = new String[1];
        var duplicateInvocationSubmitted = new boolean[] { true };
        helper.succeedWhen(() -> {
            awaitReady(helper, fixture);
            if (requester[0] == null) {
                fixture.insertMaterials(2);
                requester[0] = fixture.createRequester();
                helper.assertTrue(false, "Waiting for native storage cache after material insertion");
            }
            helper.assertTrue(requester[0].isReady(fixture.chest().getMainNode().getNode()),
                    "Waiting for native stocking requester Grid attachment");
            if (requester[0].submittedLink() == null) {
                requester[0].handleCrafting(NativeCraftingFixtures.outputKey(), 4, helper.getLevel(), fixture.service());
                if (requester[0].submittedLink() != null) {
                    linkId[0] = requester[0].submittedLink().getCraftingID().toString();
                    duplicateInvocationSubmitted[0] = requester[0].handleCrafting(
                            NativeCraftingFixtures.outputKey(), 4, helper.getLevel(), fixture.service());
                }
                helper.assertTrue(false, "Waiting for native stocking tracker submission");
            }
            if (!requester[0].observedDone() || requester[0].acceptedAmount() < 4) {
                helper.assertTrue(false, "Waiting for native stocking requester completion");
            }
            helper.assertValueEqual(requester[0].acceptedAmount(), 4L,
                    "Requester must separately accept the full native final output");
            helper.assertTrue(requester[0].observedDone(), "Requester must observe native done state");
            helper.assertValueEqual(requester[0].stateChanges(), 1, "Native completion must notify requester once");
            helper.assertValueEqual(fixture.materialAmount(), 0L, "Stocking CPU must extract initial materials");
            helper.assertValueEqual(fixture.outputAmount(), 4L, "Accepted result must be inserted into native storage");
            helper.assertTrue(!duplicateInvocationSubmitted[0],
                    "A duplicate tracker invocation must not submit a second native job");
            helper.assertValueEqual(requester[0].uniqueNativeJobCount(), 1,
                    "Duplicate tracker invocation must retain one observed native UUID");
            NativeCraftingEvidence.write("craftproofnativestocking", 12, Map.ofEntries(
                    Map.entry("requestMode", "multi-crafting-tracker"), Map.entry("submitted", "true"),
                    Map.entry("initialMaterial", "2"), Map.entry("materialAfterSubmit", "0"),
                    Map.entry("acceptedResult", "4"), Map.entry("storedResult", "4"),
                    Map.entry("linkDone", "true"), Map.entry("stateChanges", "1"),
                    Map.entry("duplicateInvocationSubmitted", Boolean.toString(duplicateInvocationSubmitted[0])),
                    Map.entry("uniqueNativeTasks", Integer.toString(requester[0].uniqueNativeJobCount())),
                    Map.entry("nativeJobIds", requester[0].nativeJobIds()), Map.entry("linkId", linkId[0]),
                    Map.entry("requester", identity(requester[0])),
                    Map.entry("requesterNode", identity(requester[0].getActionableNode())),
                    Map.entry("craftingService", identity(fixture.service())),
                    Map.entry("provider", identity(fixture.provider()))));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void craftProofDisconnectCancelRestart(GameTestHelper helper) {
        var fixture = new NativeCraftingFixtures(helper, true);
        var originalRequester = new NativeCraftingRequester[1];
        var reloadedRequester = new NativeCraftingRequester[1];
        var persisted = new CompoundTag[1];
        var linkId = new String[1];
        var materialBeforeSuspend = new long[1];
        var cpuMaterialBeforeSuspend = new long[1];
        helper.succeedWhen(() -> {
            awaitReady(helper, fixture);
            if (originalRequester[0] == null) {
                fixture.insertMaterials(64);
                originalRequester[0] = fixture.createRequester();
                helper.assertTrue(false, "Waiting for native storage cache after material insertion");
            }
            if (persisted[0] == null) {
                var chestNode = fixture.chest().getMainNode().getNode();
                helper.assertTrue(originalRequester[0].isReady(chestNode),
                        "Waiting for native restart requester Grid attachment");
                originalRequester[0].handleCrafting(NativeCraftingFixtures.outputKey(), 128,
                        helper.getLevel(), fixture.service());
                var link = originalRequester[0].activeLink();
                if (link == null || !fixture.cpuBusy()) {
                    helper.assertTrue(false, "Waiting for native request link and busy CPU");
                }
                materialBeforeSuspend[0] = fixture.materialAmount();
                cpuMaterialBeforeSuspend[0] = fixture.cpuMaterialAmount();
                helper.assertTrue(materialBeforeSuspend[0] < 64,
                        "Native submission must extract material from Grid storage before suspension");
                helper.assertValueEqual(cpuMaterialBeforeSuspend[0], 64L,
                        "Extracted material must be observed in native CPU inventory before suspension");
                fixture.suspendCpu();
                linkId[0] = link.getCraftingID().toString();
                persisted[0] = originalRequester[0].writeLink();
                originalRequester[0].close();
                reloadedRequester[0] = fixture.replaceRequester(persisted[0]);
                var loaded = reloadedRequester[0].activeLink();
                helper.assertTrue(loaded != null, "Reload must reconstruct native requester link");
                helper.assertValueEqual(loaded.getCraftingID().toString(), linkId[0],
                        "Reloaded link must preserve native crafting UUID");
                loaded.cancel();
                helper.assertTrue(false, "Waiting for native cancellation and material return");
            }
            var loaded = reloadedRequester[0].activeLink();
            helper.assertTrue(loaded != null && loaded.isCanceled(), "Reloaded native link must remain canceled");
            helper.assertTrue(!fixture.cpuBusy(), "Native CPU must stop the canceled job");
            helper.assertValueEqual(fixture.materialAmount(), 64L,
                    "Native CPU must return remaining initial materials after cancellation");
            helper.assertValueEqual(reloadedRequester[0].acceptedAmount(), 0L,
                    "Canceled request must accept no final output");
            var roundTrip = new CompoundTag();
            loaded.writeToNBT(roundTrip);
            ICraftingLink secondReload = StorageHelper.loadCraftingLink(roundTrip, reloadedRequester[0]);
            helper.assertValueEqual(secondReload.getCraftingID().toString(), linkId[0],
                    "Second native reload must retain UUID");
            helper.assertTrue(secondReload.isCanceled(), "Persisted native canceled state must round-trip");
            NativeCraftingEvidence.write("craftproofdisconnectcancelrestart", 14, Map.ofEntries(
                    Map.entry("submitted", "true"), Map.entry("initialMaterial", "64"),
                    Map.entry("materialBeforeSuspend", Long.toString(materialBeforeSuspend[0])),
                    Map.entry("cpuMaterialBeforeSuspend", Long.toString(cpuMaterialBeforeSuspend[0])),
                    Map.entry("materialAfterCancel", "64"), Map.entry("acceptedResult", "0"),
                    Map.entry("linkPersisted", "true"), Map.entry("linkReloaded", "true"),
                    Map.entry("linkCanceled", "true"), Map.entry("cpuBusyAfterCancel", "false"),
                    Map.entry("uuidPreserved", "true"),
                    Map.entry("uniqueNativeTasks", Integer.toString(originalRequester[0].uniqueNativeJobCount())),
                    Map.entry("nativeJobIds", originalRequester[0].nativeJobIds()),
                    Map.entry("linkId", linkId[0]), Map.entry("originalRequester", identity(originalRequester[0])),
                    Map.entry("reloadedRequester", identity(reloadedRequester[0])),
                    Map.entry("craftingService", identity(fixture.service())),
                    Map.entry("provider", identity(fixture.provider()))));
            fixture.close();
        });
    }

    private static void awaitReady(GameTestHelper helper, NativeCraftingFixtures fixture) {
        helper.assertTrue(fixture.ready(), "Waiting for powered native crafting Grid");
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

}
