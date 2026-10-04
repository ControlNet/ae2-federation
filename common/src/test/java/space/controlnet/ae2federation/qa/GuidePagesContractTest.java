package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The GuideME pages join AE2's own guide from {@code assets/ae2federation/ae2guide}; GuideME picks a page's
 * {@code _zh_cn/} copy for Chinese. GuideME only reports broken links and ids when the page is opened in a client, so
 * this pins what can be checked from the files.
 */
final class GuidePagesContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path GUIDE = ROOT.resolve("common/src/main/resources/assets/ae2federation/ae2guide");
    private static final Path CHINESE = GUIDE.resolve("_zh_cn");
    private static final Set<String> FEDERATION_ITEMS = Set.of("federation_logic_processor", "bridge", "router", "cable",
            "pattern_provider", "processing_endpoint");
    /** The AE2 19.2.17 guide pages and items the pages name; the client check opens them for real. */
    private static final Set<String> AE2_PAGES = Set.of("ae2-mechanics/channels.md");
    private static final Set<String> AE2_ITEMS = Set.of("inscriber", "logic_processor", "fluix_dust", "quartz_fiber",
            "pattern_provider", "fluix_glass_cable", "network_tool", "molecular_assembler", "drive", "storage_bus");
    private static final Pattern LINK = Pattern.compile("]\\(([^)#]+)(#[^)]*)?\\)");
    private static final Pattern STRUCTURE = Pattern.compile("<ImportStructure src=\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("(?:id=\"|icon: |^- )([a-z0-9_]+):([a-z0-9_/.]+)", Pattern.MULTILINE);

    @Test
    void everyPageHasAChineseCopyAndNoChinesePageIsOrphaned() throws IOException {
        assertEquals(pages(GUIDE), pages(CHINESE));
        assertTrue(pages(GUIDE).containsAll(Set.of("index.md", "getting-started.md", "mechanics.md",
                "remote-processing.md", "troubleshooting.md")));
    }

    /**
     * Every page hangs under the Federation entry, directly or through another page: the examples hang under the
     * examples page. GuideME resolves a parent against the namespace root, not relative to the page.
     */
    @Test
    void pagesHangUnderTheFederationEntryAndChineseCopiesKeepTheirPlace() throws IOException {
        var pages = pages(GUIDE);
        for (var page : pages) {
            var english = frontmatter(GUIDE.resolve(page));
            var chinese = frontmatter(CHINESE.resolve(page));
            assertTrue(english.contains("navigation:"), page);
            assertEquals(page.equals("index.md"), !english.contains("parent:"), page);
            var ancestor = page;
            for (int depth = 0; !ancestor.equals("index.md"); depth++) {
                var parent = Pattern.compile("(?m)^  parent: (\\S+)$").matcher(frontmatter(GUIDE.resolve(ancestor)));
                assertTrue(parent.find() && pages.contains(parent.group(1)) && depth < pages.size(),
                        page + " must hang under index.md through existing pages");
                ancestor = parent.group(1);
            }
            assertTrue(!page.startsWith("examples/") || page.equals("examples/index.md")
                    || english.contains("  parent: examples/index.md\n"), page + " belongs under the examples page");
            assertEquals(english.replaceAll("(?m)^  title: .*$", ""), chinese.replaceAll("(?m)^  title: .*$", ""),
                    "Only the title may differ between " + page + " and its Chinese copy");
        }
    }

    @Test
    void linksPointAtPagesThatExist() throws IOException {
        for (var root : Set.of(GUIDE, CHINESE)) {
            for (var page : pages(root)) {
                var links = LINK.matcher(Files.readString(root.resolve(page)));
                while (links.find()) {
                    var target = links.group(1);
                    if (target.startsWith("ae2:")) {
                        assertTrue(AE2_PAGES.contains(target.substring(4)), page + " -> " + target);
                    } else {
                        // Page ids are the English paths, so a Chinese copy links exactly as the English page does.
                        var resolved = GUIDE.resolve(page).getParent().resolve(target).normalize();
                        assertTrue(Files.isRegularFile(resolved) && resolved.startsWith(GUIDE), page + " -> " + target);
                    }
                }
            }
        }
    }

    @Test
    void itemIdsAreQualifiedAndRegistered() throws IOException {
        for (var root : Set.of(GUIDE, CHINESE)) {
            for (var page : pages(root)) {
                var text = Files.readString(root.resolve(page));
                assertTrue(!Pattern.compile("id=\"[a-z0-9_]+\"").matcher(text).find(),
                        page + ": write ids with their namespace; a bare id would resolve against this page's");
                var ids = ID.matcher(text);
                while (ids.find()) {
                    var known = switch (ids.group(1)) {
                        case "ae2federation" -> FEDERATION_ITEMS;
                        case "ae2" -> AE2_ITEMS;
                        default -> Set.<String>of();
                    };
                    assertTrue(known.contains(ids.group(2)), page + ": " + ids.group());
                }
            }
        }
    }

    /**
     * GuideME breaks lines only at whitespace and mishandles a run longer than a line, so Chinese text needs a break
     * point after its punctuation, and a soft-wrapped source line would end the rendered line early.
     */
    @Test
    void chineseParagraphsStayOnOneLineWithABreakAfterPunctuation() throws IOException {
        var block = Pattern.compile("^(\\s*<(?!ItemLink)|#|\\* |\\d+\\. |\\||$)");
        var cramped = Pattern.compile("[，。；：！？](?=[^\\s，。；：！？、）”*])");
        for (var page : pages(CHINESE)) {
            var text = Files.readString(CHINESE.resolve(page));
            var lines = text.substring(text.indexOf("\n---\n", 4) + 5).split("\n", -1);
            for (var i = 0; i < lines.length; i++) {
                // Tags, tables, headings and blank lines are not wrapped paragraph text.
                if (block.matcher(lines[i]).find() && !lines[i].startsWith("* ") && !lines[i].matches("\\d+\\. .*")) continue;
                assertTrue(!cramped.matcher(lines[i]).find(), page + ": no break point after punctuation: " + lines[i]);
                assertTrue(i + 1 == lines.length || block.matcher(lines[i + 1]).find(),
                        page + ": a paragraph continues on the next source line: " + lines[i + 1]);
            }
        }
    }

    @Test
    void eachFederationItemHasOnePageForTheItemIndex() throws IOException {
        var owners = new TreeMap<String, String>();
        for (var page : pages(GUIDE)) {
            var frontmatter = frontmatter(GUIDE.resolve(page));
            var items = Pattern.compile("(?m)^- ae2federation:([a-z_]+)$").matcher(frontmatter);
            while (items.find()) assertEquals(null, owners.put(items.group(1), page), items.group(1));
        }
        assertEquals(new TreeSet<>(FEDERATION_ITEMS), new TreeSet<>(owners.keySet()));
    }

    @Test
    void structureScenesResolveAndEveryStructureIsShown() throws IOException {
        var used = new TreeSet<Path>();
        for (var root : Set.of(GUIDE, CHINESE)) {
            for (var page : pages(root)) {
                var sources = STRUCTURE.matcher(Files.readString(root.resolve(page)));
                while (sources.find()) {
                    // Resolved against the page id, which is the English path for a Chinese copy too.
                    var resolved = GUIDE.resolve(page).getParent().resolve(sources.group(1)).normalize();
                    assertTrue(Files.isRegularFile(resolved) && resolved.startsWith(GUIDE.resolve("assets")),
                            page + " -> " + sources.group(1));
                    used.add(resolved);
                }
            }
        }
        try (Stream<Path> files = Files.walk(GUIDE.resolve("assets"))) {
            assertEquals(files.filter(path -> path.toString().endsWith(".snbt")).collect(
                    Collectors.toCollection(TreeSet::new)), used);
        }
    }

    /** GuideME rejects a whole scene when a block's state is missing from the palette, and only says so in a client. */
    @Test
    void everyStructureBlockStateIsInItsPalette() throws IOException {
        try (Stream<Path> files = Files.walk(GUIDE.resolve("assets"))) {
            for (var structure : files.filter(path -> path.toString().endsWith(".snbt")).toList()) {
                var text = Files.readString(structure);
                var paletteStart = text.indexOf("palette: [");
                assertTrue(text.contains("DataVersion: ") && paletteStart > 0, structure.toString());
                var palette = new TreeSet<String>();
                var entries = Pattern.compile("\"([^\"]+)\"").matcher(text.substring(paletteStart));
                while (entries.find()) palette.add(entries.group(1));
                var states = Pattern.compile("pos: \\[[^]]*], state: \"([^\"]+)\"").matcher(text.substring(0, paletteStart));
                var blocks = 0;
                while (states.find()) {
                    assertTrue(palette.contains(states.group(1)), structure.getFileName() + ": " + states.group(1));
                    blocks++;
                }
                assertTrue(blocks > 0, structure.toString());
            }
        }
    }

    private static Set<String> pages(Path root) throws IOException {
        var pages = new TreeSet<String>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> root.equals(CHINESE) || !path.startsWith(CHINESE))
                    .forEach(path -> pages.add(root.relativize(path).toString().replace('\\', '/')));
        }
        return pages;
    }

    private static String frontmatter(Path page) throws IOException {
        var text = Files.readString(page);
        assertTrue(text.startsWith("---\n"), page.toString());
        return text.substring(4, text.indexOf("\n---\n", 4) + 1);
    }
}
