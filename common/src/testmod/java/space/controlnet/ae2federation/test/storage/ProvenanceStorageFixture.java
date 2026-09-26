package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

public final class ProvenanceStorageFixture implements AutoCloseable {
    public static final BlockPos CHEST = new BlockPos(2, 2, 2);
    private static final BlockPos CABLE = CHEST.east();
    private static final BlockPos PROVIDER = CABLE.east();

    private final GameTestHelper helper;
    private final NativePortFixtures ports;

    public ProvenanceStorageFixture(GameTestHelper helper) {
        this.helper = helper;
        ports = new NativePortFixtures(helper);
        helper.setBlock(CHEST.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        ports.placeChest(CHEST);
        ports.placeCable(CABLE);
        chest().setCell(AEItems.ITEM_CELL_1K.stack());
    }

    public boolean ready() {
        var node = chest().getMainNode().getNode();
        return node != null && node.isActive() && node.hasGridBooted()
                && FederationDomainRegistryAccess.confirmedNetworkId(grid()).isPresent();
    }

    public MEChestBlockEntity chest() {
        return helper.getBlockEntity(CHEST);
    }

    public IGridNode node() {
        return ports.chestNode(CHEST);
    }

    public IGrid grid() {
        return node().getGrid();
    }

    public MEStorage source() {
        return new NativeStorageProvenance().qualify(node()).getFirst().storage();
    }

    public IManagedGridNode addProvider(IStorageProvider provider) {
        return ports.createStorageProvider(Direction.WEST, PROVIDER, provider);
    }

    public CompoundTag saveAndRemoveProvider(IManagedGridNode provider) {
        return ports.saveAndDestroy(provider);
    }

    public IManagedGridNode restoreStandaloneProvider(IStorageProvider provider, CompoundTag state) {
        return ports.createStorageProvider(Direction.WEST, PROVIDER, provider, state);
    }

    public boolean providerReady(IManagedGridNode provider) {
        var node = provider.getNode();
        return node != null && node.isActive() && node.hasGridBooted();
    }

    public void removeGrid() {
        helper.setBlock(CHEST, Blocks.AIR);
        helper.setBlock(CABLE, Blocks.AIR);
        helper.setBlock(CHEST.below(), Blocks.AIR);
    }


    @Override
    public void close() {
        ports.close();
    }
}
