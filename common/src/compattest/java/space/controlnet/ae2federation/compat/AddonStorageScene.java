package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
 * Chest holding the cell named by registry id: what is stored on the provider (iron unless given another resource,
 * such as a chemical) is seen and taken by the consumer.
 */
final class AddonStorageScene {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    private final GameTestHelper helper;
    private final RouterStorageMountFixture fixtures;
    private final String cellId;
    private final AEKey what;
    private final long stored;
    private final long taken;
    private final IActionSource source = IActionSource.empty();
    private int step;

    AddonStorageScene(GameTestHelper helper, String cellId) {
        this(helper, cellId, IRON, 9, 4);
    }

    AddonStorageScene(GameTestHelper helper, String cellId, AEKey what, long stored, long taken) {
        this.helper = helper;
        this.cellId = cellId;
        this.what = what;
        this.stored = stored;
        this.taken = taken;
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
            helper.assertValueEqual(provider.insert(what, stored, Actionable.MODULATE, source), stored,
                    "The provider network's " + cellId + " must store " + stored + " " + what);
            step = 2;
        }
        helper.assertValueEqual(consumer.getAvailableStacks().get(what), stored,
                "The consumer network must see the provider's " + what);
        if (step == 2) {
            helper.assertValueEqual(consumer.extract(what, taken, Actionable.MODULATE, source), taken,
                    "The consumer network must take " + what + " from the provider");
            step = 3;
        }
        helper.assertValueEqual(provider.getAvailableStacks().get(what), stored - taken,
                "The provider network must keep the rest");
    }

    /**
     * A resource of an addon's own key type from its saved form, such as {@code appmek:chemical} and
     * {@code mekanism:hydrogen}, without building against the addon.
     */
    static AEKey key(GameTestHelper helper, String type, String id) {
        var tag = new CompoundTag();
        tag.putString("#t", type);
        tag.putString("id", id);
        var key = AEKey.fromTagGeneric(helper.getLevel().registryAccess(), tag);
        helper.assertTrue(key != null, "No " + type + " resource " + id);
        return key;
    }
}
