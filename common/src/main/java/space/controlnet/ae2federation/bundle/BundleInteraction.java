package space.controlnet.ae2federation.bundle;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * What a wrench and Federation Cables do to Federation Cables. A wrench's right-clicks select a bundle on the client,
 * which sends the three clicks to {@link #form}; Shift+right-click with a wrench dismantles one bundled cell. Federation
 * Cables right-clicked onto a bundle extend it from an end face or branch it from a side, using one cable per cell;
 * sneaking places a single cable as usual.
 */
public final class BundleInteraction {
    /** How far the player may be from any of the three clicks when the selection arrives. */
    private static final double MAX_DISTANCE = BundleSelection.MAX_LENGTH + 8;
    private static @Nullable ClientClick clientClick;

    private BundleInteraction() {
    }

    /** The client's selection, told of each wrench right-click on a Federation Cable. */
    @FunctionalInterface
    public interface ClientClick {
        void click(Level level, BlockPos pos, BlockHitResult hit);
    }

    public static void registerClientClick(ClientClick click) {
        clientClick = Objects.requireNonNull(click);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        if (!(level.getBlockState(pos).getBlock() instanceof FederationCableBlock)) return;
        var player = event.getEntity();
        var stack = event.getItemStack();
        if (stack.is(Tags.Items.TOOLS_WRENCH)) {
            if (player.isSecondaryUseActive()) {
                if (!BundleView.bundled(level, pos)) return;
                if (level instanceof ServerLevel serverLevel) dismantle(serverLevel, player, pos);
            } else if (level.isClientSide() && clientClick != null) {
                clientClick.click(level, pos, event.getHitVec());
            }
            consume(event);
            return;
        }
        if (!stack.is(RouterRegistration.FEDERATION_CABLE_ITEM.get()) || player.isSecondaryUseActive()) return;
        var segment = BundleView.segmentAt(level, pos);
        if (segment.isEmpty()) return;
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            place(serverLevel, serverPlayer, event.getHand(), segment.get(), pos, event.getHitVec());
        }
        consume(event);
    }

    /** The extension or branch a right-click on {@code hit} asks for, before checking the world. */
    public static BundleSet.Plan plan(BundleSegment segment, BlockPos pos, BlockHitResult hit) {
        var axis = segment.axis();
        double along = Math.max(0, Math.min(1, hit.getLocation().get(axis) - pos.get(axis)));
        return BundleSet.place(segment, pos, hit.getDirection(), along);
    }

    /**
     * Why the placement's cells cannot take cables, or null when they can: each must be loaded, replaceable and clear
     * of entities.
     */
    public static @Nullable Component blocked(Level level, BundleSet.Placement placement) {
        var cable = RouterRegistration.FEDERATION_CABLE.get().defaultBlockState();
        for (var cell : placement.cells()) {
            if (BundleView.bundled(level, cell)) return BundleText.refused(BundleSet.Problem.OCCUPIED, 0);
            if (!level.isLoaded(cell) || !level.getBlockState(cell).canBeReplaced()
                    || !level.isUnobstructed(cable, cell, CollisionContext.empty())) {
                return BundleText.blocked();
            }
        }
        return null;
    }

    /** Federation Cables {@code player} carries, or {@link Integer#MAX_VALUE} in creative. */
    public static int cablesHeld(Player player) {
        if (player.getAbilities().instabuild) return Integer.MAX_VALUE;
        var item = RouterRegistration.FEDERATION_CABLE_ITEM.get();
        int held = 0;
        for (var stack : player.getInventory().items) {
            if (stack.is(item)) held += stack.getCount();
        }
        for (var stack : player.getInventory().offhand) {
            if (stack.is(item)) held += stack.getCount();
        }
        return held;
    }

    private static void place(ServerLevel level, ServerPlayer player, InteractionHand hand, BundleSegment segment,
            BlockPos pos, BlockHitResult hit) {
        var plan = plan(segment, pos, hit);
        if (plan instanceof BundleSet.Refused refused) {
            player.displayClientMessage(BundleText.refused(refused.problem(), segment.extent(hit.getDirection().getAxis())),
                    true);
            return;
        }
        var placement = (BundleSet.Placement) plan;
        var reason = blocked(level, placement);
        if (reason != null) {
            player.displayClientMessage(reason, true);
            return;
        }
        int cost = placement.cost();
        int held = cablesHeld(player);
        if (held < cost) {
            player.displayClientMessage(BundleText.notEnough(cost, held), true);
            return;
        }
        var cable = RouterRegistration.FEDERATION_CABLE.get().defaultBlockState();
        for (var cell : placement.cells()) level.setBlock(cell, cable, Block.UPDATE_ALL);
        if (!player.getAbilities().instabuild) take(player, hand, cost);
        BundledCables.get(level).form(level, placement.segment());
        var sound = cable.getSoundType(level, pos, player);
        level.playSound(null, placement.segment().min(), sound.getPlaceSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
    }

    /** Takes {@code count} Federation Cables, from the hand that used them first. */
    private static void take(Player player, InteractionHand hand, int count) {
        var item = RouterRegistration.FEDERATION_CABLE_ITEM.get();
        int left = count;
        var first = player.getItemInHand(hand);
        left -= takeFrom(first, left);
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            var stack = inventory.getItem(slot);
            if (stack != first && stack.is(item)) left -= takeFrom(stack, left);
        }
        inventory.setChanged();
    }

    private static int takeFrom(ItemStack stack, int wanted) {
        int taken = Math.min(wanted, stack.getCount());
        stack.shrink(taken);
        return taken;
    }

    private static void dismantle(ServerLevel level, Player player, BlockPos pos) {
        level.destroyBlock(pos, !player.getAbilities().instabuild, player);
    }

    /**
     * The client's three clicks, checked again here: each near the player, a valid box, and every cell a free
     * Federation Cable. Tells the player what became of them on the action bar.
     */
    public static void form(ServerPlayer player, BlockPos first, BlockPos second, BlockPos third, Direction face) {
        var level = player.serverLevel();
        for (var click : new BlockPos[] {first, second, third}) {
            if (player.distanceToSqr(Vec3.atCenterOf(click)) > MAX_DISTANCE * MAX_DISTANCE) {
                player.displayClientMessage(BundleText.tooFar(), true);
                return;
            }
        }
        var result = BundleSelection.box(first, second, third, face);
        if (result instanceof BundleSelection.Invalid invalid) {
            player.displayClientMessage(BundleText.problem(invalid.problem()), true);
            return;
        }
        var segment = ((BundleSelection.Box) result).segment();
        var bundles = BundledCables.get(level);
        var refusal = bundles.formable(level, segment);
        if (refusal.isPresent()) {
            player.displayClientMessage(BundleText.cells(refusal.get().problem(), refusal.get().count(),
                    refusal.get().total()), true);
            return;
        }
        bundles.form(level, segment);
        player.displayClientMessage(BundleText.formed(segment), true);
        var cable = level.getBlockState(third);
        var sound = cable.getSoundType(level, third, player);
        level.playSound(null, third, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2,
                sound.getPitch() * 0.8f);
    }

    private static void consume(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
