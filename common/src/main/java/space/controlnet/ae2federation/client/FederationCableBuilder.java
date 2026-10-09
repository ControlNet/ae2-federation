/*
 * Adapted from Applied Energistics 2's appeng.client.render.cablebus.CableBuilder (AE2 19.2.17) and from
 * AE2 Lightning Tech Reborn's com.moakiee.ae2lt.client.render.OverloadedCableRenderHelper.
 *
 * Applied Energistics 2: Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 * Both are free software under the GNU Lesser General Public License, version 3 or later
 * <https://www.gnu.org/licenses/lgpl-3.0.html>. Changes: dense cable paths only, one shell and one cap texture,
 * connection kinds from Federation ports instead of AECableType, and the faces a translucent shell would show
 * inside itself left out (see the class comment).
 */
package space.controlnet.ae2federation.client;

import appeng.client.render.cablebus.CubeBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * Builds a Federation Cable's quads as AE2 builds a dense cable's: a 10-voxel core, 8-voxel dense arms to other
 * Federation Cables and Routers, 4-voxel covered arms (with AE2's 6-voxel cap against a Provider or Endpoint front),
 * and one 10-voxel tube for a straight dense line.
 *
 * <p>The geometry is AE2's. Unlike AE2's opaque cable, the shell is translucent so the flow renderer shows through
 * it, so faces that only an opaque shell may hide are not drawn: an arm starts at the core's surface instead of
 * inside the core and has no inner end, and a straight tube has no end caps and does not reach past the block
 * (AE2 lets it, against facades, which a Federation Cable has none of). From outside an opaque shell this looks the
 * same.
 *
 * <p>TEMPORARY TEXTURES: the shell uses the old cable's {@code glass}, the caps its {@code collar} and the item's
 * core its {@code stream_u} and {@code stream_v}, until the artist's dense cable textures replace them.
 */
final class FederationCableBuilder {
    /** The quads of one cable shape, by layer. {@code cutout} is drawn only for the item. */
    record Quads(List<BakedQuad> solid, List<BakedQuad> translucent, List<BakedQuad> cutout) {
        List<BakedQuad> all() {
            var all = new ArrayList<BakedQuad>(solid.size() + translucent.size() + cutout.size());
            all.addAll(solid);
            all.addAll(translucent);
            all.addAll(cutout);
            return List.copyOf(all);
        }
    }

    private final TextureAtlasSprite core;
    private final TextureAtlasSprite line;
    private final TextureAtlasSprite connector;
    private final TextureAtlasSprite coreSide;
    private final TextureAtlasSprite coreEnd;

    FederationCableBuilder(TextureAtlasSprite core, TextureAtlasSprite line, TextureAtlasSprite connector, TextureAtlasSprite coreSide,
                           TextureAtlasSprite coreEnd) {
        this.core = core;
        this.line = line;
        this.connector = connector;
        this.coreSide = coreSide;
        this.coreEnd = coreEnd;
    }

    /** The world quads for {@code connections}, as {@link CableVisualConnections#connections} encodes them. */
    Quads build(int connections) {
        var solid = new ArrayList<BakedQuad>();
        var translucent = new ArrayList<BakedQuad>();
        if (CableVisualConnections.straight(connections)) {
            var facing = CableVisualConnections.DIRECTIONS[
                    Integer.numberOfTrailingZeros(CableVisualConnections.maskOf(connections))];
            addStraightDenseConnection(facing, EnumSet.complementOf(EnumSet.of(facing, facing.getOpposite())),
                    translucent);
            return new Quads(List.copyOf(solid), List.copyOf(translucent), List.of());
        }
        addDenseCore(translucent);
        for (var facing : CableVisualConnections.DIRECTIONS) {
            switch (CableVisualConnections.kind(connections, facing)) {
                case CableVisualConnections.DENSE -> addDenseConnection(facing, translucent);
                case CableVisualConnections.COVERED_CAP -> addCoveredConnection(facing, true, translucent, solid);
                case CableVisualConnections.COVERED -> addCoveredConnection(facing, false, translucent, solid);
                default -> {
                }
            }
        }
        return new Quads(List.copyOf(solid), List.copyOf(translucent), List.of());
    }

    /** The item: a straight east-west dense tube, closed at both ends, around a lit core. */
    Quads item() {
        var translucent = new ArrayList<BakedQuad>();
        addStraightDenseConnection(Direction.NORTH, EnumSet.allOf(Direction.class), translucent);
        var cutout = new ArrayList<BakedQuad>();
        //var cubeBuilder = new CubeBuilder(cutout);
        //cubeBuilder.setTextures(coreSide, coreSide, coreSide, coreSide, coreEnd, coreEnd);
        //cubeBuilder.setEmissiveMaterial(true);
        //cubeBuilder.addCube(7, 7, 1, 9, 9, 15);
        return new Quads(List.of(), List.copyOf(translucent), List.copyOf(cutout));
    }

    private void addDenseCore(List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setTexture(core);
        cubeBuilder.addCube(2, 2, 2, 14, 14, 14);
    }

    private void addDenseConnection(Direction facing, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        // We render all faces except the one on the connection side and the one inside the core
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(line);
        addDenseCableSizedCube(facing, cubeBuilder);
    }

    private void addCoveredConnection(Direction facing, boolean machineCap, List<BakedQuad> shellOut,
            List<BakedQuad> capOut) {
        // For to-machine connections, use a thicker end-cap for the connection
        if (machineCap) {
            var capBuilder = new CubeBuilder(capOut);
            capBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
            capBuilder.setTexture(connector);
            addBigCoveredCableSizedCube(facing, capBuilder);
        }
        var cubeBuilder = new CubeBuilder(shellOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing, facing.getOpposite())));
        cubeBuilder.setTexture(core);
        addCoveredCableSizedCube(facing, machineCap ? 12 : 16, cubeBuilder);
    }

    private void addStraightDenseConnection(Direction facing, EnumSet<Direction> faces, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(faces);
        cubeBuilder.setTexture(line);
        setStraightCableUVs(cubeBuilder, facing, 2 / 16f, 3 / 16f, 14 / 16f);
        addStraightDenseCableSizedCube(facing, cubeBuilder);
    }

    private static void setStraightCableUVs(CubeBuilder cubeBuilder, Direction facing, float x_0, float x, float y) {
        switch (facing) {
            case DOWN -> {
                cubeBuilder.setCustomUv(Direction.NORTH, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.SOUTH, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.WEST, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.UP, x_0, x_0, y, y);
            }
            case UP -> {
                cubeBuilder.setCustomUv(Direction.NORTH, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.SOUTH, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.WEST, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.DOWN, x_0, x_0, y, y);
            }
            case EAST -> {
                cubeBuilder.setCustomUv(Direction.UP, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.DOWN, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.NORTH, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.SOUTH, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.WEST, x_0, x_0, y, y);
            }
            case WEST -> {
                cubeBuilder.setCustomUv(Direction.UP, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.DOWN, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.NORTH, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.SOUTH, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.EAST, x_0, x_0, y, y);
            }
            case NORTH -> {
                cubeBuilder.setCustomUv(Direction.UP, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.DOWN, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.WEST, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.SOUTH, x_0, x_0, y, y);
            }
            case SOUTH -> {
                cubeBuilder.setCustomUv(Direction.UP, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.DOWN, x_0, 0, y, x);
                cubeBuilder.setCustomUv(Direction.EAST, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.WEST, 0, x_0, x, y);
                cubeBuilder.setCustomUv(Direction.NORTH, x_0, x_0, y, y);
            }
        }
    }

    // Adds a cube to the given cube builder that has the size of a dense cable connection and spans the entire
    // block for the given direction
    private static void addStraightDenseCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN, UP -> {
                cubeBuilder.setUvRotation(Direction.EAST, 2);
                cubeBuilder.addCube(2, 0, 2, 14, 16, 14);
                cubeBuilder.setUvRotation(Direction.EAST, 0);
            }
            case EAST, WEST -> {
                cubeBuilder.setUvRotation(Direction.SOUTH, 2);
                cubeBuilder.setUvRotation(Direction.NORTH, 2);
                cubeBuilder.addCube(0, 2, 2, 16, 14, 14);
                cubeBuilder.setUvRotation(Direction.SOUTH, 0);
                cubeBuilder.setUvRotation(Direction.NORTH, 0);
            }
            case NORTH, SOUTH -> {
                cubeBuilder.setUvRotation(Direction.EAST, 2);
                cubeBuilder.setUvRotation(Direction.WEST, 2);
                cubeBuilder.addCube(2, 2, 0, 14, 14, 16);
                cubeBuilder.setUvRotation(Direction.EAST, 0);
                cubeBuilder.setUvRotation(Direction.WEST, 0);
            }
        }
    }

    // Adds a cube to the given cube builder that has the size of a dense cable connection from the core of the
    // cable to the given face
    private static void addDenseCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(3, 0, 3, 13, 2, 13);
            case EAST -> cubeBuilder.addCube(14, 3, 3, 16, 13, 13);
            case NORTH -> cubeBuilder.addCube(3, 3, 0, 13, 13, 2);
            case SOUTH -> cubeBuilder.addCube(3, 3, 14, 13, 13, 16);
            case UP -> cubeBuilder.addCube(3, 14, 3, 13, 16, 13);
            case WEST -> cubeBuilder.addCube(0, 3, 3, 2, 13, 13);
        }
    }

    // Adds a cube to the given cube builder that has the size of a covered cable connection from the core of the
    // cable to {@code end} voxels toward the given face (16 reaches the face, 12 stops at a machine cap)
    private static void addCoveredCableSizedCube(Direction facing, int end, CubeBuilder cubeBuilder) {
        int start = 16 - end;
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, start, 6, 10, 3, 10);
            case EAST -> cubeBuilder.addCube(13, 6, 6, end, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, start, 10, 10, 3);
            case SOUTH -> cubeBuilder.addCube(6, 6, 13, 10, 10, end);
            case UP -> cubeBuilder.addCube(6, 13, 6, 10, end, 10);
            case WEST -> cubeBuilder.addCube(start, 6, 6, 3, 10, 10);
        }
    }

    private static void addBigCoveredCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(4, 0, 4, 12, 2, 12);
            case EAST -> cubeBuilder.addCube(14, 4, 4, 16, 12, 12);
            case NORTH -> cubeBuilder.addCube(4, 4, 0, 12, 12, 2);
            case SOUTH -> cubeBuilder.addCube(4, 4, 14, 12, 12, 16);
            case UP -> cubeBuilder.addCube(4, 14, 4, 12, 16, 12);
            case WEST -> cubeBuilder.addCube(0, 4, 4, 2, 12, 12);
        }
    }
}
