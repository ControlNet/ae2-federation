package space.controlnet.ae2federation.client.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Capability;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Domain;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Endpoint;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Network;
import space.controlnet.ae2federation.client.guide.TopologyDiagram.Rule;
import space.controlnet.ae2federation.client.policy.DomainClusterLayout;
import space.controlnet.ae2federation.client.policy.EndpointNodeLayout;
import space.controlnet.ae2federation.client.policy.TopologyLink;
import space.controlnet.ae2federation.client.policy.WireCurve;

/**
 * Where a {@link TopologyDiagram} draws, in pixels from the diagram's top-left corner: the network cards on their grid,
 * a link per pair that has a rule or shares energy, the energy chip on the link and, beside it, one label per
 * direction with a chip per capability. As on the topology screen, a direction's label points at the network that
 * uses it, and Processing Endpoints are small nodes wired to the network that maps them, placed by the screen's own
 * {@link EndpointNodeLayout}. A build that spans domains draws each on a plate around its networks, its name above,
 * as the screen's related scope does ({@link DomainClusterLayout}). The legend sits under everything.
 */
public record TopologyDiagramLayout(int width, int height, List<Card> cards, List<Link> links,
        List<EndpointNode> endpoints, List<Plate> plates, int legendY) {
    static final int PAD = 8;
    static final int CHIP_HEIGHT = 11;
    static final int CHIP_GAP = 3;
    static final int LABEL_PADDING = 2;
    static final int CAP = 4;
    static final int LINE_HEIGHT = 10;
    static final int LEGEND_HEIGHT = 2 * LINE_HEIGHT + 4;
    static final int ENDPOINT_HEIGHT = 16;
    private static final int MIN_CARD_WIDTH = 64;
    private static final int MAX_CARD_WIDTH = 150;
    private static final int MIN_SQUEEZED_CARD_WIDTH = 44;
    private static final int MIN_GAP = 40;
    private static final int MAX_GAP = 120;
    private static final int ROW_GAP = 40;
    private static final int TITLE_HEIGHT = 13;
    private static final int STRIP = 2;
    /** A domain plate's margin around its cards, and a nested domain's, which sits inside its host's. */
    static final int PLATE_PADDING = 6;
    static final int NESTED_PADDING = 3;
    static final int NAME_HEIGHT = 10;

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(float px, float py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }

        public boolean intersects(Rect other) {
            return x < other.x + other.width && other.x < x + width && y < other.y + other.height && other.y < y + height;
        }

        Rect moved(int dx, int dy) {
            return new Rect(x + dx, y + dy, width, height);
        }
    }

    public record Card(Network network, Rect rect) {
    }

    /** A domain's plate: a rounded hull around its cards, and where its name stands above it. */
    public record Plate(Domain domain, List<float[]> outline, Rect name) {
        Plate moved(int dx, int dy) {
            return new Plate(domain, outline.stream().map(point -> new float[] {point[0] + dx, point[1] + dy}).toList(),
                    name.moved(dx, dy));
        }
    }

    /** A Processing Endpoint's node, and its wire from the card of the network that maps it. */
    public record EndpointNode(Endpoint endpoint, Rect rect, WireCurve link) {
    }

    /** One capability of one direction's label. */
    public record Chip(Rule rule, String text, Rect rect) {
    }

    /**
     * One direction's label: its chips, and the side its cap points to, at the network that uses them. {@code capX}/
     * {@code capY} give that side as -1, 0 or 1.
     */
    public record Label(Rect rect, int capX, int capY, List<Chip> chips) {
    }

    /**
     * A link from {@code from} to {@code to} (in the order the page lists the networks), the energy chip on it when
     * the pair shares energy, and the label of each direction that has a rule.
     */
    public record Link(Network from, Network to, TopologyLink curve, Rect energyChip, List<Label> labels) {
        public boolean sharesEnergy() {
            return energyChip != null;
        }
    }

    /**
     * Lays {@code diagram} out in {@code availableWidth}, measuring text with {@code textWidth}; chips read
     * {@code storage}, {@code crafting} and {@code energy}.
     */
    public static TopologyDiagramLayout of(TopologyDiagram diagram, int availableWidth, ToIntFunction<String> textWidth,
            String storage, String crafting, String energy) {
        int columns = 0;
        int rows = 0;
        int details = 0;
        int wanted = MIN_CARD_WIDTH;
        for (var network : diagram.networks()) {
            columns = Math.max(columns, network.column() + 1);
            rows = Math.max(rows, network.row() + 1);
            details = Math.max(details, network.details().size());
            wanted = Math.max(wanted, textWidth.applyAsInt(network.label()) + 16);
            for (var detail : network.details()) wanted = Math.max(wanted, textWidth.applyAsInt(detail) + 8);
        }
        int cardWidth = Math.min(wanted, MAX_CARD_WIDTH);
        // Plates reach past the outer cards; keep them on the canvas.
        int inset = PAD + (diagram.domains().isEmpty() ? 0 : PLATE_PADDING);
        if (columns > 1) {
            int room = (availableWidth - 2 * inset - (columns - 1) * MIN_GAP) / columns;
            cardWidth = Math.max(MIN_SQUEEZED_CARD_WIDTH, Math.min(cardWidth, room));
        } else {
            cardWidth = Math.max(MIN_SQUEEZED_CARD_WIDTH, Math.min(cardWidth, availableWidth - 2 * inset));
        }
        int gap = columns > 1 ? Math.max(MIN_GAP, Math.min(MAX_GAP,
                (availableWidth - 2 * inset - columns * cardWidth) / (columns - 1))) : 0;
        int cardHeight = TITLE_HEIGHT + details * LINE_HEIGHT + STRIP + 2;
        int gridWidth = columns * cardWidth + (columns - 1) * gap;
        int left = Math.max(inset, (availableWidth - gridWidth) / 2);

        var cards = new LinkedHashMap<String, Card>();
        for (var network : diagram.networks()) {
            cards.put(network.key(), new Card(network, new Rect(left + network.column() * (cardWidth + gap),
                    network.row() * (cardHeight + ROW_GAP), cardWidth, cardHeight)));
        }

        var endpoints = placeEndpoints(diagram, cards, cardWidth, cardHeight, availableWidth, textWidth);

        var links = new ArrayList<Link>();
        for (var pair : pairs(diagram).entrySet()) {
            var from = cards.get(pair.getKey().get(0));
            var to = cards.get(pair.getKey().get(1));
            var curve = TopologyLink.between(from.rect().x(), from.rect().y(), to.rect().x(), to.rect().y(), cardWidth,
                    cardHeight);
            var middle = curve.middle();
            var tangent = curve.curve().tangent(0.5f);
            float length = (float) Math.hypot(tangent[0], tangent[1]);
            float tx = tangent[0] / length;
            float ty = tangent[1] / length;
            boolean across = Math.abs(tx) >= Math.abs(ty);
            Rect energyChip = null;
            int clearance = 2;
            if (pair.getValue().energy()) {
                // Where the gap is too narrow for the word, the chip shrinks to a mark; hovering it still names it.
                int chipWidth = textWidth.applyAsInt(energy) + 5;
                if (across && columns > 1 && chipWidth > gap - 4) chipWidth = 9;
                energyChip = new Rect(Math.round(middle[0] - chipWidth / 2f), Math.round(middle[1] - CHIP_HEIGHT / 2f),
                        chipWidth, CHIP_HEIGHT);
                clearance += (across ? CHIP_HEIGHT : chipWidth) / 2;
            }
            var labels = new ArrayList<Label>();
            for (int direction = 0; direction < 2; direction++) {
                var user = direction == 0 ? from : to;
                var source = direction == 0 ? to : from;
                var rules = pair.getValue().rules().stream()
                        .filter(rule -> rule.user().equals(user.network().key()) && rule.source().equals(source.network().key()))
                        .sorted(java.util.Comparator.comparing(Rule::capability))
                        .toList();
                if (rules.isEmpty()) continue;
                // The user's label sits on the start's side of the link for the start, on the other side for the end.
                int side = direction == 0 ? 1 : -1;
                int capX = across ? (int) -Math.signum(tx) * side : 0;
                int capY = across ? 0 : (int) -Math.signum(ty) * side;
                int maxWidth = across ? gap - 8 : Integer.MAX_VALUE;
                var label = label(rules, textWidth, storage, crafting, middle, -ty * side, tx * side, clearance, capX,
                        capY, maxWidth);
                if (across && label.rect().width() > gap - 4) {
                    // Too wide for the gap between the cards: stand above or below the row instead.
                    label = label(rules, textWidth, storage, crafting, middle, -ty * side, tx * side,
                            Math.max(clearance, cardHeight / 2 + 3), capX, capY, maxWidth);
                }
                labels.add(label);
            }
            links.add(new Link(from.network(), to.network(), curve, energyChip, List.copyOf(labels)));
        }

        var plates = plates(diagram, cards, cardWidth, cardHeight, textWidth);

        // Labels, Endpoints and plates may stand above the top row or below the bottom one: shift everything so they fit.
        int top = 0;
        int bottom = rows * cardHeight + (rows - 1) * ROW_GAP;
        for (var plate : plates) {
            top = Math.min(top, plate.name().y());
            for (var point : plate.outline()) bottom = Math.max(bottom, (int) Math.ceil(point[1]));
        }
        for (var link : links) {
            for (var label : link.labels()) {
                top = Math.min(top, label.rect().y());
                bottom = Math.max(bottom, label.rect().y() + label.rect().height());
            }
        }
        for (var node : endpoints) {
            top = Math.min(top, node.rect().y());
            bottom = Math.max(bottom, node.rect().y() + node.rect().height());
        }
        int shift = PAD - top;
        var placedCards = cards.values().stream().map(card -> new Card(card.network(), card.rect().moved(0, shift))).toList();
        var placedLinks = links.stream().map(link -> new Link(link.from(), link.to(),
                new TopologyLink(link.curve().curve().translated(0, shift), link.curve().labelAt()),
                link.energyChip() == null ? null : link.energyChip().moved(0, shift),
                link.labels().stream().map(label -> new Label(label.rect().moved(0, shift), label.capX(), label.capY(),
                        label.chips().stream().map(chip -> new Chip(chip.rule(), chip.text(), chip.rect().moved(0, shift)))
                                .toList())).toList())).toList();
        var placedEndpoints = endpoints.stream().map(node -> new EndpointNode(node.endpoint(), node.rect().moved(0, shift),
                node.link().translated(0, shift))).toList();
        var placedPlates = plates.stream().map(plate -> plate.moved(0, shift)).toList();
        int legendY = bottom + shift + PAD;
        return new TopologyDiagramLayout(availableWidth, legendY + LEGEND_HEIGHT + PAD / 2, placedCards, placedLinks,
                placedEndpoints, placedPlates, legendY);
    }

    /**
     * Each domain's plate around its cards, in the page's order; a domain whose networks all belong to another has the
     * smaller plate inside that one's. A name that would cover an earlier one moves along to its right.
     */
    private static List<Plate> plates(TopologyDiagram diagram, Map<String, Card> cards, int cardWidth, int cardHeight,
            ToIntFunction<String> textWidth) {
        if (diagram.domains().isEmpty()) return List.of();
        var opened = diagram.domains().stream().filter(Domain::opened).map(Domain::key).findFirst().orElse("");
        var clusterCards = diagram.networks().stream().map(network -> new DomainClusterLayout.Card(network.key(),
                diagram.domains().stream().filter(domain -> domain.networks().contains(network.key())).map(Domain::key)
                        .collect(java.util.stream.Collectors.toSet()))).toList();
        var hosts = DomainClusterLayout.hosts(clusterCards, opened);
        var plates = new ArrayList<Plate>();
        for (var domain : diagram.domains()) {
            var corners = domain.networks().stream().map(cards::get)
                    .map(card -> new float[] {card.rect().x(), card.rect().y()}).toList();
            int padding = hosts.containsKey(domain.key()) ? NESTED_PADDING : PLATE_PADDING;
            var outline = DomainClusterLayout.plate(corners, cardWidth, cardHeight, padding);
            float plateTop = (float) outline.stream().mapToDouble(point -> point[1]).min().orElse(0);
            float cardLeft = (float) outline.stream().filter(point -> point[1] <= plateTop + 1).mapToDouble(point -> point[0])
                    .min().orElse(0);
            var name = new Rect(Math.round(cardLeft - padding + 2), Math.round(plateTop) - NAME_HEIGHT,
                    textWidth.applyAsInt(domain.label()), NAME_HEIGHT);
            for (var earlier : plates) {
                if (name.intersects(earlier.name())) name = new Rect(earlier.name().x() + earlier.name().width() + 6,
                        name.y(), name.width(), name.height());
            }
            plates.add(new Plate(domain, outline, name));
        }
        return List.copyOf(plates);
    }

    /**
     * Places the Endpoints as the topology screen does, on the outer side of their network's card, and then moves the
     * cards and nodes sideways together so they are centred. Where that is too wide for the page, each network's
     * Endpoints hang under its card instead, slid along the row as far as needed to stay on the page.
     */
    private static List<EndpointNode> placeEndpoints(TopologyDiagram diagram, Map<String, Card> cards, int cardWidth,
            int cardHeight, int availableWidth, ToIntFunction<String> textWidth) {
        if (diagram.endpoints().isEmpty()) return List.of();
        // The node's padding, its state dot and the gaps around them, as the screen measures it.
        var nodes = diagram.endpoints().stream().map(endpoint -> new EndpointNodeLayout.Node(endpoint.key(),
                endpoint.owner(), textWidth.applyAsInt(endpoint.label()) + 18)).toList();
        var corners = cards.values().stream().map(card -> new EndpointNodeLayout.Card(card.network().key(),
                card.rect().x(), card.rect().y())).toList();
        var placed = EndpointNodeLayout.place(corners, cardWidth, cardHeight, nodes, ENDPOINT_HEIGHT);
        if (fits(corners, cardWidth, cardHeight, placed, availableWidth)) {
            var span = span(corners, cardWidth, placed);
            int dx = Math.max(PAD, Math.round((availableWidth - (span[1] - span[0])) / 2f)) - Math.round(span[0]);
            for (var entry : cards.entrySet()) {
                var card = entry.getValue();
                entry.setValue(new Card(card.network(), card.rect().moved(dx, 0)));
            }
            placed = placed.stream().map(node -> node.moved(dx, 0)).toList();
        } else {
            var below = new LinkedHashMap<String, EndpointNodeLayout.Placed>();
            for (var corner : corners) {
                var owned = nodes.stream().filter(node -> node.owner().equals(corner.id())).toList();
                if (owned.isEmpty()) continue;
                var group = EndpointNodeLayout.place(List.of(corner), cardWidth, cardHeight, owned, ENDPOINT_HEIGHT);
                var span = span(List.of(), cardWidth, group);
                float slide = Math.max(PAD - span[0], Math.min(0, availableWidth - PAD - span[1]));
                for (var node : group) {
                    // The wire still leaves the card where it did and now ends at the moved node.
                    var link = node.link();
                    below.put(node.id(), new EndpointNodeLayout.Placed(node.id(), node.x() + slide, node.y(), node.width(),
                            new WireCurve(link.fromX(), link.fromY(), link.toX() + slide, link.toY(), link.vertical(),
                                    link.bendEnd())));
                }
            }
            placed = nodes.stream().map(node -> below.get(node.id())).toList();
        }
        var endpoints = new ArrayList<EndpointNode>();
        for (int index = 0; index < placed.size(); index++) {
            var node = placed.get(index);
            endpoints.add(new EndpointNode(diagram.endpoints().get(index), new Rect(Math.round(node.x()),
                    Math.round(node.y()), Math.round(node.width()), ENDPOINT_HEIGHT), node.link()));
        }
        return endpoints;
    }

    /** Whether {@code placed} covers no card or node, and the cards and nodes together fit the page's width. */
    private static boolean fits(List<EndpointNodeLayout.Card> corners, int cardWidth, int cardHeight,
            List<EndpointNodeLayout.Placed> placed, int availableWidth) {
        if (!EndpointNodeLayout.clear(corners, cardWidth, cardHeight, placed, ENDPOINT_HEIGHT)) return false;
        var span = span(corners, cardWidth, placed);
        return span[1] - span[0] <= availableWidth - 2 * PAD;
    }

    /** The leftmost and rightmost x of the cards and nodes. */
    private static float[] span(List<EndpointNodeLayout.Card> corners, int cardWidth, List<EndpointNodeLayout.Placed> placed) {
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        for (var corner : corners) {
            left = Math.min(left, corner.x());
            right = Math.max(right, corner.x() + cardWidth);
        }
        for (var node : placed) {
            left = Math.min(left, node.x());
            right = Math.max(right, node.x() + node.width());
        }
        return new float[] {left, right};
    }

    /**
     * A direction's label, its chips side by side or, when that is wider than {@code maxWidth}, one under another, set
     * off from {@code middle} along the normal ({@code nx}, {@code ny}) by {@code clearance} plus its own half extent.
     */
    private static Label label(List<Rule> rules, ToIntFunction<String> textWidth, String storage, String crafting,
            float[] middle, float nx, float ny, int clearance, int capX, int capY, int maxWidth) {
        var texts = rules.stream().map(rule -> rule.capability() == Capability.STORAGE ? storage : crafting).toList();
        int widest = 0;
        int row = 2 * LABEL_PADDING + (texts.size() - 1) * CHIP_GAP;
        for (var text : texts) {
            int width = textWidth.applyAsInt(text) + 5;
            widest = Math.max(widest, width);
            row += width;
        }
        int capWidth = capX != 0 ? CAP : 0;
        int capHeight = capY != 0 ? CAP : 0;
        boolean stacked = row + capWidth > maxWidth && texts.size() > 1;
        int width = (stacked ? 2 * LABEL_PADDING + widest : row) + capWidth;
        int height = (stacked ? 2 * LABEL_PADDING + texts.size() * CHIP_HEIGHT + (texts.size() - 1) * CHIP_GAP
                : 2 * LABEL_PADDING + CHIP_HEIGHT) + capHeight;
        float extent = Math.abs(nx) * width / 2f + Math.abs(ny) * height / 2f;
        int x = Math.round(middle[0] + nx * (clearance + extent) - width / 2f);
        int y = Math.round(middle[1] + ny * (clearance + extent) - height / 2f);
        var rect = new Rect(x, y, width, height);
        var chips = new ArrayList<Chip>();
        int chipX = x + LABEL_PADDING + (capX < 0 ? CAP : 0);
        int chipY = y + LABEL_PADDING + (capY < 0 ? CAP : 0);
        for (int index = 0; index < rules.size(); index++) {
            int chipWidth = stacked ? widest : textWidth.applyAsInt(texts.get(index)) + 5;
            chips.add(new Chip(rules.get(index), texts.get(index), new Rect(chipX, chipY, chipWidth, CHIP_HEIGHT)));
            if (stacked) {
                chipY += CHIP_HEIGHT + CHIP_GAP;
            } else {
                chipX += chipWidth + CHIP_GAP;
            }
        }
        return new Label(rect, capX, capY, List.copyOf(chips));
    }

    private record PairContent(List<Rule> rules, boolean energy) {
    }

    /** The pairs that have something to draw, each as its two keys in the order the page lists the networks. */
    private static Map<List<String>, PairContent> pairs(TopologyDiagram diagram) {
        var order = diagram.networks().stream().map(Network::key).toList();
        var pairs = new LinkedHashMap<List<String>, PairContent>();
        for (var rule : diagram.rules()) {
            var key = ordered(order, rule.user(), rule.source());
            var content = pairs.getOrDefault(key, new PairContent(List.of(), false));
            var rules = new ArrayList<>(content.rules());
            rules.add(rule);
            pairs.put(key, new PairContent(List.copyOf(rules), content.energy()));
        }
        for (var shared : diagram.energy()) {
            var key = ordered(order, shared.first(), shared.second());
            var content = pairs.getOrDefault(key, new PairContent(List.of(), false));
            pairs.put(key, new PairContent(content.rules(), true));
        }
        return pairs;
    }

    private static List<String> ordered(List<String> order, String first, String second) {
        return order.indexOf(first) <= order.indexOf(second) ? List.of(first, second) : List.of(second, first);
    }
}
