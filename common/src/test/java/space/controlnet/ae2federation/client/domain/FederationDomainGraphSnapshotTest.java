package space.controlnet.ae2federation.client.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FederationDomainGraphSnapshotTest {
    @Test
    void wireFormRoundTripsUnicodeAndLargeQuantities() {
        var snapshot = new FederationDomainGraphSnapshot(23, 91,
                List.of(new FederationDomainGraphSnapshot.Node("provider:main", FederationDomainGraphNodeKind.PROVIDER,
                        "主基地超长样板供应器 9,223,372,036,854,775,807")),
                List.of(), List.of("超长高炉加工映射 -> 端点 A、B、C"));

        assertEquals(snapshot, FederationDomainGraphSnapshot.decode(snapshot.encode()));
    }

    @Test
    void duplicateNodeIdsAreRejected() {
        var node = new FederationDomainGraphSnapshot.Node("same", FederationDomainGraphNodeKind.MEMBER, "online");
        assertThrows(IllegalArgumentException.class,
                () -> new FederationDomainGraphSnapshot(1, 1, List.of(node, node), List.of(), List.of()));
    }
}
