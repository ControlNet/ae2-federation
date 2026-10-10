package space.controlnet.ae2federation.client.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Where the topology puts network cards when it shows other domains too. Rules only join networks of one domain, and
 * domains meet only in the networks they share, so each domain gets its own small ellipse and the shared networks sit
 * between them. Domains stand in columns by how many hops they are from the opened domain, each beside the domains of
 * the previous column it shares networks with. A shared network sits at the centre of its domains; a domain's own
 * networks take the widest gap between the directions of its shared networks, in the order its rules run from the
 * shared network at one end of that gap to the one at the other end, so its links follow the arc instead of crossing
 * it. A crowded domain spreads its own ellipse, as {@link TopologySpacing} spreads a single one; anything between
 * domains widens the gaps between them. Each domain is drawn on a plate: the hull of its cards with a rounded margin.
 * A domain whose shown networks all belong to another shown domain, such as a Bridge between two networks of the
 * opened domain, takes no place of its own: it sits in that domain, its plate inside the other's.
 */
public final class DomainClusterLayout {
    /** The margin of a domain's plate around its cards. */
    public static final float PLATE_PADDING = 22;
    /** The margin of a nested domain's plate, which leaves room for its name inside its host's plate. */
    public static final float NESTED_PADDING = 10;
    /** Space kept between two cards. */
    static final float CARD_MARGIN = 8;
    /** Space kept between a label and a card. */
    static final float LABEL_MARGIN = 4;
    static final float GAP_X = 40;
    static final float GAP_Y = 60;
    private static final int ATTEMPTS = 24;
    private static final int UNREACHED = Integer.MAX_VALUE;
    private static final double TAU = 2 * Math.PI;

    private DomainClusterLayout() {
    }

    /** A shown network and the shown domains it is in, the opened domain among them for its members. */
    public record Card(String id, Set<String> domains) {
    }

    /** A pair of networks drawn with a link. */
    public record Link(String a, String b) {
    }

    /** The half width and half height of a pair's labels, centred on the middle of its link. */
    @FunctionalInterface
    public interface LabelExtent {
        float[] halfSize(String a, String b, TopologyLink link);
    }

    /** Each card's top-left corner, and each domain's centre. */
    public record Result(Map<String, float[]> corners, Map<String, float[]> centres) {
    }

    /** Places {@code cards}, in their shown order, around domain {@code opened}. */
    public static Result place(List<Card> shown, String opened, List<Link> links, LabelExtent labels, float width,
            float height) {
        // Nested domains are placed as their hosts; the cards keep their own domains for the problems and plates.
        var hosts = hosts(shown, opened);
        var cards = shown.stream().map(card -> new Card(card.id(), card.domains().stream()
                .map(domain -> hosts.getOrDefault(domain, domain)).collect(Collectors.toSet()))).toList();
        var domains = domains(cards, opened);
        var neighbours = new LinkedHashMap<String, Set<String>>();
        domains.forEach(domain -> neighbours.put(domain, new LinkedHashSet<>()));
        for (var card : cards) {
            for (var first : card.domains()) {
                for (var second : card.domains()) if (!first.equals(second)) neighbours.get(first).add(second);
            }
        }
        var hops = hops(domains, neighbours, opened);
        var columns = columns(domains, neighbours, hops);
        var members = new LinkedHashMap<String, List<Card>>();
        for (var domain : domains) members.put(domain, cards.stream().filter(card -> card.domains().contains(domain)).toList());
        var semi = new HashMap<String, float[]>();
        for (var domain : domains) {
            float radius = radius(members.get(domain).size());
            semi.put(domain, new float[] {radius * 1.35f, radius * 0.8f});
        }
        float gapX = GAP_X;
        float gapY = GAP_Y;
        Map<String, float[]> centres = Map.of();
        Map<String, float[]> corners = Map.of();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            centres = centres(columns, neighbours, hops, semi, height, gapX, gapY);
            corners = corners(cards, domains, members, links, centres, semi, width, height);
            if (problems(shown, opened, links, labels, corners, width, height).isEmpty()) break;
            var crowded = new ArrayList<String>();
            for (var domain : domains) if (crowded(members.get(domain), links, labels, corners, width, height)) crowded.add(domain);
            for (var domain : crowded) semi.put(domain, new float[] {semi.get(domain)[0] * 1.1f, semi.get(domain)[1] * 1.1f});
            if (crowded.isEmpty()) {
                gapX *= 1.15f;
                gapY *= 1.15f;
            }
        }
        var all = new LinkedHashMap<>(centres);
        hosts.forEach((domain, host) -> all.put(domain, all.get(host)));
        return new Result(corners, all);
    }

    /** The opened domain, then the others in the order the cards first name them. */
    private static List<String> domains(List<Card> cards, String opened) {
        var domains = new ArrayList<String>();
        domains.add(opened);
        for (var card : cards) {
            for (var domain : card.domains().stream().sorted().toList()) if (!domains.contains(domain)) domains.add(domain);
        }
        return domains;
    }

    /**
     * Each nested domain, whose shown networks all belong to another shown domain, with the domain it sits in: the
     * smallest such domain that is not nested itself. The opened domain is never nested.
     */
    public static Map<String, String> hosts(List<Card> cards, String opened) {
        var domains = domains(cards, opened);
        var members = new HashMap<String, Set<String>>();
        for (var domain : domains) {
            members.put(domain, cards.stream().filter(card -> card.domains().contains(domain)).map(Card::id)
                    .collect(Collectors.toSet()));
        }
        var bySize = new ArrayList<>(domains);
        bySize.sort(Comparator.comparingInt((String domain) -> domain.equals(opened) ? 0 : 1)
                .thenComparingInt(domain -> -members.get(domain).size()).thenComparingInt(domains::indexOf));
        var roots = new ArrayList<String>();
        var hosts = new LinkedHashMap<String, String>();
        for (var domain : bySize) {
            var host = roots.stream().filter(root -> members.get(root).containsAll(members.get(domain)))
                    .min(Comparator.comparingInt((String root) -> members.get(root).size()).thenComparingInt(domains::indexOf));
            if (host.isPresent() && !domain.equals(opened)) hosts.put(domain, host.get());
            else roots.add(domain);
        }
        return hosts;
    }

    /** {@code FederationTopologyView}'s ellipse radius for {@code count} cards. */
    static float radius(int count) {
        return count <= 2 ? 140 : (float) Math.max(150, 100 / Math.sin(Math.PI / count));
    }

    /** Hops from the opened domain; a domain no shown network leads to counts one past the farthest. */
    private static Map<String, Integer> hops(List<String> domains, Map<String, Set<String>> neighbours, String opened) {
        var hops = new HashMap<String, Integer>();
        hops.put(opened, 0);
        var queue = new ArrayDeque<String>(List.of(opened));
        while (!queue.isEmpty()) {
            var domain = queue.poll();
            for (var next : neighbours.get(domain)) {
                if (hops.containsKey(next)) continue;
                hops.put(next, hops.get(domain) + 1);
                queue.add(next);
            }
        }
        int farthest = hops.values().stream().max(Integer::compare).orElse(0);
        for (var domain : domains) hops.putIfAbsent(domain, farthest + 1);
        return hops;
    }

    /** The domains of each column in order, each placed by where its neighbours in the column before stand. */
    private static List<List<String>> columns(List<String> domains, Map<String, Set<String>> neighbours,
            Map<String, Integer> hops) {
        int last = hops.values().stream().max(Integer::compare).orElse(0);
        var columns = new ArrayList<List<String>>();
        var rank = new HashMap<String, Float>();
        for (int hop = 0; hop <= last; hop++) {
            int column = hop;
            var domainsHere = new ArrayList<>(domains.stream().filter(domain -> hops.get(domain) == column).toList());
            domainsHere.sort(Comparator.comparingDouble((String domain) -> neighbours.get(domain).stream()
                    .filter(rank::containsKey).filter(other -> hops.get(other) == column - 1)
                    .mapToDouble(rank::get).average().orElse(0)).thenComparingInt(domains::indexOf));
            for (int index = 0; index < domainsHere.size(); index++) rank.put(domainsHere.get(index), (float) index);
            if (!domainsHere.isEmpty()) columns.add(domainsHere);
        }
        return columns;
    }

    private static Map<String, float[]> centres(List<List<String>> columns, Map<String, Set<String>> neighbours,
            Map<String, Integer> hops, Map<String, float[]> semi, float height, float gapX, float gapY) {
        var centres = new LinkedHashMap<String, float[]>();
        float x = 0;
        float previous = 0;
        for (int column = 0; column < columns.size(); column++) {
            var domains = columns.get(column);
            float widest = (float) domains.stream().mapToDouble(domain -> semi.get(domain)[0]).max().orElse(0);
            if (column > 0) x += previous + widest + gapX;
            var wanted = new float[domains.size()];
            var placed = new float[domains.size()];
            var heights = new float[domains.size()];
            for (int index = 0; index < domains.size(); index++) {
                var domain = domains.get(index);
                heights[index] = 2 * semi.get(domain)[1] + height;
                wanted[index] = (float) neighbours.get(domain).stream().filter(centres::containsKey)
                        .filter(other -> hops.get(other) < hops.get(domain)).mapToDouble(other -> centres.get(other)[1])
                        .average().orElse(0);
                placed[index] = index == 0 ? wanted[index]
                        : Math.max(wanted[index], placed[index - 1] + heights[index - 1] / 2 + gapY + heights[index] / 2);
            }
            float shift = 0;
            for (int index = 0; index < domains.size(); index++) shift += (wanted[index] - placed[index]) / domains.size();
            for (int index = 0; index < domains.size(); index++) centres.put(domains.get(index), new float[] {x, placed[index] + shift});
            previous = widest;
        }
        return centres;
    }

    private static Map<String, float[]> corners(List<Card> cards, List<String> domains, Map<String, List<Card>> members,
            List<Link> links, Map<String, float[]> centres, Map<String, float[]> semi, float width, float height) {
        var corners = new LinkedHashMap<String, float[]>();
        for (var card : cards) {
            if (card.domains().size() < 2) continue;
            float x = 0;
            float y = 0;
            for (var domain : card.domains()) {
                x += centres.get(domain)[0] / card.domains().size();
                y += centres.get(domain)[1] / card.domains().size();
            }
            corners.put(card.id(), new float[] {x - width / 2, y - height / 2});
        }
        for (var domain : domains) {
            var centre = centres.get(domain);
            var shared = members.get(domain).stream().filter(card -> card.domains().size() > 1).toList();
            var own = new ArrayList<>(members.get(domain).stream().filter(card -> card.domains().size() == 1).toList());
            if (own.isEmpty()) continue;
            var angles = new double[own.size()];
            String start = null;
            String end = null;
            if (shared.isEmpty()) {
                for (int index = 0; index < own.size(); index++) angles[index] = Math.PI + TAU * index / own.size();
            } else {
                var ring = new ArrayList<>(shared);
                ring.sort(Comparator.comparingDouble(card -> angle(corners.get(card.id()), centre, width, height)));
                int widest = 0;
                double widestLength = -1;
                for (int index = 0; index < ring.size(); index++) {
                    double from = angle(corners.get(ring.get(index).id()), centre, width, height);
                    double to = angle(corners.get(ring.get((index + 1) % ring.size()).id()), centre, width, height);
                    double length = ring.size() == 1 ? TAU : ((to - from) % TAU + TAU) % TAU;
                    if (length > widestLength) {
                        widest = index;
                        widestLength = length;
                    }
                }
                double begin = angle(corners.get(ring.get(widest).id()), centre, width, height);
                for (int index = 0; index < own.size(); index++) angles[index] = begin + widestLength * (index + 1) / (own.size() + 1);
                start = ring.get(widest).id();
                end = ring.get((widest + 1) % ring.size()).id();
            }
            var ids = new LinkedHashSet<String>();
            members.get(domain).forEach(card -> ids.add(card.id()));
            var fromStart = steps(start, ids, links);
            var fromEnd = steps(end, ids, links);
            own.sort(Comparator.comparingInt(card -> fromStart.getOrDefault(card.id(), 99) - fromEnd.getOrDefault(card.id(), 99)));
            var axes = semi.get(domain);
            for (int index = 0; index < own.size(); index++) {
                corners.put(own.get(index).id(), new float[] {
                        centre[0] + (float) Math.cos(angles[index]) * axes[0] - width / 2,
                        centre[1] + (float) Math.sin(angles[index]) * axes[1] - height / 2});
            }
        }
        // In shown order, as the view reads them.
        var ordered = new LinkedHashMap<String, float[]>();
        for (var card : cards) ordered.put(card.id(), corners.get(card.id()));
        return ordered;
    }

    /** The direction from a domain's centre to a card, in radians from 0 to 2π, y down the screen. */
    private static double angle(float[] corner, float[] centre, float width, float height) {
        double angle = Math.atan2(corner[1] + height / 2 - centre[1], corner[0] + width / 2 - centre[0]);
        return (angle % TAU + TAU) % TAU;
    }

    /** Links from {@code source} to each network of {@code ids}, through those networks only. */
    private static Map<String, Integer> steps(String source, Set<String> ids, List<Link> links) {
        var steps = new HashMap<String, Integer>();
        if (source == null) return steps;
        steps.put(source, 0);
        var queue = new ArrayDeque<String>(List.of(source));
        while (!queue.isEmpty()) {
            var from = queue.poll();
            for (var link : links) {
                var other = link.a().equals(from) ? link.b() : link.b().equals(from) ? link.a() : null;
                if (other == null || !ids.contains(other) || steps.containsKey(other)) continue;
                steps.put(other, steps.get(from) + 1);
                queue.add(other);
            }
        }
        return steps;
    }

    /** Whether one domain's cards or labels crowd each other. */
    private static boolean crowded(List<Card> members, List<Link> links, LabelExtent labels, Map<String, float[]> corners,
            float width, float height) {
        var ids = new LinkedHashSet<String>();
        members.forEach(card -> ids.add(card.id()));
        var inside = links.stream().filter(link -> ids.contains(link.a()) && ids.contains(link.b())).toList();
        var problems = new ArrayList<String>();
        cardsAndLabels(new ArrayList<>(ids), inside, labels, corners, width, height, problems);
        return !problems.isEmpty();
    }

    /**
     * What is wrong with a layout, empty when nothing: cards that touch, a label on a card or on another label, a card
     * on the plate of a domain it is not in (a nested domain's host's cards may), or the plates of two domains that share
     * nothing running into each other.
     */
    public static List<String> problems(List<Card> cards, String opened, List<Link> links, LabelExtent labels,
            Map<String, float[]> corners, float width, float height) {
        var problems = new ArrayList<String>();
        cardsAndLabels(cards.stream().map(Card::id).toList(), links, labels, corners, width, height, problems);
        var hosts = hosts(cards, opened);
        var plates = plates(cards, opened, corners, width, height);
        var domains = new ArrayList<>(plates.keySet());
        for (var card : cards) {
            var corner = corners.get(card.id());
            for (var plate : plates.entrySet()) {
                var host = hosts.get(plate.getKey());
                if (card.domains().contains(plate.getKey()) || host != null && card.domains().contains(host)) continue;
                if (overlaps(plate.getValue(), corner[0], corner[1], width, height)) {
                    problems.add(card.id() + " on the plate of " + plate.getKey());
                }
            }
        }
        for (int first = 0; first < domains.size(); first++) {
            for (int second = first + 1; second < domains.size(); second++) {
                var a = domains.get(first);
                var b = domains.get(second);
                if (cards.stream().anyMatch(card -> card.domains().contains(a) && card.domains().contains(b))) continue;
                if (meet(plates.get(a), plates.get(b))) problems.add("plates of " + a + " and " + b);
            }
        }
        return problems;
    }

    private static void cardsAndLabels(List<String> ids, List<Link> links, LabelExtent labels, Map<String, float[]> corners,
            float width, float height, List<String> problems) {
        for (int first = 0; first < ids.size(); first++) {
            for (int second = first + 1; second < ids.size(); second++) {
                var a = corners.get(ids.get(first));
                var b = corners.get(ids.get(second));
                if (intersects(a[0], a[1], width, height, b[0] - CARD_MARGIN, b[1] - CARD_MARGIN, width + 2 * CARD_MARGIN,
                        height + 2 * CARD_MARGIN)) problems.add(ids.get(first) + " touches " + ids.get(second));
            }
        }
        var boxes = new ArrayList<float[]>();
        for (var link : links) {
            var a = corners.get(link.a());
            var b = corners.get(link.b());
            var path = TopologyLink.between(a[0], a[1], b[0], b[1], width, height);
            var middle = path.middle();
            var half = labels.halfSize(link.a(), link.b(), path);
            var box = new float[] {middle[0] - half[0], middle[1] - half[1], 2 * half[0], 2 * half[1]};
            if (half[0] > 0 && half[1] > 0) {
                for (var id : ids) {
                    var corner = corners.get(id);
                    if (intersects(box[0] - LABEL_MARGIN, box[1] - LABEL_MARGIN, box[2] + 2 * LABEL_MARGIN, box[3] + 2 * LABEL_MARGIN,
                            corner[0], corner[1], width, height)) problems.add(link + " label on " + id);
                }
                for (var other : boxes) {
                    if (intersects(box[0], box[1], box[2], box[3], other[0], other[1], other[2], other[3])) {
                        problems.add(link + " label on another label");
                    }
                }
                boxes.add(box);
            }
        }
    }

    private static boolean intersects(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    /**
     * Each shown domain's plate around its cards, the domains that are not nested first, so a nested plate is drawn
     * over its host's; a nested plate has the smaller {@link #NESTED_PADDING}.
     */
    public static Map<String, List<float[]>> plates(List<Card> cards, String opened, Map<String, float[]> corners, float width,
            float height) {
        var hosts = hosts(cards, opened);
        var byDomain = new LinkedHashMap<String, List<float[]>>();
        for (var card : cards) {
            for (var domain : card.domains().stream().sorted().toList()) {
                byDomain.computeIfAbsent(domain, ignored -> new ArrayList<>()).add(corners.get(card.id()));
            }
        }
        var plates = new LinkedHashMap<String, List<float[]>>();
        byDomain.forEach((domain, domainCorners) -> {
            if (!hosts.containsKey(domain)) plates.put(domain, plate(domainCorners, width, height, PLATE_PADDING));
        });
        byDomain.forEach((domain, domainCorners) -> {
            if (hosts.containsKey(domain)) plates.put(domain, plate(domainCorners, width, height, NESTED_PADDING));
        });
        return plates;
    }

    /**
     * The plate around cards at {@code corners}: their convex hull grown by {@link #PLATE_PADDING} with round corners,
     * as a convex polygon in order.
     */
    public static List<float[]> plate(List<float[]> corners, float width, float height) {
        return plate(corners, width, height, PLATE_PADDING);
    }

    /** The plate around cards at {@code corners} with a margin of {@code padding}. */
    public static List<float[]> plate(List<float[]> corners, float width, float height, float padding) {
        var points = new ArrayList<float[]>();
        for (var corner : corners) {
            for (float[] point : new float[][] {{corner[0], corner[1]}, {corner[0] + width, corner[1]},
                    {corner[0] + width, corner[1] + height}, {corner[0], corner[1] + height}}) {
                for (int step = 0; step < 16; step++) {
                    double angle = TAU * step / 16;
                    points.add(new float[] {point[0] + (float) Math.cos(angle) * padding,
                            point[1] + (float) Math.sin(angle) * padding});
                }
            }
        }
        return hull(points);
    }

    /** Andrew's monotone chain, counter-clockwise on screen. */
    private static List<float[]> hull(List<float[]> points) {
        var sorted = new ArrayList<>(points);
        sorted.sort(Comparator.<float[]>comparingDouble(point -> point[0]).thenComparingDouble(point -> point[1]));
        var hull = new ArrayList<float[]>();
        for (int pass = 0; pass < 2; pass++) {
            int start = hull.size();
            for (var point : sorted) {
                while (hull.size() >= start + 2 && cross(hull.get(hull.size() - 2), hull.getLast(), point) <= 0) hull.removeLast();
                hull.add(point);
            }
            hull.removeLast();
            sorted = new ArrayList<>(sorted.reversed());
        }
        return hull;
    }

    private static float cross(float[] origin, float[] a, float[] b) {
        return (a[0] - origin[0]) * (b[1] - origin[1]) - (a[1] - origin[1]) * (b[0] - origin[0]);
    }

    /** Whether a point is on a plate. */
    public static boolean inside(List<float[]> plate, float x, float y) {
        boolean inside = false;
        for (int index = 0; index < plate.size(); index++) {
            var a = plate.get(index);
            var b = plate.get((index + 1) % plate.size());
            if ((a[1] > y) != (b[1] > y) && x < a[0] + (y - a[1]) * (b[0] - a[0]) / (b[1] - a[1])) inside = !inside;
        }
        return inside;
    }

    private static boolean overlaps(List<float[]> plate, float x, float y, float width, float height) {
        for (float dx = 0; dx <= width; dx += width / 10) {
            if (inside(plate, x + dx, y) || inside(plate, x + dx, y + height)) return true;
        }
        for (float dy = 0; dy <= height; dy += height / 4) {
            if (inside(plate, x, y + dy) || inside(plate, x + width, y + dy)) return true;
        }
        return false;
    }

    private static boolean meet(List<float[]> a, List<float[]> b) {
        for (var point : a) if (inside(b, point[0], point[1])) return true;
        for (var point : b) if (inside(a, point[0], point[1])) return true;
        return false;
    }

    /** A plate's fill as one-pixel rows, each {@code [y, left, right]}, top to bottom. */
    public static List<float[]> rows(List<float[]> plate) {
        float top = (float) Math.floor(plate.stream().mapToDouble(point -> point[1]).min().orElse(0));
        float bottom = (float) Math.ceil(plate.stream().mapToDouble(point -> point[1]).max().orElse(0));
        var rows = new ArrayList<float[]>();
        for (float y = top; y < bottom; y++) {
            float middle = y + 0.5f;
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            for (int index = 0; index < plate.size(); index++) {
                var a = plate.get(index);
                var b = plate.get((index + 1) % plate.size());
                if ((a[1] > middle) == (b[1] > middle)) continue;
                float x = a[0] + (middle - a[1]) * (b[0] - a[0]) / (b[1] - a[1]);
                left = Math.min(left, x);
                right = Math.max(right, x);
            }
            if (left <= right) rows.add(new float[] {y, left, right});
        }
        return rows;
    }
}
