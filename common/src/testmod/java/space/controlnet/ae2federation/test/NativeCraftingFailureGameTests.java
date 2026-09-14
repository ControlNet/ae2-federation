package space.controlnet.ae2federation.test;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.me.helpers.BaseActionSource;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.NativeCraftingFixtures;

@PrefixGameTestTemplate(false)
public final class NativeCraftingFailureGameTests {
    private NativeCraftingFailureGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void craftProofMissingMaterial(GameTestHelper helper) {
        var fixture = new NativeCraftingFixtures(helper, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for powered native crafting Grid");
            fixture.begin(CalculationStrategy.REPORT_MISSING_ITEMS, 4, new BaseActionSource());
            if (!fixture.planReady()) {
                helper.assertTrue(false, "Waiting for native missing-material plan");
            }
            helper.assertTrue(fixture.plan().simulation(), "Missing materials must produce a native simulation plan");
            helper.assertValueEqual(fixture.plan().missingItems().get(NativeCraftingFixtures.inputKey()), 2L,
                    "Native planner must report two missing planks");
            var result = fixture.submit(null);
            helper.assertTrue(!result.successful(), "Simulation plan must not submit");
            helper.assertValueEqual(result.errorCode(), CraftingSubmitErrorCode.INCOMPLETE_PLAN,
                    "Native service must reject incomplete plan");
            helper.assertValueEqual(fixture.materialAmount(), 0L, "Missing plan must extract no material");
            NativeCraftingEvidence.write("craftproofmissingmaterial", 7, Map.of(
                    "planSimulation", "true", "missingMaterial", "2", "submitted", "false",
                    "submitError", "INCOMPLETE_PLAN", "materialAfterPlan", "0", "uniqueNativeTasks", "0",
                    "craftingService", identity(fixture.service()), "cpuCount", Integer.toString(fixture.cpuCount())));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void craftProofNoCpu(GameTestHelper helper) {
        var fixture = new NativeCraftingFixtures(helper, false);
        var materialsInserted = new boolean[] { false };
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for powered native crafting Grid");
            if (!materialsInserted[0]) {
                fixture.insertMaterials(2);
                materialsInserted[0] = true;
                helper.assertTrue(false, "Waiting for native storage cache after material insertion");
            }
            fixture.begin(CalculationStrategy.REPORT_MISSING_ITEMS, 4, new BaseActionSource());
            if (!fixture.planReady()) {
                helper.assertTrue(false, "Waiting for native no-CPU plan");
            }
            helper.assertTrue(!fixture.plan().simulation(), "Materials and pattern must produce executable plan");
            helper.assertValueEqual(fixture.cpuCount(), 0, "Fixture must expose no native crafting CPU");
            var result = fixture.submit(null);
            helper.assertTrue(!result.successful(), "Native service must reject submission without CPU");
            helper.assertValueEqual(result.errorCode(), CraftingSubmitErrorCode.NO_CPU_FOUND,
                    "No-CPU failure must originate from native service");
            helper.assertValueEqual(fixture.materialAmount(), 2L, "Failed submission must leave material in storage");
            NativeCraftingEvidence.write("craftproofnocpu", 7, Map.of(
                    "planSimulation", "false", "cpuCount", "0", "submitted", "false",
                    "submitError", "NO_CPU_FOUND", "materialAfterSubmit", "2", "uniqueNativeTasks", "0",
                    "craftingService", identity(fixture.service()), "provider", identity(fixture.provider())));
            fixture.close();
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
