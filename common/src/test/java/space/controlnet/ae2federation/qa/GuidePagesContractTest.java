package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.guide.GuideExamplePacks;
import space.controlnet.ae2federation.client.guide.TopologyDiagram;
import space.controlnet.ae2federation.client.guide.TopologyDiagramLayout;
import space.controlnet.ae2federation.client.guide.TopologyDiagramLayoutTest;

/**
 * The GuideME pages join AE2's own guide from {@code assets/ae2federation/ae2guide}; GuideME picks a page's
 * {@code _zh_cn/} copy for Chinese. Examples that need other mods sit in the same folder of a built-in resource pack,
 * active only with those mods, so they may reach the base pages but never the reverse, nor another pack. GuideME only
 * reports broken links and ids when the page is opened in a client, so this pins what can be checked from the files.
 */
final class GuidePagesContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path RESOURCES = ROOT.resolve("common/src/main/resources");
    private static final Path GUIDE = RESOURCES.resolve("assets/ae2federation/ae2guide");
    private static final Set<String> BASE_NAMESPACES = Set.of("minecraft", "ae2", "ae2federation");
    private static final Set<String> FEDERATION_ITEMS = Set.of("printed_nexus_circuit", "nexus_processor", "nexus_core",
            "bridge", "router", "switch", "cable", "pattern_provider", "processing_endpoint", "federation_p2p_tunnel");
    /** The AE2 19.2.17 guide pages and items the pages name; the client check opens them for real. */
    private static final Set<String> AE2_PAGES = Set.of("ae2-mechanics/channels.md",
            "items-blocks-machines/p2p_tunnels.md");
    private static final Set<String> AE2_ITEMS = Set.of("inscriber", "ender_dust", "printed_silicon", "quartz_fiber",
            "logic_processor_press", "fluix_crystal",
            "pattern_provider", "fluix_glass_cable", "network_tool", "molecular_assembler", "drive", "storage_bus",
            "1k_crafting_storage", "energy_acceptor", "me_p2p_tunnel", "memory_card");
    /** Other mods' items the optional examples name, by mod; only a pack that requires the mod may name them. */
    private static final Map<String, Set<String>> MOD_ITEMS = Map.ofEntries(
            Map.entry("mekanism", Set.of("crusher", "basic_energy_cube", "chemical_oxidizer", "enrichment_chamber",
                    "energized_smelter", "induction_casing", "induction_port", "basic_induction_cell",
                    "basic_induction_provider", "configurator")),
            Map.entry("appmek", Set.of("chemical_storage_cell_1k")),
            Map.entry("appflux", Set.of("flux_accessor", "fe_1k_cell", "induction_card")),
            Map.entry("advanced_ae", Set.of("quantum_core", "quantum_structure")),
            Map.entry("create", Set.of("crushing_wheel", "chute", "shaft", "cogwheel", "millstone")),
            Map.entry("createaddition", Set.of("electric_motor")),
            Map.entry("enderio", Set.of("sag_mill", "basic_capacitor")),
            Map.entry("industrialforegoing", Set.of("resourceful_furnace")),
            Map.entry("extendedae", Set.of("assembler_matrix_frame")),
            Map.entry("extendedae_plus", Set.of("super_assembler_matrix_frame")),
            Map.entry("ae2lt", Set.of("tianshu_supercomputer_controller", "tianshu_supercomputer_port",
                    "matter_warping_matrix_controller", "matter_warping_matrix_port")),
            Map.entry("data_energistics", Set.of("me_solar_panel", "astronomical_observatory", "digital_storage_cell_1k")),
            Map.entry("neoecoae", Set.of("storage_system_l4", "eco_drive", "eco_item_storage_cell_16m",
                    "storage_interface", "crafting_system_l4", "crafting_pattern_bus", "crafting_interface",
                    "computation_system_l4", "computation_drive", "eco_computation_cell_l4", "computation_interface")),
            Map.entry("molecularmanipulator", Set.of("matter_fabrication_controller",
                    "matter_fabrication_pattern_assembly")),
            Map.entry("useless_mod", Set.of("advanced_alloy_furnace_block")));
    /** Other mods' guide pages the optional examples link to, by mod; only a pack that requires the mod may. */
    private static final Map<String, Set<String>> MOD_PAGES = Map.ofEntries(
            Map.entry("appflux", Set.of("appflux/flux_accessor.md", "appflux/flux_cells.md")),
            Map.entry("advanced_ae", Set.of("aae_intro/quantum_computer.md")),
            Map.entry("extendedae", Set.of("epp_intro/assembler_matrix.md")),
            Map.entry("extendedae_plus", Set.of("introduction/devices/super_assembler_matrix.md")),
            Map.entry("ae2lt", Set.of("tianshu/construction.md", "matrix/construction.md")),
            Map.entry("data_energistics", Set.of("items-blocks-machines/6.1_me_solar_panel.md",
                    "items-blocks-machines/6.12_astronomical_observatories.md")),
            Map.entry("neoecoae", Set.of("neoecoae_intro/storage_system.md", "neoecoae_intro/crafting_system.md",
                    "neoecoae_intro/computation_system.md")),
            Map.entry("molecularmanipulator", Set.of("items-blocks-machines/matter_fabrication_well.md",
                    "items-blocks-machines/matter_fabrication_research.md",
                    "items-blocks-machines/matter_fabrication_pattern_assembly.md")));
    private static final Pattern LINK = Pattern.compile("]\\(([^)#]+)(#[^)]*)?\\)");
    private static final Pattern STRUCTURE = Pattern.compile("<ImportStructure src=\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("(?:id=\"|icon: |^- )([a-z0-9_]+):([a-z0-9_/.]+)", Pattern.MULTILINE);

    @Test
    void everyPageHasAChineseCopyAndNoChinesePageIsOrphaned() throws IOException {
        for (var guide : guides()) {
            assertEquals(pages(guide.root()), pages(guide.root().resolve("_zh_cn")), guide.root().toString());
            if (guide.root() != GUIDE) {
                assertTrue(pages(guide.root()).stream().noneMatch(pages(GUIDE)::contains),
                        guide.root() + " must not replace a base page");
            }
        }
        assertTrue(pages(GUIDE).containsAll(Set.of("index.md", "getting-started.md", "mechanics.md",
                "remote-processing.md", "troubleshooting.md")));
    }

    /** Each optional pack is registered with the mods it needs, has its pack metadata, and names itself in both languages. */
    @Test
    void optionalPacksMatchTheirFolders() throws IOException {
        var folders = new TreeSet<String>();
        try (Stream<Path> packs = Files.list(RESOURCES.resolve("resourcepacks"))) {
            packs.forEach(pack -> folders.add(pack.getFileName().toString()));
        }
        assertEquals(folders, GuideExamplePacks.ALL.stream().map(GuideExamplePacks.Pack::id)
                .collect(Collectors.toCollection(TreeSet::new)));
        for (var pack : GuideExamplePacks.ALL) {
            assertTrue(!pack.requiredMods().isEmpty(), pack.id());
            for (var missing : pack.requiredMods()) {
                assertTrue(!GuideExamplePacks.active(mod -> !mod.equals(missing)).contains(pack),
                        pack.id() + " must stay off without " + missing);
            }
            var meta = Files.readString(RESOURCES.resolve(pack.path()).resolve("pack.mcmeta"));
            assertTrue(meta.contains("\"pack_format\": 34"), pack.id());
            // The pack screen shows the description under the translated name, so it is translated too; vanilla's
            // own bundle data pack writes its description the same way.
            assertTrue(meta.contains("\"translate\": \"" + pack.nameKey() + ".description\""), pack.id());
            for (var language : List.of("en_us", "zh_cn")) {
                var lang = Files.readString(RESOURCES.resolve("assets/ae2federation/lang/" + language + ".json"));
                assertTrue(lang.contains("\"" + pack.nameKey() + "\": "), language + ": " + pack.nameKey());
                assertTrue(lang.contains("\"" + pack.nameKey() + ".description\": "), language + ": " + pack.id());
            }
        }
        assertEquals(List.of(), GuideExamplePacks.active(mod -> false));
        var groups = new HashSet<String>();
        assertEquals(GuideExamplePacks.ALL.stream().filter(pack -> pack.group().isEmpty() || groups.add(pack.group()))
                .toList(), GuideExamplePacks.active(mod -> true));
    }

    /** The variants of one example show the same pages, so the guide lists the same example whichever is active. */
    @Test
    void packsOfOneGroupShowTheSamePages() throws IOException {
        var pagesByGroup = new TreeMap<String, Set<String>>();
        for (var pack : GuideExamplePacks.ALL) {
            if (pack.group().isEmpty()) continue;
            var root = RESOURCES.resolve(pack.path()).resolve("assets/ae2federation/ae2guide");
            var pages = new TreeSet<>(pages(root));
            pages(root.resolve("_zh_cn")).forEach(page -> pages.add("_zh_cn/" + page));
            var first = pagesByGroup.putIfAbsent(pack.group(), pages);
            assertEquals(first == null ? pages : first, pages, pack.id() + " must show its group's pages");
        }
    }

    /**
     * Every page hangs under the Federation entry, directly or through another page: the examples hang under the
     * examples page. GuideME resolves a parent against the namespace root, not relative to the page.
     */
    @Test
    void pagesHangUnderTheFederationEntryAndChineseCopiesKeepTheirPlace() throws IOException {
        for (var guide : guides()) {
            var pages = new TreeSet<>(pages(guide.root()));
            pages.addAll(pages(GUIDE));
            for (var page : pages(guide.root())) {
                var english = frontmatter(guide.root().resolve(page));
                var chinese = frontmatter(guide.root().resolve("_zh_cn").resolve(page));
                assertTrue(english.contains("navigation:"), page);
                assertEquals(page.equals("index.md"), !english.contains("parent:"), page);
                var ancestor = page;
                for (int depth = 0; !ancestor.equals("index.md"); depth++) {
                    var parent = Pattern.compile("(?m)^  parent: (\\S+)$").matcher(frontmatter(guide.find(ancestor)));
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
    }

    @Test
    void linksPointAtPagesThatExist() throws IOException {
        for (var guide : guides()) {
            for (var root : List.of(guide.root(), guide.root().resolve("_zh_cn"))) {
                for (var page : pages(root)) {
                    var links = LINK.matcher(Files.readString(root.resolve(page)));
                    while (links.find()) {
                        var target = links.group(1);
                        var qualified = Pattern.compile("([a-z0-9_]+):(.+)").matcher(target);
                        if (qualified.matches()) {
                            var known = qualified.group(1).equals("ae2") ? AE2_PAGES
                                    : guide.mods().contains(qualified.group(1))
                                            ? MOD_PAGES.getOrDefault(qualified.group(1), Set.of()) : Set.<String>of();
                            assertTrue(known.contains(qualified.group(2)), page + " -> " + target);
                        } else {
                            // Page ids are the English paths, so a Chinese copy links exactly as the English page does.
                            var resolved = guide.root().resolve(page).getParent().resolve(target).normalize();
                            assertTrue(resolved.startsWith(guide.root())
                                    && Files.isRegularFile(guide.find(guide.root().relativize(resolved).toString())),
                                    page + " -> " + target);
                        }
                    }
                }
            }
        }
    }

    @Test
    void itemIdsAreQualifiedAndRegistered() throws IOException {
        for (var guide : guides()) {
            for (var root : List.of(guide.root(), guide.root().resolve("_zh_cn"))) {
                for (var page : pages(root)) {
                    var text = Files.readString(root.resolve(page));
                    assertTrue(!Pattern.compile("id=\"[a-z0-9_]+\"").matcher(text).find(),
                            page + ": write ids with their namespace; a bare id would resolve against this page's");
                    var ids = ID.matcher(text);
                    while (ids.find()) {
                        var known = switch (ids.group(1)) {
                            case "ae2federation" -> FEDERATION_ITEMS;
                            case "ae2" -> AE2_ITEMS;
                            default -> guide.mods().contains(ids.group(1))
                                    ? MOD_ITEMS.getOrDefault(ids.group(1), Set.of()) : Set.<String>of();
                        };
                        assertTrue(known.contains(ids.group(2)), page + ": " + ids.group());
                    }
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
        for (var page : guides().stream().map(guide -> guide.root().resolve("_zh_cn")).flatMap(GuidePagesContractTest::files)
                .toList()) {
            var text = Files.readString(page).replace("\r\n", "\n");
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
            var items = Pattern.compile("(?m)^- ae2federation:([a-z0-9_]+)$").matcher(frontmatter);
            while (items.find()) assertEquals(null, owners.put(items.group(1), page), items.group(1));
        }
        assertEquals(new TreeSet<>(FEDERATION_ITEMS), new TreeSet<>(owners.keySet()));
    }

    @Test
    void structureScenesResolveAndEveryStructureIsShown() throws IOException {
        for (var guide : guides()) {
            var used = new TreeSet<Path>();
            for (var root : List.of(guide.root(), guide.root().resolve("_zh_cn"))) {
                for (var page : pages(root)) {
                    var sources = STRUCTURE.matcher(Files.readString(root.resolve(page)));
                    while (sources.find()) {
                        // Resolved against the page id, which is the English path for a Chinese copy too. A scene
                        // stays in its own pack, so a page never shows blocks of a mod it does not require.
                        var resolved = guide.root().resolve(page).getParent().resolve(sources.group(1)).normalize();
                        assertTrue(Files.isRegularFile(resolved) && resolved.startsWith(guide.root().resolve("assets")),
                                page + " -> " + sources.group(1));
                        used.add(resolved);
                    }
                }
            }
            assertEquals(structures(guide.root()), used);
        }
    }

    /** GuideME rejects a whole scene when a block's state is missing from the palette, and only says so in a client. */
    @Test
    void everyStructureBlockStateIsInItsPalette() throws IOException {
        for (var guide : guides()) {
            var namespaces = new HashSet<>(BASE_NAMESPACES);
            namespaces.addAll(guide.mods());
            for (var structure : structures(guide.root())) {
                var text = Files.readString(structure);
                var paletteStart = text.indexOf("palette: [");
                assertTrue(text.contains("DataVersion: ") && paletteStart > 0, structure.toString());
                var palette = new TreeSet<String>();
                var entries = Pattern.compile("\"([^\"]+)\"").matcher(text.substring(paletteStart));
                while (entries.find()) palette.add(entries.group(1));
                for (var state : palette) {
                    assertTrue(namespaces.contains(state.substring(0, state.indexOf(':'))),
                            structure.getFileName() + " needs a mod its guide does not require: " + state);
                }
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

    /**
     * Scenes use AE2's smart cables, which show each side's channels, and every cable records them. The compatibility
     * test {@code guideSceneCables} checks the counts against AE2's own in a server.
     */
    @Test
    void sceneCablesAreSmartCablesWithTheirChannels() throws IOException {
        var cable = Pattern.compile("cable: \\{id: \"ae2:(\\w+)\", visual: \\{([^}]*)}");
        for (var guide : guides()) {
            for (var structure : structures(guide.root())) {
                var cables = cable.matcher(Files.readString(structure));
                while (cables.find()) {
                    assertTrue(cables.group(1).endsWith("_smart_cable"),
                            structure.getFileName() + " has a " + cables.group(1));
                    var connections = Pattern.compile("\"(\\w+)\"").matcher(cables.group(2));
                    while (connections.find()) {
                        var side = connections.group(1);
                        assertTrue(cables.group(2).contains("channels" + Character.toUpperCase(side.charAt(0))
                                + side.substring(1) + ": "), structure.getFileName() + ": no channel count toward "
                                + side + " in {" + cables.group(2) + "}");
                    }
                }
            }
        }
    }

    /**
     * GuideME shows a broken {@code <FederationTopology>} only as an error on the opened page. Each diagram must draw,
     * and a Chinese copy's diagram may differ from the English one only in its labels and details.
     */
    @Test
    void topologyDiagramsDrawAndChineseCopiesShowTheSameNetworks() throws IOException {
        var diagrams = 0;
        for (var guide : guides()) {
            for (var page : pages(guide.root())) {
                var english = topologies(guide.root().resolve(page));
                var chinese = topologies(guide.root().resolve("_zh_cn").resolve(page));
                assertEquals(english.size(), chinese.size(), page + ": the Chinese copy's diagrams");
                for (int index = 0; index < english.size(); index++) {
                    for (var parsed : List.of(english.get(index), chinese.get(index))) {
                        assertTrue(parsed.problems().isEmpty(), page + ": " + parsed.problems());
                    }
                    assertEquals(withoutText(english.get(index).diagram()), withoutText(chinese.get(index).diagram()),
                            page + ": the Chinese copy's diagram " + index);
                    for (var parsed : List.of(english.get(index), chinese.get(index))) {
                        for (int width : TopologyDiagramLayoutTest.PAGE_WIDTHS) {
                            TopologyDiagramLayoutTest.assertApartAndOnCanvas(TopologyDiagramLayout.of(parsed.diagram(),
                                    width, GuidePagesContractTest::fontWidth, "Storage", "Crafting", "Energy"),
                                    page + " at width " + width);
                        }
                    }
                    diagrams++;
                }
            }
        }
        assertTrue(diagrams > 0, "No page draws a topology diagram");
    }

    private static final Pattern TOPOLOGY = Pattern.compile("<FederationTopology>(.*?)</FederationTopology>", Pattern.DOTALL);
    private static final Pattern TOPOLOGY_ELEMENT = Pattern.compile("<(\\w+)((?:\\s+\\w+=\"[^\"]*\")*)\\s*/>");
    private static final Pattern ATTRIBUTE = Pattern.compile("(\\w+)=\"([^\"]*)\"");

    private static List<TopologyDiagram.Parsed> topologies(Path page) throws IOException {
        var parsed = new ArrayList<TopologyDiagram.Parsed>();
        var blocks = TOPOLOGY.matcher(Files.readString(page));
        while (blocks.find()) {
            var elements = new ArrayList<TopologyDiagram.Element>();
            var body = blocks.group(1);
            var tags = TOPOLOGY_ELEMENT.matcher(body);
            var rest = new StringBuilder();
            while (tags.find()) {
                tags.appendReplacement(rest, "");
                var attributes = new java.util.LinkedHashMap<String, String>();
                var pairs = ATTRIBUTE.matcher(tags.group(2));
                while (pairs.find()) attributes.put(pairs.group(1), pairs.group(2));
                elements.add(new TopologyDiagram.Element(tags.group(1), attributes));
            }
            tags.appendTail(rest);
            assertTrue(rest.toString().isBlank(), page + ": only self-closing tags go inside a topology: " + rest);
            parsed.add(TopologyDiagram.parse(elements));
        }
        return parsed;
    }

    /** The diagram with every network's, Endpoint's and domain's label and details left out. */
    private static TopologyDiagram withoutText(TopologyDiagram diagram) {
        return new TopologyDiagram(diagram.networks().stream().map(network -> new TopologyDiagram.Network(network.key(),
                "", network.color(), network.column(), network.row(), List.of())).toList(), diagram.rules(),
                diagram.energy(), diagram.endpoints().stream().map(endpoint -> new TopologyDiagram.Endpoint(endpoint.key(),
                        "", endpoint.owner(), endpoint.energy(), List.of())).toList(),
                diagram.domains().stream().map(domain -> new TopologyDiagram.Domain(domain.key(), "", domain.networks(),
                        domain.opened())).toList());
    }

    /** Close to Minecraft's font: advances of its narrow ASCII glyphs, 6 for the rest, 9 for CJK from Unifont. */
    private static int fontWidth(String text) {
        return text.codePoints().map(point -> {
            if (point >= 0x2E80) return 9;
            if ("i!.,:;|'·".indexOf(point) >= 0) return 2;
            if ("l`".indexOf(point) >= 0) return 3;
            if ("It[] ".indexOf(point) >= 0) return 4;
            if ("fk<>\"*()".indexOf(point) >= 0) return 5;
            return 6;
        }).sum();
    }

    /** The base guide, then each optional pack's guide folder with the mods it requires. */
    private static List<Guide> guides() {
        var guides = new ArrayList<Guide>();
        guides.add(new Guide(GUIDE, Set.of()));
        for (var pack : GuideExamplePacks.ALL) {
            guides.add(new Guide(RESOURCES.resolve(pack.path()).resolve("assets/ae2federation/ae2guide"),
                    Set.copyOf(pack.requiredMods())));
        }
        return guides;
    }

    private record Guide(Path root, Set<String> mods) {
        /** A page of this guide, or of the base guide that it builds on. */
        Path find(String page) {
            var own = root.resolve(page);
            return Files.isRegularFile(own) ? own : GUIDE.resolve(page);
        }
    }

    private static Set<String> pages(Path root) throws IOException {
        var pages = new TreeSet<String>();
        for (var path : files(root).toList()) pages.add(root.relativize(path).toString().replace('\\', '/'));
        return pages;
    }

    /** The Markdown pages under {@code root}, without the Chinese copies unless {@code root} is their folder. */
    private static Stream<Path> files(Path root) {
        if (!Files.isDirectory(root)) return Stream.of();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> root.endsWith("_zh_cn") || !path.startsWith(root.resolve("_zh_cn")))
                    .toList().stream();
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Set<Path> structures(Path root) throws IOException {
        if (!Files.isDirectory(root.resolve("assets"))) return Set.of();
        try (Stream<Path> files = Files.walk(root.resolve("assets"))) {
            return files.filter(path -> path.toString().endsWith(".snbt")).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static String frontmatter(Path page) throws IOException {
        var text = Files.readString(page).replace("\r\n", "\n");
        assertTrue(text.startsWith("---\n"), page.toString());
        return text.substring(4, text.indexOf("\n---\n", 4) + 1);
    }
}
