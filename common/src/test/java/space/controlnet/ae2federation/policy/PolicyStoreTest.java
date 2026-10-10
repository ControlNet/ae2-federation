package space.controlnet.ae2federation.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

final class PolicyStoreTest {
    private static final NetworkId NETWORK_A = network(1);
    private static final NetworkId NETWORK_B = network(2);
    private static final NetworkId NETWORK_C = network(3);
    private static final PolicyKey A_USES_B = new PolicyKey(NETWORK_A, NETWORK_B, PolicyCapability.STORAGE);
    private static final PolicyKey B_USES_A = new PolicyKey(NETWORK_B, NETWORK_A, PolicyCapability.STORAGE);

    @Test
    void storesOnlyConfiguredDirectionalRulesWhenMostNetworksHaveNoPolicy() {
        // Given
        var store = new PolicyStore();

        // When
        store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true)));
        store.edit(new PolicyEdit(new PolicyKey(NETWORK_C, NETWORK_A, PolicyCapability.CRAFTING),
                PolicyRevision.NONE, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))));

        // Then
        assertEquals(2, store.storedEntryCount());
        assertEquals(2, store.configuredCount());
        assertTrue(store.configured(B_USES_A).isEmpty());
        assertFalse(A_USES_B.equals(B_USES_A));
    }

    @Test
    void advancesAuthoritativeRevisionOnlyForAcceptedEdits() {
        // Given
        var store = new PolicyStore();
        var created = accepted(store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));

        // When
        var updated = accepted(store.edit(new PolicyEdit(A_USES_B, created.revision(), storageRule(false))));

        // Then
        assertEquals(new PolicyRevision(1), created.revision());
        assertEquals(new PolicyRevision(2), updated.revision());
        assertEquals(updated, store.configured(A_USES_B).orElseThrow());
    }

    @Test
    void rejectsStaleWriteWithoutChangingNewerRule() {
        // Given
        var store = new PolicyStore();
        var first = accepted(store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));
        var second = accepted(store.edit(new PolicyEdit(A_USES_B, first.revision(), storageRule(false))));

        // When
        var stale = store.edit(new PolicyEdit(A_USES_B, first.revision(), storageRule(true)));

        // Then
        var rejected = assertInstanceOf(PolicyMutationResult.Rejected.class, stale);
        assertEquals(PolicyRejection.STALE_REVISION, rejected.reason());
        assertEquals(second, store.configured(A_USES_B).orElseThrow());
        assertEquals(2, store.nextRevision().value());
    }

    @Test
    void tombstonePreventsStaleEditFromResurrectingDeletedRule() {
        // Given
        var store = new PolicyStore();
        var configured = accepted(store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));
        var deleted = assertInstanceOf(PolicyMutationResult.Accepted.class,
                store.delete(new PolicyDelete(A_USES_B, configured.revision())));

        // When
        var stale = store.edit(new PolicyEdit(A_USES_B, configured.revision(), storageRule(true)));

        // Then
        assertInstanceOf(PolicyMutationResult.Rejected.class, stale);
        assertTrue(store.configured(A_USES_B).isEmpty());
        assertEquals(deleted.revision(), store.revision(A_USES_B));
        assertEquals(1, store.tombstoneCount());
    }

    @Test
    void staleEditCannotReactivateDisabledRule() {
        // Given
        var store = new PolicyStore();
        var enabled = accepted(store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));
        var disabled = accepted(store.edit(new PolicyEdit(A_USES_B, enabled.revision(), storageRule(false))));

        // When
        var stale = store.edit(new PolicyEdit(A_USES_B, enabled.revision(), storageRule(true)));

        // Then
        assertInstanceOf(PolicyMutationResult.Rejected.class, stale);
        assertFalse(store.configured(A_USES_B).orElseThrow().rule().enabled());
        assertEquals(disabled.revision(), store.revision(A_USES_B));
    }

    @Test
    void editAllAppliesEveryEditWithItsOwnRevision() {
        // Given
        var store = new PolicyStore();
        var crafting = new PolicyKey(NETWORK_A, NETWORK_B, PolicyCapability.CRAFTING);

        // When
        var result = store.editAll(java.util.List.of(
                new PolicyEdit(crafting, PolicyRevision.NONE, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))),
                new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));

        // Then: the result is the first edit's, and each key advanced the watermark once.
        assertEquals(new PolicyRevision(1), accepted(result).revision());
        assertEquals(crafting, accepted(result).key());
        assertEquals(new PolicyRevision(2), store.revision(A_USES_B));
        assertEquals(2, store.nextRevision().value());
    }

    @Test
    void editAllWithOneStaleEditAppliesNone() {
        // Given
        var store = new PolicyStore();
        var crafting = new PolicyKey(NETWORK_A, NETWORK_B, PolicyCapability.CRAFTING);
        var storage = accepted(store.edit(new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true))));

        // When: the storage edit expects no rule, but one exists.
        var result = store.editAll(java.util.List.of(
                new PolicyEdit(crafting, PolicyRevision.NONE, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))),
                new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(false))));

        // Then
        var rejected = assertInstanceOf(PolicyMutationResult.Rejected.class, result);
        assertEquals(PolicyRejection.STALE_REVISION, rejected.reason());
        assertEquals(storage.revision(), rejected.currentRevision());
        assertTrue(store.configured(crafting).isEmpty());
        assertEquals(storage, store.configured(A_USES_B).orElseThrow());
        assertEquals(1, store.nextRevision().value());
    }

    @Test
    void editAllRefusesTheSameKeyTwice() {
        var store = new PolicyStore();
        var edit = new PolicyEdit(A_USES_B, PolicyRevision.NONE, storageRule(true));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> store.editAll(java.util.List.of(edit, edit)));
        assertEquals(0, store.storedEntryCount());
    }

    @Test
    void storageRuleDefaultsReexportOff() {
        // Given
        var defaults = PolicyRule.storageDefaults();

        // When
        var reexport = defaults.allowReexport();

        // Then
        assertFalse(reexport);
    }

    private static PolicyRecord.Configured accepted(PolicyMutationResult result) {
        return assertInstanceOf(PolicyRecord.Configured.class,
                assertInstanceOf(PolicyMutationResult.Accepted.class, result).record());
    }

    private static PolicyRule storageRule(boolean enabled) {
        return new PolicyRule(enabled, Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT),
                PolicyFilter.allowAll(), false);
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
