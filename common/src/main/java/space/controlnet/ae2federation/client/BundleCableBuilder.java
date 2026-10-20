package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import space.controlnet.ae2federation.bundle.BundleLook;

/**
 * A bundled cell's translucent quads: its part of the bundle's one shell ({@link BundleShellLayout}), cut from the
 * artist's core frame, and inside it the ordinary cable's pieces at the cell's own size. A collar rings the cell where
 * its beam runs on into the next cell or into a branch, and a dense arm reaches each device it connects to, so a 2x3
 * bundle shows six beams in one 2x3 shell. A 2x2 or 3x3 bundle is one thick cable instead: no collars, but the artist's rings
 * ({@link LargeCableRings}) around the one beam down its middle.
 */
final class BundleCableBuilder {
    private final FederationCableBuilder cable;
    private final TextureAtlasSprite frame;
    private final LargeCableRings rings;

    BundleCableBuilder(FederationCableBuilder cable, TextureAtlasSprite frame, LargeCableRings rings) {
        this.cable = cable;
        this.frame = frame;
        this.rings = rings;
    }

    List<BakedQuad> build(BundleLook look) {
        var quads = new ArrayList<BakedQuad>();
        var baker = new QuadBakingVertexConsumer();
        for (var panel : BundleShellLayout.panels(look)) {
            for (var tile : BundleShellLayout.tiles(panel)) quads.add(quad(baker, panel, tile));
        }
        for (var side : Direction.values()) {
            if (look.open(side)) {
                if (!look.thick()) cable.addCollar(side, quads);
            } else if (look.dense(side)) {
                cable.addDenseConnection(side, quads);
            }
        }
        return List.copyOf(quads);
    }

    /**
     * A 2x2 or 3x3 cell's share of the artist's rings. They are opaque, so they go in the solid layer: in the translucent one
     * the shell, drawn first and writing depth, hid whatever of them lay behind it.
     */
    List<BakedQuad> solid(BundleLook look) {
        if (!look.thick()) return List.of();
        var quads = new ArrayList<BakedQuad>();
        rings.add(look, quads);
        return List.copyOf(quads);
    }

    /**
     * One tile of a panel, wound counter-clockwise seen from outside, as the translucent layer culls. It samples the
     * middle of its one texel, so mipmaps never blend in the next.
     */
    private BakedQuad quad(QuadBakingVertexConsumer baker, BundleShellLayout.Panel panel, BundleShellLayout.Tile tile) {
        var normal = panel.normal();
        baker.setSprite(frame);
        baker.setDirection(normal);
        baker.setShade(true);
        baker.setHasAmbientOcclusion(true);
        baker.setTintIndex(-1);
        // u then v is counter-clockwise from outside when u x v points along the normal.
        boolean forward = cross(panel.uAxis(), panel.vAxis()) == normal.getAxis().ordinal()
                && sign(panel.uAxis(), panel.vAxis()) == normal.getAxisDirection().getStep();
        float[][] corners = forward
                ? new float[][] {{tile.u0(), tile.v0()}, {tile.u1(), tile.v0()}, {tile.u1(), tile.v1()},
                        {tile.u0(), tile.v1()}}
                : new float[][] {{tile.u0(), tile.v0()}, {tile.u0(), tile.v1()}, {tile.u1(), tile.v1()},
                        {tile.u1(), tile.v0()}};
        float texelU = tile.texelU() + .5f;
        float texelV = tile.texelV() + .5f;
        for (var corner : corners) {
            float[] position = new float[3];
            position[normal.getAxis().ordinal()] = panel.plane() / 16;
            position[panel.uAxis().ordinal()] = corner[0] / 16;
            position[panel.vAxis().ordinal()] = corner[1] / 16;
            baker.addVertex(position[0], position[1], position[2]);
            baker.setColor(255, 255, 255, 255);
            baker.setUv(frame.getU(texelU / 16), frame.getV(texelV / 16));
            baker.setNormal(normal.getStepX(), normal.getStepY(), normal.getStepZ());
        }
        return baker.bakeQuad();
    }

    /** The axis of {@code a x b} for two different axes. */
    private static int cross(Direction.Axis a, Direction.Axis b) {
        return 3 - a.ordinal() - b.ordinal();
    }

    /** The sign of {@code a x b}: positive for X x Y, Y x Z and Z x X. */
    private static int sign(Direction.Axis a, Direction.Axis b) {
        return (b.ordinal() - a.ordinal() + 3) % 3 == 1 ? 1 : -1;
    }
}
