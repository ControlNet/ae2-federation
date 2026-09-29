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

    private static boolean intersects(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }
}
