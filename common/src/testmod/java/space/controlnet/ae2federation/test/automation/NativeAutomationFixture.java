package space.controlnet.ae2federation.test.automation;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.ImportBusPart;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;

public final class NativeAutomationFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos CONSUMER_INTERFACE = BASE.west();
    private static final BlockPos SECOND_INTERFACE = BASE.south();
    private static final BlockPos PROVIDER_INTERFACE = BASE.north().west();
    private static final BlockPos IMPORT_CHEST = BASE.west();
    private static final BlockPos EXPORT_CHEST = BASE.east(2);

    private final GameTestHelper helper;
    private final CraftingBindingFixture binding;
    private boolean authorized;

    public NativeAutomationFixture(GameTestHelper helper) {
        this.helper = helper;
        binding = new CraftingBindingFixture(helper, true, false, true);
    }

    public boolean ready() {
        if (!binding.ready()) {
            return false;
        }
        if (!authorized) {
            binding.enable();
            var result = PolicyService.get(helper.getLevel()).edit(new PolicyEdit(storageKey(), PolicyRevision.NONE,
                    PolicyRule.enabled(Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT, PolicyOperation.INSERT))));
            helper.assertTrue(result instanceof PolicyMutationResult.Accepted,
                    "Forward native Storage policy must be accepted");
            StorageMountService.get(helper.getLevel()).observeConnectedGrids(binding.consumerGrid(), binding.providerGrid());
            authorized = true;
            return false;
        }
        return CraftingBindingService.get(helper.getLevel()).capability(binding.key()).isPresent()
                && StorageMountService.get(helper.getLevel()).projection(storageKey()) != null;
    }

    public InterfaceBlockEntity placeConsumerInterface(boolean second) {
        return placeInterface(second ? SECOND_INTERFACE : CONSUMER_INTERFACE, false);
    }

    public InterfaceBlockEntity placeProviderInterface() {
        return placeInterface(PROVIDER_INTERFACE, true);
    }


    public void configure(InterfaceBlockEntity blockEntity, int slot, AEKey key, long amount) {
        blockEntity.getInterfaceLogic().getConfig().setStack(slot, new GenericStack(key, amount));
    }

    public void installCraftingCard(InterfaceBlockEntity blockEntity) {
        helper.assertTrue(blockEntity.getInterfaceLogic().getUpgrades().addItems(AEItems.CRAFTING_CARD.stack()).isEmpty(),
                "Native Interface must accept its Crafting Card");
    }

    public void removeCraftingCard(InterfaceBlockEntity blockEntity) {
        blockEntity.getInterfaceLogic().getUpgrades().removeItems(1, ItemStack.EMPTY, null);
    }

    public void clearConsumerCell() {
        binding.consumerChest().setCell(ItemStack.EMPTY);
    }

    public long insertSource(AEKey key, long amount) {
        return binding.sourceStorage().insert(key, amount, Actionable.MODULATE,
                appeng.api.networking.security.IActionSource.empty());
    }

    public long sourceAmount(AEKey key) {
        return binding.sourcePhysicalStorage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                appeng.api.networking.security.IActionSource.empty());
    }

    public long insertFluidSource(AEKey key, long amount) {
        return binding.sourceFluidStorage().insert(key, amount, Actionable.MODULATE,
                appeng.api.networking.security.IActionSource.empty());
    }

    public long sourceFluidAmount(AEKey key) {
        return binding.sourceFluidStorage().extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                appeng.api.networking.security.IActionSource.empty());
    }

    public long interfaceAmount(InterfaceBlockEntity blockEntity, int slot) {
        var stack = blockEntity.getInterfaceLogic().getStorage().getStack(slot);
        return stack == null ? 0 : stack.amount();
    }

    public boolean interfaceReady(InterfaceBlockEntity blockEntity, boolean provider) {
        var node = blockEntity.getMainNode().getNode();
        return node != null && node.isActive() && node.hasGridBooted()
                && node.getGrid() == (provider ? binding.providerGrid() : binding.consumerGrid());
    }

    public BusPair placeNativeBuses(ItemStack importStack, AEKey exportKey) {
        helper.setBlock(IMPORT_CHEST, Blocks.CHEST);
        helper.setBlock(EXPORT_CHEST, Blocks.CHEST);
        importContainer().setItem(0, importStack);
        var importBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BASE), Direction.WEST, null,
                AEParts.IMPORT_BUS.get());
        var exportBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BASE.east()), Direction.EAST, null,
                AEParts.EXPORT_BUS.get());
        helper.assertTrue(importBus != null && exportBus != null, "Native Import and Export Buses must be placed");
        importBus.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", binding.key().consumerNetworkId()));
        exportBus.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", binding.key().consumerNetworkId()));
        var importCable = PartHelper.getPart(helper.getLevel(), helper.absolutePos(BASE), null);
        var exportCable = PartHelper.getPart(helper.getLevel(), helper.absolutePos(BASE.east()), null);
        helper.assertTrue(importCable != null && exportCable != null, "Native buses must retain their cable hosts");
        GridHelper.createConnection(importCable.getGridNode(), importBus.getGridNode());
        GridHelper.createConnection(exportCable.getGridNode(), exportBus.getGridNode());
        exportBus.getConfig().insert(0, exportKey, 1, Actionable.MODULATE);
        return new BusPair(importBus, exportBus);
    }

    public int importChestCount(Item item) {
        return count(importContainer(), item);
    }

    public int exportChestCount(Item item) {
        return count(exportContainer(), item);
    }

    public CraftingBindingFixture binding() {
        return binding;
    }

    public PolicyKey storageKey() {
        return new PolicyKey(binding.key().consumerNetworkId(), binding.key().providerNetworkId(),
                PolicyCapability.STORAGE);
    }

    public PolicyKey reverseStorageKey() {
        return new PolicyKey(binding.key().providerNetworkId(), binding.key().consumerNetworkId(),
                PolicyCapability.STORAGE);
    }

    public int storageMountCount() {
        return StorageMountService.get(helper.getLevel()).mountedRelationshipCount();
    }

    private InterfaceBlockEntity placeInterface(BlockPos position, boolean provider) {
        helper.setBlock(position, AEBlocks.INTERFACE.block());
        var blockEntity = helper.<InterfaceBlockEntity>getBlockEntity(position);
        var networkId = provider ? binding.key().providerNetworkId() : binding.key().consumerNetworkId();
        blockEntity.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        return blockEntity;
    }

    private Container importContainer() {
        return helper.getBlockEntity(IMPORT_CHEST);
    }

    private Container exportContainer() {
        return helper.getBlockEntity(EXPORT_CHEST);
    }

    private static int count(Container container, Item item) {
        var total = 0;
        for (var slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).is(item)) {
                total += container.getItem(slot).getCount();
            }
        }
        return total;
    }

    public static AEItemKey itemKey(Item item) {
        return AEItemKey.of(item);
    }

    @Override
    public void close() {
        binding.close();
    }

    public record BusPair(ImportBusPart importBus, ExportBusPart exportBus) {
    }
}
