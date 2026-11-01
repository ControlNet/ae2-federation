package space.controlnet.ae2federation.router;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Shift+right-click with a wrench takes one of our blocks straight into the inventory, as AE2's own wrench hook does for
 * its blocks (Provider and Endpoint are AE2 block entities and go through that hook). One member of a formed body comes
 * out, and the body re-forms or unforms as when it is broken. Federation Cables go through {@code BundleInteraction},
 * which owns every wrench click on them.
 */
public final class WrenchDismantle {
    private WrenchDismantle() {
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof FormableDevice)) return;
        var result = dismantle(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
        if (result != InteractionResult.PASS) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /** Whether {@code player} is asking to dismantle: sneaking with a wrench in the main hand. */
    public static boolean asked(Player player, InteractionHand hand) {
        return !player.isSpectator() && hand == InteractionHand.MAIN_HAND && player.isSecondaryUseActive()
                && player.getItemInHand(hand).is(Tags.Items.TOOLS_WRENCH);
    }

    /**
     * Removes the clicked block and puts its drops in {@code player}'s inventory, as AE2's
     * {@code AEBaseBlockEntity.disassembleWithWrench}. It skips {@code playerWillDestroy}, which would spawn break
     * particles and sound; the frame-removal sound AE2 plays replaces them.
     */
    public static InteractionResult dismantle(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!asked(player, hand)) return InteractionResult.PASS;
        var position = hit.getBlockPos();
        if (!level.mayInteract(player, position)) return InteractionResult.FAIL;
        if (level instanceof ServerLevel serverLevel) {
            var state = level.getBlockState(position);
            var drops = Block.getDrops(state, serverLevel, position, level.getBlockEntity(position), player,
                    player.getItemInHand(hand));
            level.removeBlock(position, false);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, position);
            for (var drop : drops) player.getInventory().placeItemBackInInventory(drop);
            level.playSound(null, position, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
