package space.controlnet.ae2federation.quantum;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.items.tools.MemoryCardItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.CableLinkedBlock;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.DeviceStructureUpdater;
import space.controlnet.ae2federation.router.FormableDevice;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * The ME Federation Quantum Cube: touching cube blocks form one body as a Router's do, and the body must be a solid
 * cube of edge 3, 4 or 5 ({@link QuantumCubeShape}). Two formed cubes bound with an AE2 Memory Card are joined as if by
 * cable, in any dimensions: sneak-use copies a cube to the card, use binds the cube on the card to this one.
 */
public final class QuantumCubeBlock extends BaseEntityBlock implements FormableDevice, CableLinkedBlock {
    public static final MapCodec<QuantumCubeBlock> CODEC = simpleCodec(QuantumCubeBlock::new);
    private static final String MESSAGE = "ae2federation.quantum_cube.";

    public QuantumCubeBlock(Properties properties) {
        super(properties);
        var state = stateDefinition.any().setValue(RouterBlock.FORMED, false);
        for (var property : RouterBlock.CONNECTED) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RouterBlock.CONNECTED).add(RouterBlock.FORMED);
    }

    /**
     * Whether {@code face} of a cube block links: once formed, toward the rest of the body, and on an outer face inside
     * its centre, where the body continues both ways along each axis across the face, so the block lies strictly inside
     * the face's border. Read from the block's own connections, so it holds while the rest of the body is updated.
     */
    public static boolean links(BlockState state, Direction face) {
        if (!RouterBlock.formed(state)) return false;
        int connections = RouterBlock.connections(state);
        if ((connections & 1 << face.get3DDataValue()) != 0) return true;
        for (var axis : Direction.Axis.values()) {
            if (axis == face.getAxis()) continue;
            var positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            if ((connections & 1 << positive.get3DDataValue()) == 0
                    || (connections & 1 << positive.getOpposite().get3DDataValue()) == 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean linksCable(BlockState state, Direction face) {
        return links(state, face);
    }

    /** The cuboid must be a cube of edge 3, 4 or 5. */
    @Override
    public DeviceStructure.Result judge(BlockGetter level, DeviceStructure.Valid body) {
        return QuantumCubeShape.of(body.min(), body.max()).isPresent() ? body
                : new DeviceStructure.Invalid(DeviceStructure.Reason.WRONG_SHAPE);
    }

    /**
     * An AE2 Memory Card copies a formed cube when sneaking, as on an AE2 machine, and otherwise binds the cube it
     * holds to this one. On a cube that has not formed, it says why. Anything else in hand builds on an unformed block as on
     * any other.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos position,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof IMemoryCard card)) {
            return RouterBlock.formed(state) || stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
                    : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(level instanceof ServerLevel serverLevel)) return ItemInteractionResult.sidedSuccess(true);
        if (!RouterBlock.formed(state)
                || !(DeviceStructureUpdater.body(serverLevel, position) instanceof DeviceStructure.Valid body)) {
            player.displayClientMessage(DeviceStructureUpdater.status(serverLevel, position), true);
            return ItemInteractionResult.CONSUME;
        }
        var here = GlobalPos.of(serverLevel.dimension(), body.min());
        if (player.isSecondaryUseActive()) {
            MemoryCardItem.clearCard(stack);
            stack.set(QuantumRegistration.CARD_CUBE.get(), here);
            stack.set(AEComponents.EXPORTED_SETTINGS_SOURCE, getName());
            card.notifyUser(player, MemoryCardMessages.SETTINGS_SAVED);
            return ItemInteractionResult.CONSUME;
        }
        var from = stack.get(QuantumRegistration.CARD_CUBE.get());
        if (from == null) {
            card.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
            return ItemInteractionResult.CONSUME;
        }
        var result = QuantumLinkService.get(serverLevel.getServer()).bind(from, here);
        player.displayClientMessage(switch (result) {
            case BOUND -> Component.translatable(MESSAGE + "bound", from.pos().getX(), from.pos().getY(),
                    from.pos().getZ(), from.dimension().location().toString());
            case SAME_CUBE -> Component.translatable(MESSAGE + "same_cube");
            case CARD_CUBE_MISSING -> Component.translatable(MESSAGE + "card_cube_missing", from.pos().getX(),
                    from.pos().getY(), from.pos().getZ(), from.dimension().location().toString());
        }, true);
        return ItemInteractionResult.CONSUME;
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
        return new QuantumCubeBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                QuantumRegistration.QUANTUM_CUBE_BLOCK_ENTITY.get(), (tickLevel, position, tickState, cube) -> {
                    RouterBlockEntity.serverTick(tickLevel, position, tickState, cube);
                    cube.quantumTick();
                });
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
        if (level.getBlockEntity(position) instanceof RouterBlockEntity cube) {
            cube.neighborChanged(neighborPosition);
        }
        if (level instanceof ServerLevel serverLevel && DeviceStructureUpdater.affects(serverLevel, position, neighborPosition)) {
            DeviceStructureUpdater.request(serverLevel, position);
        }
    }
}
