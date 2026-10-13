package space.controlnet.ae2federation.bundle;

import net.minecraft.network.chat.Component;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;

/** The action bar lines of forming, extending and branching bundles, shared by the client preview and the server. */
public final class BundleText {
    private static final String PREFIX = "ae2federation.bundle.";
    /** Create's super glue colours: the selection, a problem, and a confirmed bundle. */
    public static final int HIGHLIGHT = 0x68c586;
    public static final int FAIL = 0xc5b548;
    public static final int CONFIRMED = 0xB5F2C6;
    public static final int PASSIVE = 0x4D9162;

    private BundleText() {
    }

    public static Component firstCorner() {
        return Component.translatable(PREFIX + "select.first");
    }

    public static Component section(BundleShape shape) {
        return highlight(Component.translatable(PREFIX + "select.section", shape.narrow(), shape.wide()));
    }

    public static Component box(BundleSegment segment) {
        var shape = segment.shape().orElseThrow();
        return highlight(Component.translatable(PREFIX + "select.box", shape.narrow(), shape.wide(), segment.length()));
    }

    public static Component cancelled() {
        return Component.translatable(PREFIX + "select.cancelled");
    }

    public static Component problem(BundleSelection.Problem problem) {
        var key = PREFIX + "problem." + problem.name().toLowerCase(java.util.Locale.ROOT);
        return fail(problem == BundleSelection.Problem.TOO_LONG
                ? Component.translatable(key, BundleSelection.MAX_LENGTH) : Component.translatable(key));
    }

    /** {@code count} of {@code total} positions hold no Federation Cable, or one already bundled. */
    public static Component cells(BundledCables.Problem problem, int count, int total) {
        var key = PREFIX + "problem." + problem.name().toLowerCase(java.util.Locale.ROOT) + (count == 1 ? ".one" : "");
        return fail(Component.translatable(key, count, total));
    }

    public static Component formed(BundleSegment segment) {
        var shape = segment.shape().orElseThrow();
        var factor = shape.multiplier().orElse(FederationDomainNodeEvidence.UNLIMITED);
        var capacity = CableCapacityLimit.current(factor);
        if (shape.multiplier().isEmpty()) {
            return highlight(Component.translatable(PREFIX + "formed.unlimited", shape.narrow(), shape.wide(),
                    segment.length()));
        }
        return highlight(capacity.isPresent()
                ? Component.translatable(PREFIX + "formed", shape.narrow(), shape.wide(), segment.length(),
                        capacity.getAsInt())
                : Component.translatable(PREFIX + "formed.unenforced", shape.narrow(), shape.wide(), segment.length()));
    }

    /** "Right-click to extend · uses 4 Federation Cables", or to branch. */
    public static Component placement(boolean extend, int cost) {
        return highlight(Component.translatable(PREFIX + (extend ? "place.extend" : "place.branch"), cost));
    }

    public static Component refused(BundleSet.Problem problem, int layers) {
        var key = PREFIX + "place." + problem.name().toLowerCase(java.util.Locale.ROOT);
        return fail(problem == BundleSet.Problem.TOO_SHORT ? Component.translatable(key, layers)
                : Component.translatable(key));
    }

    public static Component blocked() {
        return fail(Component.translatable(PREFIX + "place.blocked"));
    }

    public static Component notEnough(int needed, int held) {
        return fail(Component.translatable(PREFIX + "place.not_enough", needed, held));
    }

    public static Component tooFar() {
        return fail(Component.translatable(PREFIX + "problem.too_far"));
    }

    private static Component highlight(net.minecraft.network.chat.MutableComponent text) {
        return text.withStyle(style -> style.withColor(HIGHLIGHT));
    }

    private static Component fail(net.minecraft.network.chat.MutableComponent text) {
        return text.withStyle(style -> style.withColor(FAIL));
    }
}
