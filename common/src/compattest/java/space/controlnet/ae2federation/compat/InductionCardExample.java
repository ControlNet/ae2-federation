package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/**
 * The guide's Induction Card example, whichever mod's machine it is built with: the Provider's network keeps FE in an
 * FE cell, and the machine at the Provider's Endpoint, with no power of its own and no energy cell on the Endpoint's
 * subnet, runs the job on that FE alone. The guide's exercise: ordered before the Induction Card is in, the job waits
 * with nothing back for longer than the machine takes for one input; with the card in, it finishes.
 */
final class InductionCardExample {
    private static final long STORED = 1_000_000;
    /** How long a job waits for the Induction Card: longer than any variant's machine takes for one input. */
    private static final int CARD_LEFT_OUT_TICKS = 300;

    private InductionCardExample() {
    }

    /**
     * Runs the example around {@code machine}, which keeps the Provider network's FE in an FE cell
     * ({@link EndpointMachineScene.Machine#outputCell}).
     */
    static void run(GameTestHelper helper, EndpointMachineScene.Machine machine) {
        var fe = AppliedFluxCompatGameTests.fluxKey();
        var card = AddonCraftingScene.item("appflux:induction_card");
        long[] stored = {-1};
        var scene = new EndpointMachineScene(helper, machine).poweredThroughEndpoint()
                .preparingProvider(provider -> {
                    var storage = provider.getMainNode().getGrid().getStorageService().getInventory();
                    helper.assertValueEqual(storage.insert(fe, STORED, Actionable.MODULATE, IActionSource.empty()),
                            STORED, "The FE cell must store " + STORED + " FE");
                    stored[0] = STORED;
                })
                .interruptedBy(CARD_LEFT_OUT_TICKS, (test, at) -> helper.assertTrue(
                        provider(helper).upgrades().getInstalledUpgrades(card) == 0,
                        "The job must be ordered before the Induction Card goes in"),
                        (test, at) -> helper.assertTrue(provider(helper).upgrades().toItemHandler()
                                .insertItem(0, new ItemStack(card), false).isEmpty(),
                                "The Provider must take the Induction Card"));
        helper.succeedWhen(() -> {
            scene.tick();
            var left = provider(helper).getMainNode().getGrid().getStorageService().getInventory()
                    .getAvailableStacks().get(fe);
            helper.assertTrue(left < stored[0], "The machine's FE must come from the Provider's network: " + left);
        });
    }

    /** The Provider in {@link EndpointMachineScene}, which stands at (4, 1, 3). */
    private static FederationPatternProviderBlockEntity provider(GameTestHelper helper) {
        return helper.getBlockEntity(new BlockPos(4, 1, 3));
    }
}
