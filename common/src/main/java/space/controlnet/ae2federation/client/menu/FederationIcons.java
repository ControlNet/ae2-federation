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

    /** This domain only: three networks inside a dashed boundary. */
    static final IGuiTexture SCOPE_DOMAIN = glyph(
            "##.###.##.###.##",
            "#..............#",
            "................",
            "#..............#",
            "#......##......#",
            "#......##......#",
            "......#..#......",
            "#.....#..#.....#",
            "#....#....#....#",
            "....#......#....",
            "#..##......##..#",
            "#..##########..#",
            "#..............#",
            "................",
            "#..............#",
            "##.###.##.###.##");

    /** Every related domain: a globe. */
    static final IGuiTexture SCOPE_ALL = glyph(
            "....########....",
            "..##...##...##..",
            ".#....#..#....#.",
            ".#...#....#...#.",
            ".##############.",
            "#....#....#....#",
            "#....#....#....#",
            "################",
            "#....#....#....#",
            "#....#....#....#",
            "#....#....#....#",
            ".##############.",
            ".#...#....#...#.",
            ".#....#..#....#.",
            "..##...##...##..",
            "....########....");

    /** Live flow: an arrow carrying two dots. */
    static final IGuiTexture FLOW = glyph(
            "................",
            "................",
            "................",
            "..........#.....",
            "...........#....",
            "............#...",
            ".##...##.....#..",
            "###############.",
            "###############.",
            ".##...##.....#..",
            "............#...",
            "...........#....",
            "..........#.....",
            "................",
            "................",
            "................");

    /** A pattern pushed up to a wall: Blocking mode waits for the machine to empty before the next push. */
    static final IGuiTexture BLOCKING = glyph(
            "................",
            "................",
            "...........###..",
            "...........#.#..",
            "......#....#.#..",
            "......##...#.#..",
            ".########..#.#..",
            ".#########.#.#..",
            ".#########.#.#..",
            ".########..#.#..",
            "......##...#.#..",
            "......#....#.#..",
            "...........#.#..",
            "...........###..",
            "................",
            "................");

    /** A padlock: Lock crafting holds new crafts until its redstone or result condition is met. */
    static final IGuiTexture LOCK = glyph(
            "................",
            "................",
            "......####......",
            ".....#....#.....",
            "....#......#....",
            "....#......#....",
            "....#......#....",
            "...##########...",
            "...#........#...",
            "...#...##...#...",
            "...#...##...#...",
            "...#...##...#...",
            "...#........#...",
            "...##########...",
            "................",
            "................");

    /** A terminal screen: whether the Pattern Access Terminal lists this Provider. */
    static final IGuiTexture TERMINAL = glyph(
            "................",
            "................",
            ".##############.",
            ".#............#.",
            ".#.#..........#.",
            ".#..#.........#.",
            ".#...#........#.",
            ".#..#.........#.",
            ".#.#...####...#.",
            ".#............#.",
            ".##############.",
            "......####......",
            "................",
            "....########....",
            "................",
            "................");

    private FederationIcons() {}

    /** Puts {@code icon} on a text-less button, centred above its bottom lip. */
    static void apply(com.lowdragmc.lowdraglib2.gui.ui.elements.Button button, IGuiTexture icon) {
        button.noText();
        var glyph = new com.lowdragmc.lowdraglib2.gui.ui.UIElement();
        glyph.setAllowHitTest(false);
        glyph.layout(style -> style.widthPercent(100).heightPercent(100).paddingBottom(2));
        glyph.style(style -> style.backgroundTexture(icon));
        button.addChild(glyph);
    }

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
