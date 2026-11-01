package space.controlnet.ae2federation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import guideme.internal.screen.GuideNavBar;
import guideme.layout.LayoutContext;
import guideme.navigation.NavigationNode;
import guideme.navigation.NavigationTree;
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
import space.controlnet.ae2federation.client.guide.NestedGuideNavRow;
import space.controlnet.ae2federation.compat.data_energistics.DataEnergisticsGuide;

/**
 * Shows every level of the guide's navigation tree in GuideME's sidebar. GuideME builds rows for the roots and their
 * direct children only, and offers no hook for its sidebar, so our section pages would hide their pages. This builds
 * a row for each node at any depth; the row mixin folds and indents the deeper ones. Verified against GuideME 21.1.1
 * (the build) and 21.1.19, whose recreateRows, updateLayout and Row differ only in how a row's icon is read.
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

    @Unique
    private final Map<LytParagraph, Integer> ae2federation$extraIndents = new IdentityHashMap<>();

    @Shadow
    protected abstract void updateLayout();

    /**
     * GuideME's own rows, with every injection of other mods, are built first; this then nests them and adds the
     * levels below the second. Their rows are kept, so whatever another mod set on them stays.
     */
    @WrapMethod(method = "recreateRows")
    private void ae2federation$recreateNestedRows(Operation<Void> original) {
        original.call();
        // Data Energistics nests GuideME's sidebar the same way; leave the rows to it. Without our nesting, the other
        // hooks here and in the row mixin change nothing.
        if (DataEnergisticsGuide.nestsSidebar()) {
            return;
        }
        var built = new IdentityHashMap<NavigationNode, NestedGuideNavRow>();
        for (var row : rows) {
            var nested = (NestedGuideNavRow) row;
            built.put(nested.ae2federation$node(), nested);
        }
        rows.clear();
        ae2federation$extraIndents.clear();
        for (var root : navTree.getRootNodes()) {
            ae2federation$addRows(root, null, 0, built);
        }
        updateLayout();
    }

    @WrapOperation(method = "updateLayout", at = @At(value = "INVOKE",
            target = "Lguideme/document/block/LytParagraph;layout(Lguideme/layout/LayoutContext;III)Lguideme/document/LytRect;"))
    private LytRect ae2federation$indentNestedRow(LytParagraph paragraph, LayoutContext context, int x, int y,
            int width, Operation<LytRect> original) {
        var extraIndent = ae2federation$extraIndents.getOrDefault(paragraph, 0);
        return original.call(paragraph, context, x + extraIndent, y, width - extraIndent);
    }

    @Unique
    private void ae2federation$addRows(NavigationNode node, @Nullable NestedGuideNavRow parent, int depth,
            Map<NavigationNode, NestedGuideNavRow> built) {
        var row = built.get(node);
        if (row == null) {
            row = ae2federation$newRow(node, parent);
        }
        row.ae2federation$nest(parent, depth, !node.children().isEmpty());
        ae2federation$extraIndents.put(row.ae2federation$paragraph(), row.ae2federation$extraIndent());
        rows.add(row);
        for (var child : node.children()) {
            ae2federation$addRows(child, row, depth + 1, built);
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
