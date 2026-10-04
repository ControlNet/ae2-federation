package space.controlnet.ae2federation.compat;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.IConfigManager;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene;
import space.controlnet.ae2federation.test.processing.ProductionProviderScene.Target;

/** ExpandedAE's blocking modes on a Federation Pattern Provider's Lanes, which push into an Endpoint's subnet. */
@PrefixGameTestTemplate(false)
public final class ExpandedAECompatGameTests {
    private static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);
    private static final AEItemKey DIRT = AEItemKey.of(Items.DIRT);

    private ExpandedAECompatGameTests() {
    }

    /**
     * With blocking on and ExpandedAE's Smart mode, a Lane blocks as a native provider does: it pushes the same
     * pattern again while the subnet holds only that pattern's input, refuses a pattern with another input, and in
     * ExpandedAE's Default mode (AE2's) refuses the same pattern too.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void smartBlockingLane(GameTestHelper helper) {
        var scene = new ProductionProviderScene(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            switch (phase[0]) {
                case 0 -> {
                    var readiness = scene.topologyReadiness();
                    helper.assertTrue("ready".equals(readiness), "Waiting for the topology: " + readiness);
                    helper.assertTrue(scene.setAccess(Target.A, true), "The Endpoint must join the domain");
                    scene.installPattern(0);
                    helper.assertTrue(scene.provider().getTerminalPatternInventory().insertItem(1,
                            PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(DIRT, 1)),
                                    List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1))), false).isEmpty(),
                            "The provider must take the second pattern");
                    for (int slot = 0; slot < 2; slot++) {
                        var status = scene.map(slot, Target.A);
                        helper.assertTrue(status.startsWith("accepted-"), "Mapping slot " + slot + ": " + status);
                    }
                    var config = scene.provider().getConfigManager();
                    config.putSetting(Settings.BLOCKING_MODE, YesNo.YES);
                    blockingType(config, "SMART");
                    phase[0] = 1;
                    helper.fail("Mapped both patterns with Smart blocking");
                }
                case 1 -> {
                    var lane = scene.provider().lane(0);
                    helper.assertValueEqual(lane.getAvailablePatterns().size(), 2, "Waiting for the Lane's patterns");
                    helper.assertTrue(push(lane, COBBLESTONE), "The first push must go through");
                    phase[0] = 2;
                    helper.fail("Pushed the cobblestone pattern once");
                }
                case 2 -> {
                    var lane = scene.provider().lane(0);
                    helper.assertValueEqual(scene.targetAmount(Target.A, COBBLESTONE), 1L,
                            "Waiting for the cobblestone in the subnet");
                    // One tick apart, so the Lane is not busy from the push before.
                    var again = push(lane, COBBLESTONE);
                    var other = push(lane, DIRT);
                    helper.assertTrue(again, "Smart blocking must push the same pattern again");
                    helper.assertFalse(other, "Smart blocking must refuse a pattern with another input");
                    blockingType(scene.provider().getConfigManager(), "DEFAULT");
                    phase[0] = 3;
                    helper.fail("Checked Smart blocking");
                }
                default -> {
                    helper.assertValueEqual(scene.targetAmount(Target.A, COBBLESTONE), 2L,
                            "Waiting for the second cobblestone");
                    helper.assertFalse(push(scene.provider().lane(0), COBBLESTONE),
                            "Default blocking must refuse the same pattern while its input waits");
                    helper.assertValueEqual(scene.targetAmount(Target.A, DIRT), 0L, "The refused dirt never arrives");
                }
            }
        });
    }

    private static boolean push(NativeProviderLane lane, AEItemKey input) {
        IPatternDetails pattern = lane.getAvailablePatterns().stream()
                .filter(details -> details.getInputs()[0].getPossibleInputs()[0].what().equals(input))
                .findFirst().orElseThrow();
        var inputs = new KeyCounter();
        inputs.add(input, 1);
        return lane.pushPattern(pattern, new KeyCounter[] { inputs });
    }

    /** ExpandedAE's blocking type, set as its button in the provider's screen sets it. */
    private static void blockingType(IConfigManager config, String mode) {
        Settings.getOrThrow("blocking_type").setFromString(config, mode);
    }
}
