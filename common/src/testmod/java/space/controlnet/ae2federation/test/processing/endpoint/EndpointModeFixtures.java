package space.controlnet.ae2federation.test.processing.endpoint;

import appeng.api.AECapabilities;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.api.crafting.PatternDetailsHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;

public final class EndpointModeFixtures implements AutoCloseable {
    public static final List<Direction> LOGISTICS_FACES = List.of(Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST);
    private static final BlockPos ENDPOINT = new BlockPos(5, 3, 5);
    private static final BlockPos PROVIDER = ENDPOINT.west();
    private static final BlockPos PROVIDER_ENERGY = PROVIDER.west();
    private static final BlockPos SUBNET_CHEST = ENDPOINT.north();
    private static final BlockPos SUBNET_ENERGY = SUBNET_CHEST.below();
    private static final BlockPos SECOND_PROVIDER = ENDPOINT.south();
    private static final BlockPos SECOND_ENERGY = SECOND_PROVIDER.south();
    private static final BlockPos REMOTE_PROVIDER = new BlockPos(11, 3, 5);
    private static final BlockPos REMOTE_ENERGY = REMOTE_PROVIDER.east();
    private final GameTestHelper helper;
    private EndpointTargetBinding binding;

    public EndpointModeFixtures(GameTestHelper helper, boolean secondProvider) {
        this.helper = helper;
        helper.setBlock(ENDPOINT, ProcessingRegistration.ENDPOINT.get());
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
        helper.setBlock(PROVIDER_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(SUBNET_CHEST, AEBlocks.ME_CHEST.block());
        helper.setBlock(SUBNET_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(REMOTE_PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.WEST));
        helper.setBlock(REMOTE_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        if (secondProvider) {
            helper.setBlock(SECOND_PROVIDER, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                    .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.NORTH));
            helper.setBlock(SECOND_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
        var chest = helper.<MEChestBlockEntity>getBlockEntity(SUBNET_CHEST);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Endpoint subnet cell must exist");
        chest.setCell(cell);
    }

    public boolean ready() {
        var endpointNode = endpointNode();
        var providerNode = providerNode();
        var remoteNode = remoteProviderNode();
        var second = helper.getLevel().getBlockEntity(helper.absolutePos(SECOND_PROVIDER));
        var secondNode = second instanceof PatternProviderBlockEntity ? secondProviderNode() : null;
        return endpointNode != null && endpointNode.isActive() && providerNode != null && providerNode.isActive()
                && remoteNode != null && remoteNode.isActive()
                && (secondNode == null || secondNode.isActive())
                && EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(ENDPOINT)) != null;
    }

    public boolean bindLocal() {
        if (binding == null) {
            binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(ENDPOINT));
        }
        return binding != null && binding.runtime().mode().isPresent();
    }

    public EndpointTargetBinding binding() {
        binding = EndpointTargetBinding.findEndpoint(helper.getLevel(), helper.absolutePos(ENDPOINT));
        return binding;
    }

    public EndpointIdentity endpointIdentity() {
        return endpoint().endpointIdentity();
    }

    public ClaimState claimState() {
        return endpoint().claimState();
    }

    public EndpointBlockEntity endpoint() {
        return helper.getBlockEntity(ENDPOINT);
    }

    public BlockPos endpointPosition() {
        return helper.absolutePos(ENDPOINT);
    }

    public PatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    public PatternProviderBlockEntity remoteProvider() {
        return helper.getBlockEntity(REMOTE_PROVIDER);
    }

    public PatternProviderBlockEntity secondProvider() {
        return helper.getBlockEntity(SECOND_PROVIDER);
    }

    public IGridNode endpointNode() {
        var endpoint = helper.getBlockEntity(ENDPOINT);
        return endpoint instanceof EndpointBlockEntity blockEntity ? blockEntity.getMainNode().getNode() : null;
    }

    public IGridNode providerNode() {
        return provider().getMainNode().getNode();
    }

    public IGridNode secondProviderNode() {
        return helper.<PatternProviderBlockEntity>getBlockEntity(SECOND_PROVIDER).getMainNode().getNode();
    }

    public IGridNode remoteProviderNode() {
        return remoteProvider().getMainNode().getNode();
    }

    public void installPattern() {
        var pattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        provider().getLogic().getPatternInv().setItemDirect(0, pattern);
        provider().getLogic().updatePatterns();
    }

    public boolean nativePush() {
        var pattern = provider().getLogic().getAvailablePatterns().getFirst();
        var input = pattern.getInputs()[0].getPossibleInputs()[0];
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return provider().getLogic().pushPattern(pattern, new KeyCounter[] { counter });
    }

    public long subnetItemCount() {
        return endpointNode().getGrid().getStorageService().getInventory().getAvailableStacks()
                .get(AEItemKey.of(Items.COBBLESTONE));
    }

    public IItemHandler itemCapability(Direction face) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(ENDPOINT), face);
    }

    public IFluidHandler fluidCapability(Direction face) {
        return helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(ENDPOINT), face);
    }

    public Object storageCapability(Direction face) {
        return helper.getLevel().getCapability(AECapabilities.ME_STORAGE, helper.absolutePos(ENDPOINT), face);
    }

    public IGridNode exposedNode(Direction face) {
        return GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(ENDPOINT), face);
    }

    public void clearReturn(PatternProviderBlockEntity target) {
        for (int slot = 0; slot < target.getLogic().getReturnInv().size(); slot++) {
            target.getLogic().getReturnInv().setStack(slot, null);
        }
    }

    public void removeSecondProvider() {
        helper.setBlock(SECOND_PROVIDER, Blocks.AIR);
        helper.setBlock(SECOND_ENERGY, Blocks.AIR);
    }

    public SavedState reloadEndpoint() {
        var endpoint = endpoint();
        var saved = endpoint.saveWithFullMetadata(helper.getLevel().registryAccess());
        var state = new SavedState(endpoint.endpointIdentity(), endpoint.claimState(), binding().runtime().generation());
        helper.setBlock(ENDPOINT, Blocks.AIR);
        helper.setBlock(ENDPOINT, ProcessingRegistration.ENDPOINT.get());
        endpoint().loadWithComponents(saved, helper.getLevel().registryAccess());
        endpoint().setChanged();
        binding = null;
        return state;
    }

    @Override
    public void close() {
    }

    public record SavedState(EndpointIdentity identity, ClaimState claim, long generation) {
    }
}
