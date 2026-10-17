package space.controlnet.ae2federation.router;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;

/**
 * The Router and the Switch: both are this block, each with its own block entity type, and share all their code. A
 * Switch's faces attach ME networks and Federation ports; a Router's faces attach Federation ports only.
 *
 * <p>Touching blocks of one type that fill a solid cuboid form one body, like an AE2 crafting CPU; a single block is a
 * 1x1x1 body. {@link #FORMED} says the block belongs to a validated body, and its six connection properties name its
 * neighbours in that body ({@link DeviceStructureUpdater}); the client draws the connected model from them. A block
 * that is not formed, part of an incomplete, hollow, branched, protruding or oversized candidate, links nothing.
 */
public final class RouterBlock extends BaseEntityBlock implements FormableDevice {
    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);
    /** Indexed by {@link net.minecraft.core.Direction#get3DDataValue()}. */
    public static final BooleanProperty[] CONNECTED = {BlockStateProperties.DOWN, BlockStateProperties.UP,
            BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.WEST, BlockStateProperties.EAST};
    /** Whether the block belongs to a validated body; a placed block becomes formed when its body is checked. */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public RouterBlock(Properties properties) {
        super(properties);
        var state = stateDefinition.any().setValue(FORMED, false);
        for (var property : CONNECTED) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED).add(FORMED);
    }

    /** Whether {@code state} is a device block ({@link FormableDevice}) of a validated body, whose faces link. */
    public static boolean formed(BlockState state) {
        return state.getBlock() instanceof FormableDevice && state.getValue(FORMED);
    }

    /** The sides on which {@code state}'s neighbour belongs to the same formed body, one bit per 3D data value. */
    public static int connections(BlockState state) {
        if (!(state.getBlock() instanceof FormableDevice)) return 0;
        int connections = 0;
        for (int i = 0; i < CONNECTED.length; i++) {
            if (state.getValue(CONNECTED[i])) connections |= 1 << i;
        }
        return connections;
    }

    static BlockState withBody(BlockState state, boolean formed, int connections) {
        state = state.setValue(FORMED, formed);
        for (int i = 0; i < CONNECTED.length; i++) state = state.setValue(CONNECTED[i], (connections & 1 << i) != 0);
        return state;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    /** The frame has gaps on every side, so the Router hides no neighbour's face. */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos position) {
        return DeviceOcclusion.ROUTER;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new RouterBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, blockEntityType(state), RouterBlockEntity::serverTick);
    }

    /** The Router's or the Switch's block entity type, whichever block {@code state} is. */
    static BlockEntityType<RouterBlockEntity> blockEntityType(BlockState state) {
        return attachesNetworks(state) ? RouterRegistration.SWITCH_BLOCK_ENTITY.get()
                : RouterRegistration.ROUTER_BLOCK_ENTITY.get();
    }

    /** Whether {@code state} is a Switch, whose faces attach ME networks; a Router's do not. */
    static boolean attachesNetworks(BlockState state) {
        return state.is(RouterRegistration.SWITCH.get());
    }

    /**
     * A block in hand builds on an unformed body as on any other block; only an empty hand asks why it has not formed
     * ({@link #useWithoutItem}).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position,
            Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() || formed(state) ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!formed(state) && player instanceof ServerPlayer serverPlayer) {
            // An unformed body links nothing, so there is no domain to open: say what keeps it from forming.
            serverPlayer.displayClientMessage(DeviceStructureUpdater.status(serverPlayer.serverLevel(), position), true);
            return InteractionResult.CONSUME;
        }
        return player instanceof ServerPlayer serverPlayer && FederationDomainPolicyMenu.openRouter(serverPlayer, position)
                ? InteractionResult.CONSUME
                : InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (level.getBlockEntity(position) instanceof RouterBlockEntity router) {
            router.neighborChanged(neighborPosition);
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && DeviceStructureUpdater.affects(serverLevel, position, neighborPosition)) {
            DeviceStructureUpdater.request(serverLevel, position);
        }
    }
}
