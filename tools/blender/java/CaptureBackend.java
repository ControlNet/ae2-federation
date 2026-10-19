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
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);
    enum Axis { X, Y, Z }
    final int x, y, z;
    PreviewDirection(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    PreviewDirection getOpposite() { return values()[ordinal() ^ 1]; }
    Axis getAxis() { return x != 0 ? Axis.X : y != 0 ? Axis.Y : Axis.Z; }
    /** As Minecraft's Direction.rotate: the direction a rotation matrix turns this one to. */
    static PreviewDirection rotate(float[][] m, PreviewDirection d) {
        float rx = m[0][0] * d.x + m[0][1] * d.y + m[0][2] * d.z;
        float ry = m[1][0] * d.x + m[1][1] * d.y + m[1][2] * d.z;
        float rz = m[2][0] * d.x + m[2][1] * d.y + m[2][2] * d.z;
        for (var candidate : values()) {
            if (Math.round(rx) == candidate.x && Math.round(ry) == candidate.y && Math.round(rz) == candidate.z) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Not an axis rotation");
    }
}

/** The JOML vector the collar bars are built from: only what FederationCableBuilder calls. */
final class Vector3f {
    float x, y, z;
    Vector3f(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    Vector3f(Vector3f other) { this(other.x, other.y, other.z); }
    Vector3f add(float dx, float dy, float dz) { x += dx; y += dy; z += dz; return this; }
}

/** Vanilla's face UV: u1, v1, u2, v2 in 0..16 and a quarter-turn rotation in degrees. */
record BlockFaceUV(float[] uvs, int rotation) {
    private int shifted(int index) { return (index + rotation / 90) % 4; }
    float getU(int index) { int i = shifted(index); return uvs[i != 0 && i != 1 ? 2 : 0]; }
    float getV(int index) { int i = shifted(index); return uvs[i != 0 && i != 3 ? 3 : 1]; }
}
record BlockElementFace(PreviewDirection cullForDirection, int tintIndex, String texture, BlockFaceUV uv) {}

/** The block model rotations the collar uses, as vanilla builds them: rotateYXZ(-y, -x, 0) about the centre. */
enum BlockModelRotation {
    X0_Y0(0, 0), X0_Y90(0, 90), X90_Y0(90, 0);
    private final float[][] matrix;
    BlockModelRotation(int x, int y) {
        double a = Math.toRadians(-y), b = Math.toRadians(-x);
        float[][] ry = {{(float) Math.cos(a), 0, (float) Math.sin(a)}, {0, 1, 0},
                {(float) -Math.sin(a), 0, (float) Math.cos(a)}};
        float[][] rx = {{1, 0, 0}, {0, (float) Math.cos(b), (float) -Math.sin(b)},
                {0, (float) Math.sin(b), (float) Math.cos(b)}};
        matrix = new float[3][3];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) for (int k = 0; k < 3; k++) {
            matrix[i][j] += ry[i][k] * rx[k][j];
        }
    }
    BlockModelRotation getRotation() { return this; }
    float[][] getMatrix() { return matrix; }
}

/** Vanilla FaceBakery's geometry for an unrotated element under a block model rotation without UV lock. */
final class FaceBakery {
    CapturedQuad bakeQuad(Vector3f from, Vector3f to, BlockElementFace face, TextureRef sprite,
            PreviewDirection side, BlockModelRotation rotation, Object partRotation, boolean shade) {
        if (partRotation != null) throw new UnsupportedOperationException("Element rotation");
        float x1 = from.x / 16, y1 = from.y / 16, z1 = from.z / 16, x2 = to.x / 16, y2 = to.y / 16, z2 = to.z / 16;
        // FaceInfo's corner order, the same as CaptureCubeBuilder's.
        float[][] p = switch (side) {
            case DOWN -> new float[][] {{x1,y1,z2},{x1,y1,z1},{x2,y1,z1},{x2,y1,z2}};
            case UP -> new float[][] {{x1,y2,z1},{x1,y2,z2},{x2,y2,z2},{x2,y2,z1}};
            case NORTH -> new float[][] {{x2,y2,z1},{x2,y1,z1},{x1,y1,z1},{x1,y2,z1}};
            case SOUTH -> new float[][] {{x1,y2,z2},{x1,y1,z2},{x2,y1,z2},{x2,y2,z2}};
            case WEST -> new float[][] {{x1,y2,z1},{x1,y1,z1},{x1,y1,z2},{x1,y2,z2}};
            case EAST -> new float[][] {{x2,y2,z2},{x2,y1,z2},{x2,y1,z1},{x2,y2,z1}};
        };
        var m = rotation.getMatrix();
        float[][] uv = new float[4][];
        for (int i = 0; i < 4; i++) {
            float px = p[i][0] - .5f, py = p[i][1] - .5f, pz = p[i][2] - .5f;
            p[i] = new float[] {m[0][0] * px + m[0][1] * py + m[0][2] * pz + .5f,
                    m[1][0] * px + m[1][1] * py + m[1][2] * pz + .5f,
                    m[2][0] * px + m[2][1] * py + m[2][2] * pz + .5f};
            uv[i] = new float[] {face.uv().getU(i) / 16, face.uv().getV(i) / 16};
        }
        return new CapturedQuad(sprite.name(), p, uv);
    }
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
