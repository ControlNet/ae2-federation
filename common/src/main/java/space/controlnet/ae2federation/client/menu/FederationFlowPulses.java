package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.List;
import java.util.function.Supplier;
import space.controlnet.ae2federation.client.policy.TopologyLink;

/**
 * Teal dots moving from the providing network to the consumer along each link that delivered something in the flow
 * window. It sits above the links and below the link labels and cards; dots under a label are not drawn.
 */
public final class FederationFlowPulses extends UIElement {
    private static final long PERIOD_MILLIS = 1500;
    private static final int DOTS = 3;

    private final Supplier<List<Flow>> flows;
    private int drawnDots;

    /** One direction of a link that delivered something; {@code returning} runs from the link's end to its start. */
    record Flow(TopologyLink link, boolean returning, float labelHalfWidth, float labelHalfHeight) {
    }

    FederationFlowPulses(Supplier<List<Flow>> flows) {
        this.flows = flows;
        setId("graph_flow_pulses");
        // Decoration only: clicks go to the cards and links underneath.
        setAllowHitTest(false);
    }

    /** Dots drawn in the last frame, for tests and diagnostics. */
    public int drawnDots() {
        return drawnDots;
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        int drawn = 0;
        float phase = (System.currentTimeMillis() % PERIOD_MILLIS) / (float) PERIOD_MILLIS;
        var graphics = context.graphics;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(getPositionX(), getPositionY(), 0);
        for (var flow : flows.get()) {
            var dots = flow.link().dots(phase, DOTS, flow.returning(), flow.labelHalfWidth(), flow.labelHalfHeight());
            for (int index = 0; index < dots.length; index += 2) {
                pose.pushPose();
                pose.translate(dots[index], dots[index + 1], 0);
                graphics.fill(-3, -3, 3, 3, 0xff0b0a12);
                graphics.fill(-2, -2, 2, 2, FederationTheme.TEAL);
                pose.popPose();
                drawn++;
            }
        }
        pose.popPose();
        drawnDots = drawn;
    }
}
