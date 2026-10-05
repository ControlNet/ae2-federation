package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.storage.RouterStorageMountFixture;

/** Applied Flux's FE storage shared with a network that runs Mekanism machines on it. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxMekanismCompatGameTests {
    /** West of the consumer's ME Chest: the Flux Accessor, and the Crusher it powers. */
    private static final BlockPos ACCESSOR = new BlockPos(2, 4, 5);
    private static final BlockPos CRUSHER = new BlockPos(1, 4, 5);
    private static final long STORED = 1_000_000;
    private static final int COBBLESTONE = 2;
    /** Cobblestone added after the Storage rule is off: more than the Crusher's own energy buffer can crush. */
    private static final int MORE_COBBLESTONE = 8;
    /** How long the Crusher is watched once the rule is off, enough for its buffer to run out several times. */
    private static final int WATCHED_TICKS = 500;

    private AppliedFluxMekanismCompatGameTests() {
    }

    /**
     * The guide's power bank: the provider network keeps FE in a 1k FE Storage Cell, and the workshop network, with no
     * storage or energy cell of its own, uses its storage and its ME power. The workshop's Flux Accessor sends the bank's
     * FE into a Mekanism Crusher, which crushes cobblestone into gravel on it. The guide's exercise: with the Storage
     * rule switched off, the bank's FE leaves the workshop, the accessor draws no more, and the Crusher stops once the
     * energy it holds runs out.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 2000)
    public static void fluxAccessorRunsCrusher(GameTestHelper helper) {
        var fixtures = new RouterStorageMountFixture(helper, false);
        fixtures.consumerChest().setCell(ItemStack.EMPTY);
        fixtures.providerChest().setCell(new ItemStack(AddonCraftingScene.item("appflux:fe_1k_cell")));
        helper.setBlock(ACCESSOR, AddonCraftingScene.block("appflux:flux_accessor"));
        helper.setBlock(CRUSHER, AddonCraftingScene.block("mekanism:crusher"));
        MekanismSetup.configure(helper, CRUSHER, "ITEM", "INPUT", Direction.UP, false);
        MekanismSetup.configure(helper, CRUSHER, "ITEM", "OUTPUT", Direction.DOWN, false);
        MekanismSetup.configure(helper, CRUSHER, "ENERGY", "INPUT", Direction.EAST, false);
        var fe = AppliedFluxCompatGameTests.fluxKey();
        var source = IActionSource.empty();
        var level = helper.getLevel();
        int[] step = {0};
        long[] afterOff = {0, 0};
        helper.succeedWhen(() -> {
            if (step[0] == 0 && fixtures.networksSettled()) {
                fixtures.connectRouters();
                step[0] = 1;
            }
            helper.assertTrue(step[0] >= 1 && fixtures.connected(), "The two Routers must join one Federation Domain");
            var provider = fixtures.providerGrid().getStorageService().getInventory();
            if (step[0] == 1) {
                helper.assertValueEqual(provider.insert(fe, STORED, Actionable.MODULATE, source), STORED,
                        "The power bank's FE cell must store " + STORED + " FE");
                var policies = PolicyService.get(level);
                policies.edit(new PolicyEdit(fixtures.key(), PolicyRevision.NONE, PolicyRule.storageDefaults()));
                policies.edit(new PolicyEdit(fixtures.energyKey(), PolicyRevision.NONE,
                        PolicyRule.enabled(Set.of(PolicyOperation.SUPPLY))));
                var input = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                        Direction.UP);
                helper.assertTrue(input != null, "The Crusher must take items on top");
                helper.assertTrue(input.insertItem(0, new ItemStack(Items.COBBLESTONE, COBBLESTONE), false).isEmpty(),
                        "The Crusher must take the cobblestone");
                step[0] = 2;
            }
            var gravel = gravel(helper);
            var left = provider.getAvailableStacks().get(fe);
            if (step[0] == 2) {
                helper.assertValueEqual(gravel, COBBLESTONE, "Waiting for the Crusher to crush the cobblestone on "
                        + "the power bank's FE; the bank holds " + left + " FE, the workshop is powered: "
                        + fixtures.consumerGrid().getEnergyService().isNetworkPowered() + ", "
                        + MekanismSetup.describe(helper, CRUSHER));
                helper.assertTrue(left < STORED, "The FE the Crusher ran on must come from the power bank: " + left);
                var policies = PolicyService.get(level);
                var key = fixtures.key();
                policies.edit(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults().withEnabled(false)));
                var input = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                        Direction.UP);
                helper.assertTrue(input.insertItem(0, new ItemStack(Items.COBBLESTONE, MORE_COBBLESTONE), false)
                        .isEmpty(), "The Crusher must take more cobblestone");
                step[0] = 3;
            }
            if (step[0] == 3) {
                helper.assertValueEqual(fixtures.consumerGrid().getStorageService().getInventory()
                        .getAvailableStacks().get(fe), 0L, "Waiting for the bank's FE to leave the workshop");
                afterOff[0] = left;
                afterOff[1] = helper.getTick();
                step[0] = 4;
            }
            helper.assertTrue(helper.getTick() - afterOff[1] >= WATCHED_TICKS, "Watching the Crusher without the rule");
            helper.assertValueEqual(left, afterOff[0], "With the Storage rule off the accessor must draw no FE");
            helper.assertTrue(gravel < COBBLESTONE + MORE_COBBLESTONE, "The Crusher must stop once its own energy "
                    + "runs out, but crushed " + gravel);
            fixtures.close();
        });
    }

    private static int gravel(GameTestHelper helper) {
        var output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CRUSHER),
                Direction.DOWN);
        var gravel = 0;
        for (int slot = 0; output != null && slot < output.getSlots(); slot++) {
            if (output.getStackInSlot(slot).is(Items.GRAVEL)) gravel += output.getStackInSlot(slot).getCount();
        }
        return gravel;
    }
}
