package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
    /** Beside the provider's ME Chest: a cable carrying the Storage Bus, and the block the bus faces. */
    private static final BlockPos BUS_CABLE = new BlockPos(10, 4, 5);
    static final BlockPos BUS_TARGET = new BlockPos(11, 4, 5);

    private final GameTestHelper helper;
    private final RouterStorageMountFixture fixtures;
    private final String storage;
    private final AEKey what;
    private final long stored;
    private final long taken;
    private final IActionSource source = IActionSource.empty();
    private boolean infinite;
    private int step;

    AddonStorageScene(GameTestHelper helper, String cellId) {
        this(helper, cellId, IRON, 9, 4);
    }

    AddonStorageScene(GameTestHelper helper, String cellId, AEKey what, long stored, long taken) {
        this(helper, new ItemStack(AddonCraftingScene.item(cellId)), what, stored, taken);
    }

    /** The provider's ME Chest holds {@code cell}, which may carry its own configuration, such as a partition. */
    AddonStorageScene(GameTestHelper helper, ItemStack cell, AEKey what, long stored, long taken) {
        this(BuiltInRegistries.ITEM.getKey(cell.getItem()).toString(), helper, what, stored, taken);
        fixtures.providerChest().setCell(cell);
    }

    private AddonStorageScene(String storage, GameTestHelper helper, AEKey what, long stored, long taken) {
        this.helper = helper;
        this.storage = storage;
        this.what = what;
        this.stored = stored;
        this.taken = taken;
        fixtures = new RouterStorageMountFixture(helper);
        // The consumer's only storage is what the rule shares, so what it stores can only go to the provider.
        fixtures.consumerChest().setCell(ItemStack.EMPTY);
    }

    /**
     * The provider network's only storage is a Storage Bus ({@code busId}, AE2's own or an addon's) on a cable beside
     * its ME Chest, facing {@code containerId}, a block placed as a player places it. {@code prepare} configures the
     * bus part, as its player does in its screen, and may be a no-op.
     */
    static AddonStorageScene storageBus(GameTestHelper helper, String busId, String containerId, AEKey what,
            long stored, long taken, java.util.function.Consumer<Object> prepare) {
        var scene = new AddonStorageScene(busId + " on " + containerId, helper, what, stored, taken);
        scene.fixtures.providerChest().setCell(ItemStack.EMPTY);
        helper.setBlock(BUS_TARGET, AddonCraftingScene.block(containerId));
        var level = helper.getLevel();
        helper.assertTrue(PartHelper.setPart(level, helper.absolutePos(BUS_CABLE), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null, "A cable must go beside the ME Chest");
        var bus = PartHelper.setPart(level, helper.absolutePos(BUS_CABLE), Direction.EAST, null,
                (appeng.api.parts.IPartItem<?>) AddonCraftingScene.item(busId));
        helper.assertTrue(bus != null, busId + " must go on the cable");
        prepare.accept(bus);
        return scene;
    }

    /**
     * For storage that holds an endless amount of one resource and never changes, such as a creative infinity cell:
     * the consumer sees some and takes {@code taken}, and nothing is stored first.
     */
    AddonStorageScene infinite() {
        infinite = true;
        return this;
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
                "The Storage rule must mount the provider's " + storage + ": "
                        + StorageMountService.status(helper.getLevel(), key) + "; the provider network itself has "
                        + fixtures.providerGrid().getStorageService().getInventory().getAvailableStacks());
        var provider = fixtures.providerGrid().getStorageService().getInventory();
        var consumer = fixtures.consumerGrid().getStorageService().getInventory();
        if (infinite) {
            helper.assertTrue(consumer.getAvailableStacks().get(what) >= taken,
                    "The consumer network must see the provider's endless " + what + ": "
                            + consumer.getAvailableStacks().get(what));
            helper.assertValueEqual(consumer.extract(what, taken, Actionable.MODULATE, source), taken,
                    "The consumer network must take " + what + " from the provider's " + storage);
            return;
        }
        if (step == 1) {
            helper.assertValueEqual(provider.insert(what, stored, Actionable.MODULATE, source), stored,
                    "The provider network's " + storage + " must store " + stored + " " + what);
            step = 2;
        }
        helper.assertValueEqual(consumer.getAvailableStacks().get(what), stored,
                "The consumer network must see the provider's " + what + " in " + storage + ": "
                        + StorageMountService.status(helper.getLevel(), key));
        if (step == 2) {
            helper.assertValueEqual(consumer.extract(what, taken, Actionable.MODULATE, source), taken,
                    "The consumer network must take " + what + " from the provider's " + storage);
            step = 3;
        }
        helper.assertValueEqual(provider.getAvailableStacks().get(what), stored - taken,
                "The provider network must keep the rest");
        if (step == 3) {
            helper.assertValueEqual(consumer.insert(what, taken, Actionable.MODULATE, source), taken,
                    "The consumer network must store " + what + " back into the provider's " + storage);
            step = 4;
        }
        helper.assertValueEqual(provider.getAvailableStacks().get(what), stored,
                "What the consumer stored must reach the provider's " + storage);
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
