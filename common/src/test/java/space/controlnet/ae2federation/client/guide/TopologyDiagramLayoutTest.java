package space.controlnet.ae2federation.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Capability;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Endpoint;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Energy;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Network;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Rule;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.State;
import space.controlnet.ae2federation.client.guide.TopologyDiagramLayout.Rect;

public class TopologyDiagramLayoutTest {
    /** Six pixels a character, close to Minecraft's font. */
    private static int width(String text) {
        return text.length() * 6;
    }

    /** GuideME's page: a 320-pixel screen less its navigation bar and scrollbar, up to its 420-pixel cap. */
    public static final int[] PAGE_WIDTHS = {280, 420};

    static TopologyDiagramLayout lay(TopologyDiagram diagram, int width) {
        return TopologyDiagramLayout.of(diagram, width, TopologyDiagramLayoutTest::width, "Storage", "Crafting", "Energy");
    }

    private static Network network(String key, int column, int row) {
        return new Network(key, "Network " + key.toUpperCase(), 0x915dcd, column, row, List.of("Crafting CPU"));
    }

    @Test
    void eachDirectionGetsALabelPointingAtItsUser() {
        var diagram = new TopologyDiagram(List.of(network("a", 0, 0), network("b", 1, 0)), List.of(
                new Rule("a", "b", Capability.CRAFTING, State.ACTIVE), new Rule("a", "b", Capability.STORAGE, State.ACTIVE),
                new Rule("b", "a", Capability.STORAGE, State.WAITING)), List.of(new Energy("b", "a")));
        var layout = lay(diagram, 300);
        assertEquals(1, layout.links().size(), "One link per pair");
        for (var card : layout.cards()) {
            // The title starts after the accent square and keeps a margin: 16 pixels beside its text.
            assertTrue(card.rect().width() >= width(card.network().label()) + 16, "Room for " + card.network().label());
        }
        var link = layout.links().getFirst();
        assertNotNull(link.energyChip(), "The pair shares energy");
        assertEquals(2, link.labels().size());
        var forA = link.labels().get(0);
        var forB = link.labels().get(1);
        assertEquals(-1, forA.capX(), "A is on the left, so its label points left");
        assertEquals(1, forB.capX(), "B is on the right, so its label points right");
        assertEquals(List.of(Capability.STORAGE, Capability.CRAFTING),
                forA.chips().stream().map(chip -> chip.rule().capability()).toList(), "Chips in capability order");
        assertFalse(forA.rect().intersects(forB.rect()), "The two directions' labels stand apart");
        assertFalse(forA.rect().intersects(link.energyChip()) || forB.rect().intersects(link.energyChip()),
                "Labels stand off the energy chip");
    }

    @Test
    void nothingOverlapsACardAndEverythingStaysOnTheCanvas() {
        var networks = List.of(network("main", 1, 0), network("warehouse", 0, 0), network("factory", 2, 0),
                network("power", 1, 1));
        var diagram = new TopologyDiagram(networks, List.of(
                new Rule("main", "warehouse", Capability.STORAGE, State.ACTIVE),
                new Rule("main", "factory", Capability.CRAFTING, State.ACTIVE),
                new Rule("main", "factory", Capability.STORAGE, State.ACTIVE)),
                List.of(new Energy("main", "power"), new Energy("warehouse", "main"), new Energy("factory", "main")));
        for (int width : new int[] {220, 300, 420}) {
            var layout = lay(diagram, width);
            var canvas = new Rect(0, 0, layout.width(), layout.legendY());
            var drawn = new ArrayList<Rect>();
            for (var link : layout.links()) {
                if (link.energyChip() != null) drawn.add(link.energyChip());
                link.labels().forEach(label -> drawn.add(label.rect()));
            }
            for (var rect : drawn) {
                assertTrue(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.width() <= canvas.width()
                        && rect.y() + rect.height() <= canvas.height(), "On the canvas at width " + width + ": " + rect);
                for (var card : layout.cards()) {
                    assertFalse(rect.intersects(card.rect()), "Off the cards at width " + width + ": " + rect);
                }
            }
            for (var card : layout.cards()) {
                assertTrue(card.rect().x() >= 0 && card.rect().x() + card.rect().width() <= width,
                        "Card inside the width " + width);
            }
        }
    }

    @Test
    void aPairWithOnlyEnergyHasNoLabels() {
        var layout = lay(new TopologyDiagram(List.of(network("a", 0, 0), network("b", 0, 1)), List.of(),
                List.of(new Energy("a", "b"))), 300);
        var link = layout.links().getFirst();
        assertTrue(link.labels().isEmpty());
        assertNotNull(link.energyChip());
    }

    @Test
    void stackedNetworksPointUpAndDown() {
        var layout = lay(new TopologyDiagram(List.of(network("a", 0, 0), network("b", 0, 1)),
                List.of(new Rule("b", "a", Capability.STORAGE, State.ACTIVE)), List.of()), 300);
        var label = layout.links().getFirst().labels().getFirst();
        assertEquals(0, label.capX());
        assertEquals(1, label.capY(), "B is below, so its label points down");
        assertNull(layout.links().getFirst().energyChip());
    }

    private static Endpoint endpoint(String key, String owner) {
        return new Endpoint(key, "Endpoint · " + key, owner, true, List.of("Furnace"));
    }

    /** Every card, Endpoint node and label of {@code layout} inside its canvas, none covering another. */
    public static void assertApartAndOnCanvas(TopologyDiagramLayout layout, String where) {
        var drawn = new ArrayList<Rect>();
        layout.cards().forEach(card -> drawn.add(card.rect()));
        layout.endpoints().forEach(node -> drawn.add(node.rect()));
        for (var link : layout.links()) link.labels().forEach(label -> drawn.add(label.rect()));
        for (int index = 0; index < drawn.size(); index++) {
            var rect = drawn.get(index);
            assertTrue(rect.x() >= 0 && rect.y() >= 0 && rect.x() + rect.width() <= layout.width()
                    && rect.y() + rect.height() <= layout.legendY(), "On the canvas " + where + ": " + rect);
            for (int other = index + 1; other < drawn.size(); other++) {
                assertFalse(rect.intersects(drawn.get(other)), "Apart " + where + ": " + rect + " and " + drawn.get(other));
            }
        }
    }

    @Test
    void endpointsHangUnderALoneNetworkWiredToIt() {
        var diagram = new TopologyDiagram(List.of(network("main", 0, 0)), List.of(), List.of(),
                List.of(endpoint("first", "main"), endpoint("second", "main")));
        for (int width : PAGE_WIDTHS) {
            var layout = lay(diagram, width);
            assertApartAndOnCanvas(layout, "at width " + width);
            var card = layout.cards().getFirst().rect();
            assertEquals(2, layout.endpoints().size());
            for (var node : layout.endpoints()) {
                assertTrue(node.rect().y() >= card.y() + card.height(), "Below the lone card: " + node.rect());
                assertEquals(card.y() + card.height(), node.link().fromY(), 0.01f, "The wire leaves the card's bottom");
                assertEquals(node.rect().y(), node.link().toY(), 0.01f, "The wire reaches the node's top");
                assertTrue(node.rect().width() >= width(node.endpoint().label()) + 18, "Room for the dot and the label");
            }
        }
    }

    @Test
    void endpointsOfTwoNetworksStandOnTheirOuterSidesOrUnderThem() {
        var diagram = new TopologyDiagram(List.of(network("a", 0, 0), network("b", 1, 0)),
                List.of(new Rule("a", "b", Capability.CRAFTING, State.ACTIVE), new Rule("a", "b", Capability.STORAGE, State.ACTIVE)),
                List.of(new Energy("a", "b")), List.of(endpoint("furnace", "b"), endpoint("mine", "a")));
        for (int width : PAGE_WIDTHS) {
            var layout = lay(diagram, width);
            assertApartAndOnCanvas(layout, "at width " + width);
            for (var node : layout.endpoints()) {
                var owner = layout.cards().stream().filter(card -> card.network().key().equals(node.endpoint().owner()))
                        .findFirst().orElseThrow().rect();
                assertTrue(node.rect().y() >= owner.y() + owner.height() && node.rect().x() < owner.x() + owner.width()
                        && owner.x() < node.rect().x() + node.rect().width(), "Too narrow beside the cards: under its own");
            }
        }
        // Where there is room, as on the topology screen, each network's Endpoints stand on its outer side.
        var wide = lay(diagram, 700);
        var a = wide.cards().get(0).rect();
        var b = wide.cards().get(1).rect();
        for (var node : wide.endpoints()) {
            if (node.endpoint().owner().equals("b")) {
                assertTrue(node.rect().x() >= b.x() + b.width(), "B's Endpoints on its outer, right side: " + node.rect());
            } else {
                assertTrue(node.rect().x() + node.rect().width() <= a.x(), "A's Endpoint on its outer, left side");
            }
        }
    }

    @Test
    void problemsNameWhatIsWrong() {
        var diagram = new TopologyDiagram(List.of(network("a", 0, 0), network("a", 0, 0)),
                List.of(new Rule("a", "c", Capability.STORAGE, State.ACTIVE)), List.of(new Energy("a", "a")));
        var problems = String.join("\n", diagram.problems());
        assertTrue(problems.contains("\"a\" is used twice"), problems);
        assertTrue(problems.contains("shares its cell"), problems);
        assertTrue(problems.contains("unknown network \"c\""), problems);
        assertTrue(problems.contains("to itself"), problems);
        var endpoints = String.join("\n", new TopologyDiagram(List.of(network("a", 0, 0)), List.of(), List.of(),
                List.of(endpoint("a", "a"), endpoint("e", "z"))).problems());
        assertTrue(endpoints.contains("\"a\" is used twice"), endpoints);
        assertTrue(endpoints.contains("Endpoint \"e\" names the unknown network \"z\""), endpoints);
        assertTrue(new TopologyDiagram(List.of(network("a", 0, 0), network("b", 1, 0)),
                List.of(new Rule("a", "b", Capability.CRAFTING, State.ACTIVE)), List.of()).problems().isEmpty());
    }
}
