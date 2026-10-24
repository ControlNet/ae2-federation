package space.controlnet.ae2federation.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import guideme.navigation.NavigationNode;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.client.guide.NestedGuideNavRow;

/**
 * Lets a sidebar row below the second level fold with every row above it, and moves the fold arrow of a nested
 * parent row in with its text. Rows GuideME itself builds keep their look. Both hooks adjust GuideME's result, so
 * another mod's hook on the same method still applies. Verified against GuideME 21.1.1 (the build) and 21.1.19.
 */
@Mixin(targets = "guideme.internal.screen.GuideNavBar$Row", remap = false)
public abstract class GuideNavBarRowMixin implements NestedGuideNavRow {
    @Shadow
    @Final
    private NavigationNode node;

    @Shadow
    @Final
    private LytParagraph paragraph;

    @Shadow
    private boolean hasChildren;

    @Unique
    @Nullable
    private NestedGuideNavRow ae2federation$parent;

    @Unique
    private int ae2federation$depth;

    @Shadow
    public abstract boolean isVisible();

    @Override
    public void ae2federation$nest(@Nullable NestedGuideNavRow parent, int depth, boolean hasChildren) {
        this.ae2federation$parent = parent;
        this.ae2federation$depth = depth;
        this.hasChildren = hasChildren;
    }

    @Override
    public NavigationNode ae2federation$node() {
        return node;
    }

    @Override
    public LytParagraph ae2federation$paragraph() {
        return paragraph;
    }

    @Override
    public int ae2federation$extraIndent() {
        // GuideME indents a child 10 and a parent 7 whatever their depth; each level below the roots adds 10 more.
        var levelsBelowGuideMe = hasChildren ? ae2federation$depth : ae2federation$depth - 1;
        return Math.max(0, levelsBelowGuideMe) * INDENT_PER_LEVEL;
    }

    @Override
    public boolean ae2federation$isShown() {
        return isVisible();
    }

    @ModifyReturnValue(method = "isVisible()Z", at = @At("RETURN"))
    private boolean ae2federation$hideUnderFoldedAncestor(boolean visible) {
        return visible && (ae2federation$parent == null || ae2federation$parent.ae2federation$isShown());
    }

    @ModifyReturnValue(method = "getBounds", at = @At("RETURN"))
    private LytRect ae2federation$moveNestedArrow(LytRect bounds) {
        // The fold arrow is drawn at the left of these bounds.
        if (hasChildren && ae2federation$depth > 0) {
            var indent = ae2federation$extraIndent();
            return new LytRect(bounds.x() + indent, bounds.y(), bounds.width() - indent, bounds.height());
        }
        return bounds;
    }
}
