package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.ClientPrograms;
import space.controlnet.ae2federation.automation.GameCatalog;
import space.controlnet.ae2federation.automation.LocalCopies;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.automation.RegistryResources;
import space.controlnet.ae2federation.client.automation.ScriptChecker;
import space.controlnet.ae2federation.client.automation.ScriptEditor;
import space.controlnet.ae2federation.function.ScriptParser;
import space.controlnet.ae2federation.function.ScriptProblems;

/**
 * The Automation tab, switched at the top between its Edit and Install views ({@link FederationInstallView}). The
 * edit view: the programs on the left with the Library under them
 * ({@link FederationLibraryPanel}), the opened ones as tabs above the script. Programs are
 * global; the header counts this domain's devices and what is installed on them. The page subscribes to the server's
 * program index while it is open ({@link ClientPrograms}), on the client only, since the tree is built on both sides.
 *
 * <p>Edits go to a local copy at once ({@link LocalCopies}) and to the server after a pause in typing, on switching
 * program and on closing, with no save button or sync indicator. The script is replaced from the server only while
 * it holds nothing unsent.
 *
 * <p>Every program's text is fetched, so the shown one is checked on the client ({@link ScriptChecker}) against the
 * others and every interface and network the server knows, as the server checks it. Its problems are marked in the
 * editor and listed under it; the other programs show the server's count.
 */
final class FederationAutomationView {
    /** Ticks without a key before an edit is uploaded. */
    private static final int UPLOAD_PAUSE = 20;

    /** Problem rows shown before the list scrolls. */
    private static final int PROBLEM_ROWS = 4;
    /** The problem count's colour, as the program list's. */
    private static final int PROBLEM_COLOR = 0xff6a75;
    /** The problem box's head and padding, and a row, as the style sheet sizes them. */
    private static final float PROBLEM_HEAD = 17;
    private static final float PROBLEM_ROW = 13;
    /** How long a stock answer is shown before it is asked for again. */
    private static final long STOCK_MILLIS = 5000;

    private final UI ui;
    private final boolean client;
    private final ScrollerView list;
    private final UIElement tabs;
    private final ScriptEditor editor = new ScriptEditor();
    private final UIElement problemBox;
    private final ScrollerView problemList;
    private final Button push;
    private final UIElement editView;
    private final UIElement installView;
    private final Button modeEdit;
    private final Button modeInstall;
    private final FederationInstallView install;
    private final FederationGraphView graphs;
    /** A function to put the caret on once its program's text is shown, after Open in editor. */
    private @Nullable String pendingJump;
    private final UIElement newRow;
    private final TextField newName;
    /** The files opened this time, in the order they were opened, and the one shown. */
    private final Set<String> opened = new LinkedHashSet<>();
    private @Nullable String selected;
    /** Whether the shown program's local copy has been compared with the server's version. */
    private boolean reconciled;
    /** The server version the editor's text started from. */
    private String base = "";
    /** The version last asked for, by program, so an index does not ask again while the text is on its way. */
    private final Map<String, String> asked = new HashMap<>();
    /** The shown program's problems, once checked: the client's count replaces the server's in the list. */
    private @Nullable List<ScriptProblems.Mark> problems;
    /** Edits not yet sent, an upload awaiting its answer, ticks since the last edit, and a push waiting for both. */
    private boolean dirty;
    private boolean sending;
    private int idle;
    private boolean pushAfterSend;
    private @Nullable LocalCopies copies;
    private String domain = "";
    /** The domain's stock of resources chosen in the completion list, and when each was asked for. */
    private final Map<String, Long> stock = new HashMap<>();
    private final Map<String, Long> stockAsked = new HashMap<>();
    private boolean open;

    FederationAutomationView(UI ui, boolean client) {
        this.ui = ui;
        this.client = client;
        list = element("auto_programs", ScrollerView.class);
        tabs = element("auto_file_tabs", UIElement.class);
        element("auto_script", UIElement.class).addChild(editor);
        // A new program is empty: it shows nothing in place of LDLib2's placeholder.
        editor.textAreaStyle(style -> style.placeholder(Component.empty()));
        editor.setDisplay(false);
        editor.setEditing(true);
        editor.setLinesResponder(lines -> edited());
        editor.setResourceNote(this::stockNote);
        editor.setCompleter((text, line, column, done) -> {
            var snapshot = snapshot();
            if (snapshot != null) ScriptChecker.complete(text, line, column, snapshot.others(), snapshot.catalog(), done);
        });
        push = element("auto_push", Button.class);
        push.setOnClick(event -> push());
        problemBox = element("auto_problems", UIElement.class);
        problemBox.setDisplay(false);
        problemList = element("auto_problem_list", ScrollerView.class);
        newRow = element("auto_new_row", UIElement.class);
        newRow.setDisplay(false);
        newName = element("auto_new_field", TextField.class);
        newName.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) create();
        });
        element("auto_new", Button.class).setOnClick(event -> {
            boolean show = !newRow.isDisplayed();
            newRow.setDisplay(show);
            newName.setText("", false);
            if (show) newName.focus();
        });
        new FederationLibraryPanel(ui);
        graphs = new FederationGraphView(ui, shown -> {
            if (shown) editor.hideCompletion();
        });
        editView = element("auto_edit_view", UIElement.class);
        installView = element("auto_install_view", UIElement.class);
        modeEdit = element("auto_mode_edit", Button.class);
        modeInstall = element("auto_mode_install", Button.class);
        modeEdit.setOnClick(event -> setInstalling(false));
        modeInstall.setOnClick(event -> setInstalling(true));
        install = new FederationInstallView(ui, this::answer, this::openInEditor);
        setInstalling(false);
        // An empty label would show LDLib2's default text until the first answer.
        clearAnswer();
        // The root always ticks; the editor does not while another page hides it.
        ui.rootElement.addEventListener(UIEvents.TICK, event -> tick());
    }

    /** Shows the Install view, or the Edit view; Push belongs to the Edit view. */
    private void setInstalling(boolean installing) {
        editView.setDisplay(!installing);
        installView.setDisplay(installing);
        push.setDisplay(!installing);
        modeEdit.removeClass("selected");
        modeInstall.removeClass("selected");
        (installing ? modeInstall : modeEdit).addClass("selected");
        if (installing) editor.hideCompletion();
    }

    /** Open in editor, from an installation: the Edit view with the program shown and the caret on the function. */
    private void openInEditor(String file, String function) {
        setInstalling(false);
        if (file.equals(selected) && reconciled) {
            jumpTo(function);
            return;
        }
        select(file);
        pendingJump = function;
        refresh();
    }

    /** The domain whose GUI this is; its devices and installations are the ones counted. */
    void setDomain(String domain) {
        if (this.domain.equals(domain)) return;
        this.domain = domain;
        stock.clear();
        stockAsked.clear();
        refresh();
    }

    /**
     * "in stock: 1,284" under a resource chosen in the completion list: how much this domain holds, asked of the
     * server when the resource is first chosen and again once the answer is a few seconds old.
     */
    private String stockNote(String resource) {
        if (domain.isEmpty()) return "";
        long now = System.currentTimeMillis();
        var asked = stockAsked.get(resource);
        if (asked == null || now - asked > STOCK_MILLIS) {
            stockAsked.put(resource, now);
            ClientPrograms.get().request("stock", List.of(domain, resource), result -> {
                if (result.ok()) stock.put(resource, Long.parseLong(result.message()));
            });
        }
        var amount = stock.get(resource);
        return amount == null ? "" : Component.translatable("ae2federation.ui.automation.in_stock",
                String.format(java.util.Locale.ROOT, "%,d", amount)).getString();
    }

    /** Subscribes to the index while the page is shown, on the client; leaving the page sends what is unsent. */
    void setOpen(boolean open) {
        if (!client || this.open == open) return;
        this.open = open;
        var programs = ClientPrograms.get();
        if (!open) {
            send();
            // Closing drops the answers still awaited: the next opening compares versions again instead.
            programs.open(false);
            sending = false;
            pushAfterSend = false;
            reconciled = false;
            asked.clear();
            ScriptChecker.cancel();
            editor.hideCompletion();
            return;
        }
        if (copies == null) copies = new LocalCopies(folder());
        programs.open(true);
        programs.listen(this::refresh);
        refresh();
    }

    /** The screen closed. */
    void close() {
        setOpen(false);
    }

    /** Where this server's or world's local copies live, under the game folder. */
    private static Path folder() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var root = minecraft.gameDirectory.toPath().resolve("ae2federation").resolve("programs");
        var local = minecraft.getSingleplayerServer();
        if (local != null) {
            var world = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return root.resolve(LocalCopies.key("world", world == null ? "world" : world.toString()));
        }
        var server = minecraft.getCurrentServer();
        return root.resolve(LocalCopies.key("server", server == null ? "unknown" : server.ip));
    }

    private void tick() {
        if (open && dirty && !sending && ++idle >= UPLOAD_PAUSE) send();
    }

    private String text() {
        return String.join("\n", editor.getLines());
    }

    private void edited() {
        if (!reconciled || selected == null || copies == null) return;
        dirty = true;
        idle = 0;
        copies.edited(selected, text(), base);
        check();
    }

    /** Uploads the shown program's unsent edits, if there are any and no upload is awaiting its answer. */
    private void send() {
        if (!dirty || sending || selected == null) return;
        var name = selected;
        var text = text();
        dirty = false;
        sending = true;
        ClientPrograms.get().upload(name, base, text, answer -> {
            sending = false;
            if (!answer.ok()) {
                answer(answer);
                pushAfterSend = false;
                return;
            }
            if (name.equals(selected)) {
                base = ProgramSync.version(text);
                if (copies != null) {
                    if (dirty) copies.edited(name, text(), base);
                    else copies.sent(name, text);
                }
            }
            if (pushAfterSend && name.equals(selected) && !dirty) {
                pushAfterSend = false;
                ClientPrograms.get().request("push", List.of(name), this::answer);
            }
        });
    }

    /** Pushes the shown program, once its edits have reached the server. */
    private void push() {
        if (selected == null) return;
        if (dirty || sending) {
            pushAfterSend = true;
            send();
            return;
        }
        ClientPrograms.get().request("push", List.of(selected), this::answer);
    }

    /** A new program, named in the field: an empty file the server saves, then opened. */
    private void create() {
        var name = newName.getValue().strip();
        newRow.setDisplay(false);
        if (name.isEmpty()) return;
        ClientPrograms.get().upload(name, "", "", answer -> {
            // The server sends the new index before the answer, so the program is listed by now.
            if (answer.ok()) {
                select(name);
                refresh();
            } else {
                answer(answer);
            }
        });
    }

    /** A server answer on the status line: refusals in red. Saves are silent and never shown. */
    private void answer(ProgramSync.Answer answer) {
        var label = element("automation_answer", Label.class);
        label.removeClass("refused");
        if (!answer.ok()) label.addClass("refused");
        label.setText(Component.literal(answer.message()));
    }

    private void clearAnswer() {
        element("automation_answer", Label.class).setText(Component.empty());
    }

    private void refresh() {
        if (!client) return;
        var index = ClientPrograms.get().index().orElse(null);
        var files = index == null ? List.<ProgramSync.FileInfo>of() : index.files();
        var names = files.stream().map(ProgramSync.FileInfo::name).collect(Collectors.toSet());
        opened.retainAll(names);
        if (selected != null && !names.contains(selected)) select(opened.stream().findFirst().orElse(null));
        updateSummary(index);
        fetchAll(files);
        showText(files);
        if (reconciled) check();
        updatePush(files);
        rebuildList(files);
        rebuildTabs();
        install.show(index, domain);
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

    /** Push is offered while the devices run another version than the server's, or edits are on their way. */
    private void updatePush(List<ProgramSync.FileInfo> files) {
        var file = files.stream().filter(candidate -> candidate.name().equals(selected)).findFirst().orElse(null);
        push.setActive(file != null && (!file.current() || dirty || sending));
    }

    /**
     * Shows the selected program: once its text has arrived, compared with the local copy; after that, replaced
     * from the server only while nothing is unsent.
     */
    private void showText(List<ProgramSync.FileInfo> files) {
        // Until a program is opened the box stays empty.
        editor.setDisplay(selected != null);
        if (selected == null || copies == null) return;
        var latest = files.stream().filter(file -> file.name().equals(selected)).map(ProgramSync.FileInfo::version)
                .findFirst().orElse(null);
        if (latest == null) return;
        var received = ClientPrograms.get().text(selected).orElse(null);
        boolean arrived = received != null && received.version().equals(latest);
        if (!reconciled) {
            if (!arrived) {
                fetch(selected, latest);
                return;
            }
            var opening = copies.open(selected, latest, received.text(), files.stream()
                    .map(ProgramSync.FileInfo::name).collect(Collectors.toSet()));
            editor.setValue(opening.text().split("\n", -1), false);
            base = latest;
            reconciled = true;
            if (pendingJump != null) jumpTo(pendingJump);
            pendingJump = null;
            if (opening.upload()) {
                dirty = true;
                idle = UPLOAD_PAUSE;
            }
            opening.movedTo().ifPresent(this::uploadMoved);
            return;
        }
        if (latest.equals(ProgramSync.version(text()))) {
            base = latest;
            return;
        }
        if (dirty || sending) return;
        if (!arrived) {
            fetch(selected, latest);
            return;
        }
        editor.setValue(received.text().split("\n", -1), false);
        base = latest;
        copies.sent(selected, received.text());
    }

    private void fetch(String name, String version) {
        if (version.equals(asked.get(name))) return;
        asked.put(name, version);
        ClientPrograms.get().fetch(name);
    }

    /** Asks for every program whose text here is not the server's version: the shown one is checked against them. */
    private void fetchAll(List<ProgramSync.FileInfo> files) {
        var programs = ClientPrograms.get();
        for (var file : files) {
            var received = programs.text(file.name()).orElse(null);
            if (received == null || !received.version().equals(file.version())) fetch(file.name(), file.version());
        }
    }

    /**
     * Checks the shown program on the worker against the other programs as received so far and every interface and
     * network on the server. A result for a text the editor no longer holds is dropped.
     */
    private void check() {
        var snapshot = snapshot();
        if (snapshot == null) return;
        var name = selected;
        var text = text();
        ScriptChecker.check(text, snapshot.others(), snapshot.catalog(), marks -> {
            if (name.equals(selected) && reconciled && text.equals(text())) showProblems(marks);
        });
        // The graph is drawn from the same text, and Graph is offered only while it parses.
        ScriptChecker.graph(text, snapshot.others(), snapshot.catalog(), answer -> {
            if (name.equals(selected) && reconciled && text.equals(text())) graphs.show(answer);
        });
    }

    /** What the shown program is checked and completed against, handed to the worker. */
    private record Snapshot(Map<String, String> others, GameCatalog catalog) {
    }

    /** The other programs as received so far, and every interface and network on the server; null before then. */
    private @Nullable Snapshot snapshot() {
        var index = ClientPrograms.get().index().orElse(null);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (!reconciled || selected == null || index == null || level == null) return null;
        var programs = ClientPrograms.get();
        var others = new HashMap<String, String>();
        index.files().stream().map(ProgramSync.FileInfo::name).filter(name -> !name.equals(selected))
                .forEach(name -> programs.text(name).ifPresent(received -> others.put(name, received.text())));
        var interfaces = index.interfaces().stream().map(info -> new GameCatalog.Named(info.id(), info.alias()))
                .toList();
        var networks = index.networks().stream().map(info -> new GameCatalog.Named(info.id(), info.name())).toList();
        return new Snapshot(others,
                new GameCatalog(() -> interfaces, () -> networks, new RegistryResources(level.registryAccess())));
    }

    /** Marks the problems in the editor, lists them under it, and counts them beside the program. */
    private void showProblems(List<ScriptProblems.Mark> marks) {
        boolean recount = problems == null || problems.size() != marks.size();
        problems = marks;
        editor.setProblems(marks);
        problemBox.setDisplay(!marks.isEmpty());
        element("auto_problems_title", Label.class).setText(Component.translatable("ae2federation.ui.automation.problems")
                .append(" ").append(Component.literal(String.valueOf(marks.size())).withColor(PROBLEM_COLOR)));
        problemList.clearAllScrollViewChildren();
        for (var mark : marks) {
            var row = new UIElement();
            row.addClass("program-row");
            row.addClass("problem-row");
            var line = new Label();
            line.addClass("problem-line");
            line.setText(Component.literal(String.valueOf(mark.line())));
            row.addChild(line);
            var message = new Label();
            message.addClass("program-name");
            message.setText(Component.literal(mark.message()));
            row.addChild(message);
            row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                graphs.setShown(false);
                editor.setCursor(mark.line() - 1, Math.max(0, mark.start() - 1));
            });
            problemList.addScrollViewChild(row);
        }
        problemBox.layout(layout -> layout.height(PROBLEM_HEAD + Math.min(marks.size(), PROBLEM_ROWS) * PROBLEM_ROW));
        if (recount) {
            var index = ClientPrograms.get().index().orElse(null);
            if (index != null) rebuildList(index.files());
        }
    }

    /** Edits made on a version the server no longer holds become a program of their own, beside the server's. */
    private void uploadMoved(String name) {
        var copy = copies.read(name).orElseThrow();
        ClientPrograms.get().upload(name, "", copy.text(), answer -> {
            if (answer.ok()) {
                copies.sent(name, copy.text());
                answer(new ProgramSync.Answer(answer.request(), true,
                        Component.translatable("ae2federation.ui.automation.moved", name).getString()));
            } else {
                answer(answer);
            }
        });
    }

    private void select(@Nullable String name) {
        send();
        selected = name;
        reconciled = false;
        dirty = false;
        pushAfterSend = false;
        if (name != null) asked.remove(name);
        problems = null;
        graphs.clear();
        editor.setProblems(List.of());
        editor.hideCompletion();
        problemBox.setDisplay(false);
        clearAnswer();
        if (name != null) opened.add(name);
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
            int count = chosen && problems != null ? problems.size() : file.problems();
            if (count > 0) {
                var label = new Label();
                label.addClass("program-problems");
                label.setText(Component.literal(String.valueOf(count)));
                row.addChild(label);
            }
            list.addScrollViewChild(row);
            if (!chosen) continue;
            var installed = installed(file.name());
            for (var function : functions(file)) {
                var line = row(function, "program-row function-row", () -> jumpTo(function));
                if (function.startsWith("_")) line.addClass("private");
                // Installed on this domain's devices: green when they run the current version, yellow until Push.
                if (installed.contains(function)) {
                    var mark = new UIElement();
                    mark.addClass("program-installed");
                    mark.style(style -> style.backgroundTexture(FederationTheme.solid(file.current()
                            ? FederationTheme.OK : FederationTheme.WARN)));
                    line.addChild(mark);
                }
                list.addScrollViewChild(line);
            }
        }
    }

    /** The functions of {@code file} installed on this domain's devices. */
    private Set<String> installed(String file) {
        var index = ClientPrograms.get().index().orElse(null);
        if (index == null) return Set.of();
        var devices = index.devices().stream().filter(device -> device.domain().equals(domain))
                .map(ProgramSync.DeviceInfo::id).collect(Collectors.toSet());
        return index.installations().stream().filter(installation -> installation.file().equals(file)
                && devices.contains(installation.device())).map(ProgramSync.InstallationInfo::function)
                .collect(Collectors.toSet());
    }

    /** The open file's functions, private ones included, from the editor once it shows it; until then the index's. */
    private List<String> functions(ProgramSync.FileInfo file) {
        if (!reconciled) return file.functions().stream().map(ProgramSync.FunctionInfo::name).toList();
        var names = new ArrayList<String>();
        ScriptParser.parse(text()).program().functions().forEach(function -> names.add(function.name()));
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

    /** Shows a function's definition, putting the caret at its start; in the graph view, its graph. */
    private void jumpTo(String function) {
        if (graphs.shown()) {
            graphs.showFunction(function);
            return;
        }
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
