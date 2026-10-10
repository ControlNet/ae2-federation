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
import space.controlnet.ae2federation.policy.PolicyOperation;
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
    /** East of the provider's ME Chest, through which a block placed there joins the provider network. */
    static final BlockPos BESIDE_CHEST = BUS_CABLE;

    private final GameTestHelper helper;
    private final RouterStorageMountFixture fixtures;
    private final String storage;
    private final AEKey what;
    private final long stored;
    private final long taken;
    private final IActionSource source = IActionSource.empty();
    private boolean infinite;
    private boolean extractOnly;
    private java.util.function.BooleanSupplier ready = () -> true;
    private int step;
    private BlockPos dismantlePart;
    private boolean switchOff;
    private net.minecraft.world.level.block.state.BlockState dismantledState;

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
        // As the guide's warehouse is built, the consumer has no power of its own: the ME power rule shares the
        // provider's.
        fixtures = new RouterStorageMountFixture(helper, false);
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
        return storageBus(helper, busId, containerId,
                test -> test.setBlock(BUS_TARGET, AddonCraftingScene.block(containerId)), () -> true, what, stored,
                taken, prepare);
    }

    /**
     * As {@link #storageBus(GameTestHelper, String, String, AEKey, long, long, java.util.function.Consumer)}, facing a
     * multiblock ({@code name}) that {@code place} builds with a block at {@link #BUS_TARGET}; the Storage rule is set
     * once {@code ready} holds.
     */
    static AddonStorageScene storageBus(GameTestHelper helper, String busId, String name,
            java.util.function.Consumer<GameTestHelper> place, java.util.function.BooleanSupplier ready, AEKey what,
            long stored, long taken, java.util.function.Consumer<Object> prepare) {
        var scene = new AddonStorageScene(busId + " on " + name, helper, what, stored, taken);
        scene.fixtures.providerChest().setCell(ItemStack.EMPTY);
        place.accept(helper);
        scene.ready = ready;
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
     * The provider network's only storage is a multiblock that {@code place} builds from {@link #BESIDE_CHEST}; the
     * Storage rule is set once {@code ready} holds, which may also fit its storage cells.
     */
    static AddonStorageScene structure(GameTestHelper helper, String name,
            java.util.function.Consumer<GameTestHelper> place, java.util.function.BooleanSupplier ready) {
        var scene = new AddonStorageScene(name, helper, IRON, 9, 4);
        scene.fixtures.providerChest().setCell(ItemStack.EMPTY);
        place.accept(helper);
        scene.ready = ready;
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

    /**
     * The rule lets the consumer view and take but not store: its store-back must be refused and leave the provider's
     * storage as it was.
     */
    AddonStorageScene extractOnly() {
        extractOnly = true;
        return this;
    }

    /**
     * After the round trip, breaks the multiblock's block at {@code part}: its contents must leave the consumer, and
     * come back once the block is put back and the structure forms again, as the guide's exercise has its player do.
     */
    AddonStorageScene dismantlingAfterwards(BlockPos part) {
        dismantlePart = part;
        return this;
    }

    /**
     * After the round trip, switches the Storage rule off: what the provider stores must leave the consumer's view and
     * stay on the provider; switched back on, it must return, as the guide's exercise has its player do.
     */
    AddonStorageScene switchingOffAfterwards() {
        switchOff = true;
        return this;
    }

    /** Each step runs once; {@code succeedWhen} retries the checks after it until they hold. */
    void tick() {
        helper.assertTrue(step > 0 || ready.getAsBoolean(), "Waiting for the provider's " + storage);
        if (step >= 7) {
            var seen = fixtures.consumerGrid().getStorageService().getInventory().getAvailableStacks().get(what);
            if (step == 7) {
                helper.assertValueEqual(seen, 0L, "Waiting for the switched-off rule to take the provider's " + what
                        + " away from the consumer");
                helper.assertValueEqual(fixtures.providerGrid().getStorageService().getInventory().getAvailableStacks()
                        .get(what), stored, "The provider must keep its " + what + " while the rule is off");
                switchRule(true);
                step = 8;
                helper.fail("Switched the Storage rule back on");
            }
            helper.assertValueEqual(seen, stored, "Waiting for the provider's " + what + " to return to the consumer");
            return;
        }
        // Right after a multiblock breaks, the provider's grid can have no confirmed identity for a while, so only
        // the consumer's view is read here.
        if (step >= 5) {
            var seen = fixtures.consumerGrid().getStorageService().getInventory().getAvailableStacks().get(what);
            if (step == 5) {
                helper.assertValueEqual(seen, 0L, "Waiting for the broken " + storage + " to leave the consumer");
                helper.setBlock(dismantlePart, dismantledState);
                step = 6;
                helper.fail("Put the " + storage + "'s block back");
            }
            helper.assertValueEqual(seen, stored, "Waiting for the re-formed " + storage + " to return to the consumer");
            return;
        }
        if (step == 0 && fixtures.networksSettled()) {
            fixtures.connectRouters();
            step = 1;
        }
        helper.assertTrue(step >= 1 && fixtures.connected(), "The two Routers must join one Federation Domain");
        var key = fixtures.key();
        var policies = PolicyService.get(helper.getLevel());
        if (policies.revision(key).equals(PolicyRevision.NONE)) {
            policies.edit(new PolicyEdit(key, PolicyRevision.NONE, extractOnly
                    ? PolicyRule.enabled(java.util.Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT))
                    : PolicyRule.storageDefaults()));
            policies.edit(new PolicyEdit(fixtures.energyKey(), PolicyRevision.NONE,
                    PolicyRule.enabled(java.util.Set.of(PolicyOperation.SUPPLY))));
        }
        helper.assertTrue(fixtures.consumerGrid().getEnergyService().isNetworkPowered(),
                "Waiting for the ME power rule to power the consumer network");
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
        if (extractOnly) {
            helper.assertValueEqual(consumer.insert(what, taken, Actionable.MODULATE, source), 0L,
                    "A rule without Insert must refuse the consumer's " + what);
            helper.assertValueEqual(provider.getAvailableStacks().get(what), stored - taken,
                    "The refused store-back must leave the provider's " + storage + " as it was");
            return;
        }
        if (step == 3) {
            helper.assertValueEqual(consumer.insert(what, taken, Actionable.MODULATE, source), taken,
                    "The consumer network must store " + what + " back into the provider's " + storage);
            step = 4;
        }
        helper.assertValueEqual(provider.getAvailableStacks().get(what), stored,
                "What the consumer stored must reach the provider's " + storage);
        if (switchOff) {
            switchRule(false);
            step = 7;
            helper.fail("Switched the Storage rule off");
        }
        if (dismantlePart != null) {
            dismantledState = helper.getBlockState(dismantlePart);
            helper.setBlock(dismantlePart, net.minecraft.world.level.block.Blocks.AIR);
            step = 5;
            helper.fail("Broke the " + storage + " at " + dismantlePart);
        }
    }

    /** Switches the Storage rule on or off, as its switch in the Federation screen does. */
    private void switchRule(boolean enabled) {
        var key = fixtures.key();
        var policies = PolicyService.get(helper.getLevel());
        var record = policies.configured(key).orElseThrow();
        helper.assertTrue(policies.edit(new PolicyEdit(key, policies.revision(key), record.rule().withEnabled(enabled)))
                instanceof space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted,
                "The Storage rule must switch " + (enabled ? "on" : "off"));
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
