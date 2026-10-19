package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.ClientPrograms;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.client.automation.ScriptEditor;
import space.controlnet.ae2federation.function.ScriptParser;

/**
 * The Automation tab's edit view: the programs on the left, the opened ones as tabs above the script. Programs are
 * global; the header counts this domain's devices and what is installed on them. The page subscribes to the server's
 * program index while it is open ({@link ClientPrograms}), on the client only, since the tree is built on both sides.
 */
final class FederationAutomationView {
    private final UI ui;
    private final boolean client;
    private final ScrollerView list;
    private final UIElement tabs;
    private final ScriptEditor editor = new ScriptEditor();
    /** The files opened this time, in the order they were opened, and the one shown. */
    private final Set<String> opened = new LinkedHashSet<>();
    private @Nullable String selected;
    /** The version of the text in the editor, so a newer one from the server replaces it. */
    private @Nullable String shownVersion;
    private String domain = "";
    private boolean open;

    FederationAutomationView(UI ui, boolean client) {
        this.ui = ui;
        this.client = client;
        list = element("auto_programs", ScrollerView.class);
        tabs = element("auto_file_tabs", UIElement.class);
        element("auto_editor", UIElement.class).addChild(editor);
        // A new program is empty: it shows nothing in place of LDLib2's placeholder.
        editor.textAreaStyle(style -> style.placeholder(Component.empty()));
        editor.setDisplay(false);
    }

    /** The domain whose GUI this is; its devices and installations are the ones counted. */
    void setDomain(String domain) {
        if (this.domain.equals(domain)) return;
        this.domain = domain;
        refresh();
    }

    /** Subscribes to the index while the page is shown, on the client. */
    void setOpen(boolean open) {
        if (!client || this.open == open) return;
        this.open = open;
        var programs = ClientPrograms.get();
        programs.open(open);
        if (open) {
            programs.listen(this::refresh);
            if (selected != null) programs.fetch(selected);
            refresh();
        }
    }

    /** The screen closed. */
    void close() {
        setOpen(false);
    }

    private void refresh() {
        if (!client) return;
        var index = ClientPrograms.get().index().orElse(null);
        var files = index == null ? List.<ProgramSync.FileInfo>of() : index.files();
        var names = files.stream().map(ProgramSync.FileInfo::name).collect(Collectors.toSet());
        opened.retainAll(names);
        if (selected != null && !names.contains(selected)) select(opened.stream().findFirst().orElse(null));
        updateSummary(index);
        showText(files);
        rebuildList(files);
        rebuildTabs();
    }

    private void updateSummary(@Nullable ProgramSync.Index index) {
        var label = element("automation_summary", Label.class);
        if (index == null) {
            label.setText(Component.empty());
            return;
        }
        var devices = index.devices().stream().filter(device -> device.domain().equals(domain))
                .map(ProgramSync.DeviceInfo::id).collect(Collectors.toSet());
        long installed = index.installations().stream().filter(installation -> devices.contains(installation.device())).count();
        label.setText(Component.translatable("ae2federation.ui.automation.summary", index.files().size(), devices.size(),
                installed));
    }

    /** Puts the selected file's text in the editor once it arrives, and asks again when the index has a newer one. */
    private void showText(List<ProgramSync.FileInfo> files) {
        // Until a program is opened the box stays empty.
        editor.setDisplay(selected != null);
        if (selected == null) {
            if (shownVersion != null) editor.setValue(new String[] {""}, false);
            shownVersion = null;
            return;
        }
        var received = ClientPrograms.get().text(selected).orElse(null);
        var latest = files.stream().filter(file -> file.name().equals(selected)).map(ProgramSync.FileInfo::version)
                .findFirst().orElse(null);
        if (received != null && !received.version().equals(shownVersion)) {
            editor.setValue(received.text().split("\n", -1), false);
            shownVersion = received.version();
        }
        if (latest != null && (received == null || !received.version().equals(latest))) ClientPrograms.get().fetch(selected);
    }

    private void select(@Nullable String name) {
        selected = name;
        shownVersion = null;
        if (name != null) {
            opened.add(name);
            if (client) ClientPrograms.get().fetch(name);
        }
        editor.setValue(new String[] {""}, false);
    }

    private void rebuildList(List<ProgramSync.FileInfo> files) {
        list.clearAllScrollViewChildren();
        if (files.isEmpty()) {
            var empty = new Label();
            empty.addClass("program-empty");
            empty.setText(Component.translatable("ae2federation.ui.automation.no_programs"));
            list.addScrollViewChild(empty);
            return;
        }
        for (var file : files) {
            boolean chosen = file.name().equals(selected);
            var row = row(file.name(), "program-row", () -> {
                if (!file.name().equals(selected)) select(file.name());
                refresh();
            });
            row.setId("auto_program_" + file.name());
            if (chosen) row.addClass("selected");
            if (file.problems() > 0) {
                var problems = new Label();
                problems.addClass("program-problems");
                problems.setText(Component.literal(String.valueOf(file.problems())));
                row.addChild(problems);
            }
            list.addScrollViewChild(row);
            if (!chosen) continue;
            for (var function : functions(file)) {
                var line = row(function, "program-row function-row", () -> jumpTo(function));
                if (function.startsWith("_")) line.addClass("private");
                list.addScrollViewChild(line);
            }
        }
    }

    /**
     * The open file's functions, private ones included, from its text once it arrived; until then the index's public
     * ones.
     */
    private List<String> functions(ProgramSync.FileInfo file) {
        var received = ClientPrograms.get().text(file.name()).orElse(null);
        if (received == null) return file.functions().stream().map(ProgramSync.FunctionInfo::name).toList();
        var names = new ArrayList<String>();
        ScriptParser.parse(received.text()).program().functions().forEach(function -> names.add(function.name()));
        return names;
    }

    private UIElement row(String name, String classes, Runnable action) {
        var row = new UIElement();
        for (var type : classes.split(" ")) row.addClass(type);
        var label = new Label();
        label.addClass("program-name");
        label.setText(Component.literal(name));
        row.addChild(label);
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> action.run());
        return row;
    }

    /** Shows a function's definition, putting the caret at its start. */
    private void jumpTo(String function) {
        var definition = Pattern.compile("^\\s*def\\s+" + Pattern.quote(function) + "\\b");
        var lines = editor.getLines();
        for (int line = 0; line < lines.size(); line++) {
            if (definition.matcher(lines.get(line)).find()) {
                editor.setCursor(line, 0);
                return;
            }
        }
    }

    private void rebuildTabs() {
        tabs.clearAllChildren();
        for (var name : opened) {
            var tab = new Button();
            tab.addClass("kind-chip");
            tab.addClass("file-tab");
            if (name.equals(selected)) tab.addClass("selected");
            var text = Component.literal(name);
            tab.setText(text);
            int width = net.minecraft.client.Minecraft.getInstance().font.width(text) + 8;
            tab.layout(layout -> layout.width(width));
            tab.setOnClick(event -> {
                if (name.equals(selected)) return;
                select(name);
                refresh();
            });
            tabs.addChild(tab);
        }
    }

    private <T> T element(String id, Class<T> type) {
        return ui.selectId(id, type).findFirst().orElseThrow(() -> new IllegalStateException("Missing UI element #" + id));
    }
}
