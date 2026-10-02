package space.controlnet.ae2federation.storage.dependency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;

final class EffectiveStorageAuthorityTest {
    @Test
    void permitsAllOnlyForAnAllFilterOfThatOperation() {
        var view = new EffectiveStorageAuthority(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll());

        assertTrue(view.permitsAll(PolicyOperation.VIEW));
        assertFalse(view.permitsAll(PolicyOperation.INSERT), "An operation the rule does not grant is not permitted");
        assertTrue(EffectiveStorageAuthority.unbounded().permitsAll(PolicyOperation.EXTRACT));
        assertFalse(EffectiveStorageAuthority.empty().permitsAll(PolicyOperation.VIEW));
    }

    @Test
    void listFiltersAlwaysCheckEachResource() {
        var denyNothing = new EffectiveStorageAuthority(Set.of(PolicyOperation.VIEW),
                new PolicyFilter(PolicyFilterMode.DENY_LIST, Set.of()));

        assertFalse(denyNothing.permitsAll(PolicyOperation.VIEW),
                "Only an ALL filter skips the per-resource check; a list filter keeps it");
    }

    @Test
    void intersectionWithAListFilterIsNoLongerAll() {
        var all = EffectiveStorageAuthority.unbounded();
        var allowNothing = new EffectiveStorageAuthority(Set.of(PolicyOperation.VIEW),
                new PolicyFilter(PolicyFilterMode.DENY_LIST, Set.of()));

        assertFalse(all.intersect(allowNothing).permitsAll(PolicyOperation.VIEW));
    }
}
