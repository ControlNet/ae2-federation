package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DomainClusterLayoutTest {
    private static final float WIDTH = 200;
    private static final float HEIGHT = 88;
    private static final String OPENED = "";
    /** Every pair's labels as one box this size around the link's middle. */
    private static final DomainClusterLayout.LabelExtent LABELS = (a, b, link) -> new float[] {34, 14};

    private static DomainClusterLayout.Card card(String id, String... domains) {
        return new DomainClusterLayout.Card(id, Set.of(domains));
    }

    private static DomainClusterLayout.Link link(String a, String b) {
        return new DomainClusterLayout.Link(a, b);
    }

    // Two domains sharing network C.
    private static final List<DomainClusterLayout.Card> TWO_CARDS = List.of(card("A", OPENED), card("B", OPENED),
            card("C", OPENED, "d1"), card("E", "d1"), card("F", "d1"));
    private static final List<DomainClusterLayout.Link> TWO_LINKS = List.of(link("A", "B"), link("B", "C"), link("A", "C"),
            link("C", "E"), link("E", "F"));

    // Four domains: S is in the opened domain, d1 and d2; T joins d1 to d3, two hops out.
    private static final List<DomainClusterLayout.Card> FOUR_CARDS = List.of(card("A", OPENED), card("B", OPENED),
            card("S", OPENED, "d1", "d2"), card("D", "d1"), card("E", "d1"), card("T", "d1", "d3"), card("G", "d2"),
            card("H", "d3"), card("J", "d3"));
    private static final List<DomainClusterLayout.Link> FOUR_LINKS = List.of(link("A", "B"), link("A", "S"), link("B", "S"),
            link("S", "D"), link("D", "E"), link("E", "T"), link("S", "G"), link("T", "H"), link("H", "J"), link("T", "J"));

    // Six domains with a cycle (opened, d1, d2) and a domain three hops out. d3's J comes before H on purpose: its
    // rules run T, H, J, X.
    private static final List<DomainClusterLayout.Card> SIX_CARDS = List.of(card("A", OPENED), card("B", OPENED),
            card("P", OPENED, "d1"), card("Q", OPENED, "d2"), card("D", "d1"), card("E", "d1"), card("T", "d1", "d3"),
            card("U", "d1", "d2"), card("G", "d2"), card("V", "d2", "d4"), card("J", "d3"), card("H", "d3"),
            card("X", "d3", "d5"), card("K", "d4"), card("M", "d5"), card("N", "d5"));
    private static final List<DomainClusterLayout.Link> SIX_LINKS = List.of(link("A", "B"), link("A", "P"), link("B", "Q"),
            link("P", "Q"), link("P", "D"), link("D", "E"), link("E", "T"), link("P", "U"), link("Q", "G"), link("G", "U"),
            link("G", "V"), link("T", "H"), link("H", "J"), link("J", "X"), link("V", "K"), link("X", "M"), link("M", "N"));

    // Bridge domain n holds only networks of the opened domain, as a Bridge between two of its networks does; e and f
    // go on from S. m holds only N, which e holds too.
    private static final List<DomainClusterLayout.Card> NESTED_CARDS = List.of(card("A", OPENED, "n"),
            card("S", OPENED, "n", "e"), card("N", "e", "f", "m"), card("F", "f"));
    private static final List<DomainClusterLayout.Link> NESTED_LINKS = List.of(link("A", "S"), link("S", "N"), link("N", "F"));

    private static DomainClusterLayout.Result place(List<DomainClusterLayout.Card> cards, List<DomainClusterLayout.Link> links) {
        return DomainClusterLayout.place(cards, OPENED, links, LABELS, WIDTH, HEIGHT);
    }

    private static float centreX(DomainClusterLayout.Result result, String id) {
        return result.corners().get(id)[0] + WIDTH / 2;
    }

    private static float centreY(DomainClusterLayout.Result result, String id) {
        return result.corners().get(id)[1] + HEIGHT / 2;
    }

    @Test
    void domainsStandInColumnsByHowManyHopsTheyAreFromTheOpenedOne() {
        var result = place(SIX_CARDS, SIX_LINKS);
        var centres = result.centres();
        assertTrue(centres.get(OPENED)[0] < centres.get("d1")[0]);
        assertEquals(centres.get("d1")[0], centres.get("d2")[0], 0.01f);
        assertTrue(centres.get("d2")[0] < centres.get("d3")[0]);
        assertEquals(centres.get("d3")[0], centres.get("d4")[0], 0.01f);
        assertTrue(centres.get("d4")[0] < centres.get("d5")[0]);
    }

    @Test
    void aSharedNetworkSitsAtTheCentreOfItsDomains() {
        var result = place(FOUR_CARDS, FOUR_LINKS);
        var centres = result.centres();
        float x = (centres.get(OPENED)[0] + centres.get("d1")[0] + centres.get("d2")[0]) / 3;
        float y = (centres.get(OPENED)[1] + centres.get("d1")[1] + centres.get("d2")[1]) / 3;
        assertEquals(x, centreX(result, "S"), 0.01f);
        assertEquals(y, centreY(result, "S"), 0.01f);
    }

    @Test
    void aDomainsOwnNetworksFaceAwayFromTheDomainsItShares() {
        var result = place(TWO_CARDS, TWO_LINKS);
        float shared = centreX(result, "C");
        assertTrue(centreX(result, "A") < shared && centreX(result, "B") < shared);
        assertTrue(centreX(result, "E") > shared && centreX(result, "F") > shared);
    }

    @Test
    void aDomainBesideItsNeighbourKeepsItsRow() {
        // d3 hangs off d1 only, so it stays level with d1 rather than centring on the canvas.
        var centres = place(FOUR_CARDS, FOUR_LINKS).centres();
        assertEquals(centres.get("d1")[1], centres.get("d3")[1], 0.01f);
    }

    @Test
    void ownNetworksFollowTheirRulesAlongTheArcSoLinksNeitherCrossNorPassOverCards() {
        // d3's own networks stand T, H, J, X along its arc, as its rules run, though J is listed before H; in list
        // order T's link to H would cross J's link to X.
        var result = place(SIX_CARDS, SIX_LINKS);
        for (var path : SIX_LINKS) {
            var through = crossedCard(result, path);
            assertTrue(through == null, path + " runs through " + through);
        }
        for (int first = 0; first < SIX_LINKS.size(); first++) {
            for (int second = first + 1; second < SIX_LINKS.size(); second++) {
                var a = SIX_LINKS.get(first);
                var b = SIX_LINKS.get(second);
                if (a.a().equals(b.a()) || a.a().equals(b.b()) || a.b().equals(b.a()) || a.b().equals(b.b())) continue;
                assertFalse(cross(result, a, b), a + " crosses " + b);
            }
        }
    }

    @Test
    void nothingOverlapsAndPlatesKeepToTheirDomains() {
        for (var sample : List.of(List.of(TWO_CARDS, TWO_LINKS), List.of(FOUR_CARDS, FOUR_LINKS), List.of(SIX_CARDS, SIX_LINKS))) {
            @SuppressWarnings("unchecked")
            var cards = (List<DomainClusterLayout.Card>) sample.get(0);
            @SuppressWarnings("unchecked")
            var links = (List<DomainClusterLayout.Link>) sample.get(1);
            var result = place(cards, links);
            assertTrue(DomainClusterLayout.problems(cards, OPENED, links, LABELS, result.corners(), WIDTH, HEIGHT).isEmpty(),
                    () -> DomainClusterLayout.problems(cards, OPENED, links, LABELS, result.corners(), WIDTH, HEIGHT).toString());
            // Spelled out: no two cards touch, and no card sits on a plate of a domain it is not in.
            var ids = new ArrayList<>(result.corners().keySet());
            for (int i = 0; i < ids.size(); i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    var a = result.corners().get(ids.get(i));
                    var b = result.corners().get(ids.get(j));
                    assertFalse(a[0] < b[0] + WIDTH && b[0] < a[0] + WIDTH && a[1] < b[1] + HEIGHT && b[1] < a[1] + HEIGHT,
                            ids.get(i) + " overlaps " + ids.get(j));
                }
            }
            var plates = DomainClusterLayout.plates(cards, OPENED, result.corners(), WIDTH, HEIGHT);
            for (var card : cards) {
                var corner = result.corners().get(card.id());
                for (var plate : plates.entrySet()) {
                    if (card.domains().contains(plate.getKey())) continue;
                    assertFalse(DomainClusterLayout.inside(plate.getValue(), corner[0] + WIDTH / 2, corner[1] + HEIGHT / 2),
                            card.id() + " sits on the plate of " + plate.getKey());
                }
            }
        }
    }

    @Test
    void aDomainTheOpenedOneCannotReachStillGetsItsOwnColumn() {
        // The network joining d9 to the others was not sent (the related scope is capped), so d9 has no path.
        var cards = new ArrayList<>(TWO_CARDS);
        cards.add(card("Y", "d9"));
        cards.add(card("Z", "d9"));
        var links = new ArrayList<>(TWO_LINKS);
        links.add(link("Y", "Z"));
        var result = place(cards, links);
        assertTrue(result.centres().get("d9")[0] > result.centres().get("d1")[0]);
        assertTrue(DomainClusterLayout.problems(cards, OPENED, links, LABELS, result.corners(), WIDTH, HEIGHT).isEmpty());
    }

    @Test
    void aDomainWhoseNetworksAllBelongToAnotherSharesItsPlaceInsteadOfTakingAColumn() {
        assertEquals(Map.of("n", OPENED, "m", "e"), DomainClusterLayout.hosts(NESTED_CARDS, OPENED));
        // Two related domains with the same networks both sit in the domain that holds them, not one in the other.
        assertEquals(Map.of("n1", OPENED, "n2", OPENED),
                DomainClusterLayout.hosts(List.of(card("A", OPENED, "n1", "n2"), card("B", OPENED, "n1", "n2")), OPENED));
        var result = place(NESTED_CARDS, NESTED_LINKS);
        var without = place(List.of(card("A", OPENED), card("S", OPENED, "e"), card("N", "e", "f"), card("F", "f")),
                NESTED_LINKS);
        for (var id : List.of("A", "S", "N", "F")) {
            assertEquals(without.corners().get(id)[0], result.corners().get(id)[0], 0.01f, id);
            assertEquals(without.corners().get(id)[1], result.corners().get(id)[1], 0.01f, id);
        }
        assertEquals(result.centres().get(OPENED)[0], result.centres().get("n")[0], 0.01f);
        assertEquals(result.centres().get(OPENED)[1], result.centres().get("n")[1], 0.01f);
        assertEquals(result.centres().get("e")[0], result.centres().get("m")[0], 0.01f);
    }

    @Test
    void aNestedDomainsPlateSitsInsideItsHostsPlateWithRoomForItsName() {
        var result = place(NESTED_CARDS, NESTED_LINKS);
        assertTrue(DomainClusterLayout.problems(NESTED_CARDS, OPENED, NESTED_LINKS, LABELS, result.corners(), WIDTH, HEIGHT).isEmpty(),
                () -> DomainClusterLayout.problems(NESTED_CARDS, OPENED, NESTED_LINKS, LABELS, result.corners(), WIDTH, HEIGHT).toString());
        var plates = DomainClusterLayout.plates(NESTED_CARDS, OPENED, result.corners(), WIDTH, HEIGHT);
        for (var point : plates.get("n")) assertTrue(DomainClusterLayout.inside(plates.get(OPENED), point[0], point[1]));
        for (var point : plates.get("m")) assertTrue(DomainClusterLayout.inside(plates.get("e"), point[0], point[1]));
        // The nested plate's name goes in the strip between the host's top edge and the cards.
        float cardTop = Math.min(result.corners().get("A")[1], result.corners().get("S")[1]);
        float top = (float) plates.get("n").stream().mapToDouble(point -> point[1]).min().orElse(0);
        float hostTop = (float) plates.get(OPENED).stream().mapToDouble(point -> point[1]).min().orElse(0);
        assertEquals(cardTop - DomainClusterLayout.NESTED_PADDING, top, 0.5f);
        assertEquals(cardTop - DomainClusterLayout.PLATE_PADDING, hostTop, 0.5f);
    }

    @Test
    void theSameInputGivesTheSameLayout() {
        var first = place(SIX_CARDS, SIX_LINKS).corners();
        var second = place(SIX_CARDS, SIX_LINKS).corners();
        for (var entry : first.entrySet()) {
            assertEquals(entry.getValue()[0], second.get(entry.getKey())[0]);
            assertEquals(entry.getValue()[1], second.get(entry.getKey())[1]);
        }
    }

    @Test
    void aPlateSurroundsItsCardsWithARoundedMargin() {
        var corners = Map.of("A", new float[] {0, 0}, "B", new float[] {300, 120});
        var plate = DomainClusterLayout.plate(List.of(corners.get("A"), corners.get("B")), WIDTH, HEIGHT);
        float pad = DomainClusterLayout.PLATE_PADDING;
        // Every card corner, pushed out by almost the padding, is still on the plate.
        for (var corner : corners.values()) {
            for (float[] point : new float[][] {{corner[0] - pad + 1, corner[1] + 1}, {corner[0] + WIDTH + pad - 1, corner[1] + 1},
                    {corner[0] + 1, corner[1] - pad + 1}, {corner[0] + 1, corner[1] + HEIGHT + pad - 1}}) {
                assertTrue(DomainClusterLayout.inside(plate, point[0], point[1]));
            }
        }
        // A plate's corner is round: the box corner, a padding out on both axes, is off it.
        assertFalse(DomainClusterLayout.inside(plate, -pad + 1, -pad + 1));
        assertFalse(DomainClusterLayout.inside(plate, 600, 0));
    }

    @Test
    void aPlateFillsRowByRow() {
        var plate = DomainClusterLayout.plate(List.<float[]>of(new float[] {0, 0}), WIDTH, HEIGHT);
        var rows = DomainClusterLayout.rows(plate);
        float pad = DomainClusterLayout.PLATE_PADDING;
        assertEquals(Math.round(HEIGHT + 2 * pad), rows.size(), 1);
        // The middle row spans the card and the padding on both sides; the top row is shorter, as the corner is round.
        var middle = rows.get(rows.size() / 2);
        assertEquals(-pad, middle[1], 1);
        assertEquals(WIDTH + pad, middle[2], 1);
        assertTrue(rows.getFirst()[2] - rows.getFirst()[1] < middle[2] - middle[1]);
    }

    private static float[] curvePoints(DomainClusterLayout.Result result, DomainClusterLayout.Link path) {
        var a = result.corners().get(path.a());
        var b = result.corners().get(path.b());
        return TopologyLink.between(a[0], a[1], b[0], b[1], WIDTH, HEIGHT).curve().points(32);
    }

    /** Whether two links' curves cross, as polylines. */
    private static boolean cross(DomainClusterLayout.Result result, DomainClusterLayout.Link first, DomainClusterLayout.Link second) {
        var p = curvePoints(result, first);
        var q = curvePoints(result, second);
        for (int i = 0; i + 3 < p.length; i += 2) {
            for (int j = 0; j + 3 < q.length; j += 2) {
                if (segmentsCross(p[i], p[i + 1], p[i + 2], p[i + 3], q[j], q[j + 1], q[j + 2], q[j + 3])) return true;
            }
        }
        return false;
    }

    private static boolean segmentsCross(float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy) {
        float d1 = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        float d2 = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
        float d3 = (dx - cx) * (ay - cy) - (dy - cy) * (ax - cx);
        float d4 = (dx - cx) * (by - cy) - (dy - cy) * (bx - cx);
        return d1 * d2 < 0 && d3 * d4 < 0;
    }

    /** A card other than its ends that the link's curve passes through, or null. */
    private static String crossedCard(DomainClusterLayout.Result result, DomainClusterLayout.Link path) {
        var a = result.corners().get(path.a());
        var b = result.corners().get(path.b());
        var curve = TopologyLink.between(a[0], a[1], b[0], b[1], WIDTH, HEIGHT).curve();
        for (var entry : result.corners().entrySet()) {
            if (entry.getKey().equals(path.a()) || entry.getKey().equals(path.b())) continue;
            var corner = entry.getValue();
            for (int step = 1; step < 40; step++) {
                var point = curve.at(step / 40f);
                if (point[0] > corner[0] + 2 && point[0] < corner[0] + WIDTH - 2 && point[1] > corner[1] + 2
                        && point[1] < corner[1] + HEIGHT - 2) return entry.getKey();
            }
        }
        return null;
    }
}
