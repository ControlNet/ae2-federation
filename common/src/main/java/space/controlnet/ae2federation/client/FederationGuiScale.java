package space.controlnet.ae2federation.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.policy.FixedGuiScale;

/**
 * Keeps Federation screens at {@link FixedGuiScale} whatever the player's GUI scale option is, and gives the option
 * back as soon as another screen, or none, is shown.
 *
 * <p>The window's own scale is switched rather than scaling the pose: mouse events, tooltips, scissor boxes and the
 * UI test driver then all agree on one logical coordinate space. This is the same space vanilla uses; only the
 * number comes from the window size instead of the option.
 */
public final class FederationGuiScale {
    /** Marks the ModularUI of a screen that uses the fixed scale. */
    public interface Fixed {
    }

    private FederationGuiScale() {
    }

    /** Before the screen is initialised, so its first layout already has the fixed logical size. */
    public static void onOpening(ScreenEvent.Opening event) {
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        window.setGuiScale(desiredScale(minecraft, event.getNewScreen()));
    }

    /**
     * Every frame, because vanilla recomputes the scale from the option on window resizes and when the options screen
     * changes it; a Federation screen takes its own back on the next frame.
     */
    public static void onFrame(RenderFrameEvent.Pre event) {
        var minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        int desired = desiredScale(minecraft, minecraft.screen);
        if (window.getGuiScale() == desired) return;
        window.setGuiScale(desired);
        if (minecraft.screen != null) minecraft.screen.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    static boolean isFixed(@Nullable Screen screen) {
        return screen instanceof ModularUIContainerScreen container && container.getMenu().modularUI instanceof Fixed;
    }

    private static int desiredScale(Minecraft minecraft, @Nullable Screen screen) {
        var window = minecraft.getWindow();
        return isFixed(screen) ? FixedGuiScale.forFramebuffer(window.getWidth(), window.getHeight())
                : window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode());
    }
}
