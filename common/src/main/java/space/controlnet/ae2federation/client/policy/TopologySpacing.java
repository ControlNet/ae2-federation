package space.controlnet.ae2federation.client.policy;

import java.util.List;

/**
 * How far a topology layout must be spread so that no network card overlaps another and no link label sits on a
 * card. Positions are the cards' top-left corners around the layout's origin; spreading multiplies them.
 */
public final class TopologySpacing {
    public static final float MAX_FACTOR = 3f;
    private static final float STEP = 0.1f;

    private TopologySpacing() {}

    /** A link label between cards {@code a} and {@code b}, centred on the link's middle. */
    public record Label(int a, int b, float halfWidth, float halfHeight) {}

    /** The smallest spread, in steps of 0.1 from 1, that leaves {@code margin} around every card; capped. */
    public static float factor(float[][] cards, List<Label> labels, float width, float height, float margin) {
        for (float factor = 1f; factor < MAX_FACTOR; factor = Math.round((factor + STEP) * 10) / 10f) {
            if (!overlaps(cards, factor, labels, width, height, margin)) return factor;
        }
        return MAX_FACTOR;
    }

    /** Whether, spread by {@code factor}, two cards touch or a label comes within {@code margin} of a card. */
    public static boolean overlaps(float[][] cards, float factor, List<Label> labels, float width, float height, float margin) {
        for (int first = 0; first < cards.length; first++) {
            for (int second = first + 1; second < cards.length; second++) {
                if (intersects(cards[first][0] * factor, cards[first][1] * factor, width, height,
                        cards[second][0] * factor - margin, cards[second][1] * factor - margin,
                        width + 2 * margin, height + 2 * margin)) return true;
            }
        }
        for (var label : labels) {
            var a = cards[label.a()];
            var b = cards[label.b()];
            var middle = TopologyLink.between(a[0] * factor, a[1] * factor, b[0] * factor, b[1] * factor, width, height).middle();
            for (var card : cards) {
                if (intersects(middle[0] - label.halfWidth() - margin, middle[1] - label.halfHeight() - margin,
                        2 * (label.halfWidth() + margin), 2 * (label.halfHeight() + margin),
                        card[0] * factor, card[1] * factor, width, height)) return true;
            }
        }
        return false;
    }

    /**
     * Where along its link each label sits, as {@code t} from 0 to 1: the middle when it is free, otherwise the first
     * spot further out that clears every card and the labels already placed, as crossing links would otherwise stack
     * their labels. Labels are placed in order; one with no free spot stays in the middle.
     */
    public static float[] labelSpots(float[][] cards, List<Label> labels, float width, float height, float margin) {
        var spots = new float[labels.size()];
        var placed = new java.util.ArrayList<float[]>();
        for (int index = 0; index < labels.size(); index++) {
            var label = labels.get(index);
            var a = cards[label.a()];
            var b = cards[label.b()];
            var link = TopologyLink.between(a[0], a[1], b[0], b[1], width, height);
            spots[index] = 0.5f;
            for (float t : SPOTS) {
                var at = link.withLabelAt(t).label();
                float x = at[0] - label.halfWidth() - margin;
                float y = at[1] - label.halfHeight() - margin;
                float w = 2 * (label.halfWidth() + margin);
                float h = 2 * (label.halfHeight() + margin);
                boolean clear = true;
                for (var card : cards) clear &= !intersects(x, y, w, h, card[0], card[1], width, height);
                for (var other : placed) clear &= !intersects(x, y, w, h, other[0], other[1], other[2], other[3]);
                if (clear) {
                    spots[index] = t;
                    break;
                }
            }
            var at = link.withLabelAt(spots[index]).label();
            placed.add(new float[] {at[0] - label.halfWidth(), at[1] - label.halfHeight(), 2 * label.halfWidth(), 2 * label.halfHeight()});
        }
        return spots;
    }

    private static final float[] SPOTS = {0.5f, 0.35f, 0.65f, 0.25f, 0.75f, 0.15f, 0.85f};

    private static boolean intersects(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }
}
