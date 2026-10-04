package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.storage.RouterStorageMountFixture;

/**
 * Two networks joined by Routers and Federation Cable share storage through a Storage rule, with the provider's ME
 * Chest holding the cell named by registry id: iron stored on the provider is seen and taken by the consumer.
 */
final class AddonStorageScene {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    private final GameTestHelper helper;
    private final RouterStorageMountFixture fixtures;
    private final String cellId;
    private final IActionSource source = IActionSource.empty();
    private int step;

    AddonStorageScene(GameTestHelper helper, String cellId) {
        this.helper = helper;
        this.cellId = cellId;
        fixtures = new RouterStorageMountFixture(helper);
        var key = ResourceLocation.parse(cellId);
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(key), "Item " + cellId + " is not registered");
        fixtures.providerChest().setCell(new ItemStack(BuiltInRegistries.ITEM.get(key)));
    }

    /** Each step runs once; {@code succeedWhen} retries the checks after it until they hold. */
    void tick() {
        if (step == 0 && fixtures.networksSettled()) {
            fixtures.connectRouters();
            step = 1;
        }
        helper.assertTrue(step >= 1 && fixtures.connected(), "The two Routers must join one Federation Domain");
        var key = fixtures.key();
        var policies = PolicyService.get(helper.getLevel());
        if (policies.revision(key).equals(PolicyRevision.NONE)) {
            policies.edit(new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
        }
        helper.assertTrue(StorageMountService.get(helper.getLevel()).projection(key) != null,
                "The Storage rule must mount the provider's storage");
        var provider = fixtures.providerGrid().getStorageService().getInventory();
        var consumer = fixtures.consumerGrid().getStorageService().getInventory();
        if (step == 1) {
            helper.assertValueEqual(provider.insert(IRON, 9, Actionable.MODULATE, source), 9L,
                    "The provider network's " + cellId + " must store 9 iron");
            step = 2;
        }
        helper.assertValueEqual(consumer.getAvailableStacks().get(IRON), 9L,
                "The consumer network must see the provider's iron");
        if (step == 2) {
            helper.assertValueEqual(consumer.extract(IRON, 4, Actionable.MODULATE, source), 4L,
                    "The consumer network must take iron from the provider");
            step = 3;
        }
        helper.assertValueEqual(provider.getAvailableStacks().get(IRON), 5L,
                "The provider network must have 5 iron left");
    }
}
