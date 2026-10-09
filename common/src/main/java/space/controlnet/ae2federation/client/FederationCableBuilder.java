/*
 * Adapted from Applied Energistics 2's appeng.client.render.cablebus.CableBuilder (AE2 19.2.17) and from
 * AE2 Lightning Tech Reborn's com.moakiee.ae2lt.client.render.OverloadedCableRenderHelper.
 *
 * Applied Energistics 2: Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 * Both are free software under the GNU Lesser General Public License, version 3 or later
 * <https://www.gnu.org/licenses/lgpl-3.0.html>. Changes: dense cable paths only, the artist's core and line
 * textures, a 12-voxel core with 10-voxel dense arms, connection kinds from Federation ports instead of AECableType,
 * and the faces a translucent shell would show inside itself left out (see the class comment).
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
 * Builds a Federation Cable's translucent quads as AE2 builds a dense cable's, at the artist's sizes: a 12-voxel
 * core, 10-voxel dense arms to other Federation Cables, Routers and Provider or Endpoint fronts, 4-voxel covered arms
 * to Federation P2P tunnels, and one 12-voxel tube for a straight dense line.
 *
 * <p>The layout is AE2's. Unlike AE2's opaque cable, the shell is translucent so the flow renderer shows through
 * it, so an arm starts at the core's surface instead of inside the core, and a straight tube has no end caps and does
 * not reach past the block (AE2 lets it, against facades, which a Federation Cable has none of).
 */
final class FederationCableBuilder {
    private final TextureAtlasSprite core;
    private final TextureAtlasSprite line;

    FederationCableBuilder(TextureAtlasSprite core, TextureAtlasSprite line) {
        this.core = core;
        this.line = line;
    }

    /** The world quads for {@code connections}, as {@link CableVisualConnections#connections} encodes them. */
    List<BakedQuad> build(int connections) {
        var quads = new ArrayList<BakedQuad>();
        if (CableVisualConnections.straight(connections)) {
            var facing = CableVisualConnections.DIRECTIONS[
                    Integer.numberOfTrailingZeros(CableVisualConnections.maskOf(connections))];
            addStraightDenseConnection(facing, EnumSet.complementOf(EnumSet.of(facing, facing.getOpposite())), quads);
            return List.copyOf(quads);
        }
        addDenseCore(quads);
        for (var facing : CableVisualConnections.DIRECTIONS) {
            switch (CableVisualConnections.kind(connections, facing)) {
                case CableVisualConnections.DENSE -> addDenseConnection(facing, quads);
                case CableVisualConnections.COVERED -> addCoveredConnection(facing, quads);
                default -> {
                }
            }
        }
        return List.copyOf(quads);
    }

    /** The item: a straight north-south dense tube, closed at both ends. */
    List<BakedQuad> item() {
        var quads = new ArrayList<BakedQuad>();
        addStraightDenseConnection(Direction.NORTH, EnumSet.allOf(Direction.class), quads);
        return List.copyOf(quads);
    }

    private void addDenseCore(List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setTexture(core);
        cubeBuilder.addCube(2, 2, 2, 14, 14, 14);
    }

    private void addDenseConnection(Direction facing, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        // Every face but the one on the connection side; the inner end is the artist's seam against the core
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing)));
        cubeBuilder.setTexture(line);
        addDenseCableSizedCube(facing, cubeBuilder);
    }

    private void addCoveredConnection(Direction facing, List<BakedQuad> quadsOut) {
        var cubeBuilder = new CubeBuilder(quadsOut);
        cubeBuilder.setDrawFaces(EnumSet.complementOf(EnumSet.of(facing, facing.getOpposite())));
        cubeBuilder.setTexture(core);
        addCoveredCableSizedCube(facing, cubeBuilder);
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
    // cable to the given face
    private static void addCoveredCableSizedCube(Direction facing, CubeBuilder cubeBuilder) {
        switch (facing) {
            case DOWN -> cubeBuilder.addCube(6, 0, 6, 10, 2, 10);
            case EAST -> cubeBuilder.addCube(14, 6, 6, 16, 10, 10);
            case NORTH -> cubeBuilder.addCube(6, 6, 0, 10, 10, 2);
            case SOUTH -> cubeBuilder.addCube(6, 6, 14, 10, 10, 16);
            case UP -> cubeBuilder.addCube(6, 14, 6, 10, 16, 10);
            case WEST -> cubeBuilder.addCube(0, 6, 6, 2, 10, 10);
        }
    }
}
