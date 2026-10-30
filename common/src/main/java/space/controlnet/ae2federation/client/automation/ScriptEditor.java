package space.controlnet.ae2federation.client.automation;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.data.Cursor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.StyledText;
import com.lowdragmc.lowdraglib2.gui.ui.event.CommandEvents;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.utils.HistoryStack;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.ResourceStrings;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.Completions;
import space.controlnet.ae2federation.script.DryRunNotes;
import space.controlnet.ae2federation.script.ScriptProblems;

/**
 * The Automation tab's script editor: LDLib2's code editor with the scripts' colours ({@link PythonLanguage}) and a
 * line-number gutter in the left padding of its content view, widened to fit the numbers. Python's habits: Tab
 * indents by four spaces, a new line after a colon indents once more, and Ctrl+/ comments lines with {@code #}. It
 * only shows a program until editing is turned on. Problems are marked beside their line number and underlined.
 * A string naming an item or a fluid has its icon before it, in room the editor makes there: the caret, the
 * selection and clicks measure the line with that room ({@link #styledLineComponent}).
 *
 * <p>Brackets and quotes come in pairs ({@link Pairs}).
 *
 * <p>Typing a name, a dot or a quote asks its {@link Completer} for candidates (Ctrl+Space asks anywhere);
 * they show in a {@link CompletionPopup} under the caret. Up and Down choose, Tab accepts, Enter accepts unless the
 * choice is what is typed already, Escape closes. An answer for a text or caret that has since changed is dropped.
 *
 * <p>A dry run's values ({@link DryRunNotes}) are written in grey after their lines, an error in red, while the text
 * is the one they were found for; a value naming an item has its icon before it.
 */
public final class ScriptEditor extends CodeEditor {
    private static final int GUTTER = 0xff17141e;
    private static final int NUMBER = 0xff6f6a7a;
    private static final int PROBLEM = 0xffff6a75;
    /** The square beside a problem's line number, and the wave under its text, one column high per step. */
    private static final float MARK = 3;
    private static final int[] WAVE = {0, 1, 2, 1};
    /** A resource's icon before its string, and the room around it. */
    private static final float ICON = 8;
    private static final float ICON_GAP = 2;
    /** The narrowest gutter, the room on either side of its numbers, and between the gutter and the text. */
    private static final float MIN_GUTTER = 16;
    private static final float NUMBER_GAP = 3;
    private static final float TEXT_GAP = 4;
    /** Between a line's end and its dry-run note. */
    private static final float NOTE_GAP = 8;
    private static final String INDENT = "    ";
    private static final String COMMENT = "# ";

    /**
     * Undo and redo, kept here: TextArea keeps its history in a private stack no call clears, and this one editor shows
     * every program in turn, so an undo after opening another program would bring back the last one's text and save it
     * under the new name. Every edit records through {@link #pushHistory}, so the private stack stays unused.
     */
    private final HistoryStack<History> history = new HistoryStack<>(100);

    /** Completes at a place in a text (line and column from 1); the answer comes later, on the client thread. */
    public interface Completer {
        void complete(String text, int line, int column, Consumer<Completions> done);
    }

    private boolean editing;
    private boolean numbered = true;
    /** The lines shown to be read, without the room at their ends; null while the editor edits. */
    private String @Nullable [] readOnly;
    private @Nullable Completer completer;
    private @Nullable CompletionPopup popup;
    private java.util.function.Function<String, String> resourceNote = id -> "";
    /** The text and caret the shown completion was asked for; accepting it needs them unchanged. */
    private @Nullable String shownText;
    private int shownLine;
    private int shownColumn;
    /** Whether the screen closed on Escape before the list opened: while it is open, Escape only closes the list. */
    private @Nullable Boolean closedOnEscape;
    private List<ScriptProblems.Mark> problems = List.of();
    /** The dry run's notes by line from 1, and the lines they were found for. */
    private Map<Integer, DryRunNotes.Note> notes = Map.of();
    private List<String> notedLines = List.of();
    /** The content view's left padding as last set: the gutter and the gap after it. */
    private float padding = -1;
    /** When the caret last moved; it blinks from then, so a moved caret shows at once. */
    private long caretMoved;

    public ScriptEditor() {
        setLanguage(PythonLanguage.INSTANCE);
        setStyleManager(PythonLanguage.INSTANCE.styles());
        addClass("script-editor");
    }

    /** Whether keys change the text; the text can be selected and scrolled either way. */
    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    /** Whether the gutter with the line numbers shows; without it the text starts after a small gap. */
    public void setNumbered(boolean numbered) {
        this.numbered = numbered;
    }

    /**
     * Shows {@code lines} to be read, not edited. Each line ends with blank room as wide as its icons' room, measured
     * again when the font changes: LDLib2 scrolls over the plain text's width, so a line's end would otherwise stay
     * out of reach.
     */
    public void setReadOnlyLines(String[] lines) {
        setEditing(false);
        readOnly = lines.clone();
        if (LDLib2.isClient()) padReadOnly();
    }

    @Override
    protected void onTextAreaStyleChanged() {
        super.onTextAreaStyleChanged();
        if (readOnly != null && LDLib2.isClient()) padReadOnly();
    }

    @OnlyIn(Dist.CLIENT)
    private void padReadOnly() {
        setValue(readOnly, false);
        var padded = new String[readOnly.length];
        for (int line = 0; line < readOnly.length; line++) {
            padded[line] = readOnly[line] + room().repeat(resources(line).size());
        }
        setValue(padded, false);
    }

    @Override
    public void pushHistory() {
        history.record(new History(getValue(), cursorPos()));
    }

    /** Another text is shown now: undo cannot go back past it. */
    public void clearHistory() {
        history.clear();
    }

    /** As TextArea's undo and redo, on this editor's own history; the view keeps its scroll. */
    @Override
    protected void onExecuteCommand(UIEvent event) {
        boolean undo = CommandEvents.UNDO.equals(event.command);
        if (!isEditable() || !undo && !CommandEvents.REDO.equals(event.command)) {
            return;
        }
        var current = getValue();
        if (history.getCurrent() == null || !java.util.Arrays.deepEquals(history.getCurrent().lines(), current)) {
            history.record(new History(current, cursorPos()));
        }
        if (undo ? history.undo() : history.redo()) {
            var back = history.getCurrent();
            lines.clear();
            lines.addAll(List.of(back.lines()));
            onRawLinesUpdated();
            setCursor(back.cursor().line(), back.cursor().col());
            collapseSelectionToCursor();
        }
    }

    /** The problems to mark, for the text as it is now. */
    public void setProblems(List<ScriptProblems.Mark> problems) {
        this.problems = List.copyOf(problems);
    }

    /** A dry run's notes of {@code lines}, by line from 1; shown while the text is those lines. */
    public void setDryNotes(List<String> lines, Map<Integer, DryRunNotes.Note> notes) {
        this.notedLines = List.copyOf(lines);
        this.notes = Map.copyOf(notes);
    }

    /** The dry-run notes shown now: none once the text is no longer what they were found for. */
    public Map<Integer, DryRunNotes.Note> shownNotes() {
        return lines.equals(notedLines) ? notes : Map.of();
    }

    public void setCompleter(Completer completer) {
        this.completer = completer;
    }

    /** What the completion list adds under a chosen resource, such as its stock; empty while unknown. */
    public void setResourceNote(java.util.function.Function<String, String> note) {
        this.resourceNote = note;
        if (popup != null) popup.setResourceNote(note);
    }

    /** The completion list, once one was shown; for tests. */
    public @Nullable CompletionPopup completionPopup() {
        return popup;
    }

    public boolean isCompleting() {
        return popup != null && popup.isOpen();
    }

    public void hideCompletion() {
        shownText = null;
        if (popup != null) popup.hide();
        var modular = getModularUI();
        if (closedOnEscape != null && modular != null) modular.shouldCloseOnEsc(closedOnEscape);
        closedOnEscape = null;
    }

    @Override
    public void setCursor(int line, int col) {
        super.setCursor(line, col);
        caretMoved = System.currentTimeMillis();
    }

    /** LDLib2's caret, blinking from the caret's last move instead of from the clock's second. */
    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawCursor(GUIContext context, Font font, ResourceLocation textFont, float scale, float x,
            float y) {
        if (!isVisible() || !isFocused() || !isDisplayed()) return;
        if (isActive() && !CaretBlink.shown(System.currentTimeMillis(), caretMoved)) return;
        float caretX = font.getSplitter().stringWidth(styledLineComponent(getCursorLine(), 0, getCursorCol())) * scale;
        DrawerHelper.drawSolidRect(context.graphics, x + caretX - getScrollX(),
                y + getCursorLine() * lineHeight() - getScrollY(), 1, getTextAreaStyle().fontSize(),
                getTextAreaStyle().cursorColor());
    }

    @Override
    public boolean isEditable() {
        return editing && super.isEditable();
    }

    private String text() {
        return String.join("\n", lines);
    }

    /** Asks for candidates at the caret; they show when they come, if the text and caret are still the same. */
    private void requestCompletion() {
        if (completer == null || !isEditable()) return;
        var text = text();
        int line = getCursorLine();
        int column = getCursorCol();
        completer.complete(text, line + 1, column + 1, completions -> {
            if (!text.equals(text()) || line != getCursorLine() || column != getCursorCol()) return;
            if (completions.candidates().isEmpty()) {
                hideCompletion();
                return;
            }
            showCompletion(completions, text, line, column);
        });
    }

    private void showCompletion(Completions completions, String text, int line, int column) {
        var modular = getModularUI();
        if (modular == null) return;
        var root = modular.ui.rootElement;
        if (popup == null) {
            popup = new CompletionPopup(getFont(), getTextAreaStyle().font(), scale(), lineHeight(), this::accept);
            popup.setResourceNote(resourceNote);
            root.addChild(popup);
        }
        shownText = text;
        shownLine = line;
        shownColumn = column;
        popup.show(completions);
        // The screen sees Escape before any element does: keep it open so Escape reaches the list.
        if (closedOnEscape == null) closedOnEscape = modular.shouldCloseOnEsc();
        modular.shouldCloseOnEsc(false);
        var under = columnPosition(completions.line() - 1, completions.column() - 1);
        var position = root.worldToLocalLayoutOffset(localToWorld(
                new Vector2f(under.x - CompletionPopup.TEXT_INSET, under.y + lineHeight() + 1)));
        popup.layout(layout -> {
            layout.left(position.x);
            layout.top(position.y);
        });
    }

    private boolean shownIsCurrent() {
        return shownText != null && shownText.equals(text()) && shownLine == getCursorLine()
                && shownColumn == getCursorCol();
    }

    /**
     * Puts the candidate in place of what is typed of it; a resource closes its string when nothing closes it yet.
     */
    private void accept(Candidate candidate) {
        var completions = popup == null ? null : popup.completions();
        if (completions == null || !shownIsCurrent()) {
            hideCompletion();
            return;
        }
        int line = completions.line() - 1;
        var text = lines.get(line);
        int from = Math.min(completions.column() - 1, text.length());
        int to = Math.max(from, getCursorCol());
        var insert = candidate.label();
        // A resource ends its string: the caret goes after the closing quote, added when there is none yet.
        int after = 0;
        if (candidate.kind() == Candidate.Kind.VALUE && from > 0) {
            char quote = text.charAt(from - 1);
            if (quote == '"' || quote == '\'') {
                if (to >= text.length() || text.charAt(to) != quote) {
                    insert += quote;
                } else {
                    after = 1;
                }
            }
        }
        pushHistory();
        lines.set(line, text.substring(0, from) + insert + text.substring(to));
        onRawLinesUpdated();
        setCursor(line, from + insert.length() + after);
        collapseSelectionToCursor();
        hideCompletion();
        focus();
    }

    /** A name, a dot or a quote asks; a colon only goes on with a list of resources (inside an ID). */
    private boolean asksForCompletion(char typed) {
        // Inside an ID ("minecraft:iron_ingot", "network-082a5c") the list of values stays open.
        if (typed == ':' || typed == '-') {
            var completions = isCompleting() ? popup.completions() : null;
            return completions != null && completions.candidates().get(0).kind() == Candidate.Kind.VALUE;
        }
        return Character.isLetterOrDigit(typed) || typed == '_' || typed == '.' || typed == '"' || typed == '\'';
    }

    @Override
    protected void onCharTyped(UIEvent event) {
        // Ctrl+Space asked for completion on key down; the space some platforms type with it is not text.
        if (event.codePoint == ' ' && event.isCtrlDown()) return;
        boolean asks = asksForCompletion(event.codePoint);
        var typed = isEditable() && !hasSelection() && Character.isBmpCodePoint(event.codePoint)
                ? Pairs.typed(lines.get(getCursorLine()), getCursorCol(), (char) event.codePoint) : Pairs.Typed.PLAIN;
        switch (typed) {
            case STEP -> {
                setCursor(getCursorLine(), getCursorCol() + 1);
                collapseSelectionToCursor();
                asks = false;
            }
            case PAIR -> {
                char opening = (char) event.codePoint;
                insertText(String.valueOf(opening) + Pairs.closing(opening));
                setCursor(getCursorLine(), getCursorCol() - 1);
                collapseSelectionToCursor();
            }
            case PLAIN -> super.onCharTyped(event);
        }
        if (!isEditable()) return;
        if (asks) {
            requestCompletion();
        } else {
            hideCompletion();
        }
    }

    @Override
    protected void onKeyDown(UIEvent event) {
        if (isEditable() && isCompleting() && completionKey(event)) return;
        if (isEditable() && event.keyCode == GLFW.GLFW_KEY_SPACE && event.isCtrlDown()) {
            requestCompletion();
            return;
        }
        if (isEditable() && event.keyCode == GLFW.GLFW_KEY_TAB) {
            insertText(INDENT);
            return;
        }
        boolean completing = isCompleting();
        boolean pair = isEditable() && event.keyCode == GLFW.GLFW_KEY_BACKSPACE && !hasSelection()
                && Pairs.emptyPairAt(lines.get(getCursorLine()), getCursorCol());
        super.onKeyDown(event);
        if (pair) {
            // Backspace took the opening character in one undo step; the closing one goes with it.
            var text = lines.get(getCursorLine());
            lines.set(getCursorLine(), text.substring(0, getCursorCol()) + text.substring(getCursorCol() + 1));
            int line = getCursorLine();
            int column = getCursorCol();
            onRawLinesUpdated();
            setCursor(line, column);
            collapseSelectionToCursor();
        }
        if (completing && (event.keyCode == GLFW.GLFW_KEY_BACKSPACE || event.keyCode == GLFW.GLFW_KEY_DELETE)) {
            requestCompletion();
        }
    }

    /** The keys the open completion list takes; false lets the editor have the key as well. */
    private boolean completionKey(UIEvent event) {
        switch (event.keyCode) {
            case GLFW.GLFW_KEY_UP -> popup.move(-1);
            case GLFW.GLFW_KEY_DOWN -> popup.move(1);
            case GLFW.GLFW_KEY_PAGE_UP -> popup.move(-CompletionPopup.ROWS);
            case GLFW.GLFW_KEY_PAGE_DOWN -> popup.move(CompletionPopup.ROWS);
            case GLFW.GLFW_KEY_TAB -> accept(popup.chosen());
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                var completions = popup.completions();
                if (!shownIsCurrent() || completions == null
                        || popup.chosen().label().equals(completions.prefix())) {
                    hideCompletion();
                    return false;
                }
                accept(popup.chosen());
            }
            case GLFW.GLFW_KEY_ESCAPE -> hideCompletion();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_END -> {
                hideCompletion();
                return false;
            }
            default -> {
                return false;
            }
        }
        event.stopPropagation();
        return true;
    }

    @Override
    protected void onMouseDown(UIEvent event) {
        hideCompletion();
        super.onMouseDown(event);
    }

    /** Copies the line's indentation, and indents once more after a line that opens a block. */
    @Override
    protected void insertNewLine() {
        var opener = lines.get(getCursorLine()).substring(0, getCursorCol()).strip();
        super.insertNewLine();
        if (getLanguage().shouldIncreaseIndentation(opener)) insertText(INDENT);
    }

    /** Comments the caret's line, or the selected lines, with {@code #}; uncomments them when all already are. */
    @Override
    protected void toggleCommentAtBol() {
        pushHistory();
        int first = hasSelection() ? Math.min(getSelStartLine(), getSelEndLine()) : getCursorLine();
        int last = hasSelection() ? Math.max(getSelStartLine(), getSelEndLine()) : getCursorLine();
        boolean commented = true;
        for (int line = first; line <= last; line++) {
            if (!lines.get(line).isBlank() && !lines.get(line).stripLeading().startsWith("#")) commented = false;
        }
        int caretShift = 0;
        for (int line = first; line <= last; line++) {
            var text = lines.get(line);
            int indent = text.length() - text.stripLeading().length();
            String changed;
            if (commented) {
                var rest = text.substring(indent);
                if (!rest.startsWith("#")) continue;
                changed = text.substring(0, indent) + rest.substring(rest.startsWith(COMMENT) ? COMMENT.length() : 1);
            } else {
                if (text.isBlank()) continue;
                changed = text.substring(0, indent) + COMMENT + text.substring(indent);
            }
            if (line == getCursorLine() && getCursorCol() > indent) caretShift = changed.length() - text.length();
            lines.set(line, changed);
        }
        int caretLine = getCursorLine();
        int caretCol = getCursorCol();
        onRawLinesUpdated();
        setCursor(caretLine, Math.max(0, caretCol + caretShift));
    }

    /** The strings on {@code line} that name an item or a fluid. */
    @OnlyIn(Dist.CLIENT)
    private List<ResourceStrings.At> resources(int line) {
        var styled = getStyledLines();
        if (line < 0 || line >= styled.size()) return List.of();
        return ResourceStrings.find(styled.get(line).text().stream().map(StyledText::text).toList(),
                id -> !CompletionPopup.icon(id).isEmpty());
    }

    /** The room before a resource's string: enough spaces in the text's font for the icon and a gap each side. */
    @OnlyIn(Dist.CLIENT)
    private String room() {
        float space = getFont().getSplitter().stringWidth(inFont(" ")) * scale();
        return " ".repeat(Math.max(1, (int) Math.ceil((ICON + 2 * ICON_GAP) / space)));
    }

    /** As drawn: the room before each resource's string counts as part of the string's first column. */
    @Override
    @OnlyIn(Dist.CLIENT)
    protected Component styledLineComponent(int line, int from, int to) {
        var resources = resources(line);
        if (resources.isEmpty()) return super.styledLineComponent(line, from, to);
        var result = Component.empty();
        int at = from;
        for (var resource : resources) {
            if (resource.column() < from || resource.column() > to) continue;
            result.append(super.styledLineComponent(line, at, resource.column())).append(inFont(room()));
            at = resource.column();
        }
        return result.append(super.styledLineComponent(line, at, to));
    }

    /** The code editor's coloured segments, leaving the room before each resource's string. */
    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawLines(GUIContext context, Font font, ResourceLocation textFont, float scale, float x, float y,
            int firstVisibleLine, int lastVisibleLine) {
        var styled = getStyledLines();
        float room = font.getSplitter().stringWidth(inFont(room())) * scale;
        for (int line = firstVisibleLine; line <= lastVisibleLine && line < styled.size(); line++) {
            float lineY = y + line * lineHeight() - getScrollY();
            float drawX = x - getScrollX();
            var resources = resources(line);
            int column = 0;
            for (var segment : styled.get(line).text()) {
                int at = column;
                if (resources.stream().anyMatch(resource -> resource.column() == at)) drawX += room;
                var text = inFont(segment.text()).withStyle(segment.style());
                context.pose.pushPose();
                context.pose.translate(drawX, lineY, 0);
                context.pose.scale(scale, scale, 1);
                LDLibFonts.drawText(context.graphics, font, text, 0, 0, -1, getTextAreaStyle().textShadow());
                context.pose.popPose();
                drawX += font.getSplitter().stringWidth(text) * scale;
                column += segment.text().length();
            }
        }
        if (styled.isEmpty() || (styled.size() == 1 && styled.getFirst().text().isEmpty())) {
            drawPlaceHolder(context, font, scale, x, y);
        }
    }

    /** The column nearest the mouse, measured as drawn (LDLib2's own count would take the room for text). */
    @Override
    public Cursor getCursorUnderMouse(double mouseX, double mouseY) {
        if (!LDLib2.isClient()) return super.getCursorUnderMouse(mouseX, mouseY);
        float relY = (float) (mouseY - contentView.getContentY() + getScrollY()) - 2;
        int line = Mth.clamp((int) Math.floor(relY / lineHeight()), 0, Math.max(0, lines.size() - 1));
        return new Cursor(line, columnAt(line, (float) (mouseX - contentView.getContentX() + getScrollX())));
    }

    @OnlyIn(Dist.CLIENT)
    private int columnAt(int line, float x) {
        int low = 0;
        int high = lines.get(line).length();
        // The first column at or right of x, then whichever of it and the one before is nearer.
        while (low < high) {
            int middle = (low + high) / 2;
            if (columnX(line, middle) < x) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        if (low > 0 && x - columnX(line, low - 1) < columnX(line, low) - x) return low - 1;
        return low;
    }

    @OnlyIn(Dist.CLIENT)
    private float columnX(int line, int column) {
        return getFont().getSplitter().stringWidth(styledLineComponent(line, 0, column)) * scale();
    }

    /** Where a column starts on a line, as drawn: its left edge, and the line's top. */
    @OnlyIn(Dist.CLIENT)
    public Vector2f columnPosition(int line, int column) {
        return new Vector2f(contentView.getContentX() - getScrollX() + columnX(line, column),
                contentView.getContentY() + line * lineHeight() - getScrollY());
    }

    private MutableComponent inFont(String text) {
        return Component.literal(text).withStyle(style -> style.withFont(getTextAreaStyle().font()));
    }

    /**
     * The gutter and the problems' underlines are drawn after the content view, which clips its own drawing to its
     * text. A line count with another digit widens the gutter from the next frame.
     */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundOverlay(GUIContext context) {
        super.drawBackgroundOverlay(context);
        var font = getFont();
        float scale = scale();
        int lines = getLines().size();
        float lineHeight = lineHeight();
        int first = Math.max(0, (int) Math.floor(getScrollY() / lineHeight));
        int last = Math.min(lines - 1, first + (int) Math.ceil(contentView.getContentHeight() / lineHeight) + 1);
        if (!numbered) {
            if (padding != TEXT_GAP) {
                padding = TEXT_GAP;
                contentView.layout(layout -> layout.paddingLeft(padding));
            }
            drawUnderlines(context, first, last);
            drawIcons(context, first, last);
            drawNotes(context, first, last);
            return;
        }
        float width = Math.max(MIN_GUTTER, MARK + font.getSplitter().stringWidth(
                inFont("9".repeat(String.valueOf(lines).length()))) * scale + 2 * NUMBER_GAP);
        if (width + TEXT_GAP != padding) {
            padding = width + TEXT_GAP;
            contentView.layout(layout -> layout.paddingLeft(padding));
        }
        float left = contentView.getPositionX();
        DrawerHelper.drawSolidRect(context.graphics, left, contentView.getPositionY(), width, contentView.getSizeHeight(), GUTTER);
        var marked = new HashSet<Integer>();
        problems.forEach(problem -> marked.add(problem.line() - 1));
        context.graphics.flush();
        context.enableScissor(left, contentView.getContentY(), width, contentView.getContentHeight());
        for (int line = first; line <= last; line++) {
            var number = inFont(String.valueOf(line + 1));
            float top = contentView.getContentY() + line * lineHeight - getScrollY();
            if (marked.contains(line)) {
                DrawerHelper.drawSolidRect(context.graphics, left + 1, top + (getTextAreaStyle().fontSize() - MARK) / 2,
                        MARK, MARK, PROBLEM);
            }
            context.pose.pushPose();
            context.pose.translate(left + width - NUMBER_GAP - font.getSplitter().stringWidth(number) * scale, top, 0);
            context.pose.scale(scale, scale, 1);
            LDLibFonts.drawText(context.graphics, font, number, 0, 0, marked.contains(line) ? PROBLEM : NUMBER, false);
            context.pose.popPose();
        }
        context.graphics.flush();
        context.disableScissor();
        drawUnderlines(context, first, last);
        drawIcons(context, first, last);
        drawNotes(context, first, last);
    }

    /** Each line's dry-run note after its end. */
    @OnlyIn(Dist.CLIENT)
    private void drawNotes(GUIContext context, int first, int last) {
        var notes = shownNotes();
        if (notes.isEmpty()) return;
        float scale = scale();
        context.graphics.flush();
        context.enableScissor(contentView.getContentX(), contentView.getContentY(), contentView.getContentWidth(),
                contentView.getContentHeight());
        for (int line = first; line <= last && line < lines.size(); line++) {
            var note = notes.get(line + 1);
            if (note == null) continue;
            var end = columnPosition(line, lines.get(line).length());
            int color = note.error() ? PROBLEM : NUMBER;
            var text = note.text();
            // A value naming an item, as a selected block's Block('minecraft:chest'), has the item's icon before it.
            var found = note.error() ? List.<ResourceStrings.At>of() : ResourceStrings.find(segments(text),
                    id -> !CompletionPopup.icon(id).isEmpty());
            float x = end.x + NOTE_GAP;
            int at = 0;
            for (var resource : found) {
                x = drawNoteText(context, text.substring(at, resource.column()), x, end.y, scale, color);
                context.pose.pushPose();
                context.pose.translate(x + ICON_GAP, end.y + (getTextAreaStyle().fontSize() - ICON) / 2, 0);
                context.pose.scale(ICON / 16, ICON / 16, 1);
                context.graphics.renderItem(CompletionPopup.icon(resource.id()), 0, 0);
                context.pose.popPose();
                x += ICON + 2 * ICON_GAP;
                at = resource.column();
            }
            drawNoteText(context, text.substring(at), x, end.y, scale, color);
        }
        context.graphics.flush();
        context.disableScissor();
    }

    /** Draws a piece of a note at {@code x}; gives where the next piece starts. */
    @OnlyIn(Dist.CLIENT)
    private float drawNoteText(GUIContext context, String text, float x, float y, float scale, int color) {
        var component = inFont(text);
        context.pose.pushPose();
        context.pose.translate(x, y, 0);
        context.pose.scale(scale, scale, 1);
        LDLibFonts.drawText(context.graphics, getFont(), component, 0, 0, color, false);
        context.pose.popPose();
        return x + getFont().getSplitter().stringWidth(component) * scale;
    }

    /** A text cut as the editor colours a line, so strings in it are found as in the script. */
    private static List<String> segments(String text) {
        var parser = new com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.SyntaxParser();
        parser.setLanguageDefinition(PythonLanguage.INSTANCE);
        return parser.parseLine(text).stream()
                .map(com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.Token::text).toList();
    }

    /** Each resource's icon, in the room before its string. */
    @OnlyIn(Dist.CLIENT)
    private void drawIcons(GUIContext context, int first, int last) {
        context.graphics.flush();
        context.enableScissor(contentView.getContentX(), contentView.getContentY(), contentView.getContentWidth(),
                contentView.getContentHeight());
        for (int line = first; line <= last && line < lines.size(); line++) {
            for (var resource : resources(line)) {
                // The quote's place counts the room before it.
                var quote = columnPosition(line, resource.column());
                context.pose.pushPose();
                context.pose.translate(quote.x - ICON_GAP - ICON, quote.y + (getTextAreaStyle().fontSize() - ICON) / 2,
                        0);
                context.pose.scale(ICON / 16, ICON / 16, 1);
                context.graphics.renderItem(CompletionPopup.icon(resource.id()), 0, 0);
                context.pose.popPose();
            }
        }
        context.graphics.flush();
        context.disableScissor();
    }

    /** A wave under each problem's columns, clamped to its line as it is now. */
    @OnlyIn(Dist.CLIENT)
    private void drawUnderlines(GUIContext context, int first, int last) {
        if (problems.isEmpty()) return;
        float scale = scale();
        float x = contentView.getContentX() - getScrollX();
        context.graphics.flush();
        context.enableScissor(contentView.getContentX(), contentView.getContentY(), contentView.getContentWidth(),
                contentView.getContentHeight());
        for (var problem : problems) {
            int line = problem.line() - 1;
            if (line < first || line > last || line >= lines.size()) continue;
            int length = lines.get(line).length();
            int from = Math.min(problem.start() - 1, length);
            // A problem with no columns, or past the line's end, still gets one column's wave.
            int to = Math.max(from + 1, Math.min(problem.end() - 1, length));
            float start = x + getFont().getSplitter().stringWidth(styledLineComponent(line, 0, from)) * scale;
            float end = to <= length ? x + getFont().getSplitter().stringWidth(styledLineComponent(line, 0, to)) * scale
                    : start + getFont().getSplitter().stringWidth(inFont(" ")) * scale;
            float base = contentView.getContentY() + line * lineHeight() - getScrollY() + getTextAreaStyle().fontSize();
            for (int step = 0; start + step < end; step++) {
                DrawerHelper.drawSolidRect(context.graphics, start + step, base + WAVE[step % WAVE.length], 1, 1,
                        PROBLEM);
            }
        }
        context.graphics.flush();
        context.disableScissor();
    }
}
