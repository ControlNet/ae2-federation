package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.bundle.BundleContacts;
import space.controlnet.ae2federation.bundle.BundleFormSink;
import space.controlnet.ae2federation.bundle.BundleInteraction;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundleSelection;
import space.controlnet.ae2federation.bundle.BundleSet;
import space.controlnet.ae2federation.bundle.BundleText;
import space.controlnet.ae2federation.bundle.BundleView;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * The wrench's bundle selection on the client: two corners of the cross-section and a cable along it, previewed on the
 * cable under the cursor before each click and sent to the server on the third. Holding Federation Cables over a
 * bundle previews the layer an extension or a branch would add; holding a wrench over one outlines it and its device
 * contacts. Every preview line goes to the action bar.
 */
public final class BundleSelectionClient {
    private static final float SELECTION_EDGE = 1 / 16f;
    private static final float CONFIRMED_EDGE = 1 / 24f;
    private static final float PASSIVE_EDGE = 1 / 32f;
    private static final long CONFIRMED_MILLIS = 1500;

    private static @Nullable BlockPos first;
    private static @Nullable BlockPos second;
    private static @Nullable ResourceKey<Level> dimension;
    private static List<BundleOverlay.Box> boxes = List.of();
    private static @Nullable AABB confirmed;
    private static long confirmedAt;

    private BundleSelectionClient() {
    }

    /** A wrench right-click on a Federation Cable. */
    public static void click(Level level, BlockPos pos, BlockHitResult hit) {
        pos = pos.immutable();
        if (first == null || !level.dimension().equals(dimension)) {
            if (BundleView.bundled(level, pos)) return;
            first = pos;
            second = null;
            dimension = level.dimension();
            show(BundleText.firstCorner());
            return;
        }
        if (second == null) {
            var section = BundleSelection.crossSection(first, pos);
            var problem = problem(level, section);
            if (problem != null) {
                show(problem);
                return;
            }
            second = pos;
            show(BundleText.section(((BundleSelection.Section) section).shape()));
            return;
        }
        var box = BundleSelection.box(first, second, pos, hit.getDirection());
        var problem = problem(level, box);
        if (problem != null) {
            show(problem);
            return;
        }
        BundleFormSink.send(first, second, pos, hit.getDirection());
        confirmed = area(((BundleSelection.Box) box).segment());
        confirmedAt = System.currentTimeMillis();
        clear();
    }

    /** Right-clicking air with a wrench clears the selection. */
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (first != null && event.getItemStack().is(Tags.Items.TOOLS_WRENCH)) {
            clear();
            show(BundleText.cancelled());
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null) {
            clear();
            boxes = List.of();
            return;
        }
        var held = player.getMainHandItem();
        boolean wrench = held.is(Tags.Items.TOOLS_WRENCH);
        if (first != null && (!wrench || !level.dimension().equals(dimension))) clear();
        var shown = new ArrayList<BundleOverlay.Box>();
        if (confirmed != null) {
            float left = 1 - (System.currentTimeMillis() - confirmedAt) / (float) CONFIRMED_MILLIS;
            if (left > 0) shown.add(new BundleOverlay.Box(confirmed, BundleText.CONFIRMED, CONFIRMED_EDGE, false, left));
            else confirmed = null;
        }
        var hit = minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && level.getBlockState(block.getBlockPos()).getBlock() instanceof FederationCableBlock ? block : null;
        if (wrench && first != null) {
            preview(level, hit, shown);
        } else if (wrench && hit != null && !player.isSecondaryUseActive()) {
            outline(level, hit.getBlockPos(), shown, true);
        } else if (held.is(RouterRegistration.FEDERATION_CABLE_ITEM.get()) && hit != null
                && !player.isSecondaryUseActive()) {
            placement(level, player, hit, shown);
        }
        boxes = List.copyOf(shown);
    }

    public static void render(RenderLevelStageEvent event) {
        // After the level, as WorldHighlight draws: the model-view stack no longer holds the camera rotation then.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        BundleOverlay.render(event, boxes);
    }

    /** The selection so far and what a click on the cable under the cursor would make of it. */
    private static void preview(Level level, @Nullable BlockHitResult hit, List<BundleOverlay.Box> shown) {
        if (second == null) {
            if (hit == null || hit.getBlockPos().equals(first)) {
                shown.add(selection(new AABB(first), BundleText.HIGHLIGHT));
                show(BundleText.firstCorner());
                return;
            }
            var section = BundleSelection.crossSection(first, hit.getBlockPos());
            var problem = problem(level, section);
            shown.add(selection(AABB.encapsulatingFullBlocks(first, hit.getBlockPos()),
                    problem == null ? BundleText.HIGHLIGHT : BundleText.FAIL));
            show(problem == null ? BundleText.firstCorner() : problem);
            return;
        }
        var sectionArea = AABB.encapsulatingFullBlocks(first, second);
        // Still on the corner just clicked: ask for the length. A click there makes one layer, as anywhere in the plane.
        if (hit == null || hit.getBlockPos().equals(second)) {
            shown.add(selection(sectionArea, BundleText.HIGHLIGHT));
            show(BundleText.section(((BundleSelection.Section) BundleSelection.crossSection(first, second)).shape()));
            return;
        }
        var box = BundleSelection.box(first, second, hit.getBlockPos(), hit.getDirection());
        var problem = problem(level, box);
        var area = box instanceof BundleSelection.Box valid ? area(valid.segment())
                : sectionArea.minmax(new AABB(hit.getBlockPos()));
        shown.add(selection(area, problem == null ? BundleText.HIGHLIGHT : BundleText.FAIL));
        show(problem == null ? BundleText.box(((BundleSelection.Box) box).segment()) : problem);
    }

    /**
     * What a click with Federation Cables on {@code hit} would add to its bundle: the new layer, or why there is none.
     */
    private static void placement(Level level, net.minecraft.world.entity.player.Player player, BlockHitResult hit,
            List<BundleOverlay.Box> shown) {
        var segment = BundleView.segmentAt(level, hit.getBlockPos());
        if (segment.isEmpty()) return;
        outline(level, hit.getBlockPos(), shown, false);
        var plan = BundleInteraction.plan(segment.get(), hit.getBlockPos(), hit);
        if (plan instanceof BundleSet.Refused refused) {
            show(BundleText.refused(refused.problem(), segment.get().extent(hit.getDirection().getAxis())));
            return;
        }
        var placement = (BundleSet.Placement) plan;
        Component reason = BundleInteraction.blocked(level, placement);
        int held = BundleInteraction.cablesHeld(player);
        if (reason == null && held < placement.cost()) reason = BundleText.notEnough(placement.cost(), held);
        shown.add(selection(area(placement.segment()), reason == null ? BundleText.HIGHLIGHT : BundleText.FAIL));
        show(reason != null ? reason
                : BundleText.placement(hit.getDirection().getAxis() == segment.get().axis(), placement.cost()));
    }

    /**
     * The bundles at and beside {@code pos}, outlined; with {@code contacts}, also the devices against them: whole
     * contacts highlighted, partial ones in the problem colour.
     */
    private static void outline(Level level, BlockPos pos, List<BundleOverlay.Box> shown, boolean contacts) {
        var set = BundleView.around(level, pos);
        for (var segment : set.segments()) {
            shown.add(new BundleOverlay.Box(area(segment), BundleText.PASSIVE, PASSIVE_EDGE, false, 1));
        }
        if (!contacts) return;
        for (var bundle : set.bundles()) {
            var summary = BundleContacts.summary(level, bundle, cell -> set.at(cell).isPresent());
            for (var contact : summary.whole()) shown.add(selection(around(contact.devices()), BundleText.HIGHLIGHT));
            for (var contact : summary.partial()) {
                var devices = contact.devices().stream()
                        .filter(device -> RouterBlock.formed(level.getBlockState(device))).toList();
                if (!devices.isEmpty()) shown.add(selection(around(devices), BundleText.FAIL));
            }
        }
    }

    private static AABB around(List<BlockPos> blocks) {
        var area = new AABB(blocks.get(0));
        for (var block : blocks) area = area.minmax(new AABB(block));
        return area;
    }

    private static @Nullable Component problem(Level level, BundleSelection.Result result) {
        if (result instanceof BundleSelection.Invalid invalid) return BundleText.problem(invalid.problem());
        BundleSegment segment = result instanceof BundleSelection.Box box ? box.segment()
                : new BundleSegment(((BundleSelection.Section) result).min(), ((BundleSelection.Section) result).max(),
                        Direction.Axis.X);
        return BundledCables.check(level, segment)
                .map(refusal -> BundleText.cells(refusal.problem(), refusal.count(), refusal.total())).orElse(null);
    }

    private static BundleOverlay.Box selection(AABB area, int color) {
        return new BundleOverlay.Box(area, color, SELECTION_EDGE, true, 1);
    }

    private static AABB area(BundleSegment segment) {
        return AABB.encapsulatingFullBlocks(segment.min(), segment.max());
    }

    private static void show(Component message) {
        Minecraft.getInstance().gui.setOverlayMessage(message, false);
    }

    private static void clear() {
        first = null;
        second = null;
        dimension = null;
    }
}
