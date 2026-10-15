/*
 * Offline quad backend for FederationCableBuilder.
 * UV and face conventions adapted from AE2 19.2.17 CubeBuilder and its Fabric QuadEmitter.
 * Copyright (c) 2013-2014 AlgorithmX2; LGPL-3.0-or-later.
 * https://github.com/AppliedEnergistics/Applied-Energistics-2
 * This backend records real geometry; it does not emulate a Minecraft runtime.
 */
import java.util.*;

// Minecraft order is retained because some geometry code uses ordinals.
enum PreviewDirection {
    DOWN, UP, NORTH, SOUTH, WEST, EAST;
    PreviewDirection getOpposite() { return values()[ordinal() ^ 1]; }
}
record TextureRef(String name) {}
record CapturedQuad(String texture, float[][] positions, float[][] uv) {}

final class CaptureCubeBuilder {
    private final List<CapturedQuad> output;
    private final Map<PreviewDirection, TextureRef> textures = new EnumMap<>(PreviewDirection.class);
    private final Map<PreviewDirection, float[]> custom = new EnumMap<>(PreviewDirection.class);
    private final Map<PreviewDirection, Integer> rotations = new EnumMap<>(PreviewDirection.class);
    private EnumSet<PreviewDirection> faces = EnumSet.allOf(PreviewDirection.class);
    CaptureCubeBuilder(List<CapturedQuad> output) { this.output = output; }
    void setDrawFaces(EnumSet<PreviewDirection> faces) { this.faces = faces.clone(); }
    void setTexture(TextureRef texture) { for (var face : PreviewDirection.values()) setTexture(face, texture); }
    void setTexture(PreviewDirection face, TextureRef texture) { textures.put(face, texture); }
    void setUvRotation(PreviewDirection face, int turns) {
        if (turns < 0 || turns > 3) throw new IllegalArgumentException("UV rotation");
        rotations.put(face, turns);
    }
    void setCustomUv(PreviewDirection face, float u1, float v1, float u2, float v2) {
        custom.put(face, new float[] {u1, v1, u2, v2});
    }
    void addCube(float x1, float y1, float z1, float x2, float y2, float z2) {
        x1 /= 16; y1 /= 16; z1 /= 16; x2 /= 16; y2 /= 16; z2 /= 16;
        for (var face : faces) {
            float[][] p = switch (face) {
                case DOWN -> new float[][] {{x1,y1,z2},{x1,y1,z1},{x2,y1,z1},{x2,y1,z2}};
                case UP -> new float[][] {{x1,y2,z1},{x1,y2,z2},{x2,y2,z2},{x2,y2,z1}};
                case NORTH -> new float[][] {{x2,y2,z1},{x2,y1,z1},{x1,y1,z1},{x1,y2,z1}};
                case SOUTH -> new float[][] {{x1,y2,z2},{x1,y1,z2},{x2,y1,z2},{x2,y2,z2}};
                case WEST -> new float[][] {{x1,y2,z1},{x1,y1,z1},{x1,y1,z2},{x1,y2,z2}};
                case EAST -> new float[][] {{x2,y2,z2},{x2,y1,z2},{x2,y1,z1},{x2,y2,z1}};
            };
            boolean horizontal = face == PreviewDirection.UP || face == PreviewDirection.DOWN;
            float u1 = switch (face) { case NORTH -> 1-x2; case WEST -> z1; case EAST -> 1-z2; default -> x1; };
            float u2 = switch (face) { case NORTH -> 1-x1; case WEST -> z2; case EAST -> 1-z1; default -> x2; };
            float v1 = horizontal ? z1 : 1-y1, v2 = horizontal ? z2 : 1-y2;
            float[] c = custom.get(face);
            if (c != null) { u1=c[0]; v1=c[1]; u2=c[2]; v2=c[3]; }
            float[][] base = horizontal
                    ? new float[][] {{u1,v1},{u1,v2},{u2,v2},{u2,v1}}
                    : new float[][] {{u1,v2},{u1,v1},{u2,v1},{u2,v2}};
            float[][] uv = new float[4][];
            int rotation = rotations.getOrDefault(face, 0);
            for (int i=0; i<4; i++) uv[(i+4-rotation)%4] = base[i];
            output.add(new CapturedQuad(Objects.requireNonNull(textures.get(face)).name(), p, uv));
        }
    }
}
