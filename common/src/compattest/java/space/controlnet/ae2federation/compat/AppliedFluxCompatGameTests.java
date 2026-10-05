package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;

/** Federation features with Applied Flux's FE storage. */
@PrefixGameTestTemplate(false)
public final class AppliedFluxCompatGameTests {
    private AppliedFluxCompatGameTests() {
    }

    /** The provider network's storage is a 1k FE Storage Cell holding energy as an ME resource. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void fluxCellShared(GameTestHelper helper) {
        var scene = new AddonStorageScene(helper, "appflux:fe_1k_cell", fluxKey(), 100_000, 25_000);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Federation Pattern Provider has the upgrade slots AE2's own Pattern Provider has with Applied Flux, and takes
     * the cards it takes: the Induction Card goes in, keeps through a save, and a card AE2's provider refuses stays out.
     * Applied Flux's energy distributor, which it adds to every provider logic, reaches the Provider's network node.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 200)
    public static void providerInheritsUpgradeSlots(GameTestHelper helper) {
        var nativeAt = new BlockPos(1, 1, 1);
        var federationAt = new BlockPos(3, 1, 1);
        helper.setBlock(nativeAt, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(federationAt, ProcessingRegistration.PROVIDER.get());
        var card = AddonCraftingScene.item("appflux:induction_card");
        var federationItem = ProcessingRegistration.PROVIDER_ITEM.get();
        helper.succeedWhen(() -> {
            int nativeMax = Upgrades.getMaxInstallable(card, AEBlocks.PATTERN_PROVIDER);
            helper.assertTrue(nativeMax > 0, "Applied Flux must register the Induction Card for AE2's Pattern Provider");
            helper.assertValueEqual(Upgrades.getMaxInstallable(card, federationItem), nativeMax,
                    "The Federation Pattern Provider must take as many Induction Cards as AE2's");
            helper.assertValueEqual(Upgrades.getMaxInstallable(AEItems.SPEED_CARD, federationItem),
                    Upgrades.getMaxInstallable(AEItems.SPEED_CARD, AEBlocks.PATTERN_PROVIDER),
                    "A card AE2's Pattern Provider does not take must not be added");
            var nativeLogic = helper.<BlockEntity>getBlockEntity(nativeAt) instanceof PatternProviderLogicHost host
                    ? host.getLogic() : null;
            helper.assertTrue((Object) nativeLogic instanceof IUpgradeableObject,
                    "Applied Flux must give AE2's provider logic upgrade slots");
            int nativeSlots = ((IUpgradeableObject) (Object) nativeLogic).getUpgrades().size();
            var provider = helper.<FederationPatternProviderBlockEntity>getBlockEntity(federationAt);
            var upgrades = provider.upgrades();
            helper.assertValueEqual(upgrades.size(), nativeSlots,
                    "The Federation Pattern Provider must have as many upgrade slots as AE2's");
            var node = provider.getMainNode().getNode();
            helper.assertTrue(node != null, "Waiting for the Provider's network node");
            helper.assertTrue(node.getService(energyDistributor()) != null,
                    "Applied Flux's energy distributor must reach the Provider's network node");
            var slots = upgrades.toItemHandler();
            helper.assertValueEqual(slots.insertItem(0, AEItems.SPEED_CARD.stack(), true).getCount(), 1,
                    "The upgrade slot must refuse a card AE2's Pattern Provider refuses");
            helper.assertTrue(slots.insertItem(0, new ItemStack(card), false).isEmpty(),
                    "The upgrade slot must take the Induction Card");
            helper.assertTrue(upgrades.isInstalled(card), "The Induction Card must count as installed");
            var registries = helper.getLevel().registryAccess();
            var loaded = BlockEntity.loadStatic(provider.getBlockPos(), provider.getBlockState(),
                    provider.saveWithFullMetadata(registries), registries);
            helper.assertTrue(loaded instanceof FederationPatternProviderBlockEntity saved
                    && saved.upgrades().isInstalled(card), "The Induction Card must keep through a save");
        });
    }

    /** Applied Flux's energy distributor service, from its own classes. */
    @SuppressWarnings("unchecked")
    private static Class<? extends appeng.api.networking.IGridNodeService> energyDistributor() {
        try {
            return (Class<? extends appeng.api.networking.IGridNodeService>) AppliedFluxCompatGameTests.class
                    .getClassLoader().loadClass("com.glodblock.github.appflux.common.me.service.IEnergyDistributor");
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Applied Flux's energy distributor is unavailable", exception);
        }
    }

    /** Applied Flux's FE resource, from its own classes, since this test mod does not build against Applied Flux. */
    static AEKey fluxKey() {
        try {
            var loader = AppliedFluxCompatGameTests.class.getClassLoader();
            var type = loader.loadClass("com.glodblock.github.appflux.common.me.key.type.EnergyType");
            Object fe = null;
            for (var constant : type.getEnumConstants()) {
                if (((Enum<?>) constant).name().equals("FE")) fe = constant;
            }
            return (AEKey) loader.loadClass("com.glodblock.github.appflux.common.me.key.FluxKey")
                    .getMethod("of", type).invoke(null, fe);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Applied Flux's FE resource is unavailable", exception);
        }
    }
}
