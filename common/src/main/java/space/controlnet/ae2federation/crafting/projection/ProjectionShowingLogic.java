package space.controlnet.ae2federation.crafting.projection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Set;

/**
 * What AE2 holds for a projection that shows its real provider's logic and carries no add-on markers: each call goes
 * straight to the projection, where a proxy would make it by reflection.
 */
public final class ProjectionShowingLogic implements ICraftingProvider, ProjectedProviderLogic {
    private final PatternProjection projection;

    ProjectionShowingLogic(PatternProjection projection) {
        this.projection = projection;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return projection.getAvailablePatterns();
    }

    @Override
    public int getPatternPriority() {
        return projection.getPatternPriority();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return projection.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return projection.isBusy();
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        return projection.getEmitableItems();
    }

    @Override
    public Object getLogic() {
        return projection.shownLogic();
    }
}
