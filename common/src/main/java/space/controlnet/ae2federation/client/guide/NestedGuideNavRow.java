package space.controlnet.ae2federation.client.guide;

import guideme.document.block.LytParagraph;
import guideme.navigation.NavigationNode;
import org.jetbrains.annotations.Nullable;

/**
 * A row of GuideME's sidebar that knows how deep it sits. GuideME builds rows for the guide's roots and their direct
 * children only; {@code GuideNavBarMixin} builds rows for every level, and this lets it indent and fold the deeper
 * ones.
 */
public interface NestedGuideNavRow {
    int INDENT_PER_LEVEL = 10;

    void ae2federation$nest(@Nullable NestedGuideNavRow parent, int depth, boolean hasChildren);

    NavigationNode ae2federation$node();

    LytParagraph ae2federation$paragraph();

    /** Extra indent beyond what GuideME gives the row, zero for every row GuideME itself would build. */
    int ae2federation$extraIndent();

    boolean ae2federation$isShown();
}
