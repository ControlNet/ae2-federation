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
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Energy;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Network;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Rule;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.State;
import space.controlnet.ae2federation.client.guide.TopologyDiagramLayout.Rect;

class TopologyDiagramLayoutTest {
    /** Six pixels a character, close to Minecraft's font. */
    private static int width(String text) {
        return text.length() * 6;
    }

    private static TopologyDiagramLayout lay(TopologyDiagram diagram, int width) {
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

    @Test
    void problemsNameWhatIsWrong() {
        var diagram = new TopologyDiagram(List.of(network("a", 0, 0), network("a", 0, 0)),
                List.of(new Rule("a", "c", Capability.STORAGE, State.ACTIVE)), List.of(new Energy("a", "a")));
        var problems = String.join("\n", diagram.problems());
        assertTrue(problems.contains("\"a\" is used twice"), problems);
        assertTrue(problems.contains("shares its cell"), problems);
        assertTrue(problems.contains("unknown network \"c\""), problems);
        assertTrue(problems.contains("to itself"), problems);
        assertTrue(new TopologyDiagram(List.of(network("a", 0, 0), network("b", 1, 0)),
                List.of(new Rule("a", "b", Capability.CRAFTING, State.ACTIVE)), List.of()).problems().isEmpty());
    }
}
