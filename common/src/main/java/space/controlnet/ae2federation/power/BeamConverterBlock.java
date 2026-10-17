package space.controlnet.ae2federation.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;
import space.controlnet.ae2federation.router.FormableDevice;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * The ME Federation Beam Converter: FE goes in, and its domain runs on it. Touching converter blocks form one body as a
 * Router's do, and the body must also face one way and be exactly M x N across its front and 2·max(M, N) long, M x N
 * being a cable cross-section ({@link BeamConverterShape}). Only the front links to Federation cable; every other face
 * takes FE. The creative converter follows the same rules and never runs out.
 */
public final class BeamConverterBlock extends BaseEntityBlock implements FormableDevice {
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

    /**
     * A block placed against a converter of the same kind takes its facing, so a body is built without turning each
     * block; otherwise its front faces the player.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var against = context.getLevel().getBlockState(context.getClickedPos().relative(context.getClickedFace().getOpposite()));
        var facing = against.is(this) ? against.getValue(FACING) : context.getNearestLookingDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /** The cuboid must face one way and have a converter's size. */
    @Override
    public DeviceStructure.Result judge(BlockGetter level, DeviceStructure.Valid body) {
        var facing = level.getBlockState(body.min()).getValue(FACING);
        for (var position : BlockPos.betweenClosed(body.min(), body.max())) {
            if (level.getBlockState(position).getValue(FACING) != facing) {
                return new DeviceStructure.Invalid(DeviceStructure.Reason.WRONG_SHAPE);
            }
        }
        return BeamConverterShape.of(body.min(), body.max(), facing).isPresent() ? body
                : new DeviceStructure.Invalid(DeviceStructure.Reason.WRONG_SHAPE);
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
