package space.controlnet.ae2federation.test;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.blockentity.misc.InterfaceBlockEntity;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.automation.AutomationAuthorityObservation;
import space.controlnet.ae2federation.test.automation.AutomationEvidence;
import space.controlnet.ae2federation.test.automation.AutomationNativeObservation;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;

@PrefixGameTestTemplate(false)
public final class NativeAutomationGameTests {
    private NativeAutomationGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1000, required = true, manualOnly = true)
    public static void automationInterfaceStock(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var target = new InterfaceBlockEntity[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for shared Storage and Crafting capabilities");
            if (target[0] == null) {
                AutomationNativeObservation.begin("automationinterfacestock");
                AutomationAuthorityObservation.begin("automationinterfacestock", fixture);
                AutomationAuthorityObservation.authorizeProjectionOperation("extract",
                        fixture.itemKey(Items.DIAMOND));
                fixture.clearConsumerCell();
                helper.assertValueEqual(fixture.insertSource(fixture.itemKey(Items.DIAMOND), 7), 7L,
                        "Physical source must accept native stock");
                target[0] = fixture.placeConsumerInterface(false);
                AutomationAuthorityObservation.authorizeInterface(target[0].getInterfaceLogic());
                fixture.configure(target[0], 0, fixture.itemKey(Items.DIAMOND), 5);
                helper.assertTrue(false, "Waiting for native Interface stocking");
            }
            helper.assertTrue(fixture.interfaceReady(target[0], false), "Consumer Interface must join its native Grid");
            helper.assertValueEqual(fixture.interfaceAmount(target[0], 0), 5L,
                    "Native Interface must own five stocked diamonds");
            helper.assertValueEqual(fixture.sourceAmount(fixture.itemKey(Items.DIAMOND)), 2L,
                    "Task 21 source must be debited by native Interface extraction");
            helper.assertTrue(target[0].getInterfaceLogic().getRequestedJobs().isEmpty(),
                    "Storage-backed stocking must not fabricate a crafting request");
            var observation = AutomationNativeObservation.snapshot();
            helper.assertTrue(observation.storageExtractCalls() > 0 && observation.storageExtracted() == 5,
                    "Interface stocking must traverse the authorized projection exactly");
            write("automationinterfacestock", 14, fixture, target[0].getInterfaceLogic(),
                    fixture.binding().sourcePhysicalStorage(),
                    target[0].getInterfaceLogic().getStorage(), Map.of(
                            "stocked", "5", "sourceBefore", "7", "sourceAfter", "2",
                            "craftingCard", "false", "requestCount", "0", "resourceType", "item",
                            "optionalAddon", optionalAddonAbsent() ? "absent" : "present"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 4000, required = true, manualOnly = true)
    public static void automationCraftingCard(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var target = new InterfaceBlockEntity[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native provider capability");
            if (target[0] == null) {
                AutomationNativeObservation.begin("automationcraftingcard");
                AutomationAuthorityObservation.begin("automationcraftingcard", fixture);
                fixture.binding().insertMaterials(2);
                target[0] = fixture.placeProviderInterface();
                AutomationAuthorityObservation.authorizeInterface(target[0].getInterfaceLogic());
                fixture.installCraftingCard(target[0]);
                fixture.configure(target[0], 0, AEItemKey.of(Items.STICK), 4);
                helper.assertTrue(false, "Waiting for genuine Crafting Card demand");
            }
            helper.assertTrue(fixture.interfaceReady(target[0], true), "Crafting Interface must join provider Grid");
            helper.assertValueEqual(fixture.interfaceAmount(target[0], 0), 4L,
                    "Crafted output must be inserted through InterfaceLogic");
            helper.assertValueEqual(fixture.binding().materialAmount(), 0L,
                    "Native CPU must consume the real source materials");
            helper.assertTrue(target[0].getInterfaceLogic().getUpgrades().isInstalled(appeng.core.definitions.AEItems.CRAFTING_CARD),
                    "Genuine Crafting Card must remain installed");
            helper.assertTrue(target[0].getInterfaceLogic().getRequestedJobs().isEmpty(),
                    "Completed native Interface link must leave requested jobs");
            var observation = AutomationNativeObservation.snapshot();
            helper.assertValueEqual(observation.jobIds().size(), 1, "One native link UUID must own the demand");
            helper.assertValueEqual(observation.trackerSubmissions(), 1, "One native tracker submission must execute");
            write("automationcraftingcard", 15, fixture, target[0].getInterfaceLogic(), fixture.binding().sourcePhysicalStorage(),
                    target[0].getInterfaceLogic().getStorage(), Map.of(
                            "configured", "4", "accepted", "4", "materialBefore", "2", "materialAfter", "0",
                            "craftingCard", "true", "jobCardinality", "1", "jobIds", observation.joinedJobs()));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1200, required = true, manualOnly = true)
    public static void automationNativeBuses(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var placed = new boolean[1];
        var fluidInterface = new InterfaceBlockEntity[1];
        var fluidConfigured = new boolean[1];
        helper.succeedWhen(() -> {
            if (!placed[0]) {
                helper.assertTrue(fixture.ready(), "Waiting for authorized shared Storage");
                AutomationNativeObservation.begin("automationnativebuses");
                AutomationAuthorityObservation.begin("automationnativebuses", fixture);
                AutomationAuthorityObservation.authorizeProjectionOperation("insert",
                        fixture.itemKey(Items.GOLD_INGOT));
                AutomationAuthorityObservation.authorizeProjectionOperation("extract",
                        fixture.itemKey(Items.DIAMOND));
                fixture.clearConsumerCell();
                helper.assertValueEqual(fixture.insertSource(fixture.itemKey(Items.DIAMOND), 4), 4L,
                        "Physical source must accept export stock");
                helper.assertValueEqual(fixture.insertFluidSource(AEFluidKey.of(Fluids.WATER), 1_500), 1_500L,
                        "Physical fluid source must accept water stock");
                fixture.placeNativeBuses(new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT, 3),
                        fixture.itemKey(Items.DIAMOND));
                placed[0] = true;
                helper.assertTrue(false, "Waiting for native bus ticks");
            }
            if (fluidInterface[0] == null) {
                helper.assertValueEqual(fixture.importChestCount(Items.GOLD_INGOT), 0,
                        "Native Import Bus must empty its physical source chest");
                helper.assertValueEqual(fixture.exportChestCount(Items.DIAMOND), 4,
                        "Native Export Bus must fill its physical destination chest");
                var busObservation = AutomationNativeObservation.snapshot();
                helper.assertTrue(busObservation.importWork() > 0 && busObservation.exportWork() > 0,
                        "Both native bus entrypoints must report real work");
                fluidInterface[0] = fixture.placeConsumerInterface(true);
                AutomationAuthorityObservation.authorizeInterface(fluidInterface[0].getInterfaceLogic());
                helper.assertTrue(false, "Waiting for native fluid Interface authority");
            }
            if (!fluidConfigured[0]) {
                helper.assertTrue(fixture.interfaceReady(fluidInterface[0], false),
                        "Native fluid Interface must join before authority capture");
                AutomationAuthorityObservation.captureProjection(fixture);
                AutomationAuthorityObservation.authorizeProjectionOperation("extract", AEFluidKey.of(Fluids.WATER));
                fixture.configure(fluidInterface[0], 0, AEFluidKey.of(Fluids.WATER), 1_000);
                fluidConfigured[0] = true;
                helper.assertTrue(false, "Waiting for native fluid Interface stocking");
            }
            helper.assertValueEqual(fixture.importChestCount(Items.GOLD_INGOT), 0,
                    "Native Import Bus must empty its physical source chest");
            helper.assertValueEqual(fixture.exportChestCount(Items.DIAMOND), 4,
                    "Native Export Bus must fill its physical destination chest");
            helper.assertValueEqual(fixture.sourceAmount(fixture.itemKey(Items.GOLD_INGOT)), 3L,
                    "Imported items must reach the Task 21 physical source");
            helper.assertValueEqual(fixture.sourceAmount(fixture.itemKey(Items.DIAMOND)), 0L,
                    "Exported items must leave the Task 21 physical source");
            helper.assertTrue(fixture.interfaceReady(fluidInterface[0], false),
                    "Native fluid Interface must join the consumer Grid");
            helper.assertValueEqual(fixture.interfaceAmount(fluidInterface[0], 0), 1_000L,
                    "Native Interface must stock water through shared Storage");
            helper.assertValueEqual(fixture.sourceFluidAmount(AEFluidKey.of(Fluids.WATER)), 500L,
                    "Native physical fluid stock must reconcile after Interface extraction");
            var observation = AutomationNativeObservation.snapshot();
            helper.assertTrue(observation.importWork() > 0 && observation.exportWork() > 0,
                    "Both native bus entrypoints must report real work");
            helper.assertTrue(observation.storageInserted() == 3 && observation.storageExtracted() == 1_004,
                    "Bus transfers must reconcile accepted projection quantities");
            write("automationnativebuses", 21, fixture, observation.importBus(), fixture.binding().sourcePhysicalStorage(),
                    observation.exportBus(), Map.of(
                            "imported", "3", "exported", "4", "sourceGold", "3", "sourceDiamond", "0",
                            "itemPath", "native-buses", "fluidPath", "native-interface-storage",
                            "fluidStocked", "1000", "fluidSourceAfter", "500",
                            "optionalAddon", optionalAddonAbsent() ? "absent" : "present"));
            fixture.close();
        });
    }

    static void write(String testId, int assertions, NativeAutomationFixture fixture, Object owner, Object source,
            Object destination, Map<String, String> caseFacts) {
        var facts = new java.util.TreeMap<>(caseFacts);
        facts.put("owner", AutomationEvidence.identity(owner));
        facts.put("source", AutomationEvidence.identity(source));
        facts.put("destination", AutomationEvidence.identity(destination));
        facts.put("storageKey", fixture.storageKey().toString().replace(' ', '_'));
        facts.put("craftingKey", fixture.binding().key().toString().replace(' ', '_'));
        facts.put("forwardAuthority", "true");
        facts.put("reverseAuthority", Boolean.toString(PolicyService.get(fixture.binding().level())
                .configured(fixture.reverseStorageKey()).isPresent()));
        facts.put("nativeState", "ae2-owned");
        facts.put("federationController", "false");
        facts.put("federationLedger", "false");
        facts.put("mountCount", Integer.toString(fixture.storageMountCount()));
        AutomationEvidence.write(testId, assertions, facts);
    }

    private static boolean optionalAddonAbsent() {
        return NativeAutomationGameTests.class.getClassLoader()
                .getResource("com/glodblock/github/appflux/common/me/key/FluxKey.class") == null;
    }
}
