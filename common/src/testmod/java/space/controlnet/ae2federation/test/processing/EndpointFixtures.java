package space.controlnet.ae2federation.test.processing;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.crafting.PatternDetailsHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointCapabilityComposition;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeLocalProvider;

public final class EndpointFixtures {
    public static final Direction FEDERATION_FACE = Direction.WEST;
    public static final List<Direction> ALLOWED_FACES = List.of(
            Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST);
    private static final BlockPos PROVIDER_POS = new BlockPos(3, 3, 3);
    private static final BlockPos SOURCE_ENERGY_POS = PROVIDER_POS.west();
    private static final BlockPos INTERFACE_POS = PROVIDER_POS.east();
    private static final BlockPos SUBNET_CHEST_POS = INTERFACE_POS.east();
    private static final BlockPos SUBNET_ENERGY_POS = SUBNET_CHEST_POS.below();
    private static final BlockPos SECOND_PROVIDER_POS = INTERFACE_POS.north();
    private static final BlockPos SECOND_ENERGY_POS = SECOND_PROVIDER_POS.north();
    private static final BlockPos REMOTE_PROVIDER_POS = new BlockPos(9, 3, 3);
    private static final BlockPos REMOTE_ENERGY_POS = REMOTE_PROVIDER_POS.west();
    private final GameTestHelper helper;

    public EndpointFixtures(GameTestHelper helper) {
        this(helper, false);
    }

    public EndpointFixtures(GameTestHelper helper, boolean includeSecondProvider) {
        this.helper = helper;
        helper.setBlock(SOURCE_ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
        helper.setBlock(INTERFACE_POS, AEBlocks.INTERFACE.block());
        helper.setBlock(SUBNET_CHEST_POS, AEBlocks.ME_CHEST.block());
        helper.setBlock(SUBNET_ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        if (includeSecondProvider) {
            helper.setBlock(SECOND_ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(SECOND_PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                    .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.SOUTH));
        }
        helper.setBlock(REMOTE_ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(REMOTE_PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
        var chest = helper.<MEChestBlockEntity>getBlockEntity(SUBNET_CHEST_POS);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native subnet item cell must exist");
        chest.setCell(cell);
    }

    public boolean ready() {
        var providerNode = providerNode();
        var subnetNode = subnetNode();
        var second = helper.getLevel().getBlockEntity(helper.absolutePos(SECOND_PROVIDER_POS));
        var secondReady = !(second instanceof PatternProviderBlockEntity) || secondProviderNode().isActive();
        return providerNode.isActive() && secondReady && remoteProviderNode().isActive()
                && subnetNode.isActive()
                && interfaceBlockEntity().getInterfaceLogic().getInventory() != null;
    }

    public void installPattern() {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        provider().getLogic().getPatternInv().setItemDirect(0, pattern);
        provider().getLogic().updatePatterns();
    }

    public boolean nativePush() {
        NativeEndpointObservation.reset();
        var pattern = provider().getLogic().getAvailablePatterns().getFirst();
        var input = pattern.getInputs()[0].getPossibleInputs()[0];
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return provider().getLogic().pushPattern(pattern, new KeyCounter[] { counter });
    }

    public String nativePushOwnerIdentity() {
        return NativeEndpointObservation.pushOwnerIdentity();
    }

    public String nativeTargetOwnerIdentity() {
        return NativeEndpointObservation.targetOwnerIdentity();
    }

    public long subnetItemCount() {
        return subnetNode().getGrid().getStorageService().getInventory().getAvailableStacks()
                .get(AEItemKey.of(Items.COBBLESTONE));
    }

    public EndpointCapabilityComposition composition() {
        return new EndpointCapabilityComposition(helper.getLevel(), helper.absolutePos(INTERFACE_POS), subnetNode(),
                FEDERATION_FACE);
    }

    public NativeLocalProvider nativeProvider() {
        var provider = provider();
        var returned = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                helper.absolutePos(PROVIDER_POS), Direction.EAST);
        helper.assertTrue(returned != null, "Native Provider return capability must resolve");
        return new NativeLocalProvider(provider.getLogic(), providerNode(), helper.absolutePos(PROVIDER_POS),
                Direction.EAST, returned);
    }

    public NativeLocalProvider secondNativeProvider() {
        var provider = secondProvider();
        var returned = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                helper.absolutePos(SECOND_PROVIDER_POS), Direction.SOUTH);
        helper.assertTrue(returned != null, "Second native Provider return capability must resolve");
        return new NativeLocalProvider(provider.getLogic(), secondProviderNode(), helper.absolutePos(SECOND_PROVIDER_POS),
                Direction.SOUTH, returned);
    }

    public NativeLocalProvider remoteNativeProvider() {
        var provider = remoteProvider();
        var returned = helper.getLevel().getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                helper.absolutePos(REMOTE_PROVIDER_POS), Direction.EAST);
        helper.assertTrue(returned != null, "Remote native Provider return capability must resolve");
        return new NativeLocalProvider(provider.getLogic(), remoteProviderNode(), helper.absolutePos(REMOTE_PROVIDER_POS),
                Direction.EAST, returned);
    }

    public PatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER_POS);
    }

    public PatternProviderBlockEntity secondProvider() {
        return helper.getBlockEntity(SECOND_PROVIDER_POS);
    }

    public PatternProviderBlockEntity remoteProvider() {
        return helper.getBlockEntity(REMOTE_PROVIDER_POS);
    }

    public InterfaceBlockEntity interfaceBlockEntity() {
        return helper.getBlockEntity(INTERFACE_POS);
    }

    public IGridNode providerNode() {
        var node = provider().getMainNode().getNode();
        helper.assertTrue(node != null, "Native Provider node must initialize");
        return node;
    }

    public IGridNode subnetNode() {
        var node = interfaceBlockEntity().getMainNode().getNode();
        helper.assertTrue(node != null, "Native Interface subnet node must initialize");
        return node;
    }

    public IGridNode secondProviderNode() {
        var node = secondProvider().getMainNode().getNode();
        helper.assertTrue(node != null, "Second native Provider node must initialize");
        return node;
    }

    public IGridNode remoteProviderNode() {
        var node = remoteProvider().getMainNode().getNode();
        helper.assertTrue(node != null, "Remote native Provider node must initialize");
        return node;
    }

    public IItemHandler nativeItemCapability(Direction face) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(PROVIDER_POS), face);
    }

    public IFluidHandler nativeFluidCapability(Direction face) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(PROVIDER_POS), face);
    }

    public void clearSubnetInput() {
        subnetNode().getGrid().getStorageService().getInventory().extract(AEItemKey.of(Items.COBBLESTONE), 64,
                Actionable.MODULATE, IActionSource.empty());
    }
}
