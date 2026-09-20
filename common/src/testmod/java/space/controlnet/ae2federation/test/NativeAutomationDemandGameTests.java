package space.controlnet.ae2federation.test;

import appeng.api.stacks.AEItemKey;
import appeng.blockentity.misc.InterfaceBlockEntity;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;

@PrefixGameTestTemplate(false)
public final class NativeAutomationDemandGameTests {
    private NativeAutomationDemandGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void automationRejectDuplicateDemand(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var target = new InterfaceBlockEntity[1];
        var link = new appeng.api.networking.crafting.ICraftingLink[1];
        var callsAtLink = new int[1];
        var postCancelChecks = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for duplicate-demand topology");
            if (target[0] == null) {
                AutomationNativeObservation.begin("automationrejectduplicatedemand");
                AutomationAuthorityObservation.begin("automationrejectduplicatedemand", fixture);
                fixture.binding().insertMaterials(64);
                target[0] = fixture.placeProviderInterface();
                AutomationAuthorityObservation.authorizeInterface(target[0].getInterfaceLogic());
                fixture.installCraftingCard(target[0]);
                fixture.configure(target[0], 0, AEItemKey.of(Items.STICK), 128);
                helper.assertTrue(false, "Waiting for native Interface link");
            }
            if (link[0] == null) {
                var jobs = target[0].getInterfaceLogic().getRequestedJobs();
                helper.assertValueEqual(jobs.size(), 1, "One native Interface demand must own one link");
                link[0] = jobs.iterator().next();
                callsAtLink[0] = AutomationNativeObservation.snapshot().trackerCalls();
                fixture.binding().sourceService().getCpus().stream()
                        .filter(cpu -> cpu.isBusy())
                        .map(appeng.me.cluster.implementations.CraftingCPUCluster.class::cast)
                        .forEach(cpu -> cpu.craftingLogic.setJobSuspended(true));
                helper.assertTrue(false, "Waiting for repeated native demand tick");
            }
            if (!link[0].isCanceled()) {
                var observation = AutomationNativeObservation.snapshot();
                helper.assertTrue(observation.trackerCalls() > callsAtLink[0],
                        "Same logical stocking demand must be invoked on later native ticks");
                helper.assertValueEqual(observation.jobIds().size(), 1,
                        "Repeated invocation must retain one native job UUID");
                helper.assertValueEqual(target[0].getInterfaceLogic().getRequestedJobs().iterator().next()
                        .getCraftingID(), link[0].getCraftingID(), "Native link identity must remain stable");
                fixture.removeCraftingCard(target[0]);
                helper.assertTrue(false, "Waiting for native cancellation return");
            }
            helper.assertTrue(link[0].isCanceled(), "Removing Crafting Card must cancel the existing native demand");
            helper.assertTrue(target[0].getInterfaceLogic().getRequestedJobs().isEmpty(),
                    "Canceled demand must leave the Interface tracker");
            helper.assertValueEqual(fixture.binding().materialAmount(), 64L,
                    "Native cancellation must return CPU-held material");
            postCancelChecks[0]++;
            helper.assertTrue(postCancelChecks[0] >= 3, "Observing native ticks after cancellation");
            var observation = AutomationNativeObservation.snapshot();
            helper.assertValueEqual(observation.trackerSubmissions(), 1,
                    "Canceled demand must not be automatically replaced");
            NativeAutomationGameTests.write("automationrejectduplicatedemand", 18, fixture,
                    target[0].getInterfaceLogic(),
                    fixture.binding().sourcePhysicalStorage(), target[0].getInterfaceLogic().getStorage(), Map.ofEntries(
                            Map.entry("jobCardinality", "1"), Map.entry("jobIds", observation.joinedJobs()),
                            Map.entry("linkId", link[0].getCraftingID().toString()),
                            Map.entry("trackerSubmissions", "1"), Map.entry("duplicateTicks", "true"),
                            Map.entry("canceled", "true"), Map.entry("replacement", "false"),
                            Map.entry("materialBefore", "64"), Map.entry("materialAfter", "64")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void automationContention(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var targets = new InterfaceBlockEntity[2];
        var configured = new boolean[1];
        helper.succeedWhen(() -> {
            if (targets[0] == null) {
                helper.assertTrue(fixture.ready(), "Waiting for contention topology");
                AutomationNativeObservation.begin("automationcontention");
                AutomationAuthorityObservation.begin("automationcontention", fixture);
                AutomationAuthorityObservation.authorizeProjectionOperation("extract",
                        fixture.itemKey(Items.DIAMOND));
                fixture.clearConsumerCell();
                helper.assertValueEqual(fixture.insertSource(fixture.itemKey(Items.DIAMOND), 5), 5L,
                        "Physical source must accept contention stock");
                targets[0] = fixture.placeConsumerInterface(false);
                AutomationAuthorityObservation.authorizeInterface(targets[0].getInterfaceLogic());
                helper.assertTrue(false, "Waiting for first native consumer");
            }
            if (targets[1] == null) {
                helper.assertTrue(fixture.interfaceReady(targets[0], false),
                        "First native consumer must join the consumer Grid");
                targets[1] = fixture.placeConsumerInterface(true);
                AutomationAuthorityObservation.authorizeInterface(targets[1].getInterfaceLogic());
                helper.assertTrue(false, "Waiting for second native consumer");
            }
            helper.assertTrue(fixture.interfaceReady(targets[0], false)
                    && fixture.interfaceReady(targets[1], false), "Both native consumers must join the consumer Grid");
            if (!configured[0]) {
                AutomationAuthorityObservation.captureProjection(fixture);
                fixture.configure(targets[0], 0, fixture.itemKey(Items.DIAMOND), 4);
                fixture.configure(targets[1], 0, fixture.itemKey(Items.DIAMOND), 4);
                configured[0] = true;
                helper.assertTrue(false, "Waiting for simultaneous native consumers");
            }
            helper.assertValueEqual(fixture.sourceAmount(fixture.itemKey(Items.DIAMOND)), 0L,
                    "Contending consumers must exhaust only physical stock");
            var first = fixture.interfaceAmount(targets[0], 0);
            var second = fixture.interfaceAmount(targets[1], 0);
            helper.assertValueEqual(first + second, 5L, "Accepted consumer quantities must equal physical extraction");
            helper.assertTrue(first <= 4 && second <= 4, "Neither consumer may exceed its native configured demand");
            var observation = AutomationNativeObservation.snapshot();
            helper.assertValueEqual(observation.storageExtracted(), 5L,
                    "Authorized projection must never extract more than available");
            NativeAutomationGameTests.write("automationcontention", 15, fixture, targets[0].getInterfaceLogic(),
                    fixture.binding().sourcePhysicalStorage(), targets[1], Map.ofEntries(
                            Map.entry("physicalBefore", "5"), Map.entry("physicalAfter", "0"),
                            Map.entry("firstAccepted", Long.toString(first)),
                            Map.entry("secondAccepted", Long.toString(second)),
                            Map.entry("acceptedTotal", Long.toString(first + second)),
                            Map.entry("extractedTotal", Long.toString(observation.storageExtracted())),
                            Map.entry("configuredTotal", "8"), Map.entry("overExtracted", "false"),
                            Map.entry("reservationEngine", "false")));
            fixture.close();
        });
    }
}
