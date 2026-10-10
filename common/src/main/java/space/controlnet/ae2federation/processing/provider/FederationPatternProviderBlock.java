package space.controlnet.ae2federation.processing.provider;

import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import space.controlnet.ae2federation.router.DeviceOcclusion;

/**
 * ME Federation Pattern Provider block. Its facing is the Federation face; AE2's facing strategy handles placement,
 * wrench rotation and persistence of the orientation.
 */
public final class FederationPatternProviderBlock extends AEBaseEntityBlock<FederationPatternProviderBlockEntity> {
    public FederationPatternProviderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public IOrientationStrategy getOrientationStrategy() {
        return OrientationStrategies.facing();
    }

    /** Only the back closes completely; the recessed front must not hide the block in front of it. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return DeviceOcclusion.front(getOrientationStrategy().getFacing(state));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /** Places the Federation face against the block that was clicked, e.g. a Federation Cable. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return getOrientationStrategy().setFacing(defaultBlockState(), context.getClickedFace().getOpposite());
    }

    /**
     * Opens the Provider's own screen, sneaking or not: its patterns, AE2 settings, priority and the wires of this
     * Provider to the Endpoints of the domain its Federation face joins. AE2's Provider screen opens only if it cannot.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        var provider = getBlockEntity(level, position);
        if (provider == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            boolean opened = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu.openProvider(serverPlayer, position);
            if (!opened) provider.openMenu(player, MenuLocators.forBlockEntity(provider));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        var provider = getBlockEntity(level, position);
        if (provider != null && !level.isClientSide()) {
            provider.neighborChanged(neighborPosition);
        }
    }
}
