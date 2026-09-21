package space.controlnet.ae2federation.test.multiclient;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.uitest.capture.FrameCapture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL11;

public final class MultiClientClientHarness {
    private static final String ROLE = System.getProperty("ae2federation.multiclient.role", "");
    private static final Path OUTPUT = Path.of(System.getProperty("ae2federation.multiclient.clientOutput", ""));
    private static final Path SERVER_EVIDENCE = Path.of(
            System.getProperty("ae2federation.multiclient.serverEvidence", ""));
    private static int screenTicks;
    private static boolean firstAccepted;
    private static boolean staleRevision;
    private static boolean staleContext;
    private static boolean refreshed;
    private static boolean stopped;
    private static boolean connecting;
    private static int startupTicks;
    private static boolean firstMenuClosed;
    private static boolean firstClickSent;
    private static boolean conflictClickSent;
    private static boolean splitClickSent;
    private static boolean staleRevisionCaptured;
    private static boolean staleContextCaptured;
    private static boolean staleRevisionSignaled;
    private static boolean refreshedCaptured;
    private static int staleRevisionStableFrames;
    private static int staleContextStableFrames;
    private static int refreshedStableFrames;
    private static String staleRevisionRenderedText = "";
    private static String staleContextRenderedText = "";
    private static String refreshedRenderedStatus = "";
    private static String refreshedRenderedMembers = "";

    private MultiClientClientHarness() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(MultiClientClientHarness::tick);
        NeoForge.EVENT_BUS.addListener(MultiClientClientHarness::rendered);
    }

    private static void tick(ClientTickEvent.Post event) {
        if (stopped) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null && minecraft.player == null) {
            startupTicks++;
            if (!connecting && startupTicks >= 20 && minecraft.screen != null) {
                connecting = true;
                var endpoint = "127.0.0.1:" + System.getProperty("ae2federation.multiclient.port");
                ConnectScreen.startConnecting(minecraft.screen, minecraft, ServerAddress.parseString(endpoint),
                        new ServerData("Task 34", endpoint, ServerData.Type.OTHER), false, null);
            }
            return;
        }
        minecraft.getToasts().clear();
        minecraft.getTutorial().stop();
        minecraft.gui.getChat().clearMessages(true);
        if (minecraft.screen instanceof ModularUIContainerScreen screen) {
            if (!firstMenuClosed) {
                screenTicks++;
                firstScreen(minecraft, screen);
            } else {
                screenTicks++;
                refreshedScreen(minecraft, screen);
            }
        } else {
            screenTicks = 0;
            if (ROLE.equals("A") && refreshed && clientFinal("b")) {
                stop(minecraft);
            }
        }
    }

    private static void firstScreen(Minecraft minecraft, ModularUIContainerScreen screen) {
        if (ROLE.equals("A") && !firstClickSent && screenTicks >= 20 && element(screen, "policy_toggle").isActive()) {
            click(screen, "policy_toggle");
            firstClickSent = true;
        }
        if (ROLE.equals("B") && !conflictClickSent && Files.isRegularFile(marker("revision-one.ready"))
                && element(screen, "policy_toggle").isActive()) {
            click(screen, "policy_toggle");
            conflictClickSent = true;
            consumeMarker("revision-one.ready");
        }
        var status = element(screen, "ack_status");
        if (ROLE.equals("A") && firstClickSent) {
            firstAccepted |= status.hasClass("accepted");
        }
        if (ROLE.equals("B") && conflictClickSent) {
            staleRevision |= status.hasClass("stale_revision");
        }
        if (ROLE.equals("A") && !splitClickSent && firstAccepted
                && Files.isRegularFile(marker("fabric-split.ready"))
                && element(screen, "policy_toggle").isActive()) {
            click(screen, "policy_toggle");
            splitClickSent = true;
            consumeMarker("fabric-split.ready");
        }
        if (ROLE.equals("A") && splitClickSent) {
            staleContext |= status.hasClass("stale_context");
        }
        var completed = ROLE.equals("A") ? firstAccepted && staleContext : staleRevision;
        var captured = ROLE.equals("A") ? staleContextCaptured : staleRevisionCaptured;
        if (screenTicks >= 220 && completed && captured) {
            write(false);
            firstMenuClosed = true;
            screenTicks = 0;
            screen.onClose();
            minecraft.setScreen(null);
        }
    }

    private static void rendered(ScreenEvent.Render.Post event) {
        if (stopped || !(event.getScreen() instanceof ModularUIContainerScreen screen)) {
            return;
        }
        if (firstMenuClosed) {
            renderedRefreshed(screen);
            return;
        }
        var status = element(screen, "ack_status");
        var renderedText = text(screen, "ack_status");
        if (ROLE.equals("B") && staleRevision && !staleRevisionCaptured) {
            var expectedText = Component.translatable("ae2federation.ui.fabric.status.stale_revision", 1).getString();
            staleRevisionStableFrames = status.hasClass("stale_revision") && renderedText.equals(expectedText)
                    ? staleRevisionStableFrames + 1
                    : 0;
            if (staleRevisionStableFrames >= 3) {
                staleRevisionRenderedText = renderedText;
                capture("client-b-stale-revision.png");
                staleRevisionCaptured = true;
                signal("stale-revision.ready");
                staleRevisionSignaled = true;
            }
        }
        if (ROLE.equals("A") && staleContext && !staleContextCaptured) {
            var expectedText = Component.translatable("ae2federation.ui.fabric.status.stale_context").getString();
            staleContextStableFrames = status.hasClass("stale_context") && renderedText.equals(expectedText)
                    ? staleContextStableFrames + 1
                    : 0;
            if (staleContextStableFrames >= 3) {
                staleContextRenderedText = renderedText;
                capture("client-a-fabric-split.png");
                staleContextCaptured = true;
            }
        }
    }

    private static void renderedRefreshed(ModularUIContainerScreen screen) {
        if (refreshedCaptured) {
            return;
        }
        var status = element(screen, "ack_status");
        var renderedStatus = text(screen, "ack_status");
        var renderedMembers = text(screen, "members_value");
        var expectedStatus = Component.translatable("ae2federation.ui.fabric.status.ready").getString();
        var expectedMembers = Component.translatable("ae2federation.ui.fabric.members", 2).getString();
        var expectedEntrance = Component.translatable("ae2federation.ui.fabric.entrance.hub").getString();
        var memberLines = renderedMembers.lines().toList();
        var membersCurrent = memberLines.size() == 3 && memberLines.getFirst().equals(expectedMembers)
                && memberLines.stream().skip(1).noneMatch(String::isBlank)
                && memberLines.stream().skip(1).distinct().count() == 2;
        var current = status.hasClass("ready") && renderedStatus.equals(expectedStatus)
                && membersCurrent
                && text(screen, "entrance_value").equals(expectedEntrance)
                && element(screen, "policy_toggle").isActive();
        refreshedStableFrames = current ? refreshedStableFrames + 1 : 0;
        if (refreshedStableFrames >= 3) {
            refreshed = true;
            refreshedRenderedStatus = renderedStatus;
            refreshedRenderedMembers = memberLines.getFirst();
            capture("client-" + ROLE.toLowerCase(java.util.Locale.ROOT) + "-refreshed.png");
            refreshedCaptured = true;
        }
    }

    private static void refreshedScreen(Minecraft minecraft, ModularUIContainerScreen screen) {
        if (refreshedCaptured) {
            write(true);
            screen.onClose();
            minecraft.setScreen(null);
            stop(minecraft);
        }
    }

    private static void click(ModularUIContainerScreen screen, String id) {
        var element = element(screen, id);
        var click = UIEvent.create(UIEvents.MOUSE_DOWN);
        click.target = element;
        click.button = 0;
        UIEventDispatcher.dispatchEvent(click);
    }

    private static String text(ModularUIContainerScreen screen, String id) {
        var element = element(screen, id);
        return element instanceof TextElement text ? text.getText().getString() : element.toString();
    }

    private static UIElement element(ModularUIContainerScreen screen, String id) {
        var element = screen.getMenu().modularUI.getElementById(id);
        if (element == null) {
            throw new IllegalStateException("Missing Task 34 production UI element #" + id);
        }
        return element;
    }

    private static void capture(String name) {
        var image = FrameCapture.grab();
        try {
            Files.createDirectories(OUTPUT);
            if (FrameCapture.write(image, OUTPUT.resolve(name))) {
                throw new IllegalStateException("Task 34 screenshot is uniform: " + name);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not capture Task 34 screenshot " + name, exception);
        } finally {
            FrameCapture.closeQuietly(image);
        }
    }

    private static Path marker(String name) {
        return OUTPUT.resolve(name);
    }

    private static void signal(String name) {
        try {
            Files.createDirectories(OUTPUT);
            Files.writeString(marker(name), "ready\n");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Task 34 phase receipt " + name, exception);
        }
    }

    private static void consumeMarker(String name) {
        try {
            Files.deleteIfExists(marker(name));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot consume Task 34 phase receipt " + name, exception);
        }
    }

    private static boolean clientFinal(String role) {
        var path = OUTPUT.resolve("client-" + role + ".properties");
        if (!Files.isRegularFile(path)) {
            return false;
        }
        var properties = new Properties();
        try (var input = Files.newInputStream(path)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Task 34 client evidence " + path, exception);
        }
        return properties.getProperty("finalState", "false").equals("true");
    }

    private static void write(boolean finalState) {
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("role", ROLE);
        properties.setProperty("realConnection", "true");
        properties.setProperty("singleplayer", Boolean.toString(Minecraft.getInstance().hasSingleplayerServer()));
        properties.setProperty("firstAccepted", Boolean.toString(firstAccepted));
        properties.setProperty("staleRevision", Boolean.toString(staleRevision));
        properties.setProperty("staleContext", Boolean.toString(staleContext));
        properties.setProperty("staleRevisionRenderedText", staleRevisionRenderedText);
        properties.setProperty("staleRevisionStableFrames", Integer.toString(staleRevisionStableFrames));
        properties.setProperty("staleContextRenderedText", staleContextRenderedText);
        properties.setProperty("staleContextStableFrames", Integer.toString(staleContextStableFrames));
        properties.setProperty("refreshedRenderedStatus", refreshedRenderedStatus);
        properties.setProperty("refreshedRenderedMembers", refreshedRenderedMembers);
        properties.setProperty("refreshedStableFrames", Integer.toString(refreshedStableFrames));
        properties.setProperty("scopeRefreshed", Boolean.toString(refreshed));
        properties.setProperty("finalState", Boolean.toString(finalState));
        var minecraft = Minecraft.getInstance();
        properties.setProperty("guiScale", Double.toString(minecraft.getWindow().getGuiScale()));
        properties.setProperty("windowWidth", Integer.toString(minecraft.getWindow().getScreenWidth()));
        properties.setProperty("windowHeight", Integer.toString(minecraft.getWindow().getScreenHeight()));
        properties.setProperty("framebufferWidth", Integer.toString(minecraft.getWindow().getWidth()));
        properties.setProperty("framebufferHeight", Integer.toString(minecraft.getWindow().getHeight()));
        properties.setProperty("language", minecraft.getLanguageManager().getSelected());
        properties.setProperty("glVendor", java.util.Objects.toString(GL11.glGetString(GL11.GL_VENDOR), ""));
        properties.setProperty("glRenderer", java.util.Objects.toString(GL11.glGetString(GL11.GL_RENDERER), ""));
        properties.setProperty("glVersion", java.util.Objects.toString(GL11.glGetString(GL11.GL_VERSION), ""));
        var path = OUTPUT.resolve("client-" + ROLE.toLowerCase(java.util.Locale.ROOT) + ".properties");
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation multi-client evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write Task 34 client evidence", exception);
        }
    }

    private static void stop(Minecraft minecraft) {
        stopped = true;
        minecraft.stop();
    }
}
