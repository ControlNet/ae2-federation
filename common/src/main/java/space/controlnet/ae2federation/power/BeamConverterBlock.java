package space.controlnet.ae2federation.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import space.controlnet.ae2federation.router.CableLinkedBlock;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.FormableDevice;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * The ME Federation Beam Converter: FE goes in, and its domain runs on it. Touching converter blocks form one body as a
 * Router's do, and the body must be exactly M x N across its front and 2·max(M, N) long, M x N being a cable
 * cross-section ({@link BeamConverterShape}), whichever way its blocks face. Formed, all its blocks face one end of its
 * length ({@link #forming}), and a wrench turns it end for end; an unformed block's facing means nothing. Only the
 * front links to Federation cable; every other face takes FE. The creative converter follows the same rules and never
 * runs out.
 */
public final class BeamConverterBlock extends BaseEntityBlock implements FormableDevice, CableLinkedBlock {
    public static final MapCodec<BeamConverterBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("creative").forGetter(BeamConverterBlock::creative), propertiesCodec())
            .apply(instance, BeamConverterBlock::new));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private final boolean creative;

    public BeamConverterBlock(boolean creative, Properties properties) {
        super(properties);
        this.creative = creative;
        var state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RouterBlock.FORMED, false);
        for (var property : RouterBlock.CONNECTED) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    public boolean creative() {
        return creative;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING).add(RouterBlock.CONNECTED).add(RouterBlock.FORMED);
    }

    /** Once formed, the front links, and so do faces toward the rest of the body. */
    @Override
    public boolean linksCable(BlockState state, Direction face) {
        return RouterBlock.formed(state) && faces(state, face);
    }

    /** The front, and faces toward the rest of the body. */
    public static boolean faces(BlockState state, Direction face) {
        return face == state.getValue(FACING) || (RouterBlock.connections(state) & 1 << face.get3DDataValue()) != 0;
    }

    /**
     * A block placed against a converter of the same kind takes its facing, so it votes with the body it joins
     * ({@link #forming}); otherwise its front faces the player.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var facing = against.is(this) ? against.getValue(FACING) : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The cuboid must have a converter's size; its blocks may face any way. */
    @Override
    public DeviceStructure.Result judge(BlockGetter level, DeviceStructure.Valid body) {
        return BeamConverterShape.of(body.min(), body.max()).isPresent() ? body
                : new DeviceStructure.Invalid(DeviceStructure.Reason.WRONG_SHAPE);
    }

    /**
     * A forming body turns all its blocks toward one end of its length: the end most of them face, else the one end
     * with Federation cable beside it, else the axis's positive end. Most blocks face the way the body last did, so a
     * body turned by a wrench keeps that facing when it forms again.
     */
    @Override
    public UnaryOperator<BlockState> forming(BlockGetter level, DeviceStructure.Valid body) {
        var facing = facing(level, body);
        return state -> state.is(this) ? state.setValue(FACING, facing) : state;
    }

    private Direction facing(BlockGetter level, DeviceStructure.Valid body) {
        var axis = BeamConverterShape.axis(body.min(), body.max()).orElseThrow();
        int votes = 0;
        for (var position : BlockPos.betweenClosed(body.min(), body.max())) {
            var facing = level.getBlockState(position).getValue(FACING);
            if (facing.getAxis() == axis) votes += facing.getAxisDirection().getStep();
        }
        if (votes != 0) return Direction.fromAxisAndDirection(axis,
                votes > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        var positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        boolean cableAhead = cableAt(level, body, positive);
        return cableAhead == cableAt(level, body, positive.getOpposite()) || cableAhead ? positive : positive.getOpposite();
    }

    /** Whether Federation cable touches the body's end face toward {@code side}. */
    private static boolean cableAt(BlockGetter level, DeviceStructure.Valid body, Direction side) {
        int span = body.max().get(side.getAxis()) - body.min().get(side.getAxis());
        boolean ahead = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        var min = ahead ? body.min().relative(side, span) : body.min();
        var max = ahead ? body.max() : body.max().relative(side, span);
        for (var position : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(position.relative(side)).getBlock() instanceof FederationCableBlock) return true;
        }
        return false;
    }

    /**
     * A wrench turns a formed body end for end. Every block is updated with its neighbours, so the cables at both ends
     * link again; the body forms again facing the new way, which all its blocks now vote for. An unformed block keeps
     * its facing. Anything else in hand builds on an unformed block as on any other; only an empty hand asks why it
     * has not formed.
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
            var turned = state.getValue(FACING).getOpposite();
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
    protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new BeamConverterBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, PowerRegistration.BEAM_CONVERTER_BLOCK_ENTITY.get(),
                (tickLevel, position, tickState, converter) -> RouterBlockEntity.serverTick(tickLevel, position,
                        tickState, converter));
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
        if (level.getBlockEntity(position) instanceof RouterBlockEntity converter) {
            converter.neighborChanged(neighborPosition);
        }
        if (level instanceof ServerLevel serverLevel && DeviceStructureUpdater.affects(serverLevel, position, neighborPosition)) {
            DeviceStructureUpdater.request(serverLevel, position);
        }
    }
}
