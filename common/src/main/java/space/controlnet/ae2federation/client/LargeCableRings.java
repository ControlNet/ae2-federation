package space.controlnet.ae2federation.client;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import space.controlnet.ae2federation.bundle.BundleLook;

/**
 * The artist's rings inside a 2x2 or 3x3 bundle's shell ({@link LargeCableRingModels}). Each ring is modelled over a
 * frame as many blocks each way as the bundle is wide, turned so that its axis lies along the world's; a box of it
 * belongs to the cell that holds its centre (the lower one on a tie), so every box is drawn once and may reach into the
 * next cell.
 */
final class LargeCableRings {
    private static final FaceBakery BAKERY = new FaceBakery();

    private final TextureAtlasSprite texture;

    LargeCableRings(TextureAtlasSprite texture) {
        this.texture = texture;
    }

    /**
     * The cell's share of the rings its look names: a straight ring (on a layer boundary in a 2x2 bundle, inside the
     * layer in a 3x3 one), or a branch's corner rings.
     */
    void add(BundleLook look, List<BakedQuad> quads) {
        var axis = look.axis();
        int side = look.side();
        int last = side - 1;
        // Where the cell lies in the frame, one index per world axis: across from the segment's edges, along later.
        int[] cell = new int[3];
        for (var other : Direction.Axis.values()) {
            if (other == axis) continue;
            cell[other.ordinal()] = look.edge(Direction.fromAxisAndDirection(other, Direction.AxisDirection.POSITIVE))
                    ? last : look.edge(Direction.fromAxisAndDirection(other, Direction.AxisDirection.NEGATIVE)) ? 0 : 1;
        }
        var straight = side == 2 ? LargeCableRingModels.STRAIGHT : LargeCableRingModels.HUGE_STRAIGHT;
        var corner = side == 2 ? LargeCableRingModels.CORNER : LargeCableRingModels.HUGE_CORNER;
        if (side == 2) {
            if (look.ringBefore()) add(straight, Direction.Axis.Z, axis, along(cell, axis, 1), quads);
            if (look.ringAfter()) add(straight, Direction.Axis.Z, axis, along(cell, axis, 0), quads);
        } else if (look.ringInside()) {
            add(straight, Direction.Axis.Z, axis, along(cell, axis, 1), quads);
        }
        // Branches toward both ends of one axis share the same rings.
        int drawn = 0;
        for (var toward : Direction.values()) {
            boolean before = look.jointBefore(toward);
            boolean after = look.jointAfter(toward);
            if (!before && !after) continue;
            // The layer's place in the joint: a 3x3 branch covers the previous, this and the next layer in the middle.
            int index = !before ? 0 : after ? 1 : last;
            int key = 1 << (toward.getAxis().ordinal() * 3 + index);
            if ((drawn & key) != 0) continue;
            drawn |= key;
            var third = Direction.Axis.values()[3 - axis.ordinal() - toward.getAxis().ordinal()];
            add(corner, Direction.Axis.Y, third, along(cell, axis, index), quads);
        }
    }

    private static int[] along(int[] cell, Direction.Axis axis, int index) {
        var copy = cell.clone();
        copy[axis.ordinal()] = index;
        return copy;
    }

    /**
     * The boxes of {@code model} in the cell at {@code cell} (an index per world axis) of the frame, turned so that the
     * model's {@code modelAxis} lies along {@code worldAxis}.
     */
    private void add(LargeCableRingModels.Model model, Direction.Axis modelAxis, Direction.Axis worldAxis, int[] cell,
            List<BakedQuad> quads) {
        var rotation = rotation(modelAxis, worldAxis);
        for (var box : boxes(model, rotation, cell)) {
            for (var face : box.element().faces()) {
                // The texture is 32 pixels; a block face's UVs count 16 across any sprite.
                var uv = new BlockFaceUV(new float[] {face.u0() / 2f, face.v0() / 2f, face.u1() / 2f, face.v1() / 2f},
                        face.rotation());
                var baked = new BlockElementFace(null, -1, "", uv);
                quads.add(BAKERY.bakeQuad(box.from(), box.to(), baked, texture, face.side(), rotation, box.turn(),
                        true));
            }
        }
    }

    /** A box of a ring in one cell's own coordinates, before the rotation, with its own turn about a point there. */
    record Box(LargeCableRingModels.Element element, Vector3f from, Vector3f to, @Nullable BlockElementRotation turn) {
    }

    /** The boxes of {@code model} the cell at {@code cell} (an index per world axis) of the turned frame draws. */
    static List<Box> boxes(LargeCableRingModels.Model model, BlockModelRotation rotation, int[] cell) {
        var matrix = rotation.getRotation().getMatrix();
        int last = model.cells() - 1;
        // The same cell in the model's own frame: each model axis turns onto a world axis, maybe reversed.
        int[] own = new int[3];
        for (var axis : Direction.Axis.values()) {
            var turned = matrix.transformDirection(unit(axis));
            var world = dominant(turned);
            int index = cell[world.ordinal()];
            own[axis.ordinal()] = turned.get(world.ordinal()) < 0 ? last - index : index;
        }
        var boxes = new java.util.ArrayList<Box>();
        for (var element : model.elements()) {
            var home = home(element, last);
            if (home[0] != own[0] || home[1] != own[1] || home[2] != own[2]) continue;
            var shift = new Vector3f(own[0] * 16, own[1] * 16, own[2] * 16);
            var low = new Vector3f(element.x0(), element.y0(), element.z0()).sub(shift);
            var high = new Vector3f(element.x1(), element.y1(), element.z1()).sub(shift);
            var turn = element.turn();
            // A block model's turn is about a point in blocks.
            var rotated = turn == null ? null : new BlockElementRotation(
                    new Vector3f(turn.x(), turn.y(), turn.z()).sub(shift).div(16), turn.axis(), turn.angle(), false);
            boxes.add(new Box(element, low, high, rotated));
        }
        return boxes;
    }

    /** The cell, an index per model axis, holding the centre of {@code element} once turned; the lower on a tie. */
    static int[] home(LargeCableRingModels.Element element, int last) {
        var centre = new Vector3f(element.x0() + element.x1(), element.y0() + element.y1(), element.z0() + element.z1())
                .div(2);
        var turn = element.turn();
        if (turn != null) {
            var origin = new Vector3f(turn.x(), turn.y(), turn.z());
            float angle = (float) Math.toRadians(turn.angle());
            new Quaternionf().rotationAxis(angle, unit(turn.axis())).transform(centre.sub(origin)).add(origin);
        }
        int[] home = new int[3];
        for (int i = 0; i < 3; i++) {
            // Rounded first, so a centre on a boundary stays on it however the turn's arithmetic falls.
            float at = Math.round(centre.get(i) * 1000) / 1000f;
            home[i] = Math.max(0, Math.min(last, (int) Math.ceil(at / 16) - 1));
        }
        return home;
    }

    /** A block rotation that turns {@code modelAxis} onto {@code worldAxis}. */
    static BlockModelRotation rotation(Direction.Axis modelAxis, Direction.Axis worldAxis) {
        for (var rotation : BlockModelRotation.values()) {
            var turned = rotation.getRotation().getMatrix().transformDirection(unit(modelAxis));
            if (dominant(turned) == worldAxis) return rotation;
        }
        throw new IllegalStateException("No rotation turns " + modelAxis + " onto " + worldAxis);
    }

    private static Vector3f unit(Direction.Axis axis) {
        var unit = new Vector3f();
        unit.setComponent(axis.ordinal(), 1);
        return unit;
    }

    private static Direction.Axis dominant(Vector3f vector) {
        float x = Math.abs(vector.x());
        float y = Math.abs(vector.y());
        float z = Math.abs(vector.z());
        return x >= y && x >= z ? Direction.Axis.X : y >= z ? Direction.Axis.Y : Direction.Axis.Z;
    }
}
