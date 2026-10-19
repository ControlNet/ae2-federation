package space.controlnet.ae2federation.client;

import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import space.controlnet.ae2federation.bundle.BundleLook;

/**
 * The artist's rings inside a 2x2 bundle's shell ({@link LargeCableRingModels}). Each ring is modelled over two blocks
 * each way, turned so that its axis lies along the world's; a box of it belongs to the cell that holds its centre (the
 * lower one on a tie), so every box is drawn once and may reach into the next cell.
 */
final class LargeCableRings {
    private static final FaceBakery BAKERY = new FaceBakery();

    private final TextureAtlasSprite texture;

    LargeCableRings(TextureAtlasSprite texture) {
        this.texture = texture;
    }

    /** The cell's share of the rings its look names: a straight ring on a layer boundary, or a branch's corner rings. */
    void add(BundleLook look, List<BakedQuad> quads) {
        var axis = look.axis();
        // Which half of the two-block frame the cell lies in, one bit per world axis.
        int across = 0;
        for (var other : Direction.Axis.values()) {
            if (other != axis && look.edge(Direction.fromAxisAndDirection(other, Direction.AxisDirection.POSITIVE))) {
                across |= 1 << other.ordinal();
            }
        }
        int after = 1 << axis.ordinal();
        if (look.ringBefore()) add(LargeCableRingModels.STRAIGHT, Direction.Axis.Z, axis, across | after, quads);
        if (look.ringAfter()) add(LargeCableRingModels.STRAIGHT, Direction.Axis.Z, axis, across, quads);
        // Branches toward both ends of one axis share the same rings.
        int drawn = 0;
        for (var side : Direction.values()) {
            boolean before = look.jointBefore(side);
            if (!before && !look.jointAfter(side)) continue;
            int key = 1 << (side.getAxis().ordinal() * 2 + (before ? 1 : 0));
            if ((drawn & key) != 0) continue;
            drawn |= key;
            var third = Direction.Axis.values()[3 - axis.ordinal() - side.getAxis().ordinal()];
            add(LargeCableRingModels.CORNER, Direction.Axis.Y, third, across | (before ? after : 0), quads);
        }
    }

    /**
     * The boxes of {@code model} in the cell at {@code octant} (one bit per world axis) of the frame, turned so that
     * the model's {@code modelAxis} lies along {@code worldAxis}.
     */
    private void add(List<LargeCableRingModels.Element> model, Direction.Axis modelAxis, Direction.Axis worldAxis,
            int octant, List<BakedQuad> quads) {
        var rotation = rotation(modelAxis, worldAxis);
        for (var box : boxes(model, rotation, octant)) {
            for (var face : box.element().faces()) {
                // The texture is 32 pixels; a block face's UVs count 16 across any sprite.
                var uv = new BlockFaceUV(new float[] {face.u0() / 2f, face.v0() / 2f, face.u1() / 2f, face.v1() / 2f},
                        face.rotation());
                var baked = new BlockElementFace(null, -1, "", uv);
                quads.add(BAKERY.bakeQuad(box.from(), box.to(), baked, texture, face.side(), rotation, null, true));
            }
        }
    }

    /** A box of a ring in one cell's own coordinates, before the rotation. */
    record Box(LargeCableRingModels.Element element, Vector3f from, Vector3f to) {
    }

    /** The boxes of {@code model} the cell at {@code octant} of the turned frame draws. */
    static List<Box> boxes(List<LargeCableRingModels.Element> model, BlockModelRotation rotation, int octant) {
        var matrix = rotation.getRotation().getMatrix();
        // The same cell in the model's own frame: each model axis turns onto a world axis, maybe reversed.
        int cell = 0;
        for (var axis : Direction.Axis.values()) {
            var turned = matrix.transformDirection(unit(axis));
            var world = dominant(turned);
            int bit = octant >> world.ordinal() & 1;
            if (turned.get(world.ordinal()) < 0) bit ^= 1;
            cell |= bit << axis.ordinal();
        }
        var boxes = new java.util.ArrayList<Box>();
        for (var element : model) {
            int[] from = {element.x0(), element.y0(), element.z0()};
            int[] to = {element.x1(), element.y1(), element.z1()};
            int home = 0;
            for (int i = 0; i < 3; i++) {
                if (from[i] + to[i] > 32) home |= 1 << i;
            }
            if (home != cell) continue;
            var low = new Vector3f();
            var high = new Vector3f();
            for (int i = 0; i < 3; i++) {
                int shift = (cell >> i & 1) * 16;
                low.setComponent(i, from[i] - shift);
                high.setComponent(i, to[i] - shift);
            }
            boxes.add(new Box(element, low, high));
        }
        return boxes;
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
