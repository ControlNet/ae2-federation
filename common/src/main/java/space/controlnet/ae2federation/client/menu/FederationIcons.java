package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;

/** 16x16 pixel glyphs for the workspace rail, drawn in AE2's dark icon ink and centred in their element. */
final class FederationIcons {
    private static final int INK = 0xff1f2438;

    /** Three networks joined by two links. */
    static final IGuiTexture TOPOLOGY = glyph(
            "................",
            "..........####..",
            "..........#..#..",
            "..........#..#..",
            ".........##.##..",
            ".####...##......",
            ".#..#.##........",
            ".#..###.........",
            ".#..###.........",
            ".#..#.##........",
            ".####...##......",
            ".........##.##..",
            "..........#..#..",
            "..........#..#..",
            "..........####..",
            "................");

    /** A pattern wired to a machine. */
    static final IGuiTexture PROCESSING = glyph(
            "................",
            ".#####..........",
            ".#...#..........",
            ".#.#.#..........",
            ".#...####.......",
            ".#####..#.......",
            "........#.......",
            "........#.......",
            "........#.......",
            "........#.......",
            ".......####.....",
            "..........######",
            "..........#....#",
            "..........#.##.#",
            "..........#....#",
            "..........######");

    /** A signal trace. */
    static final IGuiTexture DIAGNOSTICS = glyph(
            "................",
            "................",
            "................",
            "......#.........",
            "......##........",
            "......##........",
            ".....#.#........",
            ".....#.#......#.",
            "#....#..#....#..",
            "##..#...#...#...",
            ".#..#...#...#...",
            "..##.....#.#....",
            ".........#.#....",
            "..........#.....",
            "................",
            "................");

    private FederationIcons() {}

    private static IGuiTexture glyph(String... rows) {
        return FederationTheme.painted((pen, x, y, width, height) -> {
            float left = x + (width - 16) / 2f;
            float top = y + (height - 16) / 2f;
            for (int row = 0; row < rows.length; row++) {
                var line = rows[row];
                for (int column = 0; column < line.length(); column++) {
                    if (line.charAt(column) == '#') pen.rect(left + column, top + row, 1, 1, INK);
                }
            }
        });
    }
}
