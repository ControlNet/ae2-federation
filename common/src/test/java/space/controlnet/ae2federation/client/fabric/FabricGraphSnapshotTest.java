package space.controlnet.ae2federation.client.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class FabricGraphSnapshotTest {
    @Test
    void wireFormRoundTripsUnicodeAndLargeQuantities() {
        var snapshot = new FabricGraphSnapshot(23, 91,
                List.of(new FabricGraphSnapshot.Node("provider:main", FabricGraphNodeKind.PROVIDER,
                        "主基地超长样板供应器 9,223,372,036,854,775,807")),
                List.of(), List.of("超长高炉加工映射 -> 端点 A、B、C"));

        assertEquals(snapshot, FabricGraphSnapshot.decode(snapshot.encode()));
    }

    @Test
    void duplicateNodeIdsAreRejected() {
        var node = new FabricGraphSnapshot.Node("same", FabricGraphNodeKind.MEMBER, "online");
        assertThrows(IllegalArgumentException.class,
                () -> new FabricGraphSnapshot(1, 1, List.of(node, node), List.of(), List.of()));
    }
}
