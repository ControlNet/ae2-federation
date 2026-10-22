package space.controlnet.ae2federation.mixin.client;

import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import guideme.internal.screen.GuideNavBar;
import guideme.layout.LayoutContext;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
import guideme.ui.GuideUiHost;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.client.guide.NestedGuideNavRow;

/**
 * Shows every level of the guide's navigation tree in GuideME's sidebar. GuideME builds rows for the roots and their
 * direct children only, and offers no hook for its sidebar, so our section pages would hide their pages. This builds
 * a row for each node at any depth; the row mixin folds and indents the deeper ones.
 */
@Mixin(value = GuideNavBar.class, remap = false)
public abstract class GuideNavBarMixin {
    @Unique
    private static final String ae2federation$ROW_CLASS = "guideme.internal.screen.GuideNavBar$Row";

    @Unique
    @Nullable
    private static MethodHandle ae2federation$rowConstructor;

    @Shadow
    private NavigationTree navTree;

    @Shadow
    @Final
    private List<Object> rows;

    @Shadow
    @Final
    private GuideUiHost screen;

    @Unique
    private final Map<LytParagraph, Integer> ae2federation$extraIndents = new IdentityHashMap<>();

    @Shadow
    protected abstract void updateLayout();

    @Inject(method = "recreateRows", at = @At("HEAD"), cancellable = true)
    private void ae2federation$recreateNestedRows(CallbackInfo callback) {
        navTree = screen.getGuide().getNavigationTree();
        rows.clear();
        ae2federation$extraIndents.clear();
        for (var root : navTree.getRootNodes()) {
            ae2federation$addRows(root, null, 0);
        }
        updateLayout();
        callback.cancel();
    }

    @Redirect(method = "updateLayout", at = @At(value = "INVOKE",
            target = "Lguideme/document/block/LytParagraph;layout(Lguideme/layout/LayoutContext;III)Lguideme/document/LytRect;"))
    private LytRect ae2federation$indentNestedRow(LytParagraph paragraph, LayoutContext context, int x, int y,
            int width) {
        var extraIndent = ae2federation$extraIndents.getOrDefault(paragraph, 0);
        return paragraph.layout(context, x + extraIndent, y, width - extraIndent);
    }

    @Unique
    private void ae2federation$addRows(NavigationNode node, @Nullable NestedGuideNavRow parent, int depth) {
        var row = ae2federation$newRow(node, parent);
        row.ae2federation$nest(parent, depth, !node.children().isEmpty());
        ae2federation$extraIndents.put(row.ae2federation$paragraph(), row.ae2federation$extraIndent());
        rows.add(row);
        for (var child : node.children()) {
            ae2federation$addRows(child, row, depth + 1);
        }
    }

    @Unique
    private NestedGuideNavRow ae2federation$newRow(NavigationNode node, @Nullable NestedGuideNavRow parent) {
        try {
            if (ae2federation$rowConstructor == null) {
                // Row is a private inner class; this code runs inside GuideNavBar, whose lookup may reach it.
                var lookup = MethodHandles.lookup();
                var rowClass = Class.forName(ae2federation$ROW_CLASS, false, GuideNavBar.class.getClassLoader());
                ae2federation$rowConstructor = lookup.findConstructor(rowClass,
                        MethodType.methodType(void.class, GuideNavBar.class, NavigationNode.class, rowClass));
            }
            return (NestedGuideNavRow) ae2federation$rowConstructor.invoke((GuideNavBar) (Object) this, node, parent);
        } catch (Throwable e) {
            throw new IllegalStateException("Cannot build a GuideME sidebar row", e);
        }
    }
}
