package space.controlnet.ae2federation.mixin.client;

import guideme.document.LytRect;
import guideme.document.block.LytParagraph;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.client.guide.NestedGuideNavRow;

/**
 * Lets a sidebar row below the second level fold with every row above it, and moves the fold arrow of a nested
 * parent row in with its text. Rows GuideME itself builds keep their look.
 */
@Mixin(targets = "guideme.internal.screen.GuideNavBar$Row", remap = false)
public abstract class GuideNavBarRowMixin implements NestedGuideNavRow {
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

    @Inject(method = "isVisible()Z", at = @At("HEAD"), cancellable = true)
    private void ae2federation$hideUnderFoldedAncestor(CallbackInfoReturnable<Boolean> callback) {
        if (ae2federation$parent != null && !ae2federation$parent.ae2federation$isShown()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "getBounds", at = @At("RETURN"), cancellable = true)
    private void ae2federation$moveNestedArrow(CallbackInfoReturnable<LytRect> callback) {
        // The fold arrow is drawn at the left of these bounds.
        if (hasChildren && ae2federation$depth > 0) {
            var bounds = callback.getReturnValue();
            var indent = ae2federation$extraIndent();
            callback.setReturnValue(new LytRect(bounds.x() + indent, bounds.y(), bounds.width() - indent,
                    bounds.height()));
        }
    }
}
