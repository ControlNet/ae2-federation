package space.controlnet.ae2federation.client.menu;

/**
 * Moves a drawn span's edges to whole screen pixels. Layout leaves fractional positions and sizes, and a nine-slice
 * sprite whose seam falls on a pixel's centre samples the texel row beside it, which shows as a broken line.
 */
final class PixelSnap {
    private PixelSnap() {}

    /**
     * A span's start and size in local units, for a pose that maps local {@code u} to {@code scale * u + translate}
     * GUI units and a window of {@code guiScale} screen pixels per GUI unit.
     */
    static float[] span(float start, float size, float scale, float translate, double guiScale) {
        double first = Math.round((scale * start + translate) * guiScale) / guiScale;
        double last = Math.round((scale * (start + size) + translate) * guiScale) / guiScale;
        float snappedStart = (float) ((first - translate) / scale);
        return new float[] {snappedStart, (float) ((last - translate) / scale) - snappedStart};
    }
}
