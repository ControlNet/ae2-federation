package space.controlnet.ae2federation.client.automation;

import com.lowdragmc.lowdraglib2.gui.LDLibFonts;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.CodeEditor;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.function.Candidate;
import space.controlnet.ae2federation.function.Completions;
import space.controlnet.ae2federation.function.ScriptProblems;

/**
 * The Automation tab's script editor: LDLib2's code editor with the scripts' colours ({@link PythonLanguage}) and a
 * line-number gutter in the left padding of its content view, widened to fit the numbers. Python's habits: Tab
 * indents by four spaces, a new line after a colon indents once more, and Ctrl+/ comments lines with {@code #}. It
 * only shows a program until editing is turned on. Problems are marked beside their line number and underlined.
 *
 * <p>Typing a name, a dot or a quote asks its {@link Completer} for candidates (Ctrl+Space asks anywhere);
 * they show in a {@link CompletionPopup} under the caret. Up and Down choose, Tab accepts, Enter accepts unless the
 * choice is what is typed already, Escape closes. An answer for a text or caret that has since changed is dropped.
 */
public final class ScriptEditor extends CodeEditor {
    private static final int GUTTER = 0xff17141e;
    private static final int NUMBER = 0xff6f6a7a;
    private static final int PROBLEM = 0xffff6a75;
    /** The square beside a problem's line number, and the wave under its text, one column high per step. */
    private static final float MARK = 3;
    private static final int[] WAVE = {0, 1, 2, 1};
    /** The narrowest gutter, the room on either side of its numbers, and between the gutter and the text. */
    private static final float MIN_GUTTER = 16;
    private static final float NUMBER_GAP = 3;
    private static final float TEXT_GAP = 4;
    private static final String INDENT = "    ";
    private static final String COMMENT = "# ";

    /** Completes at a place in a text (line and column from 1); the answer comes later, on the client thread. */
    public interface Completer {
        void complete(String text, int line, int column, Consumer<Completions> done);
    }

    private boolean editing;
    private @Nullable Completer completer;
    private @Nullable CompletionPopup popup;
    /** The text and caret the shown completion was asked for; accepting it needs them unchanged. */
    private @Nullable String shownText;
    private int shownLine;
    private int shownColumn;
    /** Whether the screen closed on Escape before the list opened: while it is open, Escape only closes the list. */
    private @Nullable Boolean closedOnEscape;
    private List<ScriptProblems.Mark> problems = List.of();
    /** The content view's left padding as last set: the gutter and the gap after it. */
    private float padding = -1;

    public ScriptEditor() {
        setLanguage(PythonLanguage.INSTANCE);
        setStyleManager(PythonLanguage.INSTANCE.styles());
        addClass("script-editor");
    }

    /** Whether keys change the text; the text can be selected and scrolled either way. */
    public void setEditing(boolean editing) {
        this.editing = editing;
    }

    /** The problems to mark, for the text as it is now. */
    public void setProblems(List<ScriptProblems.Mark> problems) {
        this.problems = List.copyOf(problems);
    }

    public void setCompleter(Completer completer) {
        this.completer = completer;
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
            root.addChild(popup);
        }
        shownText = text;
        shownLine = line;
        shownColumn = column;
        popup.show(completions);
        // The screen sees Escape before any element does: keep it open so Escape reaches the list.
        if (closedOnEscape == null) closedOnEscape = modular.shouldCloseOnEsc();
        modular.shouldCloseOnEsc(false);
        int at = completions.line() - 1;
        float x = contentView.getContentX() - getScrollX()
                + getFont().getSplitter().stringWidth(styledLineComponent(at, 0, completions.column() - 1)) * scale()
                - CompletionPopup.TEXT_INSET;
        float y = contentView.getContentY() + (at + 1) * lineHeight() - getScrollY() + 1;
        var position = root.worldToLocalLayoutOffset(localToWorld(new Vector2f(x, y)));
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
        if (candidate.kind() == Candidate.Kind.VALUE && from > 0) {
            char quote = text.charAt(from - 1);
            if ((quote == '"' || quote == '\'') && (to >= text.length() || text.charAt(to) != quote)) insert += quote;
        }
        pushHistory();
        lines.set(line, text.substring(0, from) + insert + text.substring(to));
        onRawLinesUpdated();
        setCursor(line, from + insert.length());
        hideCompletion();
        focus();
    }

    /** A name, a dot or a quote asks; a colon only goes on with a list of resources (inside an ID). */
    private boolean asksForCompletion(char typed) {
        if (typed == ':') {
            var completions = isCompleting() ? popup.completions() : null;
            return completions != null && completions.candidates().get(0).kind() == Candidate.Kind.VALUE;
        }
        return Character.isLetterOrDigit(typed) || typed == '_' || typed == '.' || typed == '"' || typed == '\'';
    }

    @Override
    protected void onCharTyped(UIEvent event) {
        boolean asks = asksForCompletion(event.codePoint);
        super.onCharTyped(event);
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
        super.onKeyDown(event);
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
        float width = Math.max(MIN_GUTTER, MARK + font.getSplitter().stringWidth(
                inFont("9".repeat(String.valueOf(lines).length()))) * scale + 2 * NUMBER_GAP);
        if (width + TEXT_GAP != padding) {
            padding = width + TEXT_GAP;
            contentView.layout(layout -> layout.paddingLeft(padding));
        }
        float left = contentView.getPositionX();
        DrawerHelper.drawSolidRect(context.graphics, left, contentView.getPositionY(), width, contentView.getSizeHeight(), GUTTER);
        float lineHeight = lineHeight();
        int first = Math.max(0, (int) Math.floor(getScrollY() / lineHeight));
        int last = Math.min(lines - 1, first + (int) Math.ceil(contentView.getContentHeight() / lineHeight) + 1);
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
