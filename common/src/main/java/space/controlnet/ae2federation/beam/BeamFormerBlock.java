package space.controlnet.ae2federation.beam;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.function.UnaryOperator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.FormableDevice;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * The ME Federation Beam Former: Federation cable goes in at its back, and a beam leaves its front for the former facing
 * it. Touching former blocks form one body as a Router's do, whichever way they face, and the body must be a square
 * 1x1, 2x2 or 3x3 one block thick ({@link BeamFormerShape}). Formed, all its blocks face one way along a thin axis
 * ({@link #forming}); a wrench flips a 2x2 or 3x3 and turns a 1x1 to the clicked face.
 */
public final class BeamFormerBlock extends BaseEntityBlock implements FormableDevice {
    public static final MapCodec<BeamFormerBlock> CODEC = simpleCodec(BeamFormerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BeamFormerBlock(Properties properties) {
        super(properties);
        var state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RouterBlock.FORMED, false);
        for (var property : RouterBlock.CONNECTED) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(RouterBlock.CONNECTED).add(RouterBlock.FORMED);
    }

    /**
     * A block placed against a former takes its facing, so it votes with the body it joins ({@link #forming});
     * otherwise its front faces where the player looks, toward the partner being aimed at.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var facing = against.is(this) ? against.getValue(FACING) : context.getNearestLookingDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The cuboid must be a former's square; its blocks may face any way. */
    @Override
    public DeviceStructure.Result judge(BlockGetter level, DeviceStructure.Valid body) {
        return BeamFormerShape.of(body.min(), body.max()).isPresent() ? body
                : new DeviceStructure.Invalid(DeviceStructure.Reason.WRONG_SHAPE);
    }

    /**
     * A forming body turns all its blocks one way along a thin axis: the way most of them face, else away from the one
     * side with Federation cable beside it (the back takes the cable), else the axis's positive end. Most blocks face
     * the way the body last did, so a body turned by a wrench keeps that facing when it forms again.
     */
    @Override
    public UnaryOperator<BlockState> forming(BlockGetter level, DeviceStructure.Valid body) {
        var facing = facing(level, body);
        return state -> state.is(this) ? state.setValue(FACING, facing) : state;
    }

    private Direction facing(BlockGetter level, DeviceStructure.Valid body) {
        var axes = BeamFormerShape.axes(body.min(), body.max());
        var votes = new EnumMap<Direction, Integer>(Direction.class);
        for (var position : BlockPos.betweenClosed(body.min(), body.max())) {
            var facing = level.getBlockState(position).getValue(FACING);
            if (axes.contains(facing.getAxis())) votes.merge(facing, 1, Integer::sum);
        }
        Direction best = null;
        boolean tie = false;
        for (var vote : votes.entrySet()) {
            if (best == null || vote.getValue() > votes.get(best)) {
                best = vote.getKey();
                tie = false;
            } else if (vote.getValue().equals(votes.get(best))) {
                tie = true;
            }
        }
        if (best != null && !tie) return best;
        // No single choice: the first thin axis (a 2x2 or 3x3 has one), away from the one side with cable.
        var axis = axes.iterator().next();
        var positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        boolean cableBehindNegative = cableAt(level, body, positive);
        return cableBehindNegative && !cableAt(level, body, positive.getOpposite()) ? positive.getOpposite() : positive;
    }

    /** Whether Federation cable touches the body's face toward {@code side}. */
    private static boolean cableAt(BlockGetter level, DeviceStructure.Valid body, Direction side) {
        for (var position : BlockPos.betweenClosed(body.min(), body.max())) {
            if (level.getBlockState(position.relative(side)).getBlock() instanceof FederationCableBlock) return true;
        }
        return false;
    }

    /**
     * A wrench flips a formed 2x2 or 3x3 front for back and turns a formed 1x1 to the clicked face. Every block is
     * updated with its neighbours, so the cables at its back link again; the body forms again facing the new way,
     * which all its blocks now vote for. Anything else in hand builds on an unformed block as on any other; only an
     * empty hand asks why it has not formed.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!RouterBlock.formed(state)) {
            return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(Tags.Items.TOOLS_WRENCH) || player.isSecondaryUseActive()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel serverLevel
                && DeviceStructureUpdater.body(serverLevel, position) instanceof DeviceStructure.Valid body) {
            var axes = BeamFormerShape.axes(body.min(), body.max());
            var turned = axes.size() == 1 ? state.getValue(FACING).getOpposite() : hit.getDirection();
            for (var member : BlockPos.betweenClosed(body.min(), body.max())) {
                var memberState = level.getBlockState(member);
                if (memberState.is(this)) level.setBlock(member, memberState.setValue(FACING, turned), Block.UPDATE_ALL);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new BeamFormerBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, BeamRegistration.BEAM_FORMER_BLOCK_ENTITY.get(),
                (tickLevel, position, tickState, former) -> RouterBlockEntity.serverTick(tickLevel, position,
                        tickState, former));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos position, Player player,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!RouterBlock.formed(state) && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(DeviceStructureUpdater.status(serverPlayer.serverLevel(), position), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighborBlock,
            BlockPos neighborPosition, boolean movedByPiston) {
        if (level.getBlockEntity(position) instanceof RouterBlockEntity former) {
            former.neighborChanged(neighborPosition);
        }
        if (level instanceof ServerLevel serverLevel && DeviceStructureUpdater.affects(serverLevel, position, neighborPosition)) {
            DeviceStructureUpdater.request(serverLevel, position);
        }
    }
}
